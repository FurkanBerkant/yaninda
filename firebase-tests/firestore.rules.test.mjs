import { readFile } from "node:fs/promises";
import { after, afterEach, before, describe, it } from "node:test";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  Timestamp,
  deleteDoc,
  doc,
  getDoc,
  serverTimestamp,
  setDoc,
  updateDoc,
  writeBatch,
} from "firebase/firestore";

const projectId = "demo-yaninda";
const familyId = "sefer-family";
let testEnvironment;

function authenticatedDatabase(
  uid,
  provider = "anonymous",
) {
  return testEnvironment
    .authenticatedContext(uid, {
      firebase: {
        sign_in_provider: provider,
      },
    })
    .firestore();
}

function unauthenticatedDatabase() {
  return testEnvironment
    .unauthenticatedContext()
    .firestore();
}

async function seedProvisionedFamily({
  adminUid = "admin-a",
  adminDeviceId = "admin-device-a",
  alarmUid,
  alarmDeviceId = "alarm-device-a",
} = {}) {
  await testEnvironment.withSecurityRulesDisabled(
    async (context) => {
      const database = context.firestore();
      const now = Timestamp.now();

      await setDoc(
        doc(database, "families", familyId),
        {
          familyId,
          name: "Sefer Ailesi",
          createdByUid: adminUid,
          createdAt: now,
          version: 1,
        },
      );

      await seedMemberAndDevice({
        database,
        now,
        uid: adminUid,
        deviceId: adminDeviceId,
        memberRole: "ADMIN",
        deviceRole: "ADMIN_DEVICE",
        displayName: "Berkant",
      });

      if (alarmUid) {
        await seedDevice({
          database,
          now,
          uid: alarmUid,
          deviceId: alarmDeviceId,
          deviceRole: "ALARM_DEVICE",
          displayName: "Dede telefonu",
        });

        await setDoc(
          doc(database, "deviceAccess", alarmUid),
          {
            uid: alarmUid,
            familyId,
            deviceId: alarmDeviceId,
            role: "ALARM_DEVICE",
            updatedAt: now,
          },
        );
      }
    },
  );
}

async function seedMemberAndDevice({
  database,
  now,
  uid,
  deviceId,
  memberRole,
  deviceRole,
  displayName,
}) {
  await setDoc(
    doc(
      database,
      "families",
      familyId,
      "members",
      uid,
    ),
    {
      uid,
      familyId,
      role: memberRole,
      displayName,
      joinedAt: now,
      deviceId,
      version: 1,
    },
  );

  await setDoc(
    doc(
      database,
      "users",
      uid,
      "memberships",
      familyId,
    ),
    {
      familyId,
      familyName: "Sefer Ailesi",
      role: memberRole,
      displayName,
      joinedAt: now,
      version: 1,
    },
  );

  await seedDevice({
    database,
    now,
    uid,
    deviceId,
    deviceRole,
    displayName,
  });
}

async function seedDevice({
  database,
  now,
  uid,
  deviceId,
  deviceRole,
  displayName,
}) {
  await setDoc(
    doc(
      database,
      "families",
      familyId,
      "devices",
      deviceId,
    ),
    {
      deviceId,
      familyId,
      ownerUid: uid,
      role: deviceRole,
      displayName,
      appVersion: "1.0",
      lastSeenAt: now,
      lastSuccessfulSyncAt: null,
      version: 1,
    },
  );
}

function occurrenceDocument({
  occurrenceId = "occurrence-a",
  sourceDeviceId = "alarm-device-a",
  sourceEventId = "event-a",
  status = "ACKNOWLEDGED_TAKEN",
  version = 3,
  scheduledAt = Timestamp.now(),
} = {}) {
  const now = Timestamp.now();

  return {
    occurrenceId,
    medicationDisplayName: "Test ilacı",
    scheduledAt,
    scheduledLocalTime: "20:00",
    scheduledZoneId: "Europe/Istanbul",
    status,
    acknowledgedAt:
      status === "ACKNOWLEDGED_TAKEN"
        ? now
        : null,
    acknowledgementActor:
      status === "ACKNOWLEDGED_TAKEN"
        ? "GRANDFATHER"
        : null,
    lastAlertedAt:
      status === "SCHEDULED"
        ? null
        : now,
    updatedAt: now,
    version,
    sourceDeviceId,
    sourceEventId,
    syncedAt: serverTimestamp(),
  };
}

