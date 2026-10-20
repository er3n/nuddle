# 0026 Releases reach Play internal testing from the command line

Status: Accepted (2026-10-20). Decided in NUD-117.

## Context
Every release so far was built with `mise run android:bundle` and dragged into Play Console by hand. The bundle (about 14 MB) is over the browser extension's 10 MB upload limit, so an agent cannot upload it, and the human has to be at the computer for every release (NUD-112, 0.1.3). Releases should be code like the rest (CLAUDE.md rule 5).

## Decision
- **Gradle Play Publisher** (`com.github.triplet.play`) uploads through the Google Play Developer Publishing API. `mise run android:publish` runs `:app:publishProdReleaseBundle`: it builds the prod release bundle, signs it with the upload key and puts it on the **internal** track as a completed release.
- Only the `prod` flavor publishes; `dev` and `e2e` are disabled.
- Release notes are files in the repo: `apps/android/app/src/prod/play/release-notes/<locale>/default.txt` (tr-TR and en-US, the listing's languages). They are rewritten for every release.
- The API acts as the service account `play-publisher@nuddle-app-er3n-512208.iam.gserviceaccount.com`, in the Google Cloud project `nuddle-app-er3n-512208` of the Play Console account (dokukoza@), apart from the Firebase project `nuddle-app-er3n`. Play Console gives it admin rights (the human's choice on 2026-10-20, for later automation); what it does is limited by the commands in the repo, which only upload to the internal track. Its key lives outside the repo at `~/.nuddle/release/play-service-account.json`, or the path in the Gradle property `nuddle.play.serviceAccount` or `NUDDLE_PLAY_SERVICE_ACCOUNT` (CLAUDE.md rule 11).
- Closed testing and production stay promotions in Play Console (CLAUDE.md rule 6).

## Consequences
- A release is: raise `versionCode`, write the release notes, `mise run android:publish`. No browser.
- The same command can run from GitHub Actions later, with the two keys as repository secrets.
- `bootstrap` and `publishListing` tasks exist but are not used: the store listing stays in Play Console.
- The key can do anything in Play Console, production included. It never leaves `~/.nuddle/release/` (or a CI secret); if it leaks, delete the key in Google Cloud and make a new one. The rights can be narrowed to the two testing-track permissions any time without a new key.

## Rejected
- **fastlane supply:** the same API, but a Ruby toolchain in a repo that has none.
- **Uploading in the browser:** needs the human for every release, and the agent cannot attach a file over 10 MB.
