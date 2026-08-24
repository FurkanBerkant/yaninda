import { readFile } from "node:fs/promises";
import { after, afterEach, before, describe, it } from "node:test";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  Timestamp,
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
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

      await seedDeviceAuthorization({
        database,
        now,
        uid: adminUid,
        deviceId: adminDeviceId,
        deviceRole: "ADMIN_DEVICE",
      });

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
        await seedDeviceAuthorization({
          database,
          now,
          uid: alarmUid,
          deviceId: alarmDeviceId,
          deviceRole: "ALARM_DEVICE",
        });

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

async function seedDeviceAuthorization({
  database,
  now,
  uid,
  deviceId,
  deviceRole,
  active = true,
}) {
  await setDoc(
    doc(database, "deviceAuthorizations", uid),
    {
      uid,
      familyId,
      deviceId,
      role: deviceRole,
      active,
      approvedAt: now,
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
      "lets a signed-in device request approval but never approve itself",
      async () => {
        const database =
          authenticatedDatabase("pending-user");

        await assertSucceeds(
          setDoc(
            doc(
              database,
              "deviceApprovalRequests",
              "pending-user",
            ),
            {
              uid: "pending-user",
              familyId,
              deviceId: "pending-device",
              requestedRole: "ADMIN_DEVICE",
              displayName: "Berkant telefonu",
              appVersion: "1.0",
              status: "PENDING",
              requestedAt: Timestamp.now(),
              updatedAt: serverTimestamp(),
            },
          ),
        );

        await assertFails(
          setDoc(
            doc(
              database,
              "deviceAuthorizations",
              "pending-user",
            ),
            {
              uid: "pending-user",
              familyId,
              deviceId: "pending-device",
              role: "ADMIN_DEVICE",
              active: true,
            },
          ),
        );
      },
    );

    it(
      "lets an existing admin review and approve an exact pending device binding",
      async () => {
        await seedProvisionedFamily();
        await testEnvironment.withSecurityRulesDisabled(
          async (context) => {
            await setDoc(
              doc(
                context.firestore(),
                "deviceApprovalRequests",
                "grandfather-user",
              ),
              {
                uid: "grandfather-user",
                familyId,
                deviceId: "grandfather-device",
                requestedRole: "ALARM_DEVICE",
                displayName: "Dede telefonu",
                appVersion: "1.0.1",
                status: "PENDING",
                requestedAt: Timestamp.now(),
                updatedAt: Timestamp.now(),
              },
            );
          },
        );

        const adminDatabase = authenticatedDatabase("admin-a");

        await assertSucceeds(
          getDocs(
            collection(
              adminDatabase,
              "deviceApprovalRequests",
            ),
          ),
        );

        await assertSucceeds(
          setDoc(
            doc(
              adminDatabase,
              "deviceAuthorizations",
              "grandfather-user",
            ),
            {
              uid: "grandfather-user",
              familyId,
              deviceId: "grandfather-device",
              role: "ALARM_DEVICE",
              active: true,
              approvedAt: serverTimestamp(),
              approvedByUid: "admin-a",
            },
          ),
        );
      },
    );

    it(
      "rejects admin approval when device or role differs from the pending request",
      async () => {
        await seedProvisionedFamily();
        await testEnvironment.withSecurityRulesDisabled(
          async (context) => {
            await setDoc(
              doc(
                context.firestore(),
                "deviceApprovalRequests",
                "grandmother-user",
              ),
              {
                uid: "grandmother-user",
                familyId,
                deviceId: "grandmother-device",
                requestedRole: "ALARM_DEVICE",
                displayName: "Anneanne telefonu",
                appVersion: "1.0.1",
                status: "PENDING",
                requestedAt: Timestamp.now(),
                updatedAt: Timestamp.now(),
              },
            );
          },
        );

        const adminDatabase = authenticatedDatabase("admin-a");
        const authorization = doc(
          adminDatabase,
          "deviceAuthorizations",
          "grandmother-user",
        );

        await assertFails(
          setDoc(authorization, {
            uid: "grandmother-user",
            familyId,
            deviceId: "different-device",
            role: "ALARM_DEVICE",
            active: true,
            approvedAt: serverTimestamp(),
            approvedByUid: "admin-a",
          }),
        );

        await assertFails(
          setDoc(authorization, {
            uid: "grandmother-user",
            familyId,
            deviceId: "grandmother-device",
            role: "ADMIN_DEVICE",
            active: true,
            approvedAt: serverTimestamp(),
            approvedByUid: "admin-a",
          }),
        );
      },
    );

    it(
      "rejects approval when the pending request identity is malformed",
      async () => {
        await seedProvisionedFamily();
        await testEnvironment.withSecurityRulesDisabled(
          async (context) => {
            await setDoc(
              doc(
                context.firestore(),
                "deviceApprovalRequests",
                "tampered-user",
              ),
              {
                uid: "different-user",
                familyId: "different-family",
                deviceId: "tampered-device",
                requestedRole: "ALARM_DEVICE",
                displayName: "Tanımsız telefon",
                appVersion: "1.0.2",
                status: "APPROVED",
                requestedAt: Timestamp.now(),
                updatedAt: Timestamp.now(),
              },
            );
          },
        );

        await assertFails(
          setDoc(
            doc(
              authenticatedDatabase("admin-a"),
              "deviceAuthorizations",
              "tampered-user",
            ),
            {
              uid: "tampered-user",
              familyId,
              deviceId: "tampered-device",
              role: "ALARM_DEVICE",
              active: true,
              approvedAt: serverTimestamp(),
              approvedByUid: "admin-a",
            },
          ),
        );
      },
    );

    it(
      "completes pending request through admin approval, alarm provisioning, and schedule read",
      async () => {
        await seedProvisionedFamily();
        const pendingDatabase = authenticatedDatabase("new-alarm-user");

        await assertSucceeds(
          setDoc(
            doc(
              pendingDatabase,
              "deviceApprovalRequests",
              "new-alarm-user",
            ),
            {
              uid: "new-alarm-user",
              familyId,
              deviceId: "new-alarm-device",
              requestedRole: "ALARM_DEVICE",
              displayName: "Dede telefonu",
              appVersion: "1.0.2",
              status: "PENDING",
              requestedAt: Timestamp.now(),
              updatedAt: serverTimestamp(),
            },
          ),
        );

        const adminDatabase = authenticatedDatabase("admin-a");
        await assertSucceeds(
          setDoc(
            doc(
              adminDatabase,
              "deviceAuthorizations",
              "new-alarm-user",
            ),
            {
              uid: "new-alarm-user",
              familyId,
              deviceId: "new-alarm-device",
              role: "ALARM_DEVICE",
              active: true,
              approvedAt: serverTimestamp(),
              approvedByUid: "admin-a",
            },
          ),
        );

        await assertSucceeds(
          setDoc(
            doc(
              pendingDatabase,
              "families",
              familyId,
              "devices",
              "new-alarm-device",
            ),
            {
              deviceId: "new-alarm-device",
              familyId,
              ownerUid: "new-alarm-user",
              role: "ALARM_DEVICE",
              displayName: "Dede telefonu",
              appVersion: "1.0.2",
              lastSeenAt: serverTimestamp(),
              lastSuccessfulSyncAt: null,
              version: 1,
            },
          ),
        );

        await assertSucceeds(
          setDoc(
            doc(
              pendingDatabase,
              "deviceAccess",
              "new-alarm-user",
            ),
            {
              uid: "new-alarm-user",
              familyId,
              deviceId: "new-alarm-device",
              role: "ALARM_DEVICE",
              updatedAt: serverTimestamp(),
            },
          ),
        );

        await assertSucceeds(
          publishTestSchedule(adminDatabase),
        );
        await assertSucceeds(
          getDoc(
            doc(
              pendingDatabase,
              "families",
              familyId,
              "scheduleState",
              "current",
            ),
          ),
        );
      },
    );

    it(
      "never lets an alarm device list requests or approve another phone",
      async () => {
        await seedProvisionedFamily({
          alarmUid: "alarm-a",
        });
        const alarmDatabase = authenticatedDatabase("alarm-a");

        await assertFails(
          getDocs(
            collection(
              alarmDatabase,
              "deviceApprovalRequests",
            ),
          ),
        );

        await assertFails(
          setDoc(
            doc(
              alarmDatabase,
              "deviceAuthorizations",
              "other-device-user",
            ),
            {
              uid: "other-device-user",
              familyId,
              deviceId: "other-device",
              role: "ADMIN_DEVICE",
              active: true,
              approvedAt: serverTimestamp(),
              approvedByUid: "alarm-a",
            },
          ),
        );
      },
    );

    it(
      "lets a manually authorized admin provision only its bound device",
      async () => {
        await testEnvironment.withSecurityRulesDisabled(
          async (context) => {
            await seedDeviceAuthorization({
              database: context.firestore(),
              now: Timestamp.now(),
              uid: "new-admin",
              deviceId: "new-admin-device",
              deviceRole: "ADMIN_DEVICE",
            });
          },
        );

        const database =
          authenticatedDatabase("new-admin");
        const batch = writeBatch(database);

        batch.set(
          doc(database, "families", familyId),
          {
            familyId,
            name: "Sefer Ailesi",
            createdByUid: "new-admin",
            createdAt: serverTimestamp(),
            version: 1,
          },
        );
        batch.set(
          doc(
            database,
            "families",
            familyId,
            "members",
            "new-admin",
          ),
          {
            uid: "new-admin",
            familyId,
            role: "ADMIN",
            displayName: "Berkant telefonu",
            joinedAt: serverTimestamp(),
            deviceId: "new-admin-device",
            version: 1,
          },
        );
        batch.set(
          doc(
            database,
            "users",
            "new-admin",
            "memberships",
            familyId,
          ),
          {
            familyId,
            familyName: "Sefer Ailesi",
            role: "ADMIN",
            displayName: "Berkant telefonu",
            joinedAt: serverTimestamp(),
            version: 1,
          },
        );
        batch.set(
          doc(
            database,
            "families",
            familyId,
            "devices",
            "new-admin-device",
          ),
          {
            deviceId: "new-admin-device",
            familyId,
            ownerUid: "new-admin",
            role: "ADMIN_DEVICE",
            displayName: "Berkant telefonu",
            appVersion: "1.0",
            lastSeenAt: serverTimestamp(),
            lastSuccessfulSyncAt: null,
            version: 1,
          },
        );

        await assertSucceeds(batch.commit());

        await assertFails(
          setDoc(
            doc(
              database,
              "families",
              familyId,
              "devices",
              "different-device",
            ),
            {
              deviceId: "different-device",
              familyId,
              ownerUid: "new-admin",
              role: "ADMIN_DEVICE",
              displayName: "Başka telefon",
              appVersion: "1.0",
              lastSeenAt: serverTimestamp(),
              lastSuccessfulSyncAt: null,
              version: 1,
            },
          ),
        );
      },
    );

    it(
      "denies unapproved provisioning and legacy pairing",
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
      "allows an admin to remove another device authorization and access",
      async () => {
        await seedProvisionedFamily({ alarmUid: "alarm-user" });
        await testEnvironment.withSecurityRulesDisabled(async (context) => {
          const now = Timestamp.now();
          await setDoc(
            doc(context.firestore(), "deviceApprovalRequests", "alarm-user"),
            {
              uid: "alarm-user",
              familyId,
              deviceId: "alarm-device-a",
              requestedRole: "ALARM_DEVICE",
              displayName: "Dede telefonu",
              appVersion: "1.0.3",
              status: "PENDING",
              requestedAt: now,
              updatedAt: now,
            },
          );
        });
        const adminDatabase = authenticatedDatabase("admin-a");
        const batch = writeBatch(adminDatabase);
        batch.delete(doc(adminDatabase, "families", familyId, "devices", "alarm-device-a"));
        batch.delete(doc(adminDatabase, "deviceAccess", "alarm-user"));
        batch.delete(doc(adminDatabase, "deviceAuthorizations", "alarm-user"));
        batch.delete(doc(adminDatabase, "deviceApprovalRequests", "alarm-user"));
        await assertSucceeds(batch.commit());
        await assertFails(
          deleteDoc(doc(adminDatabase, "deviceAuthorizations", "admin-a")),
        );
      },
    );

    it(
      "revokes admin access when its manual authorization is disabled",
      async () => {
        await seedProvisionedFamily();
        const adminDatabase =
          authenticatedDatabase("admin-a");

        await assertSucceeds(
          getDoc(
            doc(
              adminDatabase,
              "families",
              familyId,
            ),
          ),
        );

        await testEnvironment.withSecurityRulesDisabled(
          async (context) => {
            await updateDoc(
              doc(
                context.firestore(),
                "deviceAuthorizations",
                "admin-a",
              ),
              {
                active: false,
              },
            );
          },
        );

        await assertFails(
          getDoc(
            doc(
              adminDatabase,
              "families",
              familyId,
            ),
          ),
        );

        await assertFails(
          updateDoc(
            doc(
              adminDatabase,
              "families",
              familyId,
              "devices",
              "admin-device-a",
            ),
            {
              lastSeenAt: serverTimestamp(),
              version: 2,
            },
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