function syncEventDocument({
  eventId = "event-a",
  eventType = "DOSE_OCCURRENCE_ACKNOWLEDGED",
  occurrenceId = "occurrence-a",
  aggregateVersion = 3,
  sourceDeviceId = "alarm-device-a",
} = {}) {
  return {
    eventId,
    eventType,
    aggregateId: occurrenceId,
    aggregateVersion,
    payloadVersion: 1,
    sourceDeviceId,
    createdAt: Timestamp.now(),
    deliveredAt: serverTimestamp(),
  };
}

function publishOccurrence(
  database,
  occurrence,
  event,
) {
  const batch = writeBatch(database);
  const reportId =
    `${occurrence.sourceDeviceId}--${occurrence.occurrenceId}`;

  batch.set(
    doc(
      database,
      "families",
      familyId,
      "occurrences",
      reportId,
    ),
    occurrence,
  );

  batch.set(
    doc(
      database,
      "families",
      familyId,
      "syncEvents",
      event.eventId,
    ),
    event,
  );

  return batch.commit();
}

function pushRegistration({
  registrationId,
  installationId,
  deviceId,
  ownerUid,
  role,
}) {
  return {
    registrationId,
    familyId,
    installationId,
    deviceId,
    ownerUid,
    role,
    platform: "ANDROID",
    appVersion: "1.0",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    version: 1,
  };
}

async function publishTestSchedule(database) {
  const batch = writeBatch(database);

  batch.set(
    doc(
      database,
      "families",
      familyId,
      "scheduleState",
      "current",
    ),
    {
      desiredVersion: 1,
      updatedAt: serverTimestamp(),
      updatedByUid: "admin-a",
      schemaVersion: 1,
    },
  );

  batch.set(
    doc(
      database,
      "families",
      familyId,
      "scheduleVersions",
      "1",
    ),
    {
      familyId,
      scheduleVersion: 1,
      schemaVersion: 1,
      publishedAt: serverTimestamp(),
      publishedByUid: "admin-a",
      medications: [],
    },
  );

  return batch.commit();
}

