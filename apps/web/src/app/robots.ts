// /robots.txt: crawl the pages, not the API. Invite links stay crawlable for link previews and
// carry noindex on the page itself.
import type { MetadataRoute } from "next";
import { SITE } from "../landing/site";

export default function robots(): MetadataRoute.Robots {
  return {
    rules: { userAgent: "*", allow: "/", disallow: "/api/" },
    sitemap: new URL("/sitemap.xml", SITE).href,
  };
}
