import assert from "node:assert/strict";

import {
  describe,
  it,
} from "node:test";

import {
  SCHEDULE_CHANGED_TTL_MILLIS,
  buildScheduleChangedMessage,
} from "../notificationPayload.js";

describe(
  "schedule changed payload",
  () => {

    it(
      "creates a schedule hint without medication data",
      () => {

        const message =
          buildScheduleChangedMessage(
            {
              desiredVersion: 12,
            },
            "family-1",
          );

        assert.deepEqual(
          message.data,
          {
            type:
              "SCHEDULE_CHANGED",

            familyId:
              "family-1",

            scheduleVersion:
              "12",
          },
        );

        assert.equal(
          message.android.ttl,
          SCHEDULE_CHANGED_TTL_MILLIS,
        );

        assert.equal(
          "medications" in
            message.data,
          false,
        );
      },
    );

    it(
      "rejects invalid schedule versions",
      () => {

        assert.equal(
          buildScheduleChangedMessage(
            {
              desiredVersion: 0,
            },
            "family-1",
          ),
          null,
        );

        assert.equal(
          buildScheduleChangedMessage(
            {
              desiredVersion:
                "12",
            },
            "family-1",
          ),
          null,
        );
      },
    );
  },
);