// Root layout of the English pages (open-questions#14).
import type { ReactNode } from "react";
import { Document } from "../../landing/Document";

export default function EnglishLayout({ children }: { children: ReactNode }) {
  return <Document locale="en">{children}</Document>;
}
