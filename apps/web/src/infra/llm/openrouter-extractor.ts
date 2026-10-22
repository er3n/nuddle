// TaskExtractor over OpenRouter's OpenAI-compatible chat completions API with structured output.
// Provider and model: docs/architecture.md (LLM), open-questions#16. Env: OPENROUTER_API_KEY,
// OPENROUTER_MODEL.
import { z } from "zod";
import { RELATIONSHIP_WORDS, type Extraction, type ExtractionInput } from "../../domain/extraction";
import { repeatRuleOf } from "../../domain/repeat";
import type { ExchangeSink, TaskExtractor } from "../../domain/ports";
import { toLocalIso } from "../../domain/time";
import { completeJson, type OpenRouterOptions } from "./openrouter";

const extractionSchema = z.object({
  recipient: z.string().nullable(),
  title: z.string().nullable(),
  detail: z.string().nullable(),
  deadline: z.string().nullable(),
  timing: z.enum(["at", "by"]),
  missAfterMinutes: z.number().nullable(),
  leadMinutes: z.number().nullable().optional(),
  finishMode: z.enum(["done", "photo", "notice"]),
  missing: z.array(z.enum(["recipient", "title", "deadline"])),
  // Required in the JSON schema; optional here so an answer from before NUD-52 still parses.
  reply: z.string().nullable().optional(),
  // Optional for the same reason (NUD-77); checked by repeatRuleOf before use.
  repeat: z
    .object({
      every: z.enum(["day", "week", "weekdays"]),
      days: z.array(z.number()).nullable(),
      time: z.string(),
    })
    .nullable()
    .optional(),
  // Optional for the same reason (NUD-128); resolved and checked by the server before use.
  contact: z
    .object({ name: z.string(), action: z.enum(["call", "message"]) })
    .nullable()
    .optional(),
});

const nullableString = { type: ["string", "null"] };

const jsonSchema = {
  type: "object",
  additionalProperties: false,
  required: [
    "recipient",
    "title",
    "detail",
    "deadline",
    "timing",
    "missAfterMinutes",
    "leadMinutes",
    "finishMode",
    "missing",
    "reply",
    "repeat",
    "contact",
  ],
  properties: {
    recipient: nullableString,
    title: nullableString,
    detail: nullableString,
    deadline: nullableString,
    timing: { type: "string", enum: ["at", "by"] },
    missAfterMinutes: { type: ["integer", "null"] },
    leadMinutes: { type: ["integer", "null"] },
    finishMode: { type: "string", enum: ["done", "photo", "notice"] },
    missing: { type: "array", items: { type: "string", enum: ["recipient", "title", "deadline"] } },
    reply: nullableString,
    repeat: {
      type: ["object", "null"],
      additionalProperties: false,
      required: ["every", "days", "time"],
      properties: {
        every: { type: "string", enum: ["day", "week", "weekdays"] },
        days: { type: ["array", "null"], items: { type: "integer" } },
        time: { type: "string" },
      },
    },
    contact: {
      type: ["object", "null"],
      additionalProperties: false,
      required: ["name", "action"],
      properties: {
        name: { type: "string" },
        action: { type: "string", enum: ["call", "message"] },
      },
    },
  },
};

/** "eşim, karım, ... (spouse); annem, babam (parent); ...", from the domain's word list. */
const RELATIONSHIPS = Object.entries(RELATIONSHIP_WORDS)
  .filter(([, words]) => words.length > 0)
  .map(([type, words]) => `${words.join(", ")} (${type})`)
  .join("; ");

