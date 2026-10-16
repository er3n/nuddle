import { LegalPage } from "../../../../landing/LegalPage";
import { legalMetadata } from "../../../../landing/metadata";

export const metadata = legalMetadata("privacy", "tr");

export default function TurkishPrivacy() {
  return <LegalPage page="privacy" locale="tr" />;
}
