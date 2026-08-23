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
  doc,
  getDoc,
  getDocs,
  serverTimestamp,
  setDoc,
  writeBatch,
} from "firebase/firestore";

const projectId = "demo-yaninda";
let testEnvironment;

function authenticatedDatabase(uid, provider = "password") {
  return testEnvironment.authenticatedContext(uid, {
    firebase: { sign_in_provider: provider },
  }).firestore();
}

function unauthenticatedDatabase() {
  return testEnvironment.unauthenticatedContext().firestore();
}

async function seedFamily({
  familyId = "family-a",
  adminUid = "admin-a",
  viewerUid,
  primaryUid,
  primaryDeviceId = "primary-device-a",
} = {}) {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const database = context.firestore();
    const now = Timestamp.now();
    await setDoc(doc(database, "families", familyId), {
      familyId,
      name: "Test Ailesi",
      createdByUid: adminUid,
      createdAt: now,
      version: 1,
    });
    await setDoc(doc(database, "families", familyId, "members", adminUid), {
      uid: adminUid,
      familyId,
      role: "ADMIN",
      displayName: "Yönetici",
      joinedAt: now,
      pairingInviteId: null,
      deviceId: "admin-device-a",
      version: 1,
    });
    await setDoc(doc(database, "users", adminUid, "memberships", familyId), {
      familyId,
      familyName: "Test Ailesi",
      role: "ADMIN",
      displayName: "Yönetici",
      joinedAt: now,
      version: 1,
    });

    if (viewerUid) {
      await setDoc(doc(database, "families", familyId, "members", viewerUid), {
        uid: viewerUid,
        familyId,
        role: "CAREGIVER_VIEWER",
        displayName: "Bakıcı",
        joinedAt: now,
        pairingInviteId: "INVITE-VIEWER",
        deviceId: "viewer-device-a",
        version: 1,
      });
      await setDoc(doc(database, "users", viewerUid, "memberships", familyId), {
        familyId,
        familyName: "Test Ailesi",
        role: "CAREGIVER_VIEWER",
        displayName: "Bakıcı",
        joinedAt: now,
        version: 1,
      });
      await setDoc(doc(database, "families", familyId, "devices", "viewer-device-a"), {
        deviceId: "viewer-device-a",
        familyId,
        ownerUid: viewerUid,
        role: "CAREGIVER_DEVICE",
        displayName: "Bakıcı telefonu",
        appVersion: "1.0",
        lastSeenAt: now,
        lastSuccessfulSyncAt: null,
        pairingInviteId: "INVITE-VIEWER",
        version: 1,
      });
    }

    if (primaryUid) {
      await setDoc(doc(database, "families", familyId, "devices", primaryDeviceId), {
        deviceId: primaryDeviceId,
        familyId,
        ownerUid: primaryUid,
        role: "PRIMARY_MEDICATION_DEVICE",
        displayName: "Dede telefonu",
        appVersion: "1.0",
        lastSeenAt: now,
        lastSuccessfulSyncAt: null,
        pairingInviteId: "INVITE-PRIMARY",
        version: 1,
      });
    }
  });
}