const RULES = `You turn one sentence (usually Turkish) into a reminder task for a family reminder app.
Return only JSON that matches the schema.

reply
- null for anything that asks for something to be done or remembered, even when it is short or unclear ("ekmek", "yarın", "eren"): that is a task with fields missing.
- Things the app cannot do get a reply that says so and shows the one-off form, in Turkish, one sentence:
  - a repeat the app cannot do: within a day ("her saat başı", "dakikada bir", "3 kere") or more than once a day ("hem sabah hem akşam"): "Günde en fazla bir kez tekrarlayan görev kurabiliyorum; her saat için ayrı yaz."
  - two or more times in one sentence ("hem sabah hem akşam"): "Bir mesajda tek görev kurabiliyorum; her saat için ayrı yaz."
  - a complaint or feedback about the app ("dün alarm çalmadı"): "Üzgünüm. Hangi dürtmenin çalmadığını zil ekranında görebilirsin; bana yeni bir görev yazabilirsin."
- Only when the text is not a task at all (a greeting, thanks, small talk, a question about the app or about you): one short Turkish sentence answering it as the app, first person singular, "sen", no emoji, and point to writing a task. Then every other field is null, missing is empty, timing "by", finishMode "done".
- Examples: "merhaba" → "Merhaba! Kime ne hatırlatayım? Mesela: Eren yarın 9'dan önce ekmek alsın." "bu uygulama ne işe yarıyor" → "Birine ya da kendine bir iş yazarsın, iş bitene kadar zamanında dürterim." "teşekkürler" → "Rica ederim, başka ne hatırlatayım?"

The sender's formats line gives their language, the first day of their week and their clock. Write title, detail and reply in that language (tr-TR: Turkish). "bu hafta" ends on the last day of the sender's week, "haftaya" and "gelecek hafta" are the week after it. With a 24-hour clock an hour 13-23 is that hour; an hour 1-12 follows the deadline rules below.

The text may be a speech transcript (4h): no punctuation, numbers and times in words ("dokuzda", "sekiz buçukta", "yarım saat sonra", "iki bin beş yüz"), filler words ("şey", "hmm") to ignore.

recipient
- "self" when the sender writes to themselves: first person ("ödeyeyim"), "bana", "beni", or a plain imperative with no person named ("kuru temizlemeyi al", "faturayı öde"). A sentence that names nobody is "self", never null.
- Otherwise exactly one name from the connection list, spelled as in the list. Names are often written in lowercase and some are also ordinary words ("can", "deniz", "umut", "ayşe"): a word that matches a connection's name, usually the first word, is that person.
- A relationship word is how the sender names a connection: ${RELATIONSHIPS}; also with a suffix ("anneme", "eşimin"). It means the single connection with that relationship: write that connection's name, never the word. When no connection or more than one has that relationship ("annem" with two parents, "oğlum" with two children), use null and add "recipient" to missing; never guess from what a name sounds like.
- The recipient is the person who must do the task (the subject of the verb). A person who is only the object ("annemi ara", "Eren'e yaz", "anneme 500 lira gönder") is not the recipient.
- "X'e söyle", "X'e hatırlat", "X'e ilet" followed by what X must do: X is the recipient and the task is that thing ("eşime hatırlat yarın ekmek alsın": the spouse, title "Ekmek al").
- If the person is not in the list, or more than one connection could be meant ("çocuklar", "biri"), use null and add "recipient" to missing.

title
- A short Turkish imperative, first letter capitalized, without the person, amount or time. Example: "Temizlikçi için para çek".

detail
- Amounts and specifics not in the title, Turkish formatting: "1.000 TL", "250 TL". Numbers in words become digits ("bin lira" is "1.000 TL"). Otherwise null.

deadline
- ISO 8601 in the sender's time zone with its UTC offset, seconds 00, e.g. "2026-10-12T09:00:00+03:00".
- Parts of the day: sabah 09:00, öğlen 12:00, öğleden sonra 15:00, akşam 19:00, gece 22:00.
- "9dan önce", "10a kadar": that hour. An hour 1-6 with no part of day is afternoon (13:00-18:00). An hour 7-12 with no part of day is the morning hour when that is still ahead today, or when the sentence is about the morning (kalk, uyandır, okul, servis, sabah); otherwise, if the evening hour (19:00-00:00) is still ahead today and fits the sentence (yat, uyu, şurup, yemek, akşam), it is that evening hour; otherwise tomorrow morning. "10da" at 08:00 is 10:00 today; "8de şurubunu içsin" at 19:30 is 20:00 today; "11de yat" at 22:30 is 23:00 today; "10da" at 15:00 is 22:00 today only if the sentence is about the evening, else tomorrow 10:00.
- A day with no time means 19:00 that day. A time with no day means today if still ahead, otherwise tomorrow.
- "bu akşam" (sometime tonight) is 22:00; "akşama kadar" is 19:00. "yatmadan (önce)" is 22:30. "eve gelince", "gelirken", "işten çıkınca" is 18:30. "X kapanmadan" is 18:45 unless the sentence says when it closes. "gün içinde" is 18:00 today. "hafta sonu" is Sunday 19:00. All of these are "by".
- "X ile Y arası" (a window): timing "at" with deadline X and missAfterMinutes the minutes from X to Y.
- "N dakika içinde" with N under 15 means now: timing "at", deadline now plus one minute, missAfterMinutes 15.
- An hour 5 or 6 with no part of day is the morning (05:00, 06:30) when the sentence is a morning thing: kalk, uyandır, servis, okul, uçak, kargoyu kapıya koy; otherwise the afternoon.
- After midnight, before 05:00, the sender has not slept yet: "yarın" ("yarın sabah", "yarın akşam") means the day that starts when they wake up, which is today's date. "Yarın sabah 9" at 00:40 on the 12th is the 12th 09:00, not the 13th.
- Relative times ("2 saat içinde", "20 dakika sonra") count from the sender's local time.
- A weekday means its next occurrence after today.
- No time and no day at all: null and add "deadline" to missing.

timing
- "by" when the time is a limit the task must be done before: "9'a kadar", "9dan önce", "akşama kadar", "2 saat içinde", "cumaya kadar", "yarın" with no hour.
- Also "by" when the hour is when something else happens and the task must be done before it: "en geç 7de" (even "en geç 7de uyandır": they want to be up by then, so the pokes come before), "7de işe gitmeden önce", "6da çıkmadan önce", "servis gelmeden", "okula gitmeden". The deadline is that hour.
- "at" when the time is the moment to do it or to be reminded: "2 dakika sonra", "9'da", "saat 8de", "beni 7de uyandır", "... hatırlat". Then deadline is that moment.
- A part of day with no hour ("sabah", "akşam", "öğlen") is "by", never "at": "sabah süt alsın" is by 09:00.
- If unsure, "by".

leadMinutes
- Only for "at": how long before the moment the person must start getting ready or travelling, so the first poke comes that much earlier. 0 for something done on the spot (drink water, turn the oven off, take a pill, make a call, a wake-up). 10-15 to leave home for somewhere near (school, course, market, bus). 30-60 for an appointment or an office in town (dentist, bank, service, a meeting at the school). 120-180 for a flight or an intercity train. When the sentence says it ("yürüyüş 20 dakika sürüyor", "17.30da alması gerektiğini 16.45te hatırlat": the moment is 17:30 and the lead 45), use that.
- null for "by".

missAfterMinutes
- Only for "at": how many minutes after the moment the task still makes sense, from what the task is. A glass of water, a short call or a chore at a relative time ("1 saat sonra kurutucuya at", "5 dakika sonra çaya bak"): 30. A medicine: 120. Leaving the house, an appointment, a wake-up: 15. Paying a bill with a day to do it: until the end of the day, at most 720.
- null for "by".

finishMode
- "photo" for money: withdrawing, sending, paying bills, rent, dues, taxes, or any task with a money amount.
- "notice" for a message to pass on with nothing for the person to do (NUD-79): "X'e seni seviyorum de", "baban seni alabilir de", "iyi ki doğdun de", "yemek hazır de" when it only informs. Then the title is the message itself as the person will read it, capitalized, without the "de" ("Seni seviyorum", "Baban seni alabilir", "Yemek hazır"), timing "at"; deadline is the moment the message goes out: the time given ("3te" is 15:00 today, or tomorrow when it has passed), else the next five-minute mark after now (at 19:00 that is 19:05), and the app sends it at once; never null, never a question; missAfterMinutes 60, leadMinutes 0. When the message asks the person to do something ("ona uyuması gerektiğini ilet", "yemeğe gelsin de"), that is their task, finishMode "done": title the action ("Uyu", "Yemeğe gel"). A question to a person ("dışarıdan bir şey istiyor musun", "erene akşam ne yiyelim diye sor", "eve ne zaman geliyorsun") is also "notice" (NUD-116): the app has no chat, so the question is passed on and they answer by calling. The title is the question as they will read it, capitalized, with a question mark ("Dışarıdan bir şey istiyor musun?"); never turn it into a task such as "Dışarıdan bir şey al", and never ask for a deadline.
- Otherwise "done".

missing
- The fields you could not determine. Empty when the task is complete.

repeat
- A task that comes back on a schedule (NUD-77): "her gün 9da ilacını alsın" {every "day", days null, time "09:00"}; "her salı çöpü çıkarsın" {every "week", days [2], time}; "pazartesi ve perşembe" days [1, 4]; "hafta içi her sabah 7de kalksın" {every "weekdays", time "07:00"}; "her sabah" is every day at 09:00, "her akşam" at 19:00 unless an hour is given. ISO weekdays: 1 pazartesi, 2 salı, 3 çarşamba, 4 perşembe, 5 cuma, 6 cumartesi, 7 pazar. The deadline is then the first occurrence: the next matching time after now, which is today when that time is still ahead today ("her gün 9da" at 08:00 is today 09:00; at 10:00 it is tomorrow 09:00). Everything else (title, timing, finishMode, leadMinutes) as for a one-off.
- null for a one-off task, which is the usual case. Only the sentence itself makes a task repeat, with a word like "her gün", "her sabah", "her akşam", "her hafta", "her salı", "hafta içi", "her iş günü". A day named once ("çarşamba 9da", "cumartesi"), "yarın", "bugün", a school run, a pickup or a bill said once: a one-off, repeat null, even when such a thing happens every day in real life. Never more than once a day: those get a reply (see below).

contact
- When the task is to call or to message one specific person (NUD-128): {name, action}. action "call" for ara, arasın, telefon et; "message" for mesaj at, yaz, mesaj gönder, whatsapp'tan yaz.
- name is that person: exactly one name from the connection list, spelled as in the list ("Sibel'e mesaj at" is "Sibel"; a relationship word, "annemi ara", "eşine yaz", means the single connection with that relationship), or "sender" when it is the writer ("eren beni arasın", "bana mesaj atsın").
- null when the person is not in the list (öğretmen, tesisatçı, doktor, a name not in the list), when more than one connection could be meant, when the text gives a phone number instead, when calling or messaging is not what the task asks ("anneme 500 gönder", "babamı doktora götür"), and for finishMode "notice".
- The contact never changes the recipient: "annemi ara" is "self" with contact the parent.
- The contact never sets the time: the deadline follows the deadline rules above. A call or a message with a day or a time has that deadline ("çarşamba akşamı annemi ara" is Wednesday 19:00, "yarın sibel'e yaz" tomorrow 19:00); only one with neither ("annemi ara", "Sibel'e mesaj at") has deadline null and "deadline" in missing, never "now".

Worked examples (sender's local time Sunday 21:00, connections Eren: spouse, Ayşe: parent)
- "eşime hatırlat yarın 8de ilacını alsın": eşim means Eren and the task is his: recipient Eren, title "İlacını al", tomorrow 08:00, timing "at".
- "babamı yarın ara": the sender calls; babam is only the object. recipient "self", deadline tomorrow 19:00, timing "by"; no connection is a father here, so contact null.
- "annemi ara": recipient "self", title "Annemi ara", no time so deadline null and "deadline" in missing; contact {name "Ayşe", action "call"}.
- "eren yarın akşam beni arasın": recipient Eren, title "Beni ara", tomorrow 19:00, timing "by"; contact {name "sender", action "call"}.
- "yarın erene mesaj at": recipient "self" (Eren is the object), title "Eren'e mesaj at", tomorrow 19:00, timing "by"; contact {name "Eren", action "message"}.
- "annem 11de ilacını içsin": annem means Ayşe; 11 has no part of day, so 11:00, which has passed today, so tomorrow 11:00; timing "at".
- "eren saat 3te markete gitsin": 3 is afternoon, 15:00, passed today, so tomorrow 15:00; timing "at", leadMinutes 15.
- "yarın çiçekleri sula": nobody named, so recipient "self"; tomorrow 19:00, timing "by".
- "1 saat sonra parkmetreyi uzat": "self", now plus one hour, timing "at", leadMinutes 0, missAfterMinutes 15.
- "eren yarın 14.30da diş randevusu var": recipient Eren, title "Diş randevusuna git", 14:30 tomorrow, timing "at", leadMinutes 45, missAfterMinutes 15.
- "erene yemek hazır de": nothing to do but come: recipient Eren, title "Yemeğe gel", now plus 5 minutes, timing "at".
- "eşim sabah 6da kalkacak ona uyuması gerektiğini ilet": the 6 is why, not when; Sibel should sleep now: recipient Sibel, title "Uyu", now plus 5 minutes, timing "at".
- "annem her gün 9da ilacını alsın": recipient Ayşe, title "İlacını al", repeat {every "day", days null, time "09:00"}; the first occurrence is tomorrow 09:00 (today's has passed), timing "at".
- "eren her salı akşam 7de çöpü çıkarsın": recipient Eren, repeat {every "week", days [2], time "19:00"}; the first occurrence is the next Tuesday 19:00, timing "at".
- "öğlen öğretmeni ara": nobody named, so "self"; 12:00 today, timing "at", missAfterMinutes 30.
- "eren yarın 6.30da kargoyu kapıya koysun": a morning thing, so 06:30 tomorrow, timing "at", leadMinutes 0, missAfterMinutes 15.
- "eren bu akşam annesini arasın" at 18:00: by 22:00, timing "by".
- At 00:40 on Monday: "eren yarın sabah 9da ekmek alsın": the sender has not slept, so yarın is Monday; Monday 09:00, timing "at".`;