describe(
  "Yaninda Firestore V2 security rules",
  () => {
    before(async () => {
      const rules = await readFile(
        new URL(
          "../firestore.rules",
          import.meta.url,
        ),
        "utf8",
      );

      testEnvironment =
        await initializeTestEnvironment({
          projectId,
          firestore: {
            rules,
          },
        });
    });

    afterEach(async () => {
      await testEnvironment.clearFirestore();
    });

    after(async () => {
      await testEnvironment.cleanup();
    });

    it(
      "denies unauthenticated access",
      async () => {
        await seedProvisionedFamily();

        await assertFails(
          getDoc(
            doc(
              unauthenticatedDatabase(),
              "families",
              familyId,
            ),
          ),
        );
      },
    );

    it(
      "does not treat authentication alone as family membership",
      async () => {
        await seedProvisionedFamily();

        await assertFails(
          getDoc(
            doc(
              authenticatedDatabase("unrelated-user"),
              "families",
              familyId,
            ),
          ),
        );
      },
    );

    it(
      "keeps all device provisioning server-only",
      async () => {
        await seedProvisionedFamily();
        const database =
          authenticatedDatabase(
            "admin-a",
            "password",
          );

        await assertFails(
          setDoc(
            doc(database, "families", "other-family"),
            {
              familyId: "other-family",
              name: "Başka Aile",
              createdByUid: "admin-a",
              createdAt: serverTimestamp(),
              version: 1,
            },
          ),
        );

        await assertFails(
          setDoc(
            doc(
              database,
              "families",
              familyId,
              "devices",
              "unapproved-device",
            ),
            {
              deviceId: "unapproved-device",
              familyId,
              ownerUid: "admin-a",
              role: "ADMIN_DEVICE",
              displayName: "Onaysız telefon",
              appVersion: "1.0",
              lastSeenAt: serverTimestamp(),
              lastSuccessfulSyncAt: null,
              version: 1,
            },
          ),
        );

        await assertFails(
          setDoc(
            doc(
              database,
              "pairingInvites",
              "LEGACY-INVITE",
            ),
            {
              familyId,
            },
          ),
        );
      },
    );

    it(
      "allows an approved admin to publish schedules and manage contacts",
      async () => {
        await seedProvisionedFamily();
        const database =
          authenticatedDatabase("admin-a");

        await assertSucceeds(
          publishTestSchedule(database),
        );

        await assertSucceeds(
          setDoc(
            doc(
              database,
              "families",
              familyId,
              "contacts",
              "contact-a",
            ),
            {
              contactId: "contact-a",
              familyId,
              displayName: "Berkant",
              phoneNumber: "+905551112233",
              isDefault: true,
              updatedAt: serverTimestamp(),
            },
          ),
        );
      },
    );

    it(
      "lets an alarm device read but never edit the desired schedule",
      async () => {
        await seedProvisionedFamily({
          alarmUid: "alarm-user",
        });

        await publishTestSchedule(
          authenticatedDatabase("admin-a"),
        );

        const alarmDatabase =
          authenticatedDatabase("alarm-user");

        await assertSucceeds(
          getDoc(
            doc(
              alarmDatabase,
              "families",
              familyId,
              "scheduleState",
              "current",
            ),
          ),
        );

        // Outbox transactions must be allowed to read their own not-yet-created
        // scoped documents before creating them.
        await assertSucceeds(
          getDoc(
            doc(
              alarmDatabase,
              "families",
              familyId,
              "occurrences",
              "alarm-device-a--missing-occurrence",
            ),
          ),
        );

        await assertSucceeds(
          getDoc(
            doc(
              alarmDatabase,
              "families",
              familyId,
              "syncEvents",
              "alarm-device-a--missing-event",
            ),
          ),
        );

        await assertFails(
          updateDoc(
            doc(
              alarmDatabase,
              "families",
              familyId,
              "scheduleState",
              "current",
            ),
            {
              desiredVersion: 2,
              updatedAt: serverTimestamp(),
              updatedByUid: "alarm-user",
            },
          ),
        );
      },
    );

    it(
      "keeps alarm devices outside family membership while allowing operational reads",
      async () => {
        await seedProvisionedFamily({
          alarmUid: "alarm-user",
        });

        const adminDatabase =
          authenticatedDatabase("admin-a");
        const alarmDatabase =
          authenticatedDatabase("alarm-user");

        await assertSucceeds(
          setDoc(
            doc(
              adminDatabase,
              "families",
              familyId,
              "contacts",
              "contact-a",
            ),
            {
              contactId: "contact-a",
              familyId,
              displayName: "Berkant",
              phoneNumber: "+905551112233",
              isDefault: true,
              updatedAt: serverTimestamp(),
            },
          ),
        );

        await assertSucceeds(
          getDoc(
            doc(
              alarmDatabase,
              "families",
              familyId,
              "contacts",
              "contact-a",
            ),
          ),
        );

        await assertSucceeds(
          getDoc(
            doc(
              alarmDatabase,
              "families",
              familyId,
              "devices",
              "alarm-device-a",
            ),
          ),
        );

        await assertFails(
          getDoc(
            doc(
              alarmDatabase,
              "families",
              familyId,
            ),
          ),
        );

        await assertFails(
          getDoc(
            doc(
              alarmDatabase,
              "families",
              familyId,
              "members",
              "admin-a",
            ),
          ),
        );

        await assertFails(
          getDoc(
            doc(
              alarmDatabase,
              "families",
              familyId,
              "devices",
              "admin-device-a",
            ),
          ),
        );

        await assertFails(
          getDoc(
            doc(
              alarmDatabase,
              "families",
              familyId,
              "occurrences",
              "admin-device-a--missing-occurrence",
            ),
          ),
        );
      },
    );

    it(
      "revokes alarm-device reads when its live device registration is deleted",
      async () => {
        await seedProvisionedFamily({
          alarmUid: "alarm-user",
        });

        const adminDatabase =
          authenticatedDatabase("admin-a");
        const alarmDatabase =
          authenticatedDatabase("alarm-user");

        await assertSucceeds(
          setDoc(
            doc(
              adminDatabase,
              "families",
              familyId,
              "contacts",
              "contact-a",
            ),
            {
              contactId: "contact-a",
              familyId,
              displayName: "Berkant",
              phoneNumber: "+905551112233",
              isDefault: true,
              updatedAt: serverTimestamp(),
            },
          ),
        );

        await assertSucceeds(
          deleteDoc(
            doc(
              adminDatabase,
              "families",
              familyId,
              "devices",
              "alarm-device-a",
            ),
          ),
        );

        await assertFails(
          getDoc(
            doc(
              alarmDatabase,
              "families",
              familyId,
              "contacts",
              "contact-a",
            ),
          ),
        );
      },
    );

    it(
      "allows only the owning alarm device to publish occurrence state",
      async () => {
        await seedProvisionedFamily({
          alarmUid: "alarm-user",
        });

        await assertSucceeds(
          publishOccurrence(
            authenticatedDatabase("alarm-user"),
            occurrenceDocument(),
            syncEventDocument(),
          ),
        );

        await assertFails(
          publishOccurrence(
            authenticatedDatabase("unrelated-user"),
            occurrenceDocument({
              occurrenceId: "occurrence-b",
              sourceEventId: "event-b",
            }),
            syncEventDocument({
              eventId: "event-b",
              occurrenceId: "occurrence-b",
            }),
          ),
        );

        await assertFails(
          publishOccurrence(
            authenticatedDatabase("admin-a"),
            occurrenceDocument({
              occurrenceId: "occurrence-c",
              sourceDeviceId: "admin-device-a",
              sourceEventId: "event-c",
            }),
            syncEventDocument({
              eventId: "event-c",
              occurrenceId: "occurrence-c",
              sourceDeviceId: "admin-device-a",
            }),
          ),
        );
      },
    );

    it(
      "rejects extra projection fields and replayed versions",
      async () => {
        await seedProvisionedFamily({
          alarmUid: "alarm-user",
        });

        const database =
          authenticatedDatabase("alarm-user");
        const scheduledAt = Timestamp.now();
        const scheduled = occurrenceDocument({
          sourceEventId: "event-scheduled",
          status: "SCHEDULED",
          version: 1,
          scheduledAt,
        });

        await assertSucceeds(
          publishOccurrence(
            database,
            scheduled,
            syncEventDocument({
              eventId: "event-scheduled",
              eventType:
                "DOSE_OCCURRENCE_SCHEDULED",
              aggregateVersion: 1,
            }),
          ),
        );

        const due = occurrenceDocument({
          sourceEventId: "event-due",
          status: "DUE",
          version: 2,
          scheduledAt,
        });

        await assertSucceeds(
          publishOccurrence(
            database,
            due,
            syncEventDocument({
              eventId: "event-due",
              eventType:
                "DOSE_OCCURRENCE_DUE",
              aggregateVersion: 2,
            }),
          ),
        );

        await assertFails(
          publishOccurrence(
            database,
            {
              ...due,
              sourceEventId: "event-extra",
              version: 3,
              untrustedField: true,
            },
            syncEventDocument({
              eventId: "event-extra",
              eventType:
                "DOSE_OCCURRENCE_DUE",
              aggregateVersion: 3,
            }),
          ),
        );

        await assertFails(
          publishOccurrence(
            database,
            {
              ...due,
              sourceEventId: "event-replay",
            },
            syncEventDocument({
              eventId: "event-replay",
              eventType:
                "DOSE_OCCURRENCE_DUE",
              aggregateVersion: 2,
            }),
          ),
        );
      },
    );

    it(
      "lets admins read history but never forge acknowledgements",
      async () => {
        await seedProvisionedFamily({
          alarmUid: "alarm-user",
        });

        await assertSucceeds(
          publishOccurrence(
            authenticatedDatabase("alarm-user"),
            occurrenceDocument(),
            syncEventDocument(),
          ),
        );

        const adminDatabase =
          authenticatedDatabase("admin-a");

        await assertSucceeds(
          getDoc(
            doc(
              adminDatabase,
              "families",
              familyId,
              "occurrences",
              "alarm-device-a--occurrence-a",
            ),
          ),
        );

        await assertFails(
          setDoc(
            doc(
              adminDatabase,
              "families",
              familyId,
              "occurrences",
              "admin-device-a--forged",
            ),
            occurrenceDocument({
              occurrenceId: "forged",
              sourceDeviceId: "admin-device-a",
              sourceEventId: "forged-event",
            }),
          ),
        );
      },
    );

    it(
      "allows each approved device to manage only its own FCM registration",
      async () => {
        await seedProvisionedFamily({
          alarmUid: "alarm-user",
        });

        const adminRegistrationId = "a".repeat(64);
        const alarmRegistrationId = "b".repeat(64);

        await assertSucceeds(
          setDoc(
            doc(
              authenticatedDatabase("admin-a"),
              "families",
              familyId,
              "pushRegistrations",
              adminRegistrationId,
            ),
            pushRegistration({
              registrationId: adminRegistrationId,
              installationId:
                "installation_admin_123",
              deviceId: "admin-device-a",
              ownerUid: "admin-a",
              role: "ADMIN_DEVICE",
            }),
          ),
        );

        await assertSucceeds(
          setDoc(
            doc(
              authenticatedDatabase("alarm-user"),
              "families",
              familyId,
              "pushRegistrations",
              alarmRegistrationId,
            ),
            pushRegistration({
              registrationId: alarmRegistrationId,
              installationId:
                "installation_alarm_123",
              deviceId: "alarm-device-a",
              ownerUid: "alarm-user",
              role: "ALARM_DEVICE",
            }),
          ),
        );

        const forgedRegistrationId = "c".repeat(64);

        await assertFails(
          setDoc(
            doc(
              authenticatedDatabase("admin-a"),
              "families",
              familyId,
              "pushRegistrations",
              forgedRegistrationId,
            ),
            pushRegistration({
              registrationId: forgedRegistrationId,
              installationId:
                "installation_forged_123",
              deviceId: "alarm-device-a",
              ownerUid: "admin-a",
              role: "ALARM_DEVICE",
            }),
          ),
        );
      },
    );

    it(
      "allows only a device owner to update its heartbeat",
      async () => {
        await seedProvisionedFamily({
          alarmUid: "alarm-user",
        });

        await assertSucceeds(
          updateDoc(
            doc(
              authenticatedDatabase("alarm-user"),
              "families",
              familyId,
              "devices",
              "alarm-device-a",
            ),
            {
              lastSeenAt: serverTimestamp(),
              lastSuccessfulSyncAt:
                serverTimestamp(),
              version: 2,
            },
          ),
        );

        await assertFails(
          updateDoc(
            doc(
              authenticatedDatabase("admin-a"),
              "families",
              familyId,
              "devices",
              "alarm-device-a",
            ),
            {
              lastSeenAt: serverTimestamp(),
              version: 3,
            },
          ),
        );
      },
    );
  },
);
