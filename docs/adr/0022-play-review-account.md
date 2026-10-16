# 0022 One review number with a fixed code in the cloud

Status: Accepted (2026-10-16). Decided in NUD-92. Narrows ADR 0011.

## Context
Google Play reviews every release and asks, under App access, how its reviewers can get past sign-in. Our only sign-in is a phone number and a code sent by SMS (ADR 0011). A reviewer has no Turkish phone to receive it, and an app that cannot be opened is rejected. ADR 0011 and CLAUDE.md rule 11 said test numbers with fixed codes exist only locally, because the repo is public and anyone reading a fixed code could sign in.

## Decision
- **Exactly one review number** has a fixed code in the cloud project. It is a number the human owns, so no stranger can ever sign up with it and find the account taken.
- **The number and code are never committed.** They live in `~/.nuddle/prod/PLAY_REVIEW_OTP` as one line, `<E.164 digits without +>=<6 digits>`. `supabase/cloud/configure.sh auth` adds them to `sms_test_otp` at apply time, like the Send SMS hook secret. `supabase/cloud/auth.json` keeps `sms_test_otp` empty, and a unit test keeps it so.
- **Nothing prints them.** `configure.sh auth` rejects a malformed file, and its read-back shows only how many test numbers the project has, never the value.
- **Only the human applies it** (CLAUDE.md rule 6) and types the number and code into Play Console, App access. The agent never sees or enters them.
- **Revoking is one step:** empty the file and run `configure.sh auth` again; the project then has no test number.
- The review number is an ordinary account: it signs in, writes tasks to itself and gets poked. Supabase never calls the Send SMS hook for it, so no SMS is sent or paid for.

## Consequences
- Anyone who learns the number and code can sign in to that one account. It holds only what the reviewers and the human put in it, never real users' data, and it cannot reach other people except by an invite they accept.
- If the code leaks, the human changes the file and reapplies; Play Console gets the new code with the next review.
- `supabase config push` stays forbidden for the cloud project: it would push the local test numbers (ADR 0011).

## Rejected
- **Putting the review number in `auth.json`:** the repo is public.
- **An email or password login only for reviewers:** a second sign-in path to build, test and keep safe, for one user.
- **A review build that skips sign-in:** Play reviews the build users get; a different build would not be reviewed.
- **Leaving App access empty:** the review fails when the reviewer cannot get past the sign-in screen.
