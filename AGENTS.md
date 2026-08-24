# AGENTS.md

## Read first

Before any coding task, read in this order:

1. `PROJECT_CONTEXT.md`
2. `ARCHITECTURE.md`
3. `AGENTS.md`
4. `IMPLEMENTATION_PLAN.md`

If repository code later conflicts with these documents, report the conflict before changing safety-critical behavior.

## Mission priority

1. medication safety boundaries
2. reliable local alarms
3. offline behavior
4. cognitive accessibility
5. data privacy/security
6. family synchronization
7. maintainability
8. visual polish

## Medical safety

Never:
- infer/calculate/recommend/change a dose
- create catch-up or double-dose logic
- treat NO_CONFIRMATION as proof medication was not taken
- implement insulin/glucose-dependent/PRN medication as ordinary fixed-dose reminder logic
- fetch medical advice
- use AI for treatment decisions

V1 fixed-schedule medication only.

If a requested feature crosses this boundary, stop and explain.

## Android stack

Preferred:
- Kotlin
- Jetpack Compose
- stable Material 3
- ViewModel + StateFlow
- Coroutines
- Room
- DataStore
- AlarmManager
- NotificationManager
- BroadcastReceiver
- WorkManager for network synchronization only
- Firebase Authentication
- Firestore
- FCM

Stable dependencies only.
No alpha/beta/RC unless explicitly approved.

Do not add Hilt until complexity demonstrates a need.
Do not add a custom backend unless explicitly approved.

## Alarm rule

NEVER use WorkManager, FCM, Firestore listeners, or a backend timer as the primary medication alarm.

Primary medication alarm must be device-local.

Alarm scheduling code must:
- be idempotent
- use unique occurrence IDs / PendingIntent identities
- persist occurrences before scheduling
- survive process death
- restore after reboot
- cancel obsolete alarms after schedule changes
- never re-alert an acknowledged occurrence
- be testable with injected clock/time abstraction
- expose failures to caregiver diagnostics

Verify Android 16 exact-alarm requirements from official Android documentation before implementation.

Because exact alarm is the app's core function, evaluate `USE_EXACT_ALARM` where valid.
Do not blindly copy old `SCHEDULE_EXACT_ALARM` snippets.

## Full-screen rule

On modern Android:
- check `canUseFullScreenIntent()` where applicable
- do not assume access
- show configuration guidance
- provide high-importance notification fallback

## Samsung A06 rule

Target physical test device includes Samsung Galaxy A06, Android 16.

Do not consider alarm work complete until tested with:
- normal state
- Battery Saver
- Sleeping apps behavior
- Deep sleeping behavior
- Never sleeping exception
- locked screen
- process killed
- reboot

Caregiver setup must include Samsung Background usage limits guidance.

## Audio rule

Desired spoken phrase:
"Dede, ilacını alma zamanı."

Do not make live TTS the only alarm audio path.
There must be a bundled/fallback alarm sound.

If using Android TextToSpeech:
- verify Turkish locale
- verify offline behavior on the A06
- handle missing voice gracefully
- do not block alarm delivery while TTS initializes

## Offline-first rule

Primary device commits critical state locally first.

For acknowledgement:
1. Room transaction updates occurrence
2. same transaction or durable workflow creates SyncOutbox item
3. UI updates immediately
4. network sync happens later

Never make "İlacımı aldım" wait for Firestore.

Use WorkManager for retryable sync.

## Family sync semantics

Family app language must distinguish:
- "Aldığını onayladı"
- "Henüz onay yok"
- "Cihaz çevrimdışı / son durum bilinmiyor"

Never label absence of acknowledgement as certain non-adherence.

FCM is a hint/notification mechanism, not authoritative state.

Family UI should query synchronized state and show timestamps.

## Firestore security

Never deploy Firestore with open development rules.

`request.auth != null` alone is insufficient.

Data access must be scoped to family membership and role.

Prefer deny-by-default.

Add security rule tests using Firebase Emulator Suite before treating cloud integration as complete.

Do not log or expose:
- medication data unnecessarily
- auth tokens
- phone numbers
- family identifiers
- provisioning allow-lists

## Private-family V2 schedule policy

Medication schedules are editable only on an authorized `ADMIN_DEVICE`.

`ALARM_DEVICE` is read-only for remote schedule configuration. It validates and applies complete versioned schedules locally, and a failed remote update must preserve the last known-good Room schedule.

Do not add schedule editing to grandfather-facing or alarm-device UI.

## UI rules — grandfather

- Turkish
- one primary task per screen
- very large type
- large touch targets; primary actions preferably 64dp+
- never below 48dp for touch targets
- no icon-only critical controls
- no color-only state
- no hidden gestures
- no prominent skip button
- no dense navigation
- no shame/guilt language
- no childish language
- support font scaling
- TalkBack semantics
- avoid scrolling on alarm screen when possible

Acknowledgement:
"İlacını aldın mı?"
"EVET, ALDIM"
"HAYIR"

## Trusted administration and diagnostics

The private-family V2 flow has no caregiver PIN or user-visible e-mail/password/pairing-code UI. Cloud access is authorized per provisioned device by the backend and Firestore rules. Do not reintroduce legacy auth or invitation screens unless explicitly requested.

Trusted setup/diagnostics must show:
- exact alarm capability
- notification permission
- full-screen capability
- next alarm
- last alarm fired
- sound test
- vibration test
- Samsung sleeping-app guidance
- network state
- last sync
- pending outbox count
- auth state
- FCM state

## Data model discipline

Use explicit domain names:
- Medication
- MedicationSchedule
- DoseOccurrence
- SyncOutbox
- DeviceRegistration
- FamilyMember

Persist timestamps and versions.

Do not use medication name/time as a database identity.
Use generated stable IDs.

## Code quality

- UI never talks directly to Room/Firestore
- Composables contain presentation, not business logic
- repositories mediate data
- immutable UI state where practical
- centralize time calculations
- avoid global mutable state
- no silent exception swallowing
- no unrelated refactors
- no over-engineered generic architecture
- no premature multi-module split

## Tests

Prioritize:
- next occurrence calculation
- DST/timezone behavior if relevant
- day boundaries
- recurring schedules
- inactive medication
- acknowledgement idempotency
- duplicate receiver delivery
- snooze
- no-confirmation transition
- old alarm cancellation after edit
- reboot rescheduling
- outbox retry
- duplicate cloud event idempotency
- stale family-state presentation
- permission diagnostic state

Physical A06 testing remains mandatory.

## Git safety

Read-only allowed:
- git status
- git diff
- git log
- git show

Without explicit approval do NOT:
- git add
- commit
- push
- create/switch branches
- merge/rebase
- reset
- restore
- clean
- discard user changes
- publish releases/tags

## Collaboration style

Repository owner is an experienced software engineer but new to Android.

For each Android-specific feature:
- explain the concept briefly in Turkish
- say why it is needed
- point to the files
- give exact Android Studio / Gradle test steps

Do not dump unrelated Android theory.

## Phase discipline

Do not build the whole app in one pass.

For every phase:
1. inspect current repo
2. state scope
3. list intended files
4. implement only that scope
5. compile
6. run relevant tests
7. lint where appropriate
8. inspect diff
9. report exact manual tests
10. STOP for user review

Reliability over speed.
