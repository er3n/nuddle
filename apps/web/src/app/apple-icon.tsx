// The icon a phone shows for the site on its home screen.
import { brandIcon } from "../landing/BrandIcon";

export const size = { width: 180, height: 180 };
export const contentType = "image/png";

export default function AppleIcon() {
  return brandIcon(size.width);
}
