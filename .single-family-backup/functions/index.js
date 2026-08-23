import {
  initializeApp,
} from "firebase-admin/app";

import {
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
  logger,
} from "firebase-functions";

import {
  buildFamilyNotificationMessage,
  buildScheduleChangedMessage,
} from "./notificationPayload.js";

initializeApp();

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
            familyId,
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
            familyId,
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