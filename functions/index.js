import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions";
import { buildFamilyNotificationMessage } from "./notificationPayload.js";

initializeApp();

export const notifyFamilyOnOccurrenceEvent = onDocumentCreated(
  {
    document: "families/{familyId}/syncEvents/{eventId}",
    region: "europe-west1",
    // The Firestore dashboard remains authoritative. Avoid retry-generated
    // duplicate family notifications when FCM has a transient failure.
    retry: false,
  },
  async (event) => {
    const eventData = event.data?.data();
    if (!eventData) return;
    const familyId = event.params.familyId;
    const database = getFirestore();
    const familyReference = database.collection("families").doc(familyId);
    const occurrence = await familyReference
      .collection("occurrences")
      .doc(eventData.aggregateId)
      .get();
    const message = buildFamilyNotificationMessage(
      eventData,
      occurrence.data(),
      familyId,
    );
    if (!message) return;

    const registrations = await familyReference
      .collection("pushRegistrations")
      .limit(500)
      .get();
    const fids = registrations.docs
      .map((document) => document.get("installationId"))
      .filter((value) => typeof value === "string" && value.length > 0);
    if (fids.length === 0) return;

    const response = await getMessaging().sendEachForMulticast({
      ...message,
      fids,
    });
    logger.info("Family status notification batch completed.", {
      successCount: response.successCount,
      failureCount: response.failureCount,
    });
  },
);
