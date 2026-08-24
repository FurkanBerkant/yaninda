# PROJECT_CONTEXT.md

## Project

Private family-only Android medication reminder and caregiver monitoring application.

Primary physical test device:
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
- DEVICE_OFFLINE / STALE = family view cannot know the current state because an alarm device has not synchronized

Never display NO_CONFIRMATION as "Kesin almadı".
Preferred Turkish:
- "Aldığını onayladı"
- "Henüz onay yok"
- "Cihaz çevrimdışı — son durum bilinmiyor"
- "Son senkronizasyon: ..."

## Device roles

Use one Android codebase / one APK with explicit device roles.

### ADMIN_DEVICE
Berkant's and the mother's phones.

Responsibilities:
- create, edit, deactivate, and publish the canonical desired fixed schedule
- view synchronized history and acknowledgement timestamps
- manage family call contacts
- view alarm-device freshness and notification settings
- never schedule medication alarms

### ALARM_DEVICE
Grandfather's and grandmother's phones.

Responsibilities:
- independently download and validate the desired schedule
- preserve the last known-good local schedule if remote application fails
- store the applied schedule and occurrences in Room
- schedule exact local alarms
- work without internet
- record acknowledgement locally first and queue synchronization
- never depend on Firebase/Internet for alarm delivery

There is no PRIMARY/SECONDARY distinction between alarm devices. Firestore is canonical for the desired schedule; each alarm device's Room state is authoritative at medication time.

## Grandmother's phone

Grandmother lives with grandfather and uses `ALARM_DEVICE` mode. Her phone independently stores the validated schedule and creates the same local medication alarms. It is not a secondary reminder and does not depend on the grandfather's phone.

## Connectivity model

The system is LOCAL-FIRST.

Internet loss must not stop grandfather's medication alarms.

Local database (Room) is authoritative for each alarm device's applied schedule and occurrence state at medication time.

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
- role = ADMIN
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
- local diagnostic secrets
- unnecessary logs
- raw device identifiers beyond an app-generated random device ID
- unrelated phone data

## Authentication / authorization

Grandfather should never need to log in during normal use.

The family is fixed as `sefer-family`. On first launch a trusted family member selects one of four private device profiles: Dede, Anneanne, Berkant, or Anne.

Every installation uses its own anonymous Firebase Auth UID and app-generated device ID. Local development uses the callable emulator function `provisionPrivateFamilyDevice`. The no-cost Spark production path instead creates a restricted `deviceApprovalRequests/{uid}` request and waits for a trusted operator to create the matching `deviceAuthorizations/{uid}` document in Firebase Console. Firestore rules bind that approval to the exact UID, device ID, family, and role before allowing the device to create its own projection. The client-provided role is never sufficient authority.

There is no user-visible e-mail/password, family creation, invitation, or pairing-code flow.

Security rules must restrict data by family membership and role.
`auth != null` alone is NOT sufficient.

Admin:
- publishes the fixed desired schedule
- manages family call contacts
- can view all family data

Alarm device:
- may read the desired schedule for its provisioned family
- may write only its own device state and occurrence projection
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

## V2 schedule editing safety

- only `ADMIN_DEVICE` exposes schedule editing
- only fixed schedules supported by the medical boundary may be published
- Firestore stores a versioned canonical desired schedule
- every `ALARM_DEVICE` validates the complete payload before applying it
- Room replacement and local occurrence planning are transactional
- obsolete future PendingIntents are cancelled and the new schedule is applied idempotently
- a failed/partial/invalid remote update never deletes the last known-good local schedule
- the grandfather-facing UI has no settings, admin controls, or hidden edit gestures

There is no caregiver PIN in the private-family V2 flow. Device-level backend authorization protects cloud access; physical possession of an approved Admin phone is the private-family administration boundary.

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

The active implementation status and release gates are maintained in `IMPLEMENTATION_PLAN.md`. The code has reached V2 private-family monitoring and acknowledgement convergence in the emulator. It is not release-complete until the Samsung Galaxy A06 physical matrix, production provisioning hardening, and signed APK verification pass.

## Remaining facts to verify before release

- exact model string shown in Settings
- grandmother's Android phone model/version
- other family phone Android versions
- exact fixed-dose medication schedule entered from written medical instructions
- which medications are variable/insulin/PRN and therefore excluded from v1
- caregiver phone number / call target
- whether DND must be overridden
- desired snooze duration / max snoozes
