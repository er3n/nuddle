// Root layout of the Turkish pages under /tr (open-questions#14).
import type { ReactNode } from "react";
import { Document } from "../../landing/Document";

export default function TurkishLayout({ children }: { children: ReactNode }) {
  return <Document locale="tr">{children}</Document>;
}
