// TaskExtractor over OpenRouter's OpenAI-compatible chat completions API with structured output.
// Provider and model: docs/architecture.md (LLM), open-questions#16. Env: OPENROUTER_API_KEY,
// OPENROUTER_MODEL.
import { z } from "zod";
import type { Extraction, ExtractionInput } from "../../domain/extraction";
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
  finishMode: z.enum(["done", "photo"]),
  missing: z.array(z.enum(["recipient", "title", "deadline"])),
  // Required in the JSON schema; optional here so an answer from before NUD-52 still parses.
  reply: z.string().nullable().optional(),
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
  ],
  properties: {
    recipient: nullableString,
    title: nullableString,
    detail: nullableString,
    deadline: nullableString,
    timing: { type: "string", enum: ["at", "by"] },
    missAfterMinutes: { type: ["integer", "null"] },
    leadMinutes: { type: ["integer", "null"] },
    finishMode: { type: "string", enum: ["done", "photo"] },
    missing: { type: "array", items: { type: "string", enum: ["recipient", "title", "deadline"] } },
    reply: nullableString,
  },
};

const RULES = `You turn one sentence (usually Turkish) into a reminder task for a family reminder app.
Return only JSON that matches the schema.

reply
- null for anything that asks for something to be done or remembered, even when it is short or unclear ("ekmek", "yarın", "eren"): that is a task with fields missing.
- Things the app cannot do get a reply that says so and shows the one-off form, in Turkish, one sentence:
  - a repeat ("her gün", "her salı", "her saat başı", "3 kere", "dakikada bir"): "Tekrarlayan görev henüz yok. Tek seferlik yazarsan kurarım: 'annem yarın 9da ilacını alsın'."
  - two or more times in one sentence ("hem sabah hem akşam"): "Bir mesajda tek görev kurabiliyorum; her saat için ayrı yaz."
  - a message to pass on with nothing for the person to do ("X'e seni seviyorum de", "baban seni alabilir de", "yemek hazır de" when it only informs): "Mesaj iletmiyorum, sadece görev. Birine yapması gereken bir şey yazarsan dürterim." But when the message asks the person to do something ("ona uyuması gerektiğini ilet", "yemeğe gelsin de"), that is their task: title the action ("Uyu", "Yemeğe gel").
  - a complaint or feedback about the app ("dün alarm çalmadı"): "Üzgünüm. Hangi dürtmenin çalmadığını zil ekranında görebilirsin; bana yeni bir görev yazabilirsin."
- Only when the text is not a task at all (a greeting, thanks, small talk, a question about the app or about you): one short Turkish sentence answering it as the app, first person singular, "sen", no emoji, and point to writing a task. Then every other field is null, missing is empty, timing "by", finishMode "done".
- Examples: "merhaba" → "Merhaba! Kime ne hatırlatayım? Mesela: Eren yarın 9'dan önce ekmek alsın." "bu uygulama ne işe yarıyor" → "Birine ya da kendine bir iş yazarsın, iş bitene kadar zamanında dürterim." "teşekkürler" → "Rica ederim, başka ne hatırlatayım?"

The text may be a speech transcript (4h): no punctuation, numbers and times in words ("dokuzda", "sekiz buçukta", "yarım saat sonra", "iki bin beş yüz"), filler words ("şey", "hmm") to ignore.

recipient
- "self" when the sender writes to themselves: first person ("ödeyeyim"), "bana", "beni", or a plain imperative with no person named ("kuru temizlemeyi al", "faturayı öde"). A sentence that names nobody is "self", never null.
- Otherwise exactly one name from the connection list, spelled as in the list. Names are often written in lowercase and some are also ordinary words ("can", "deniz", "umut", "ayşe"): a word that matches a connection's name, usually the first word, is that person. A relationship word ("annem", "eşim", "babam") means the single connection with that relationship.
- The recipient is the person who must do the task (the subject of the verb). A person who is only the object ("annemi ara", "Eren'e yaz") is not the recipient.
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
- "photo" for money: withdrawing, sending, paying bills, rent, dues, taxes, or any task with a money amount. Otherwise "done".

missing
- The fields you could not determine. Empty when the task is complete.

Worked examples (sender's local time Sunday 21:00, connections Eren: spouse, Ayşe: parent)
- "babamı yarın ara": the sender calls; babam is only the object. recipient "self", deadline tomorrow 19:00, timing "by".
- "annem 11de ilacını içsin": annem means Ayşe; 11 has no part of day, so 11:00, which has passed today, so tomorrow 11:00; timing "at".
- "eren saat 3te markete gitsin": 3 is afternoon, 15:00, passed today, so tomorrow 15:00; timing "at", leadMinutes 15.
- "yarın çiçekleri sula": nobody named, so recipient "self"; tomorrow 19:00, timing "by".
- "1 saat sonra parkmetreyi uzat": "self", now plus one hour, timing "at", leadMinutes 0, missAfterMinutes 15.
- "eren yarın 14.30da diş randevusu var": recipient Eren, title "Diş randevusuna git", 14:30 tomorrow, timing "at", leadMinutes 45, missAfterMinutes 15.
- "erene yemek hazır de": nothing to do but come: recipient Eren, title "Yemeğe gel", now plus 5 minutes, timing "at".
- "eşim sabah 6da kalkacak ona uyuması gerektiğini ilet": the 6 is why, not when; Sibel should sleep now: recipient Sibel, title "Uyu", now plus 5 minutes, timing "at".
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
    return extractionSchema.parse(json);
  }
}
