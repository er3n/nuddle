// Served at /.well-known/assetlinks.json for Android App Links (src/api/app-links.ts).
import { assetLinks } from "../../../api/app-links";

export const dynamic = "force-dynamic";

export function GET(): Response {
  return Response.json(assetLinks(process.env));
}
