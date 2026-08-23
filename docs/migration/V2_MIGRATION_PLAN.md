# V2 MIGRATION PLAN

## 1. Current architecture
The existing codebase is built around two primary roles: `PRIMARY_MEDICATION_DEVICE` (the grandfather's phone) and `CAREGIVER_DEVICE` (family phones).
- The `PRIMARY_MEDICATION_DEVICE` owns the authoritative local schedule, schedules exact AlarmManager alarms, and works offline.
- The `CAREGIVER_DEVICE` is a read-only viewer for family members and optionally creates non-authoritative "secondary reminders".
- Routing logic in `MainActivity.kt` and `MainRoutingPolicy.kt` relies on these two roles. There is an unfinished routing fix in the working tree that attempts to fix a bug where exiting the caregiver PIN setup early leaves the primary device on an empty home screen.

## 2. Target architecture
The V2 model transitions to two new roles: `ADMIN_DEVICE` and `ALARM_DEVICE`.
- **`ADMIN_DEVICE`**: Used by the trusted family administrator to create medication schedules, view history, and monitor device status. It does not trigger exact alarms.
- **`ALARM_DEVICE`**: Used by both the grandfather and grandmother. These are independent devices that download the desired schedule, validate it, persist it locally, and schedule exact medication alarms. They must continue to operate flawlessly offline and sync acknowledgements later.

## 3. Reusable components
- **Local Alarm Infrastructure**: AlarmManager scheduling, receivers, boot recovery, and notification logic.
- **Room Data Model**: The core of Medication and DoseOccurrence, though they will need updates for grouping.
- **SyncOutbox & WorkManager**: The local-first queue and retry mechanism for synchronization.
- **Firebase/Firestore Foundations**: Device pairing, authentication, and push notifications (FCM).
- **The Unfinished Routing Fix**: The intent of the uncommitted routing changes in `MainRoutingPolicy` is valid (preventing premature setup completion) and can be adapted to V2.

## 4. Required refactors
- Change `DeviceRole` enum to `ADMIN_DEVICE` and `ALARM_DEVICE`.
- Adapt the initial setup UI (`DeviceRoleSetupScreen`) and routing (`MainRoutingPolicy`) to the new roles, resolving the unfinished bug gracefully.
- Move schedule configuration out of the ALARM_DEVICE and exclusively to the ADMIN_DEVICE.
- Implement robust schedule downloading, validation, and versioning (`appliedScheduleVersion` vs `desiredScheduleVersion`) on the ALARM_DEVICE.
- Group multiple medications scheduled for the exact same time into a single `DoseGroupOccurrence`.

## 5. Components that become obsolete
- The `PRIMARY_MEDICATION_DEVICE` and `CAREGIVER_DEVICE` enums and branching logic.
- The concept of a "secondary reminder" for the grandmother (she will now use a standard `ALARM_DEVICE`).
- Any UI/UX logic that assumes only one device generates an authoritative alarm.

## 6. Migration phases
- **Phase 1: Role & Routing Migration**: Introduce the `ADMIN_DEVICE` and `ALARM_DEVICE` roles. Refactor the setup screens and routing policy, incorporating the previous agent's uncommitted routing fix safely.
- **Phase 2: Admin Device Core**: Enable the `ADMIN_DEVICE` to create and publish schedule versions to Firestore.
- **Phase 3: Alarm Device Sync & Versioning**: Enable `ALARM_DEVICE`s to download, validate, and apply schedule versions safely without breaking existing alarms.
- **Phase 4: Same-Time Grouping**: Update the local occurrence logic to group same-time medications into a single `DoseGroupOccurrence` and update the alarm UI.
- **Phase 5: Acknowledgement Convergence**: Ensure offline acknowledgements from multiple `ALARM_DEVICE`s converge safely when connectivity is restored.

## 7. Data/schema implications
- `DeviceRole` changes impact Room entities and Firestore documents.
- Firestore schedule models must introduce versioning (`desiredScheduleVersion`, `appliedScheduleVersion`).
- The Room database needs a concept of `DoseGroupOccurrence` to group medications for the same time slot.
- Acknowledgements must track which `ALARM_DEVICE` (sourceDeviceId) confirmed the dose.

## 8. Main risks
- **Lost Alarms During Sync**: A partially downloaded or corrupted remote schedule could break the known-good local schedule.
- **Duplicate Alarms**: Failing to properly group same-time medications or failing to cancel old PendingIntents during an update.
- **Setup Limbo**: Devices getting stuck in a partially set up state due to routing refactors.
- **Schema Migration**: Breaking existing local Room data during the transition to grouped occurrences.

## 9. Minimal validation strategy
- Run targeted unit tests for `MainRoutingPolicy` and occurrence planning.
- Manually verify the setup and routing flows on an emulator.
- Use the physical Samsung Galaxy A06 to rigorously test exact alarm scheduling, offline behavior, and sleeping app restrictions after alarm logic is touched.
- Test schedule replacement by pushing a new version while the ALARM_DEVICE is offline, then reconnecting.

## 10. Git/checkpoint strategy
- **DO NOT DISCARD EXISTING CHANGES.**
- Checkpoint 1: Use `git stash` to safely store the current uncommitted changes.
- Checkpoint 2: Create and checkout a new branch (e.g., `v2-migration`).
- Checkpoint 3: Commit this migration plan document.
- Checkpoint 4: Use `git stash pop` to re-apply the previous agent's work.
- Proceed with Phase 1 by adapting those working tree changes to the V2 architecture and committing the result.

## 11. Definition of done
- The V2 device roles (`ADMIN_DEVICE` and `ALARM_DEVICE`) are fully implemented and functional.
- The `ADMIN_DEVICE` can create schedules; `ALARM_DEVICE`s can securely download, apply, and alert based on them.
- Medications at the same time appear as a single alarm.
- All alarms function perfectly offline.
- Acknowledgements sync to Firestore eventually and display correctly on the `ADMIN_DEVICE`.

## 12. Progress Log
- **Phase 1 (Role & Routing Migration):** COMPLETED. Enum values renamed to `ADMIN_DEVICE` and `ALARM_DEVICE`. Setup screens updated. `MainRoutingPolicy` retains the setup bugfix logic but now applies to `ALARM_DEVICE`. `AdminHomeRoute` scaffold created and wired into `MainActivity`.
- **Phase 2 (Admin Device Core) & Beyond:** BLOCKED. Proceeding to implement the Admin schedule publishing (Phase 2), Alarm Device downloading (Phase 3), and Same-Time Grouping (Phase 4) requires a unified Room data migration to introduce `DoseGroupOccurrence` and `desiredScheduleVersion`/`appliedScheduleVersion`. Per the safety rules, work is stopped here because this data migration could destroy existing grandfather data if not planned and verified carefully.
