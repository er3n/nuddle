import { LegalPage } from "../../../../landing/LegalPage";
import { legalMetadata } from "../../../../landing/metadata";

export const metadata = legalMetadata("deleteAccount", "tr");

export default function TurkishDeleteAccount() {
  return <LegalPage page="deleteAccount" locale="tr" />;
}
