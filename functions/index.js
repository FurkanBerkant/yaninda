import {
  initializeApp,
} from "firebase-admin/app";

import {
  FieldValue,
  getFirestore,
} from "firebase-admin/firestore";

import {
  getMessaging,
} from "firebase-admin/messaging";

import {
  onDocumentCreated,
  onDocumentWritten,
} from "firebase-functions/v2/firestore";

import {
  HttpsError,
  onCall,
} from "firebase-functions/v2/https";

import {
  logger,
} from "firebase-functions";

import {
  defineString,
} from "firebase-functions/params";

import {
  buildFamilyNotificationMessage,
  buildScheduleChangedMessage,
} from "./notificationPayload.js";

import {
  isPrivateProvisioningAuthorized,
  isValidPrivateDeviceId,
  parseUidAllowList,
  privateDeviceAccessProjection,
} from "./privateProvisioningPolicy.js";

initializeApp();

/*
 * Boş varsayılan güvenli biçimde fail-closed davranır:
 * production'da hiçbir gerçek UID kendisini ADMIN yapamaz.
 * Emulator ADMIN provisioning ise aşağıdaki runtime kontrolünde
 * bilinçli olarak allow-list dışında tutulur.
 */
const approvedAdminUids =
  defineString(
    "YANINDA_ADMIN_UIDS",
    {
      default: "",
    },
  );

/*
 * Alarm cihazları da aile verisini okuyabildiği için production'da
 * ayrı bir allow-list ile fail-closed provision edilir.
 */
const approvedAlarmUids =
  defineString(
    "YANINDA_ALARM_UIDS",
    {
      default: "",
    },
  );

/*
 * Dede/ALARM_DEVICE tarafından oluşturulan
 * acknowledgement/no-confirmation eventlerini
 * aile ADMIN telefonlarına bildirir.
 */
export const notifyFamilyOnOccurrenceEvent =
  onDocumentCreated(
    {
      document:
        "families/{familyId}/syncEvents/{eventId}",

      region:
        "europe-west1",

      /*
       * Firestore authoritative olmaya
       * devam ediyor.
       *
       * Push transient hata yüzünden otomatik
       * retry edilirse kullanıcıya aynı
       * bildirimin birkaç kez gitmesini
       * istemiyoruz.
       */
      retry: false,
    },

    async (event) => {
      const eventData =
        event.data?.data();

      if (!eventData) {
        return;
      }

      const familyId =
        event.params.familyId;

      const database =
        getFirestore();

      const familyReference =
        database
          .collection("families")
          .doc(familyId);

      const occurrenceReportId =
        `${eventData.sourceDeviceId}--${eventData.aggregateId}`;

      const occurrence =
        await familyReference
          .collection("occurrences")
          .doc(occurrenceReportId)
          .get();

      const message =
        buildFamilyNotificationMessage(
          eventData,
          occurrence.data(),
          familyId,
        );

      if (!message) {
        return;
      }

      /*
       * Artık pushRegistrations içinde
       * ALARM_DEVICE da bulunabiliyor.
       *
       * Bu nedenle acknowledgement
       * bildirimlerini kesin olarak yalnızca
       * ADMIN_DEVICE'lara gönderiyoruz.
       */
      const registrations =
        await familyReference
          .collection(
            "pushRegistrations"
          )
          .where(
            "role",
            "==",
            "ADMIN_DEVICE",
          )
          .limit(500)
          .get();

      const fids =
        collectInstallationIds(
          registrations
        );

      if (fids.length === 0) {
        return;
      }

      const response =
        await getMessaging()
          .sendEachForMulticast({
            ...message,
            fids,
          });

      logger.info(
        "Family status notification batch completed.",
        {
          successCount:
            response.successCount,

          failureCount:
            response.failureCount,
        },
      );
    },
  );

/*
 * ADMIN medication programını kaydettiğinde:
 *
 * scheduleState/current
 * desiredVersion: 10 -> 11
 *
 * değişimini yakalar.
 *
 * Bu function schedule içeriğini FCM'e koymaz.
 * ALARM_DEVICE'a yalnızca:
 *
 * "yeni program olabilir"
 *
 * hint'i gönderir.
 */
