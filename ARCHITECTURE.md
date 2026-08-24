# ARCHITECTURE.md

## Architecture decision

Yanında is a single native Android application with two device roles and a local-first alarm path:

- `ADMIN_DEVICE`: Berkant and the mother. Creates and publishes the desired fixed schedule, sees history, contacts, devices, and notifications.
- `ALARM_DEVICE`: Grandfather and grandmother. Independently downloads a validated schedule, persists it in Room, schedules local alarms, and acknowledges locally first.

There is no `PRIMARY` / `SECONDARY` alarm distinction. Every alarm device is independently responsible for its own local alarms.

## Core safety rule

```text
Firestore = canonical desired schedule
ALARM_DEVICE Room = authoritative state at medication time
```

Network, FCM, Firestore listeners, and WorkManager are never the primary medication alarm. A temporary cloud failure must leave the last known-good local schedule intact.

The app only displays the dosage/instruction text entered by an administrator. It never infers a dose, recommends treatment, creates catch-up/double-dose logic, or treats missing acknowledgement as proof that medication was not taken.

## High-level flow

```text
 ADMIN_DEVICE
 schedule editor
      |
      v
 Firestore desired schedule (versioned)
      |
      | listener / FCM hint / WorkManager retry
      v
 ALARM_DEVICE A ---------------- ALARM_DEVICE B
 validate + Room                 validate + Room
 AlarmManager                    AlarmManager
 local ACK + outbox              local ACK + outbox
      |                               |
      +------------- Firestore -------+
                         |
                         v
              ADMIN_DEVICE history
```

## Private-family provisioning

The family is fixed:

```text
familyId = sefer-family
familyName = Sefer Ailesi
```

First launch asks only who owns the phone:

- Dede telefonu -> `ALARM_DEVICE`
- Anneanne telefonu -> `ALARM_DEVICE`
- Berkant telefonu -> `ADMIN_DEVICE`
- Anne telefonu -> `ADMIN_DEVICE`

Each installation has its own anonymous Firebase Auth UID and app-generated local `deviceId`. Local emulator development uses the callable function `provisionPrivateFamilyDevice`. Production stays on the no-cost Firebase Spark plan: the app may create only its own pending approval request. The first administrator is authorized once in Firebase Console. After bootstrap, an existing authorized administrator can review pending phones in `Ayarlar -> Cihazlar`; Firestore permits that administrator to create only an authorization matching the pending UID, `deviceId`, family, and requested role. The client-provided role alone is never sufficient authority.

There is no user-visible e-mail/password, family creation, invitation code, pairing-code, or caregiver-PIN flow.

## Schedule application path

```text
Admin publishes desiredScheduleVersion
      |
      v
ALARM_DEVICE fetches schedule
      |
      v
validate fixed-schedule payload and version
      |
      v
Room transaction replaces applicable local schedule
      |
      v
OccurrencePlanner persists DoseOccurrence rows
      |
      v
cancel obsolete PendingIntents + schedule exact alarms
      |
      v
record appliedScheduleVersion
```

A failed or invalid remote update must not erase the last working local schedule.

## Same-time dose grouping

Medications due at the same instant are one logical dose group, one alarm, and one acknowledgement. The alarm screen lists every medication in that group.

The group identifier intentionally depends only on the scheduled instant:

```text
dose-group-${scheduledAt.toEpochMilli()}
```

Pending alarms are grouped by `(scheduledAt, nextReminderAt)`. Do not change these identities casually; they protect idempotency and duplicate-alarm behavior.

## Local alarm path

```text
Room DoseOccurrence(SCHEDULED)
      |
      v
AlarmManager exact alarm
      |
      v
MedicationAlarmReceiver
      |
      +--> MedicationAlarmAttentionService
      |      foreground mediaPlayback service
      |      USAGE_ALARM + vibration + looping fallback tone
      |      hard safety timeout
      |
      +--> high-importance alarm notification
      |
      +--> full-screen MedicationAlarmActivity when allowed
```

The foreground attention service is not stopped merely because the activity is recreated, backgrounded, or destroyed. It stops after acknowledgement/snooze or the hard safety timeout.

On Android versions that restrict full-screen intents, capability is checked with `canUseFullScreenIntent()` and a high-importance notification remains the fallback.

## Acknowledgement and convergence

```text
"İLACIMI ALDIM"
      |
      v
explicit confirmation
      |
      v
single Room transaction
  DoseOccurrence -> ACKNOWLEDGED_TAKEN
  SyncOutbox     -> versioned event
      |
      v
UI updates immediately; alarm attention stops
      |
      v
WorkManager retries Firestore delivery later
```

The cloud occurrence document ID is device-specific (`$deviceId--$localId`) while `occurrenceId` is the device-independent logical identity. Admin history merges multiple device reports by logical `occurrenceId`; an acknowledgement outranks a merely scheduled report.

`NO_CONFIRMATION` means only “Henüz onay yok.” It must never be presented as certain non-adherence.

## Important boundaries

### Local data and alarm

- `data/local`: Room database, migrations, DAOs
- `data/repository`: medication, occurrence, and outbox repositories
- `reminder`: coordinator, exact-alarm scheduler, attention service policy
- `receiver`: alarm, reboot, and exact-alarm capability receivers
- `notification`: alarm channel and full-screen fallback

### Remote schedule and synchronization

- `schedule`: Admin publish, alarm-device download/application, schedule WorkManager
- `sync`: durable outbox processing and Firestore occurrence projection
- `family/private`: fixed-family device profile and callable provisioning
- `family`: monitoring, contacts, devices, and history reads
- `push`: FCM registration and non-authoritative sync hints

### UI

- `ui/grandfather`: calm home and alarm presentation
- `ui/alarm`: real alarm activity and confirmation routing
- `ui/admin`: dashboard, medication editing, history, contacts, devices, notifications
- `ui/setup`: four-profile private-device selection

Composables contain presentation; repositories and coordinators own data/business behavior.

## Android and Samsung release gates

Exact alarms are a core app function. Current platform permission behavior must be verified against official Android documentation whenever alarm code changes.

Emulator success is not sufficient. Samsung Galaxy A06 / Android 16 must pass:

- normal, locked, and screen-off alarm
- process killed
- reboot rescheduling
- Battery Saver
- Sleeping apps / Deep sleeping apps
- Never sleeping exception
- notification and full-screen capability fallbacks
- audible alarm volume and vibration
- offline alarm and later ACK synchronization

Do not call alarm reliability complete until this physical test matrix is recorded.
