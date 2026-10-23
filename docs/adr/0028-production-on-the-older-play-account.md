# 0028 Production ships as com.dokukoza.nuddle from the older Play account

Status: Accepted (2026-10-23). Decided in NUD-144. Changes the `applicationId` in `docs/product.md`.

## Context
The Play Console account that holds `app.nuddle` (dokukoza@, created 2026-10-16) is a new personal account. Google lets it publish to production only after a closed test with at least 12 opted-in testers for 14 days in a row, and then after an application review; the earliest date was about 2026-10-31. The human has an older personal account (erenozturk@, developer id `9034579623613508143`, shown as "Dokukoza", identity and phone verified 2026-10-23) that was opened before November 2023 and is exempt from that rule. On 2026-10-23 a new app on that account showed "Create new release" on the Production track with no application step.

A package name belongs to the account that first used it, so `app.nuddle` cannot move to the older account without an app transfer between the two accounts, which is slow and needs both accounts in good standing with matching payment profiles.

## Decision
- **The `prod` flavor builds `com.dokukoza.nuddle`** and goes to production on the erenozturk@ account (app id `4973549448533661897`). Only the flavor's `applicationId` changes; the Kotlin `namespace` stays `app.nuddle`, and `dev` and `e2e` stay `app.nuddle.debug`.
- **`app.nuddle` stays on the dokukoza@ account** as it is (internal and closed testing), as a backup. Nothing new is uploaded there unless the human asks.
- **Firebase:** the same project `nuddle-app-er3n` has a third Android app, `com.dokukoza.nuddle`, with the upload key and the new Play App Signing key. FCM and Crashlytics work per app inside one project, so Vercel's service account does not change.
- **App Links:** `assetlinks.json` lists both packages with the same fingerprint list. `ANDROID_APP_LINK_FINGERPRINTS` in Vercel Production gets the new Play App Signing SHA-256 next to the existing ones (the human sets it, CLAUDE.md rule 6).
- **SMS Retriever:** the hash depends on the package and the signing key, and the server sends one. When production goes live, `SMS_RETRIEVER_HASH` switches to the `com.dokukoza.nuddle` hash (the human sets it). Testers still on `app.nuddle` then type the code or take the keyboard's suggestion, as sideloaded builds already do (docs/ux/platform.md).
- **Uploads (ADR 0026):** Gradle Play Publisher takes the package from the variant, so `mise run android:publish` targets the new app once the service account is invited in the erenozturk@ Play Console. The first bundle of a new app goes through Play Console in the browser.
- **App content** is filled again on the new app with the same answers as `app.nuddle` (alarm app declarations, review number from ADR 0022, privacy and deletion pages on `nuddle.app`).

## Consequences
- People on the `app.nuddle` test builds install the production app as a new app. Their account lives on the server (phone identity, ADR 0011), so they sign in again and find their tasks and connections; local alarms are rebuilt by sync.
- Two apps share one backend and one Firebase project until `app.nuddle` is unpublished; push and sessions are per installation (ADR 0015, ADR 0027), so both work side by side.
- The public store link becomes `https://play.google.com/store/apps/details?id=com.dokukoza.nuddle`.

## Rejected
- **Waiting for the 14-day closed test on the new account:** production at the earliest on 2026-10-31, plus the application review, with no guarantee.
- **Transferring `app.nuddle` to the older account:** a manual Google support process with its own wait, and the receiving account takes on the app's history.
- **Changing the Kotlin `namespace` too:** touches every source file and buys nothing; the namespace is not visible to Play or users.
