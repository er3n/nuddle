import { LegalPage } from "../../../landing/LegalPage";
import { legalMetadata } from "../../../landing/metadata";

export const metadata = legalMetadata("deleteAccount", "en");

export default function EnglishDeleteAccount() {
  return <LegalPage page="deleteAccount" locale="en" />;
}
