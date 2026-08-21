# ARCHITECTURE.md

## Architecture decision

Use a single native Android app with two device roles and a local-first sync design.

### Why not a cloud-first app?

The grandfather lives where internet/mobile signal can be unreliable.
A network outage must never prevent an already-configured medication alarm.

Therefore:

PRIMARY DEVICE
AlarmManager -> local reminder -> Room -> outbox -> network later

NOT:

cloud -> push -> alarm

Push notifications are never the medication alarm trigger.

## High-level flow

```text
                     ┌──────────────────────────┐
                     │ Firebase Auth            │
                     │ Firestore                │
                     │ FCM                      │
                     └────────────┬─────────────┘
                                  │
                    sync when network exists
                                  │
             ┌────────────────────┴────────────────────┐
             │                                         │
┌────────────▼────────────┐               ┌────────────▼────────────┐
│ Grandfather phone       │               │ Family / grandmother   │
│ PRIMARY                 │               │ CAREGIVER              │
│                         │               │                         │
│ Room = local truth      │               │ local cache            │
│ AlarmManager            │               │ status view            │
│ Alarm UI + audio        │               │ optional local reminder│
│ acknowledgement        │               │ FCM notifications       │
│ SyncOutbox              │               │ last-sync visibility    │
└─────────────────────────┘               └─────────────────────────┘
```

## Local alarm path

```text
MedicationSchedule
      |
      v
OccurrencePlanner
      |
      v
Room: DoseOccurrence(SCHEDULED)
      |
      v
ReminderScheduler
      |
      v
AlarmManager exact alarm
      |
      v
ReminderReceiver
      |
      +--> high-importance alarm notification
      |
      +--> full-screen AlarmActivity if allowed
      |
      +--> bundled voice/fallback alarm sound
```

## Acknowledgement path

```text
"İLACIMI ALDIM"
      |
      v
simple confirmation
      |
      v
Room transaction
  DoseOccurrence -> ACKNOWLEDGED_TAKEN
  SyncOutbox     -> new event
      |
      v
cancel alarm/notification
      |
      v
network available?
  no  -> stop here safely; sync later
  yes -> WorkManager sync
      |
      v
Firestore
      |
      v
family devices
```

## Important component boundaries

### reminder
- ReminderScheduler interface
- AlarmManagerReminderScheduler
- OccurrencePlanner
- AlarmIntentFactory

### receiver
- MedicationAlarmReceiver
- BootReceiver
- TimeChangeReceiver if required
- ExactAlarmPermissionReceiver if used

### notification
- MedicationNotificationFactory
- NotificationChannelManager
- FullScreenCapabilityChecker

### audio
- AlarmAudioController
- bundled Turkish phrase
- fallback system/bundled tone
- optional vibration controller

### data/local
- Room database
- entities
- DAOs

### data/repository
- MedicationRepository
- DoseOccurrenceRepository
- SyncRepository

### sync
- SyncOutboxProcessor
- SyncWorker
- RemoteFamilyDataSource
- Firestore mapper

### auth
- FamilyAuthRepository
- DevicePairingService

### ui/grandfather
- Home
- Alarm
- Confirmation

### ui/caregiver
- PIN
- Medication list/configuration (primary device only)
- History
- Diagnostics
- Family status
- Pairing

## Source-of-truth rules

Primary-device medication state:
Room is authoritative.

Remote family projection:
Firestore is a synchronized projection of primary-device state.

Family-device UI:
local Firestore/Room cache may be shown, but must label stale state using last-sync metadata.

## Schedule conflict policy

V1 does not allow remote schedule writes.
This intentionally eliminates the highest-risk offline conflict.

When remote editing is introduced later:

REMOTE_REQUESTED -> PRIMARY_RECEIVED -> PRIMARY_VALIDATED -> PRIMARY_APPLIED -> FAMILY_CONFIRMED

A remote schedule must not be displayed as "active on grandfather phone" until PRIMARY_APPLIED exists.

## Alarm permission strategy

The coding agent must verify current API behavior.

Because exact alarms are a core function, evaluate `USE_EXACT_ALARM` on supported Android versions.
Do not declare both exact-alarm permissions for the same target path.

Always add runtime capability checks and diagnostics.

Full-screen intent must be capability-checked on modern Android.
Fallback is a high-priority visible notification.

## Samsung-specific reliability

Galaxy "Sleeping apps" and especially "Deep sleeping apps" can prevent needed background behavior.
Caregiver setup and diagnostics must explicitly cover Samsung Background usage limits.

Do not assume generic Android emulator testing is sufficient.
