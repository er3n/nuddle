import type { Metadata } from "next";
import { messages } from "../../../../i18n/messages";
import { requestLocale } from "../../../../i18n/request";
import { Invite } from "../../../../landing/Invite";

export async function generateMetadata(): Promise<Metadata> {
  const m = messages[await requestLocale()];
  return { title: m.invite.title, robots: { index: false } };
}

export default async function InvitePage() {
  return <Invite locale={await requestLocale()} />;
}