export const notifyAlarmDevicesOnScheduleChanged =
  onDocumentWritten(
    {
      document:
        "families/{familyId}/scheduleState/current",

      region:
        "europe-west1",

      /*
       * FCM kaçırılırsa periodic WorkManager
       * zaten safety-net.
       *
       * Bu yüzden duplicate push retry
       * davranışına ihtiyacımız yok.
       */
      retry: false,
    },

    async (event) => {
      const beforeData =
        event.data?.before?.data();

      const afterData =
        event.data?.after?.data();

      if (!afterData) {
        return;
      }

      const previousVersion =
        beforeData?.desiredVersion;

      const desiredVersion =
        afterData.desiredVersion;

      /*
       * desiredVersion değişmediyse
       * gereksiz push gönderme.
       */
      if (
        previousVersion ===
        desiredVersion
      ) {
        return;
      }

      const familyId =
        event.params.familyId;

      const message =
        buildScheduleChangedMessage(
          afterData,
          familyId,
        );

      if (!message) {
        logger.warn(
          "Schedule change push payload was rejected.",
          {
            desiredVersion,
          },
        );

        return;
      }

      const database =
        getFirestore();

      const familyReference =
        database
          .collection("families")
          .doc(familyId);

      /*
       * Schedule hint kesinlikle yalnızca
       * alarm telefonlarına gidiyor.
       */
      const registrations =
        await familyReference
          .collection(
            "pushRegistrations"
          )
          .where(
            "role",
            "==",
            "ALARM_DEVICE",
          )
          .limit(500)
          .get();

      const fids =
        collectInstallationIds(
          registrations
        );

      if (fids.length === 0) {
        logger.info(
          "No alarm-device push registration for schedule change.",
          {
            desiredVersion,
          },
        );

        return;
      }

      const response =
        await getMessaging()
          .sendEachForMulticast({
            ...message,
            fids,
          });

      logger.info(
        "Schedule change notification batch completed.",
        {
          desiredVersion,

          successCount:
            response.successCount,

          failureCount:
            response.failureCount,
        },
      );
    },
  );

/*
 * Aynı installation yanlışlıkla iki
 * registration dokümanında kalmış olsa bile
 * duplicate push göndermeyelim.
 */
function collectInstallationIds(
  registrations,
) {
  return [
    ...new Set(
      registrations.docs
        .map(
          (document) =>
            document.get(
              "installationId"
            )
        )
        .filter(
          (value) =>
            typeof value === "string" &&
            value.length > 0
        ),
    ),
  ];
}

/*
 * Private/family-only installation provisioning.
 *
 * User-visible e-mail/password and pairing codes are intentionally removed.
 * Every installation still gets its own Firebase anonymous UID and deviceId.
 *
 * Production ADMIN_DEVICE authorization:
 *   YANINDA_ADMIN_UIDS="uid1,uid2"
 *
 * Emulator ADMIN_DEVICE provisioning is allowed automatically so local
 * two-emulator testing remains simple.
 */
