// Root layout of the invite link page: its language comes from the browser (open-questions#14).
import type { ReactNode } from "react";
import { requestLocale } from "../../i18n/request";
import { Document } from "../../landing/Document";

export default async function InviteLayout({ children }: { children: ReactNode }) {
  return <Document locale={await requestLocale()}>{children}</Document>;
}
