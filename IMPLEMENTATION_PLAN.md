# IMPLEMENTATION_PLAN.md

## Phase 0 — Android foundation

Goal:
create a minimal buildable native Android project.

Deliver:
- Kotlin + Compose
- app theme
- navigation shell
- no Firebase
- no medication logic
- debug build succeeds

Teach:
Android Studio project anatomy, Gradle, app module, Manifest, Activity, Compose.

STOP.

## Phase 1 — Grandfather UI prototype

Goal:
validate whether the UI can realistically be used by grandfather.

Deliver:
- home screen
- alarm screen preview
- confirmation dialog
- fake data only
- large font / targets
- TalkBack semantics basics

No real alarms.

Test on Galaxy A06 immediately.

STOP.

## Phase 2 — Local caregiver configuration

Goal:
store safe fixed schedules locally.

Deliver:
- Room
- Medication
- MedicationSchedule
- caregiver PIN screen
- add/edit/deactivate fixed schedule
- explicit warning/rejection for variable/insulin/PRN schedule types

No cloud.

STOP.

## Phase 3 — Occurrence planner

Goal:
turn schedules into persisted dose occurrences.

Deliver:
- time abstraction
- next occurrence calculation
- recurrence calculation
- unit tests
- occurrence IDs
- state machine

No AlarmManager until calculation tests are solid.

STOP.

## Phase 4 — Exact alarm engine

Goal:
fire local alarms reliably.

Deliver:
- ReminderScheduler
- AlarmManager implementation
- receiver
- notification channel
- exact-alarm permission/capability handling
- unique PendingIntents
- alarm cancel/reschedule
- 1-minute test alarm

Test on A06:
locked, screen off, killed, battery saver.

STOP.

## Phase 5 — Alarm experience + sound

Goal:
make the reminder usable.

Deliver:
- full-screen path where allowed
- heads-up fallback
- bundled/fallback alarm tone
- "Dede, ilacını alma zamanı" spoken audio path
- vibration
- acknowledgement confirmation
- snooze
- call caregiver action

STOP.

## Phase 6 — Reboot/Samsung hardening

Goal:
protect against real device behavior.

Deliver:
- reboot rescheduling
- permission diagnostics
- Samsung sleeping/deep-sleep guidance
- last-alarm diagnostics
- test matrix documentation

Do not continue until A06 physical tests pass.

STOP.

## Phase 7 — Local sync outbox

Goal:
prepare offline-safe family synchronization without cloud dependency in alarm flow.

Deliver:
- SyncOutbox table
- idempotent event IDs
- WorkManager sync abstraction
- fake remote data source
- retry tests

STOP.

## Phase 8 — Firebase family layer

Goal:
authenticated private family data.

Deliver:
- Firebase project configuration
- Firebase Auth for caregiver family phones
- family/member model
- device pairing
- Firestore
- deny-by-default + family-scoped Security Rules
- Emulator Suite tests

Primary alarm still completely local.

STOP.

## Phase 9 — Family monitoring

Goal:
family sees synchronized status.

Deliver:
- family dashboard
- occurrence status
- last synced
- stale/offline warning
- wording that distinguishes acknowledgement from certainty

No remote schedule editing.

STOP.

## Phase 10 — Push notifications

Goal:
surface synchronized events to family.

Deliver:
- FCM
- taken-confirmation notification
- no-confirmation notification
- offline/stale alert if designed
- TTL choices
- token rotation handling

Push is never authoritative.

STOP.

## Phase 11 — Grandmother local secondary reminder

Goal:
redundancy near grandfather.

Deliver:
- cached schedule
- optional local "Dedenin ilaç zamanı" reminder
- clear label that primary alarm device is grandfather phone

STOP.

## Phase 12 — Release hardening

Deliver:
- accessibility pass
- security review
- backup policy review
- release signing
- signed APK
- private installation/update guide
- no keystore in repo
- final A06 physical test record


## Optional Location Safety Module — only after medication release hardening

### L1 — One-shot local GNSS prototype
- permission flow
- local coordinate + timestamp + accuracy
- physical A06 test with mobile data off

### L2 — Local safety zone
- configurable home radius
- local exit event
- simple grandfather help screen
- no cloud dependency

### L3 — Offline location outbox
- persist safety events / short breadcrumb history
- reconnect synchronization

### L4 — Family map
- last location + timestamp + accuracy
- stale/offline state
- never imply stale location is live

### L5 — Temporary emergency tracking
- higher-frequency updates only during an active safety event
- battery limits
- clear stop conditions

### L6 — Village field test
- no coverage
- outdoor/indoor
- battery
- Samsung background restrictions

### L7 — Decide on off-grid hardware
If live remote tracking is required with zero cellular/Wi-Fi coverage, evaluate a dedicated satellite/off-grid device. The phone app alone cannot provide a remote communication link.
