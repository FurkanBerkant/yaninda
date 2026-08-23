const ACKNOWLEDGED_EVENT = "DOSE_OCCURRENCE_ACKNOWLEDGED";
const NO_CONFIRMATION_EVENT = "DOSE_OCCURRENCE_NO_CONFIRMATION";
const ACKNOWLEDGED_STATUS = "ACKNOWLEDGED_TAKEN";
const NO_CONFIRMATION_STATUS = "NO_CONFIRMATION";
const TIME_PATTERN = /^(?:[01]\d|2[0-3]):[0-5]\d$/;
const ID_PATTERN = /^[^/]{1,256}$/;

export const FAMILY_NOTIFICATION_TTL_MILLIS = Object.freeze({
  ACKNOWLEDGED_TAKEN: 6 * 60 * 60 * 1000,
  NO_CONFIRMATION: 2 * 60 * 60 * 1000,
});

export function buildFamilyNotificationMessage(eventData, occurrenceData, familyId) {
  if (!eventData || !occurrenceData || !validId(familyId, 128)) return null;
  const eventId = eventData.eventId;
  const occurrenceId = eventData.aggregateId;
  const scheduledTime = occurrenceData.scheduledLocalTime;
  if (!validId(eventId, 256) || !validId(occurrenceId, 128)) return null;
  if (!TIME_PATTERN.test(scheduledTime ?? "")) return null;
  if (occurrenceData.occurrenceId !== occurrenceId) return null;
  if (!Number.isInteger(eventData.aggregateVersion) || eventData.aggregateVersion < 1) {
    return null;
  }
  if (!Number.isInteger(occurrenceData.version) ||
      occurrenceData.version < eventData.aggregateVersion) {
    return null;
  }

  let type;
  let ttl;
  if (eventData.eventType === ACKNOWLEDGED_EVENT &&
      occurrenceData.status === ACKNOWLEDGED_STATUS) {
    type = ACKNOWLEDGED_STATUS;
    ttl = FAMILY_NOTIFICATION_TTL_MILLIS.ACKNOWLEDGED_TAKEN;
  } else if (eventData.eventType === NO_CONFIRMATION_EVENT &&
      occurrenceData.status === NO_CONFIRMATION_STATUS) {
    type = NO_CONFIRMATION_STATUS;
    ttl = FAMILY_NOTIFICATION_TTL_MILLIS.NO_CONFIRMATION;
  } else {
    return null;
  }

  return {
    data: {
      type,
      familyId,
      occurrenceId,
      eventId,
      scheduledTime,
    },
    android: {
      priority: "high",
      ttl,
      collapseKey: `occurrence-${occurrenceId}`,
    },
  };
}

function validId(value, maxLength) {
  return typeof value === "string" &&
    value.length > 0 &&
    value.length <= maxLength &&
    ID_PATTERN.test(value);
}

export const SCHEDULE_CHANGED_TTL_MILLIS =
  15 * 60 * 1000;

export function buildScheduleChangedMessage(
  scheduleStateData,
  familyId,
) {
  if (
    !scheduleStateData ||
    !validId(familyId, 128)
  ) {
    return null;
  }

  const desiredVersion =
    scheduleStateData.desiredVersion;

  if (
    !Number.isInteger(desiredVersion) ||
    desiredVersion < 1
  ) {
    return null;
  }

  return {
    data: {
      type: "SCHEDULE_CHANGED",
      familyId,
      scheduleVersion:
        String(desiredVersion),
    },

    android: {
      priority: "high",
      ttl:
        SCHEDULE_CHANGED_TTL_MILLIS,

      /*
       * Dede telefonu çevrimdışıyken
       * v10, v11, v12 push'ları birikirse
       * hepsine ihtiyacımız yok.
       *
       * Sonuncusu yeterli; client zaten
       * Firestore'dan gerçek desiredVersion'ı
       * tekrar okuyacak.
       */
      collapseKey:
        `schedule-${familyId}`,
    },
  };
}