export const provisionPrivateFamilyDevice =
  onCall(
    {
      region: "europe-west1",
    },
    async (request) => {
      const uid = request.auth?.uid;

      if (!uid) {
        throw new HttpsError(
          "unauthenticated",
          "A Firebase device session is required.",
        );
      }

      const familyId = request.data?.familyId;
      const role = request.data?.role;
      const deviceId = request.data?.deviceId;
      const displayName = request.data?.displayName;
      const appVersion = request.data?.appVersion;

      if (familyId !== "sefer-family") {
        throw new HttpsError(
          "invalid-argument",
          "Unknown private family.",
        );
      }

      if (
        role !== "ADMIN_DEVICE" &&
        role !== "ALARM_DEVICE"
      ) {
        throw new HttpsError(
          "invalid-argument",
          "Invalid device role.",
        );
      }

      const accessProjection =
        privateDeviceAccessProjection(role);

      if (!isValidPrivateDeviceId(deviceId)) {
        throw new HttpsError(
          "invalid-argument",
          "Invalid device id.",
        );
      }

      if (!isValidPrivateText(displayName, 80)) {
        throw new HttpsError(
          "invalid-argument",
          "Invalid display name.",
        );
      }

      if (!isValidPrivateText(appVersion, 40)) {
        throw new HttpsError(
          "invalid-argument",
          "Invalid app version.",
        );
      }

      const authorized =
        isPrivateProvisioningAuthorized({
          uid,
          role,
          isEmulator: isFunctionsEmulator(),
          adminUids: configuredAdminUids(),
          alarmUids: configuredAlarmUids(),
        });

      if (!authorized) {
        logger.warn(
          "Rejected unauthorized private device provisioning.",
          {
            role,
          },
        );

        throw new HttpsError(
          "permission-denied",
          "This private-family device has not been approved.",
        );
      }

      const database = getFirestore();
      const family =
        database
          .collection("families")
          .doc("sefer-family");

      const member =
        family
          .collection("members")
          .doc(uid);

      const membership =
        database
          .collection("users")
          .doc(uid)
          .collection("memberships")
          .doc("sefer-family");

      const device =
        family
          .collection("devices")
          .doc(deviceId);

      const deviceAccess =
        database
          .collection("deviceAccess")
          .doc(uid);

      await database.runTransaction(
        async (transaction) => {
          const [
            familySnapshot,
            memberSnapshot,
            membershipSnapshot,
            deviceSnapshot,
          ] = await Promise.all([
            transaction.get(family),
            transaction.get(member),
            transaction.get(membership),
            transaction.get(device),
          ]);

          if (!familySnapshot.exists) {
            transaction.set(
              family,
              {
                familyId: "sefer-family",
                name: "Sefer Ailesi",
                createdByUid: uid,
                createdAt: FieldValue.serverTimestamp(),
                version: 1,
              },
            );
          }

          if (accessProjection.grantsFamilyMembership) {
            const memberVersion =
              memberSnapshot.exists
                ? (memberSnapshot.get("version") ?? 0) + 1
                : 1;

            transaction.set(
              member,
              {
                uid,
                familyId: "sefer-family",
                role: accessProjection.familyMemberRole,
                displayName,
                joinedAt:
                  memberSnapshot.exists
                    ? memberSnapshot.get("joinedAt")
                    : FieldValue.serverTimestamp(),
                deviceId,
                version: memberVersion,
              },
            );

            const membershipVersion =
              membershipSnapshot.exists
                ? (membershipSnapshot.get("version") ?? 0) + 1
                : 1;

            transaction.set(
              membership,
              {
                familyId: "sefer-family",
                familyName: "Sefer Ailesi",
                role: accessProjection.familyMemberRole,
                displayName,
                joinedAt:
                  membershipSnapshot.exists
                    ? membershipSnapshot.get("joinedAt")
                    : FieldValue.serverTimestamp(),
                version: membershipVersion,
              },
            );
          } else {
            // Alarm devices need schedule/contact access, not family membership.
            transaction.delete(member);
            transaction.delete(membership);
          }

          const deviceVersion =
            deviceSnapshot.exists
              ? (deviceSnapshot.get("version") ?? 0) + 1
              : 1;

          transaction.set(
            device,
            {
              deviceId,
              familyId: "sefer-family",
              ownerUid: uid,
              role,
              displayName,
              appVersion,
              lastSeenAt: FieldValue.serverTimestamp(),
              lastSuccessfulSyncAt:
                deviceSnapshot.exists
                  ? deviceSnapshot.get("lastSuccessfulSyncAt") ?? null
                  : null,
              version: deviceVersion,
            },
          );

          if (accessProjection.grantsAlarmScheduleAccess) {
            transaction.set(
              deviceAccess,
              {
                uid,
                familyId: "sefer-family",
                deviceId,
                role: "ALARM_DEVICE",
                updatedAt: FieldValue.serverTimestamp(),
              },
            );
          } else {
            transaction.delete(deviceAccess);
          }
        },
      );

      logger.info(
        "Private family device provisioned.",
        {
          role,
        },
      );

      return {
        familyId: "sefer-family",
        role,
        deviceId,
      };
    },
  );

function configuredAdminUids() {
  return parseUidAllowList(
    approvedAdminUids.value(),
  );
}

function configuredAlarmUids() {
  return parseUidAllowList(
    approvedAlarmUids.value(),
  );
}

function isFunctionsEmulator() {
  return process.env.FUNCTIONS_EMULATOR === "true";
}

function isValidPrivateText(value, maxLength) {
  return typeof value === "string" &&
    value.trim().length > 0 &&
    value.length <= maxLength;
}
