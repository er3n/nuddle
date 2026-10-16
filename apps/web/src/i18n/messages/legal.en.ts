// Translated from legal.tr.ts (the source); keep the two saying the same thing, section by section.
import { CONTROLLER, PRIVACY_EMAIL } from "../contact";
import type { Legal } from "./types";

const deleted = [
  "Your phone number, name and profile picture.",
  "Your connections and the invites you sent.",
  "The tasks you wrote and the tasks written to you, with their notes and photos.",
  "Your poke history and the poke summary we learned for you.",
  "The records of the AI calls.",
  "The notification keys of your phones.",
];

export const legalEn: Legal = {
  home: "Home",
  otherLanguage: "Türkçe",
  privacy: {
    metaTitle: "Privacy · Nuddle",
    title: "Privacy notice",
    updated: "Last updated: 16 October 2026",
    intro:
      "Nuddle is an app where someone says a task once and the person who forgets is poked until it is done. This notice explains which of your data we process and why, who we share it with, how long we keep it and what your rights are, under the Turkish Personal Data Protection Law No. 6698 (KVKK).",
    sections: [
      {
        heading: "Data controller",
        paragraphs: [
          `The data controller is ${CONTROLLER}. For any privacy question or request: ${PRIVACY_EMAIL}`,
        ],
      },
      {
        heading: "What we process",
        paragraphs: [],
        items: [
          "Your phone number. It is the only identity of your account; your sign-in code is sent to it by SMS.",
          "Your name and, if you add one, your profile picture.",
          "Your connections: whom you invited, your relationship (partner, mother and so on) and the name and phone number of the person you invited. We do not read your contacts; we only take the number of the person you pick. If the person you invited does not use the app yet, their number is kept so they see the invite when they sign up with it.",
          "Tasks: the text you wrote, the task's title, detail, time, who it was written to, notes and how it ended. When you dictate, your phone's speech recognition turns your speech into text; your voice never reaches us.",
          "Your poke history: which poke rang when, after which one you acted, snoozes. From it a short summary of how you should be poked is written.",
          "Photos: the photo you send to show that a task is done.",
          "Your settings: quiet hours, time zone and whether people you know may be told.",
          "Device data: your phone's notification key so we can reach it, whether permissions are on, whether pokes rang on time on the phone, and if the app crashes a crash report (phone model, Android version, error log).",
        ],
      },
      {
        heading: "Why we process it",
        paragraphs: [
          "To provide the app to you: signing in, delivering tasks, ringing pokes on time, showing the photo to the other person. This is necessary to perform our contract with you (KVKK art. 5/2-c).",
          "To keep the app working and make it better: crash reports, counting sign-in attempts against abuse, and measuring the quality of the AI's plans. This relies on our legitimate interest (KVKK art. 5/2-f).",
          "We do not use your data for advertising, we do not sell it and we do not give it to anyone for marketing.",
        ],
      },
      {
        heading: "AI",
        paragraphs: [
          "We use an AI model to turn what you write into a task and to plan when and how loudly the pokes ring. The text you wrote goes to the model. For the plan, the task itself, your time zone, your quiet hours, the names of the person who wrote to you and of your connections with your relationship, and the titles of your recent tasks with how they ended also go. Your photos do not.",
          "We keep the input and the answer of these calls to see whether the plans were right and to improve them. When you delete your account, these records are deleted too.",
        ],
      },
      {
        heading: "Who we share it with",
        paragraphs: [
          'The person you are connected with sees your name, your profile picture, your phone number (so they can call you) and how your phone can be poked right now. Only the two of you see the tasks, notes and photos you write to each other. One exception: if you miss a task, one of your own connections may be told its title and that you are not answering. You can turn this off with the setting "Benim işlerimde tanıdıklarımı kullanabilirsin" in Profile.',
          "The companies we use, only as much as they need for their job:",
        ],
        items: [
          "Supabase: database, sign-in and photo storage. Servers in Frankfurt, Germany.",
          "Vercel: website and server code. The code runs in Frankfurt, Germany.",
          "Google Firebase: notifications and crash reports. The US and other countries.",
          "Twilio: the sign-in code SMS. The US.",
          "OpenRouter and the provider of the AI model that runs through it. The US and other countries.",
        ],
      },
      // TODO(open-questions#50): which article 9 safeguard applies.
      {
        heading: "Transfer abroad",
        paragraphs: [
          "These companies are abroad or were founded abroad, so your personal data is transferred outside Turkey: to Germany, the US and the other countries these providers work in. The transfer is made in line with article 9 of KVKK.",
        ],
      },
      {
        heading: "How long we keep it",
        paragraphs: [],
        items: [
          "Photos are deleted when the task closes.",
          "Everything else is kept while your account is open.",
          "When you delete your account, your phone number, name and profile picture are deleted at once, your tasks and everything else within 7 days. What is deleted is listed below.",
          "Crash reports stay in Firebase for at most 90 days, not linked to your phone number.",
        ],
      },
      {
        heading: "Deleting your account",
        paragraphs: [
          "You can delete your account in the app under Profile > Hesabı sil. If you cannot reach the app, follow the steps on nuddle.app/delete-account. When you delete your account, this is deleted:",
        ],
        items: deleted,
      },
      {
        heading: "Your rights",
        paragraphs: ["Under article 11 of KVKK you can ask:"],
        items: [
          "Whether your data is processed, and if so, for information about it.",
          "Why it is processed and whether it is used for that purpose.",
          "To whom it is transferred, in Turkey and abroad.",
          "To have it corrected if it is incomplete or wrong, deleted or destroyed, and for those it was transferred to to be told.",
          "To object to a result against you that comes only from automated analysis.",
          "For compensation if you suffer damage from unlawful processing.",
        ],
      },
      {
        heading: "Requests",
        paragraphs: [
          `Write your request to ${PRIVACY_EMAIL}, with the phone number of your account. We answer within 30 days at the latest.`,
        ],
      },
      // TODO(open-questions#52): minimum age.
      {
        heading: "Children",
        paragraphs: [
          "Nuddle is not for children under 13. People under 18 should use Nuddle with the permission of a parent or guardian.",
        ],
      },
      {
        heading: "Website",
        paragraphs: [
          "nuddle.app uses one cookie, only to remember the language you picked. There are no advertising or tracking cookies.",
        ],
      },
      {
        heading: "Changes",
        paragraphs: [
          "If we change this notice, the current version is published on this page and the date above is updated.",
        ],
      },
    ],
  },
  deleteAccount: {
    metaTitle: "Delete your account · Nuddle",
    title: "Delete your account",
    updated: "Last updated: 16 October 2026",
    intro: "You can delete your Nuddle account and your data at any time.",
    sections: [
      {
        heading: "In the app",
        paragraphs: [
          "Open Profile in the app, tap Hesabı sil (Delete account) at the bottom and confirm with Hesabımı sil. Your account is closed at once.",
        ],
      },
      {
        heading: "Without the app",
        paragraphs: [
          `If you cannot reach the app, write "Delete my account" to ${PRIVACY_EMAIL} and include the phone number of your account. Once we have confirmed that the number is yours, we delete your account within 30 days at the latest and let you know.`,
        ],
      },
      {
        heading: "What is deleted",
        paragraphs: [],
        items: deleted,
      },
      {
        heading: "The other side",
        paragraphs: [
          "Unfinished tasks written to you or by you are cancelled and the other person is told. Your connections no longer see you in their list.",
        ],
      },
      {
        heading: "When it is deleted",
        paragraphs: [
          "Your phone number, name, picture, devices, invites and AI records are deleted at once; you can sign up again with the same number right away. Your tasks, connections and poke history stay on the server for 7 more days so the other person's phone receives the cancellations, and are then deleted. What the other person already saw on their phone may stay on that phone.",
          "Crash reports stay in Firebase for at most 90 days, not linked to your phone number, and are then deleted. No other data is kept.",
        ],
      },
      {
        heading: "It cannot be undone",
        paragraphs: [
          "A deleted account cannot be brought back. If you sign up again with the same number, you start with an empty account.",
        ],
      },
    ],
  },
};
