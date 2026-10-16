import { LegalPage } from "../../../landing/LegalPage";
import { legalMetadata } from "../../../landing/metadata";

export const metadata = legalMetadata("privacy", "en");

export default function EnglishPrivacy() {
  return <LegalPage page="privacy" locale="en" />;
}