function occurrenceDocument({
  occurrenceId = "occurrence-a",
  sourceDeviceId = "primary-device-a",
  sourceEventId = "event-a",
  status = "ACKNOWLEDGED_TAKEN",
  version = 3,
} = {}) {
  const now = Timestamp.now();
  return {
    occurrenceId,
    medicationDisplayName: "Test ilacı",
    scheduledAt: now,
    scheduledLocalTime: "20:00",
    scheduledZoneId: "Europe/Istanbul",
    status,
    acknowledgedAt: status === "ACKNOWLEDGED_TAKEN" ? now : null,
    acknowledgementActor: status === "ACKNOWLEDGED_TAKEN" ? "GRANDFATHER" : null,
    lastAlertedAt: status === "SCHEDULED" ? null : now,
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
  sourceDeviceId = "primary-device-a",
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

function publishOccurrence(database, occurrence, event) {
  const batch = writeBatch(database);
  batch.set(
    doc(
      database,
      "families",
      "family-a",
      "occurrences",
      occurrence.occurrenceId,
    ),
    occurrence,
  );
  batch.set(
    doc(database, "families", "family-a", "syncEvents", event.eventId),
    event,
  );
  return batch.commit();
}

describe("Yaninda Firestore security rules", () => {
  before(async () => {
    const rules = await readFile(new URL("../firestore.rules", import.meta.url), "utf8");
    testEnvironment = await initializeTestEnvironment({
      projectId,
      firestore: { rules },
    });
  });

  afterEach(async () => {
    await testEnvironment.clearFirestore();
  });

  after(async () => {
    await testEnvironment.cleanup();
  });

  it("denies unauthenticated access", async () => {
    await seedFamily();
    const database = unauthenticatedDatabase();

    await assertFails(getDoc(doc(database, "families", "family-a")));
  });

  it("does not treat authentication alone as family membership", async () => {
    await seedFamily();
    const database = authenticatedDatabase("unrelated-user");

    await assertFails(getDoc(doc(database, "families", "family-a")));
  });

  it("allows a permanent account to create its family atomically", async () => {
    const uid = "new-admin";
    const familyId = "new-family";
    const deviceId = "new-admin-device";
    const database = authenticatedDatabase(uid);
    const batch = writeBatch(database);
    batch.set(doc(database, "families", familyId), {
      familyId,
      name: "Yeni Aile",
      createdByUid: uid,
      createdAt: serverTimestamp(),
      version: 1,
    });
    batch.set(doc(database, "families", familyId, "members", uid), {
      uid,
      familyId,
      role: "ADMIN",
      displayName: "Aile yöneticisi",
      joinedAt: serverTimestamp(),
      pairingInviteId: null,
      deviceId,
      version: 1,
    });
    batch.set(doc(database, "users", uid, "memberships", familyId), {
      familyId,
      familyName: "Yeni Aile",
      role: "ADMIN",
      displayName: "Aile yöneticisi",
      joinedAt: serverTimestamp(),
      version: 1,
    });
    batch.set(doc(database, "families", familyId, "devices", deviceId), {
      deviceId,
      familyId,
      ownerUid: uid,
      role: "CAREGIVER_DEVICE",
      displayName: "Bakıcı telefonu",
      appVersion: "1.0",
      lastSeenAt: serverTimestamp(),
      lastSuccessfulSyncAt: null,
      pairingInviteId: null,
      version: 1,
    });

    await assertSucceeds(batch.commit());
  });

  it("does not allow an anonymous account to create an admin family", async () => {
    const uid = "anonymous-admin";
    const database = authenticatedDatabase(uid, "anonymous");

    await assertFails(setDoc(doc(database, "families", "bad-family"), {
      familyId: "bad-family",
      name: "Güvensiz Aile",
      createdByUid: uid,
      createdAt: serverTimestamp(),
      version: 1,
    }));
  });

  it("lets an admin create a short-lived pairing invitation but never list invitations", async () => {
    await seedFamily();
    const database = authenticatedDatabase("admin-a");
    const inviteId = "ABCD2345EFGH6789";

    await assertSucceeds(setDoc(doc(database, "pairingInvites", inviteId), {
      inviteId,
      familyId: "family-a",
      targetRole: "PRIMARY_MEDICATION_DEVICE",
      createdByUid: "admin-a",
      createdAt: serverTimestamp(),
      expiresAt: Timestamp.fromMillis(Date.now() + 15 * 60 * 1000),
      claimedByUid: null,
      claimedDeviceId: null,
      claimedAt: null,
      version: 1,
    }));
    await assertFails(getDocs(collection(database, "pairingInvites")));
  });

  it("claims a primary invitation only with the paired device in the same batch", async () => {
    await seedFamily();
    const inviteId = "PRIMARY2345CODE";
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "pairingInvites", inviteId), {
        inviteId,
        familyId: "family-a",
        targetRole: "PRIMARY_MEDICATION_DEVICE",
        createdByUid: "admin-a",
        createdAt: Timestamp.now(),
        expiresAt: Timestamp.fromMillis(Date.now() + 15 * 60 * 1000),
        claimedByUid: null,
        claimedDeviceId: null,
        claimedAt: null,
        version: 1,
      });
    });
    const uid = "primary-user";
    const deviceId = "primary-device";
    const database = authenticatedDatabase(uid, "anonymous");
    const batch = writeBatch(database);
    batch.update(doc(database, "pairingInvites", inviteId), {
      claimedByUid: uid,
      claimedDeviceId: deviceId,
      claimedAt: serverTimestamp(),
      version: 2,
    });
    batch.set(doc(database, "families", "family-a", "devices", deviceId), {
      deviceId,
      familyId: "family-a",
      ownerUid: uid,
      role: "PRIMARY_MEDICATION_DEVICE",
      displayName: "Dede telefonu",
      appVersion: "1.0",
      lastSeenAt: serverTimestamp(),
      lastSuccessfulSyncAt: null,
      pairingInviteId: inviteId,
      version: 1,
    });

    await assertSucceeds(batch.commit());

    const secondDatabase = authenticatedDatabase("other-primary", "anonymous");
    await assertFails(setDoc(
      doc(secondDatabase, "families", "family-a", "devices", "other-device"),
      {
        deviceId: "other-device",
        familyId: "family-a",
        ownerUid: "other-primary",
        role: "PRIMARY_MEDICATION_DEVICE",
        displayName: "Başka telefon",
        appVersion: "1.0",
        lastSeenAt: serverTimestamp(),
        lastSuccessfulSyncAt: null,
        pairingInviteId: inviteId,
        version: 1,
      },
    ));
  });

  it("allows only the paired primary device to publish occurrence state", async () => {
    await seedFamily({ primaryUid: "primary-user" });
    const primaryDatabase = authenticatedDatabase("primary-user", "anonymous");
    const unrelatedDatabase = authenticatedDatabase("unrelated-primary", "anonymous");

    await assertSucceeds(publishOccurrence(
      primaryDatabase,
      occurrenceDocument(),
      syncEventDocument(),
    ));
    await assertFails(publishOccurrence(
      unrelatedDatabase,
      occurrenceDocument({
        occurrenceId: "occurrence-b",
        sourceEventId: "event-b",
      }),
      syncEventDocument({ eventId: "event-b", occurrenceId: "occurrence-b" }),
    ));
  });

  it("accepts a newer matching projection but rejects extra fields and replayed versions", async () => {
    await seedFamily({ primaryUid: "primary-user" });
    const database = authenticatedDatabase("primary-user", "anonymous");
    const scheduled = occurrenceDocument({
      sourceEventId: "event-scheduled",
      status: "SCHEDULED",
      version: 1,
    });
    await assertSucceeds(publishOccurrence(
      database,
      scheduled,
      syncEventDocument({
        eventId: "event-scheduled",
        eventType: "DOSE_OCCURRENCE_SCHEDULED",
        aggregateVersion: 1,
      }),
    ));

    const due = {
      ...occurrenceDocument({
        sourceEventId: "event-due",
        status: "DUE",
        version: 2,
      }),
      scheduledAt: scheduled.scheduledAt,
    };
    await assertSucceeds(publishOccurrence(
      database,
      due,
      syncEventDocument({
        eventId: "event-due",
        eventType: "DOSE_OCCURRENCE_DUE",
        aggregateVersion: 2,
      }),
    ));

    await assertFails(publishOccurrence(
      database,
      { ...due, sourceEventId: "event-extra", version: 3, untrustedField: true },
      syncEventDocument({
        eventId: "event-extra",
        eventType: "DOSE_OCCURRENCE_DUE",
        aggregateVersion: 3,
      }),
    ));
    await assertFails(publishOccurrence(
      database,
      { ...due, sourceEventId: "event-replay" },
      syncEventDocument({
        eventId: "event-replay",
        eventType: "DOSE_OCCURRENCE_DUE",
        aggregateVersion: 2,
      }),
    ));
  });

  it("gives family viewers read-only occurrence access", async () => {
    await seedFamily({ viewerUid: "viewer-a", primaryUid: "primary-user" });
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
      await setDoc(
        doc(context.firestore(), "families", "family-a", "occurrences", "occurrence-a"),
        { ...occurrenceDocument(), syncedAt: Timestamp.now() },
      );
    });
    const viewerDatabase = authenticatedDatabase("viewer-a");

    await assertSucceeds(getDoc(
      doc(viewerDatabase, "families", "family-a", "occurrences", "occurrence-a"),
    ));
    await assertFails(setDoc(
      doc(viewerDatabase, "families", "family-a", "occurrences", "occurrence-b"),
      occurrenceDocument({ occurrenceId: "occurrence-b" }),
    ));
    await assertFails(setDoc(
      doc(viewerDatabase, "families", "family-a", "medications", "medication-a"),
      { displayName: "Uzaktan değişiklik" },
    ));
  });

  it("lets only the owning caregiver device manage its FCM installation registration", async () => {
    await seedFamily({ viewerUid: "viewer-a", primaryUid: "primary-user" });
    const registrationId = "a".repeat(64);
    const registration = {
      registrationId,
      familyId: "family-a",
      installationId: "installation_id_viewer_123",
      deviceId: "viewer-device-a",
      ownerUid: "viewer-a",
      platform: "ANDROID",
      appVersion: "1.0",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
      version: 1,
    };
    const viewerDatabase = authenticatedDatabase("viewer-a");
    const primaryDatabase = authenticatedDatabase("primary-user", "anonymous");

    await assertSucceeds(setDoc(
      doc(viewerDatabase, "families", "family-a", "pushRegistrations", registrationId),
      registration,
    ));
    await assertFails(setDoc(
      doc(primaryDatabase, "families", "family-a", "pushRegistrations", "b".repeat(64)),
      {
        ...registration,
        registrationId: "b".repeat(64),
        installationId: "installation_id_primary_123",
        deviceId: "primary-device-a",
        ownerUid: "primary-user",
      },
    ));
  });
});
