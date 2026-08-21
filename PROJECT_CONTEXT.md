# PROJECT_CONTEXT.md

## Project

Private family-only Android medication reminder and caregiver monitoring application.

Primary medication device:
- Samsung Galaxy A06
- model family: SM-A065F (user reported SM-AO65FTPS; verify exact Settings value later)
- Android 16

Other devices:
- grandmother's Android phone
- optionally mother / family members' Android phones

The app is not planned for Google Play. It will be sideloaded privately.

## User context

Primary user is an elderly grandfather with cognitive impairment / dementia and diabetes.
He may forget scheduled medication and is not confident using a smartphone.

Grandmother lives with him in a village.
Internet and cellular connectivity can be intermittent.

Because some diabetes treatment may be insulin / measurement-dependent / variable-dose, v1 MUST NOT assume every medication is a fixed-dose medication.

## Core product goal

Make fixed-schedule medication reminders difficult to miss and easy to understand, even when internet is unavailable.

Second goal:
Allow trusted family members to see whether the grandfather explicitly acknowledged a reminder once devices synchronize.

The app must NOT claim to know whether medication was physically swallowed.

## Non-negotiable medical safety

The app is a reminder and acknowledgement system, not a medical decision system.

Never:
- calculate a dose
- infer a dose
- change a dose
- recommend treatment
- advise a catch-up dose
- advise a double dose
- automatically reschedule a missed dose as medical advice
- implement insulin / glucose-driven dosing in v1
- implement PRN / "as needed" dosing in v1
- use AI/LLMs to make medication decisions
- scrape medication instructions from the internet

Medication content is entered by a trusted caregiver using the physician/pharmacist's written instructions.

V1 supports only medications for which the caregiver can enter:
- fixed medication display name
- fixed prescribed dosage text
- fixed prescribed instruction text
- fixed scheduled time(s)

Variable-dose / glucose-dependent / insulin / PRN medication must be marked UNSUPPORTED_BY_V1 and must not be represented as a normal "take this dose now" reminder.

## Important semantic rule

Remote family status must use these meanings:

- ACKNOWLEDGED_TAKEN = grandfather/caregiver explicitly pressed a confirmation that says the medication was taken
- DUE = reminder is currently due
- SNOOZED = user requested another reminder
- NO_CONFIRMATION = no acknowledgement was received within the configured response window
- DEVICE_OFFLINE / STALE = family view cannot know the current state because the primary device has not synchronized

Never display NO_CONFIRMATION as "Kesin almadı".
Preferred Turkish:
- "Aldığını onayladı"
- "Henüz onay yok"
- "Cihaz çevrimdışı — son durum bilinmiyor"
- "Son senkronizasyon: ..."

## Device roles

Use one Android codebase / one APK with explicit device roles.

### PRIMARY_MEDICATION_DEVICE
Grandfather's phone.

Responsibilities:
- owns the authoritative local medication schedule for v1
- stores all schedules and dose occurrences in Room
- schedules exact local alarms
- works without internet
- plays visible/audible medication reminder
- records acknowledgement locally first
- queues synchronization
- never depends on Firebase/Internet for alarm delivery

### CAREGIVER_DEVICE
Grandmother / mother / family phones.

Responsibilities:
- authenticated family access
- show synchronized schedule/status
- show last synchronization time
- receive family notifications when network is available
- optionally keep a local cached copy of the schedule
- optionally provide a secondary caregiver reminder

V1 caregiver devices are READ-ONLY for medication schedule editing.

Reason:
remote medication schedule edits plus intermittent internet create dangerous stale/conflict cases.
For v1, medication schedule changes happen physically on the PRIMARY_MEDICATION_DEVICE under caregiver mode.

Remote editing is a later feature and requires an explicit pending-change / primary-device-applied acknowledgement protocol.

## Grandmother's phone

Grandmother lives with grandfather and can use CAREGIVER_DEVICE mode.

Recommended optional behavior:
- when schedule is already synchronized, store it locally
- optionally create a secondary local reminder such as:
  "Dedenin ilaç zamanı"
- never treat this secondary reminder as the authoritative medication occurrence
- grandfather phone remains the primary medication-alarm device

## Connectivity model

The system is LOCAL-FIRST.

Internet loss must not stop grandfather's medication alarms.

Local database (Room) is the source of truth for the primary device's medication and occurrence state.

Cloud is used for:
- synchronization
- family visibility
- trusted-family access
- push hints/notifications

Cloud must not be on the critical path of the medication alarm.

When offline:
1. exact alarm still fires
2. acknowledgement is committed to Room immediately
3. a SyncOutbox item is persisted
4. family devices may show stale data
5. when connectivity returns, queued events synchronize

WorkManager may be used for deferred NETWORK SYNCHRONIZATION.
WorkManager must not be used as the primary exact medication alarm.

## Recommended cloud choice for this project

Prefer Firebase for the connected family layer because it minimizes server operations for a small private Android project:

