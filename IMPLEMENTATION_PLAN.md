# IMPLEMENTATION_PLAN.md

This is the active V2 plan. A phase is complete only when its automated checks and required manual checks are recorded. Medication safety, offline alarms, and existing user data take priority over phase speed.

## Phase 0 — Android foundation — COMPLETE

- Kotlin + Jetpack Compose
- package `com.berkant.yaninda`
- minSdk 26, compileSdk/targetSdk 37
- build, test, lint baseline

## Phase 1 — Grandfather UI prototype — COMPLETE

- calm home screen
- large alarm screen
- explicit taken confirmation
- large type/touch targets and TalkBack semantics

## Phase 2 — Local fixed-schedule data — COMPLETE

- Room medication/schedule model
- fixed schedule only
- variable/insulin/PRN safety rejection
- no dosage inference or recommendation

## Phase 3 — Occurrence planning — COMPLETE

- injected time abstraction
- recurring occurrence calculation
- stable occurrence identities
- state machine and unit tests

## Phase 4 — Exact local alarm engine — IMPLEMENTED / PHYSICAL GATE OPEN

- exact AlarmManager scheduling
- unique PendingIntents and idempotent rescheduling
- notification/full-screen fallback
- process-death persistence
- cancel obsolete alarms

Still required on Samsung Galaxy A06: locked, screen-off, killed process, Battery Saver.

## Phase 5 — Alarm attention and acknowledgement — IMPLEMENTED / PHYSICAL GATE OPEN

- foreground alarm attention service
- alarm audio/vibration fallback
- explicit taken confirmation
- snooze
- `ACTION_DIAL` family call action
- safe Back behavior
- hard attention timeout

Still required on Samsung Galaxy A06: audible volume, vibration, notification denied, full-screen denied, locked-screen behavior.

## Phase 6 — Reboot and Samsung reliability — IMPLEMENTED / PHYSICAL GATE OPEN

- reboot restoration
- exact-alarm/notification/full-screen diagnostics
- Samsung sleeping/deep-sleep guidance
- test matrix documentation

Release cannot be called reliable until the physical A06 matrix passes.

## Phase 7 — Offline sync outbox — COMPLETE

- durable Room outbox
- idempotent event IDs and versions
- WorkManager retry
- local ACK never waits for network
- retry/readiness tests

## Phase 8 — Private Firebase family layer — COMPLETE IN EMULATOR

- fixed `sefer-family`
- per-installation anonymous Auth UID + local device ID
- server-controlled `provisionPrivateFamilyDevice`
- `ADMIN_DEVICE` / `ALARM_DEVICE` authorization
- deny-by-default family-scoped Firestore rules
- emulator rule/function tests
- persistent local emulator data workflow

Production gate: configure server-side admin/alarm UID allow-lists and App Check policy before real cloud provisioning.

## Phase 9 — Admin schedule and alarm-device convergence — COMPLETE IN EMULATOR

- Admin publishes versioned canonical desired schedule
- every alarm device validates, persists, and schedules independently
- failed remote updates preserve the last known-good local schedule
- same-time medications form one logical dose group, one alarm, one ACK
- multi-device reports retain device-specific documents and a shared logical `occurrenceId`

## Phase 10 — Family monitoring and push — COMPLETE IN EMULATOR

- dashboard, day-based history, devices, contacts
- ACK timestamp and “Henüz onay yok” semantics
- stale/offline wording based on freshness
- FCM schedule/occurrence hints without medication payload
- token rotation handling
- Admin navigation: Ana Sayfa / İlaçlar / Geçmiş / Ayarlar

## Phase 11 — Design, accessibility, and cleanup — COMPLETE IN SOURCE

- calm appliance-style grandfather UI
- trusted-family-console admin UI
- high contrast, large typography, 48dp minimum critical targets
- no icon-only critical actions or color-only status
- font scaling and TalkBack considerations
- legacy e-mail/password, invitation-code, pairing-code, caregiver-PIN, and unreachable caregiver configuration flows removed
- sensitive logging minimized

Manual gate: TalkBack and maximum practical Android font-size checks on target hardware.

## Phase 12 — Private release hardening — NOT COMPLETE

Required before final release:

1. Run and record the full Samsung Galaxy A06 matrix.
2. Verify offline alarm, offline ACK, reconnect sync, reboot, and app-update behavior.
3. Verify notification/full-screen denial does not leave an uncontrollable alarm.
4. Configure production Firebase UID allow-lists and security environment.
5. Run final source/security review and all automated checks.
6. Create a private release keystore outside the repository.
7. Build and verify a signed release APK.
8. Follow the private installation/update guide; never distribute the debug APK as the final build.

## Explicitly excluded

- glucose tracking
- insulin/variable/PRN dosing as ordinary fixed reminders
- medical advice or dosage calculation
- location/GPS until medication release hardening is complete and separately approved
- automatic phone calls
- FCM/WorkManager/backend timers as primary alarms

## Current next action

Do not add a new product feature. The next milestone is the physical Samsung Galaxy A06 release test and production provisioning hardening.
