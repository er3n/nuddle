// English for the global brand (Nuddle). Translated from tr.ts; keep the jokes, not the words.
import { legalEn } from "./legal.en";
import type { Messages } from "./types";

export const en: Messages = {
  brand: "nuddle",
  domain: "nuddle.app",
  meta: {
    title: "Nuddle · Reminders and alarms for someone who always forgets",
    description:
      "A reminder app that nags: say it once and Nuddle pokes them until it's done. Pills, bills, rent, bread. Reads it aloud, rings full screen. Free on Android.",
    appName: "Nuddle",
    ogLocale: "en_US",
    currency: "USD",
  },
  nav: {
    label: "Main menu",
    how: "How it works",
    video: "Watch",
    levels: "Poke levels",
    pricing: "Pricing",
    download: "Get it for Android",
    otherLanguage: "Türkçe",
  },
  hero: {
    pokedYou: (count) =>
      `This page has poked you ${String(count)} ${count === 1 ? "time" : "times"}. So far.`,
    title: "Someone at home always forgets.",
    titleAccent: "Now they can't.",
    lead: "You say it once. Nuddle pokes them until it's done. It remembers how many pokes it took, and plans the next time around that.",
    primary: "Set up the forgetful one",
    secondary: "Wait, how?",
  },
  phone: {
    label: "Poke 4 · full screen",
    task: "BREAD.",
    sub: "This is your last snooze.",
    button: "Got it, here's the photo",
    excuse: "Didn't see the message.",
    reply: "The full screen too?",
  },
  marquee: [
    "BUY BREAD",
    "PAY THE RENT",
    "PAY THE BILL",
    "TRASH EVERY TUESDAY",
    "CALL YOUR MOM",
    "TAKE YOUR PILLS",
    "DRY CLEANING",
  ],
  story: {
    title: "The story of a loaf of bread",
    subtitle: "Inspired by true events. Sadly.",
    panels: [
      {
        time: "14:32",
        caption: 'The task arrives. Reply: "yeah yeah."',
        alt: "Someone on the couch playing a game. The phone says buy bread, they think yeah yeah.",
      },
      {
        time: "18:15",
        caption: 'A poke with sound. Reply: "later."',
        alt: "The phone pokes out loud and vibrates. The person on the couch thinks later.",
      },
      {
        time: "18:45",
        caption: "Full screen. Reply: none, just a scream.",
        alt: "The phone is huge now and its screen says BREAD. The person's eyes pop out and the controller flies into the air.",
      },
      {
        time: "19:02",
        caption: "The bread is home. With a photo.",
        alt: "The person stands at the door proudly holding a huge loaf while a phone takes the proof photo. A label outside says Approved.",
      },
    ],
    drawn: {
      order: "Buy bread",
      firstReply: "yeah yeah",
      loudOrder: "BREAD!!",
      secondReply: "later...",
      fullScreen: "BREAD.",
      approved: "Approved",
    },
  },
  video: {
    title: "Nuddle in 28 seconds",
    subtitle: "The rent is written once. Watch the rest.",
    play: "Play the promo video",
    watch: "Watch · 0:28",
    posterAlt: "A frame from the video: poke 3, full screen, PAY THE RENT.",
    note: "The video loads from YouTube when you play it.",
  },
  features: {
    title: "Oh, and",
    titleAccent: "there's more.",
    items: [
      {
        tag: "Every day 09:00",
        title: "Don't write it every day",
        body: '"Mom takes her pills at 9 every day." Written once, poked every day. "Every Tuesday" and "weekday mornings" work too. "Not today" skips a day, a cancel ends it.',
      },
      {
        tag: "Message",
        title: "Don't forget to say it",
        body: '"Tell Sam I love him at 3." Nothing to do: it rings once at that time and "OK" closes it. No nagging.',
      },
      {
        tag: "Listening",
        title: "Too lazy to type",
        body: "Tap the mic and say it. What you say lands in the box, so you can fix it before you send.",
      },
      {
        tag: "Attached photo",
        title: "Show what you mean",
        body: "Send a photo of the shopping list or the right medicine box with the task. They see it on the task and on the poke.",
      },
      {
        tag: "Share · Nuddle",
        title: "A job from WhatsApp",
        body: "Share a message or a photo from another app to Nuddle and it lands in the box. Check it, fix it, send it. Nothing goes out on its own.",
      },
      {
        tag: "Tell mom",
        title: "It knows who you mean",
        body: '"Tell mom", "remind my wife" is enough. If you have one mom in Your people, the task goes straight to her.',
      },
      {
        tag: "✓✓ Seen",
        title: "The sender knows too",
        body: 'Did it arrive, was it seen, how many pokes: "Poked 3 times, last at 18:15." The plan stays hidden, only what happened is told.',
      },
      {
        tag: "Profile picture",
        title: "You know who it's from",
        body: "Pick your picture from the gallery. The sender's face shows on the poke notification, so you know Mom's poke from across the room.",
      },
      {
        tag: "To yourself",
        title: "Write to yourself too",
        body: "No one in Your people yet? Fine. A task to yourself gets poked just as stubbornly.",
      },
    ],
  },
  ringing: {
    title: "When the poke rings,",
    titleAccent: "there's no escape.",
    lead: "First a quiet notification, then sound if needed, and finally the full screen. Each step a little more insistent than the last, but always fair.",
    items: [
      {
        tag: '"Sam says: take your pills"',
        title: "It reads the task aloud",
        body: "On a poke with sound, the phone says the task out loud. Even with the phone in your pocket, you hear what to do.",
      },
      {
        tag: "I won't give up",
        title: "A little more insistent each time",
        body: 'First "Just so you know", then "Still here" once it makes noise, and on the full screen "I won\'t give up". No blame, no counting, it just doesn\'t let go.',
      },
      {
        tag: "Already done?",
        title: "It asks quietly first",
        body: 'If you are using your phone right before a loud poke, it asks quietly first. Already did it? Say "Done" and it never rings.',
      },
      {
        tag: "Slide to finish",
        title: "No accidental Done in a pocket",
        body: "On a locked phone, Done is a slide. One stray tap in a pocket doesn't finish the task, and the bread doesn't go unbought.",
      },
      {
        tag: "Call · Message",
        title: 'When it says "call mom"',
        body: "If the task is to call or text someone in Your people, the full screen poke shows Call or Message. One tap and the phone is dialing.",
      },
      {
        tag: "Remind me at 17:00",
        title: "Not now, later",
        body: "Whoever gets poked can move the time the pokes start. The deadline stays put, and so does the job.",
      },
      {
        tag: "Snooze · 22:37",
        title: "Even snoozing has rules",
        body: "Snoozes are limited and shrink to fit the time left. The button says when it rings again, and when it's the last one.",
      },
      {
        tag: "I'll handle it",
        title: "When today won't work",
        body: 'They say "Not today" and can leave a note. Whoever wrote the task gives a new time or says "I\'ll handle it", and the job moves to their list.',
      },
      {
        tag: "My poke didn't ring",
        title: "It rings even after a restart",
        body: "Pokes ring after the phone restarts, even before you unlock it. If one didn't ring, Profile shows the settings for your phone and sets your pokes again in one tap.",
      },
    ],
  },
  medicine: {
    title: "Medicine reminder:",
    titleAccent: "mom won't forget.",
    lead: '"Mom takes her blood pressure pill every day at 9." Say it once, and Nuddle pokes her every morning. You know whether she took it.',
    items: [
      {
        tag: "Every day 09:00",
        title: "Said once",
        body: "It rings at the same time every day. No sticky notes on the fridge, no alarms to set. Add a photo of the right box if you like. Cancel it when the course ends.",
      },
      {
        tag: "Full screen",
        title: "Hard to ignore",
        body: 'Miss the notification and it gets louder and reads the task aloud: "Sam says: take your pills." Then a full screen alarm. It keeps poking until she taps "Done".',
      },
      {
        tag: "✓ Done",
        title: "You know too",
        body: "When she takes it, you are told. When she doesn't, you see that too and can give her a call.",
      },
    ],
    note: "Nuddle is a reminder app and gives no medical advice. Ask your doctor how to take your medicine.",
  },
  steps: {
    title: "Three steps.",
    titleSecondLine: "Zero excuses.",
    items: [
      {
        title: "Write it or say it once",
        body: '"Alex pays the rent by 9 tomorrow." Who, what, by when: the AI works out the rest. "At 3" is a moment, "by 9" is a deadline. No forms, no date pickers, no patience test.',
      },
      {
        title: "They get poked",
        body: "At the right time, a little louder each time. It won't bother them the night before, and it won't leave it to the last minute. It learns which poke wakes them up.",
      },
      {
        title: 'Say "Done", add a photo if needed',
        body: 'For most things "Done" is enough. For things like cash, a photo is suggested: taken in the app or picked from the gallery, approved by whoever wrote the task. The "but I did get it" era is over.',
      },
    ],
  },
  levels: {
    title: "Poke",
    titleAccent: "levels",
    lead: "Not everyone wakes up to the same poke. Some turn around at a whisper, some only when their phone turns into a loaf of bread. Go on, find out which one wakes you.",
    button: ["Poke me", "Again", "Still can't hear you", "Okay okay", "I give up", "Start over"],
    items: [
      { title: "Whisper", body: "Bread? You know... bread." },
      { title: "Reminder", body: "A notification with sound. Polite but firm." },
      { title: "Insisting", body: "BREAD. With vibration." },
      { title: "Full screen", body: "Your phone is a loaf of bread now." },
      { title: "Telling on you", body: "I'm telling. I'm really telling." },
    ],
  },
  excuses: {
    title: "Excuse disposal unit",
    items: [
      {
        excuse: "I didn't see the message.",
        reply: "You didn't see the full screen alarm either?",
      },
      { excuse: "I forgot.", reply: "We reminded you four times. It's on record." },
      { excuse: "I got it, it's in the car.", reply: "Send me a photo of it, then." },
      { excuse: "You didn't say today.", reply: "14:32, word for word. Want to see?" },
    ],
  },
  pricing: {
    title: "Cheaper than a loaf of bread.",
    titleAccent: "Almost.",
    free: {
      label: "Free",
      price: "$0",
      features: [
        "Write tasks freely in the app, or say them",
        "Repeating tasks and one-time messages",
        "Adaptive pokes, read aloud, and a full screen alarm",
        "A photo if you want it, approved by the sender",
        "Attached photos and sharing from other apps",
        "Your people: unlimited forgetful ones",
      ],
      cta: "Start",
    },
    premium: {
      label: "Premium",
      tag: "For the very forgetful",
      // TODO(open-questions#10): the price is not decided; the page says "soon" until it is.
      price: "Soon",
      features: [
        "Tasks by email: forward a bill, get its due date",
        "Tasks by SMS, without opening the app",
        "A phone call for the most stubborn",
        "A monthly poke report (if you want one)",
      ],
    },
  },
  faq: {
    title: "Frequently asked, then forgotten questions",
    items: [
      {
        question: "My partner installed this for me. Can I remove it?",
        answer: "You can. You will still have to buy the bread.",
      },
      {
        question: "Does it poke at night?",
        answer:
          "No. It stays quiet from 23:00 to 07:00. Pick a time inside those hours and it asks first. We're human too. Well, software, but you get it.",
      },
      {
        question: "Does it start the night before?",
        answer:
          "It won't bother you tonight about something due tomorrow morning. It works out the right time on its own.",
      },
      {
        question: "Do I have to write the same thing every day?",
        answer:
          'Write "at 9 every day", "every Tuesday" or "weekday mornings" and it repeats on its own. Not more than once a day, though. Let\'s not get carried away.',
      },
      {
        question: "I'm busy right now. Can it poke me later?",
        answer:
          "You can move the time the pokes start. The deadline stays where it is, and the bread still gets bought.",
      },
      {
        question: "What if I upload an old photo from the gallery?",
        answer:
          "You can. But whoever wrote the task approves it, and they know yesterday's bread when they see it.",
      },
      {
        question: "What if I tap Done by accident in my pocket?",
        answer:
          "You can't. On a locked phone Done is a slide, and your pocket isn't that talented.",
      },
      {
        question: "Can I turn a WhatsApp message into a task?",
        answer:
          "Share the message and pick Nuddle. The text lands in the box, you check it and send it. Nuddle never reads your WhatsApp on its own.",
      },
      {
        question: "A poke didn't ring. What do I do?",
        answer:
          "In Profile, tap My poke didn't ring. It shows the settings for your phone and sets your pokes again in one tap.",
      },
      {
        question: "Is there an iPhone app?",
        answer: "Android only for now. iPhone is coming too; we need someone to remind us.",
      },
    ],
  },
  download: {
    title: "The next loaf of bread:",
    titleSecondLine: "guaranteed.",
    store: "Get it on Google Play",
    storeSoon: "Coming soon to Google Play",
    iphone: "An iPhone version is coming too. If we don't forget.",
  },
  footer: {
    quietHours: "Nobody gets poked between 23:00 and 07:00.",
    privacy: "Privacy",
    deleteAccount: "Delete account",
  },
  invite: {
    title: "You've been invited to Nuddle.",
    body: "Install the app and sign in with the number you were invited on. The invite will be waiting for you.",
    more: "What is Nuddle?",
  },
  notFound: { title: "This page doesn't exist. We may have forgotten it too.", home: "Back home" },
  legal: legalEn,
};