- Firebase Authentication
- Cloud Firestore
- Firebase Cloud Messaging (FCM)
- optional Cloud Functions only if server-side fan-out is needed

Important:
- Firebase is not the source of truth for local alarm delivery
- Firestore security rules must be deny-by-default and family-scoped
- do not use unauthenticated/open Firestore rules
- cloud contains sensitive health-adjacent data, so sync only fields actually needed

If the owner explicitly wants to learn/build a custom backend later, Firebase can be replaced by a Spring Boot/PostgreSQL service without changing the local alarm domain.

## Android technical direction

- Kotlin
- Jetpack Compose
- Material 3 foundation
- ViewModel
- Coroutines / Flow / StateFlow
- Room
- DataStore for small preferences
- AlarmManager
- NotificationManager
- BroadcastReceiver
- Navigation Compose
- WorkManager only for sync/retry jobs
- Firebase Auth / Firestore / FCM for connected family functions
- manual DI initially unless complexity justifies Hilt

Recommended:
- compileSdk / targetSdk: Android 16 / API 36
- minSdk: choose after checking grandmother/family phone versions; default proposal is API 26 if no older phone requires support

Use stable dependencies only.

## Exact alarm decision

This app's core function is user-visible exact medication alarms.

For Android versions that support it, evaluate `USE_EXACT_ALARM` rather than blindly requesting `SCHEDULE_EXACT_ALARM`.
Only one should be declared for the applicable device version.

The coding agent must verify the current Android API requirements before implementing.

Always check actual scheduling capability before assuming it works.

Use an alarm-oriented API such as AlarmManager with wakeup semantics appropriate to an actual user alarm.

## Full-screen / lock-screen behavior

The desired alarm experience is:
- visible while locked when platform permissions allow
- large alarm screen
- notification remains as fallback
- audible reminder
- vibration where available/configured

On Android versions where full-screen intent access can be denied:
- check capability
- guide caregiver through setup if needed
- gracefully fall back to a high-importance heads-up notification
- never silently claim "full screen alarm OK" when capability is unavailable

## Samsung Galaxy A06 requirement

Samsung may place unused apps in Sleeping / Deep sleeping states.
Deep sleeping apps may not perform background work or deliver normal notifications.

Setup checklist on the target A06 must include:
Settings -> Battery / Device care -> Background usage limits
and ensure the medication app is NOT in Sleeping/Deep sleeping lists.
Where supported, add it to "Never sleeping apps".

The app should have a caregiver diagnostics screen explaining this Samsung-specific requirement.

## Reminder sound

User preference:
a phone-generated/synthetic Turkish voice such as:

"Dede, ilacını alma zamanı."

Reliability design:
- never rely exclusively on live TextToSpeech availability
- primary alarm must always have a bundled/fallback alarm sound
- if TTS is used, test Turkish voice availability and offline behavior on the A06
- preferred release solution is a bundled pre-generated Turkish voice audio file plus fallback alarm tone
- the spoken phrase must not include medication/dose advice unless it is static caregiver-entered text and has been specifically approved

## Grandfather UX

Keep grandfather mode extremely small.

Home:
- large date/time
- simple status
- next fixed-schedule medication
- no settings/edit buttons
- optional large "Aileyi Ara"

Alarm:
- "İLAÇ ZAMANI"
- scheduled time
- medication image if configured
- medication display name
- exact caregiver-entered dosage text
- exact caregiver-entered short instruction
- huge "İLACIMI ALDIM"
- optional "10 dakika sonra hatırlat"
- "Aileyi Ara"

After "İLACIMI ALDIM":
- "İlacını aldın mı?"
- "EVET, ALDIM"
- "HAYIR"

Avoid:
- "Skip dose" as a primary action
- jargon
- scrolling if avoidable
- icon-only important controls
- small close buttons
- shame/guilt language
- animations
- color-only state

## Accessibility

Assume vision/hearing/dexterity/attention can change.

Requirements:
- large typography
- Android font scaling
- strong contrast
- important targets normally 64dp+ high where practical
- never below Android's 48dp minimum target
- icon + text
- screen-reader semantics
- TalkBack smoke test
- haptic/vibration fallback
- clear focus order
- one primary action per screen
- short Turkish phrases

## Data model

### Medication
- id
- displayName
- dosageText
- instructionText
- optionalPhotoUri
- scheduleType = FIXED_ONLY in v1
- active
- createdAt
- updatedAt
- version

### MedicationSchedule
- id
- medicationId
- localTime
- daysOfWeek
- validFrom
- validUntil
- snoozeEnabled
- snoozeMinutes
- maxSnoozes
- version

### DoseOccurrence
- id
- medicationId
- scheduleId
- scheduledAt
- status
- acknowledgedAt
- acknowledgementActor
- snoozeCount
- lastAlertedAt
- createdAt
- updatedAt

### SyncOutbox
- id
- eventType
- aggregateId
- payloadVersion
- createdAt
- attemptCount
- lastAttemptAt
- syncState

