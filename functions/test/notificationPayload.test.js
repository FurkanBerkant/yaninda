import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  FAMILY_NOTIFICATION_TTL_MILLIS,
  buildFamilyNotificationMessage,
} from "../notificationPayload.js";

const baseEvent = {
  eventId: "event-1",
  eventType: "DOSE_OCCURRENCE_ACKNOWLEDGED",
  aggregateId: "occurrence-1",
  aggregateVersion: 3,
};
const baseOccurrence = {
  occurrenceId: "occurrence-1",
  scheduledLocalTime: "20:00",
  status: "ACKNOWLEDGED_TAKEN",
  version: 3,
};

describe("family notification payload", () => {
  it("creates a short-lived acknowledgement hint without medication data", () => {
    const message = buildFamilyNotificationMessage(
      baseEvent,
      baseOccurrence,
      "family-1",
    );

    assert.deepEqual(message.data, {
      type: "ACKNOWLEDGED_TAKEN",
      familyId: "family-1",
      occurrenceId: "occurrence-1",
      eventId: "event-1",
      scheduledTime: "20:00",
    });
    assert.equal(message.android.ttl, FAMILY_NOTIFICATION_TTL_MILLIS.ACKNOWLEDGED_TAKEN);
    assert.equal("medicationName" in message.data, false);
  });

  it("uses a shorter TTL and careful semantics for no-confirmation", () => {
    const message = buildFamilyNotificationMessage(
      {
        ...baseEvent,
        eventType: "DOSE_OCCURRENCE_NO_CONFIRMATION",
        aggregateVersion: 4,
      },
      {
        ...baseOccurrence,
        status: "NO_CONFIRMATION",
        version: 4,
      },
      "family-1",
    );

    assert.equal(message.data.type, "NO_CONFIRMATION");
    assert.equal(message.android.ttl, FAMILY_NOTIFICATION_TTL_MILLIS.NO_CONFIRMATION);
  });

  it("rejects mismatched, stale, and unrelated occurrence events", () => {
    assert.equal(
      buildFamilyNotificationMessage(
        baseEvent,
        { ...baseOccurrence, status: "DUE" },
        "family-1",
      ),
      null,
    );
    assert.equal(
      buildFamilyNotificationMessage(
        { ...baseEvent, aggregateVersion: 4 },
        baseOccurrence,
        "family-1",
      ),
      null,
    );
    assert.equal(
      buildFamilyNotificationMessage(
        { ...baseEvent, eventType: "DOSE_OCCURRENCE_DUE" },
        baseOccurrence,
        "family-1",
      ),
      null,
    );
  });
});
