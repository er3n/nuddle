# 0015 Push reaches a device by Firebase Installation ID

Status: Accepted (2026-10-13). Refines ADR 0005 (how a plan reaches the device); decided in NUD-26.

## Context
FCM data messages carry our hints (docs/architecture.md, Push). Until now a device was addressed by its FCM registration token. firebase-admin 14 deprecates `token` and `tokens` in favor of Firebase Installation IDs (`fid`, `fids`), and firebase-messaging 26 on Android deprecates `getToken()` and `onNewToken()` in favor of `register()` and `onRegistered(fid)`. Both places kept the old API under a written suppression.

## Decision
- A device is addressed by its **Firebase Installation ID (FID)**. `devices.fid` replaces `devices.fcm_token` (still unique: one row per install), and `POST /api/v1/devices` takes `fid` instead of `fcmToken`.
- The app calls `FirebaseMessaging.register()`, reads the FID from `FirebaseInstallations`, and registers the device on every launch and on `onRegistered`.
- The server sends with `sendEachForMulticast({ fids })`.
- The migration deletes the existing device rows: their tokens are not FIDs, and every app launch registers the device again. This is fine only because there are no users before the beta (NUD-33).

## Consequences
- No deprecated push API is left in the code, and both suppressions are gone.
- An FID lives as long as the install. A reinstall or cleared app data gives a new FID, as a new token did before.
- Logout still deletes the device row (NUD-8), so the server stops sending to that FID; the app does not need `unregister()`.

## Rejected
- **Keep registration tokens until the SDKs remove them:** the same change later, but with real users whose rows would need to be kept, and two suppressions meanwhile.
- **Store both token and FID during a transition:** nothing to transition, since there are no users yet.
