// The browser tab icon of every page.
import { brandIcon } from "../landing/BrandIcon";

export const size = { width: 192, height: 192 };
export const contentType = "image/png";

export default function Icon() {
  return brandIcon(size.width);
}