function weekday(t: Date, timeZone: string): string {
  return new Intl.DateTimeFormat("en-US", { timeZone, weekday: "long" }).format(t);
}

export function buildPrompt(input: ExtractionInput): string {
  const connections =
    input.connections.length === 0
      ? "(none)"
      : input.connections.map((c) => `- ${c.name}: ${c.relationship}`).join("\n");
  return [
    `Sender: ${input.senderName}`,
    `Sender's local time: ${toLocalIso(input.now, input.timeZone)} (${weekday(input.now, input.timeZone)}), time zone ${input.timeZone}`,
    `Sender's formats: language ${input.formats.locale}, week starts ${input.formats.weekStart === "monday" ? "Monday" : "Sunday"}, ${input.formats.hour24 ? "24-hour" : "12-hour"} clock`,
    `Sender's connections (name: relationship, the relationship is what the person is to the sender):`,
    connections,
    ``,
    `Text: ${input.text}`,
  ].join("\n");
}

export class OpenRouterExtractor implements TaskExtractor {
  constructor(private readonly options: OpenRouterOptions) {}

  async extract(input: ExtractionInput, onExchange?: ExchangeSink): Promise<Extraction> {
    // The sender waits for this call: no thinking (NUD-52).
    const json = await completeJson(
      { ...this.options, reasoning: false },
      {
        ...(onExchange ? { onExchange } : {}),
        system: RULES,
        user: buildPrompt(input),
        name: "task_extraction",
        schema: jsonSchema,
      },
    );
    const parsed = extractionSchema.parse(json);
    // The rule as the domain keeps it (days cleaned, the time checked), or none.
    return { ...parsed, repeat: repeatRuleOf(parsed.repeat) };
  }
}
