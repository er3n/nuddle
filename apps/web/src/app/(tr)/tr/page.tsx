import { Landing } from "../../../landing/Landing";
import { landingMetadata } from "../../../landing/metadata";

export const metadata = landingMetadata("tr");

export default function TurkishLanding() {
  return <Landing locale="tr" />;
}
