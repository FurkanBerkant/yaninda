# MASTER_PROMPT.md

You are the lead Android engineer for a private, safety-sensitive family medication reminder project.

I am a software engineer but I have no Android development experience.

Before doing anything:
read `PROJECT_CONTEXT.md`, `ARCHITECTURE.md`, `AGENTS.md`, `IMPLEMENTATION_PLAN.md`, `SCREEN_SPEC.md`, and `LOCATION_SAFETY.md`.

## Known product facts

- Primary device: Samsung Galaxy A06, Android 16.
- Grandfather has cognitive impairment/dementia and diabetes.
- Grandmother lives with him.
- Internet/mobile connectivity can be intermittent.
- App will also exist on grandmother/family Android phones.
- Family wants to see synchronized acknowledgement status.
- Some diabetes medication may be variable-dose / insulin / measurement-dependent.
- V1 must therefore support ONLY explicitly fixed-time/fixed-dose medication reminders.
- Desired spoken reminder: "Dede, ilacını alma zamanı."
- App is private/sideloaded; no Play Store release is currently planned.

## Architectural decision

Do NOT design a cloud-triggered medication alarm.

The grandfather's primary phone must remain fully capable of firing already-configured alarms while offline.

Use:

PRIMARY:
Room -> occurrence planner -> AlarmManager -> local alarm UI/audio -> local acknowledgement -> SyncOutbox

NETWORK:
WorkManager -> Firebase Auth / Firestore / FCM

FAMILY:
read synchronized status + last-sync timestamps

One APK, multiple device roles.

V1 family devices are read-only for medication schedule changes.

## Safety language

Never turn "no button press" into "he definitely did not take the medication".

Use concepts such as:
- "Aldığını onayladı"
- "Henüz onay yok"
- "Cihaz çevrimdışı — son durum bilinmiyor"

## Your first task

DO NOT create the whole app.

If the repository is empty:
implement ONLY Phase 0 from `IMPLEMENTATION_PLAN.md`.

If the repository already contains code:
inspect it first and determine which phase it represents.

Before editing, respond with:

1. Current repository state
2. Exact scope of the next phase
3. Files you expect to create/change
4. Android concepts I need for this phase, explained briefly in Turkish
5. Risks / assumptions

Then implement only that phase.

After implementation:
- run build
- run relevant tests
- run lint where appropriate
- inspect git diff
- give exact local run/test steps
- explain new Android concepts briefly
- STOP

Do not start the next phase until I explicitly approve it.

Never commit/push/branch/reset/restore/clean unless I explicitly ask.