### DeviceRegistration
- deviceId
- familyId
- role
- displayName
- appVersion
- lastSeenAt
- lastSuccessfulSyncAt

### FamilyMember
- firebaseUid
- familyId
- role = ADMIN | CAREGIVER_VIEWER
- displayName

## Suggested remote data

Cloud should contain only the information required by family features.

Example:
- family membership
- device last-seen status
- sanitized medication display/schedule snapshot
- dose occurrence acknowledgement/status
- event timestamps

Do not sync:
- caregiver PIN
- local diagnostic secrets
- unnecessary logs
- raw device identifiers beyond an app-generated random device ID
- unrelated phone data

## Authentication / authorization

Grandfather should never need to log in during normal use.

Initial pairing is performed by a trusted family member.

Caregiver family phones require authenticated access.

Security rules must restrict data by family membership and role.
`auth != null` alone is NOT sufficient.

Admin:
- manages family membership
- pairs devices
- can view all family data

Viewer/caregiver:
- can view family status
- no v1 remote medication schedule writes

Primary medication device:
- may write its device state and dose occurrence state for its paired family
- should not have broad access to unrelated families

## Sync rules

Local commit first.

A medication acknowledgement flow is:

1. user confirms
2. Room transaction updates DoseOccurrence
3. Room transaction inserts SyncOutbox event
4. UI immediately shows local confirmation
5. sync worker attempts cloud write when network exists
6. cloud write uses occurrence/event id for idempotency
7. family devices refresh
8. optional FCM is a notification/hint, not the source of truth

Avoid "last write wins" for medical acknowledgement semantics where it could erase meaningful state.
Prefer immutable event IDs / versioned state transitions.

## Family notification behavior

Possible push messages:
- "20:00 hatırlatması için 'aldım' onayı geldi."
- "20:00 hatırlatması için henüz onay yok."
- "Dede cihazı bir süredir çevrimdışı."

Do not claim:
- "İlacı kesin aldı."
- "İlacı kesin almadı."

FCM requires connectivity and may be delayed; family UI must always show timestamps / last sync status.

## V1 schedule editing safety

V1:
- caregiver unlocks configuration on grandfather's primary phone
- medication and fixed schedule are edited there
- app validates required fields
- old future alarm PendingIntents are cancelled
- new occurrence schedule is created transactionally
- a schedule snapshot syncs to family devices

Remote schedule editing is out of scope until a safe protocol is implemented.

## Caregiver PIN

Used to stop accidental changes on the grandfather's phone.
It is not a substitute for Firebase Authentication.

- store securely; never raw in source/logs
- support family-controlled reset/recovery
- do not make grandfather remember it

## Caregiver diagnostics

Show:

LOCAL REMINDER
- Exact alarm capability
- Notification permission
- Notification channel importance/sound
- Full-screen intent capability
- Alarm sound test
- Vibration test
- Next local alarm
- Last local alarm fired

SAMSUNG
- App sleeping/deep-sleep guidance
- "Never sleeping" setup instructions
- battery saver warning if relevant

SYNC
- Network: online/offline
- Last successful cloud sync
- Number of pending outbox events
- Family authentication state
- FCM registration state

TEST
- "1 dakika sonra yerel test alarmı"
- "Senkronizasyon testi"
- "Aile bildirimi testi"

## Release acceptance test

Physical A06 test is mandatory.

Test:
- screen on
- screen off
- locked
- app foreground/background
- app removed from recents
- process killed
- reboot
- battery saver
- Samsung sleeping/deep-sleep states
- Never sleeping exception
- notification permission denied/granted
- exact alarm capability
- full-screen capability denied/granted
- DND/silent
- volume low/high
- snooze
- acknowledgement
- duplicate taps
- duplicate receiver delivery
- offline acknowledgement
- reconnect + sync
- family phone stale status
- family phone receives synced status later
- large font
- TalkBack
- app update
- schedule edit/cancel/reschedule
- timezone/time change if device may experience it

## Phase plan

0. Repository + Android Studio setup
1. Static grandfather UI
2. Room data model + caregiver local configuration
3. Exact local alarm engine
4. Lock-screen/audio/snooze/acknowledgement
5. Samsung A06 reliability diagnostics + physical testing
6. Local outbox/sync architecture
7. Firebase Auth + family/device pairing
8. Firestore read-only family monitoring
9. FCM family notifications
10. Grandmother secondary reminder
11. Accessibility/reliability hardening
12. Signed private APK + update/install guide

Do NOT skip directly to Firebase.
The local medication alarm must be proven first.

## Remaining facts to verify before release

- exact model string shown in Settings
- grandmother's Android phone model/version
- other family phone Android versions
- exact fixed-dose medication schedule entered from written medical instructions
- which medications are variable/insulin/PRN and therefore excluded from v1
- caregiver phone number / call target
- whether DND must be overridden
- desired snooze duration / max snoozes
