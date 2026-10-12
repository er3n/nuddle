import type { Locale } from "../locales";
import { en } from "./en";
import { tr } from "./tr";
import type { Messages } from "./types";

export const messages: Record<Locale, Messages> = { en, tr };
