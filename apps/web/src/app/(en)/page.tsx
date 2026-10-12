import { Landing } from "../../landing/Landing";
import { landingMetadata } from "../../landing/metadata";

export const metadata = landingMetadata("en");

export default function EnglishLanding() {
  return <Landing locale="en" />;
}
