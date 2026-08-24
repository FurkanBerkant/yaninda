import assert from "node:assert/strict";
import test from "node:test";

import {
  isPrivateProvisioningAuthorized,
  isValidPrivateDeviceId,
  parseUidAllowList,
  privateDeviceAccessProjection,
} from "../privateProvisioningPolicy.js";

test(
  "missing admin configuration fails closed",
  () => {
    assert.deepEqual(
      [...parseUidAllowList(undefined)],
      [],
    );
  },
);

test(
  "configured admin UIDs are trimmed and deduplicated",
  () => {
    assert.deepEqual(
      [
        ...parseUidAllowList(
          " admin-a,admin-b, admin-a ",
        ),
      ],
      ["admin-a", "admin-b"],
    );
  },
);

test(
  "device IDs cannot alter Firestore document-prefix matching",
  () => {
    assert.equal(
      isValidPrivateDeviceId(
        "8ab06e54-c8b5-4cc9-91cf-6f83927dbd0f",
      ),
      true,
    );
    assert.equal(
      isValidPrivateDeviceId(".*"),
      false,
    );
    assert.equal(
      isValidPrivateDeviceId("device/other"),
      false,
    );
  },
);

test(
  "production provisioning is role-scoped and fails closed",
  () => {
    const adminUids = new Set(["admin-a"]);
    const alarmUids = new Set(["alarm-a"]);

    assert.equal(
      isPrivateProvisioningAuthorized({
        uid: "admin-a",
        role: "ADMIN_DEVICE",
        isEmulator: false,
        adminUids,
        alarmUids,
      }),
      true,
    );

    assert.equal(
      isPrivateProvisioningAuthorized({
        uid: "admin-a",
        role: "ALARM_DEVICE",
        isEmulator: false,
        adminUids,
        alarmUids,
      }),
      false,
    );

    assert.equal(
      isPrivateProvisioningAuthorized({
        uid: "unknown",
        role: "ALARM_DEVICE",
        isEmulator: false,
        adminUids,
        alarmUids,
      }),
      false,
    );
  },
);

test(
  "emulator provisioning bypass remains limited to emulator runtime",
  () => {
    assert.equal(
      isPrivateProvisioningAuthorized({
        uid: "local-test-user",
        role: "ALARM_DEVICE",
        isEmulator: true,
        adminUids: new Set(),
        alarmUids: new Set(),
      }),
      true,
    );
  },
);

test(
  "only admin devices receive family membership",
  () => {
    assert.deepEqual(
      privateDeviceAccessProjection("ADMIN_DEVICE"),
      {
        grantsFamilyMembership: true,
        familyMemberRole: "ADMIN",
        grantsAlarmScheduleAccess: false,
      },
    );

    assert.deepEqual(
      privateDeviceAccessProjection("ALARM_DEVICE"),
      {
        grantsFamilyMembership: false,
        familyMemberRole: null,
        grantsAlarmScheduleAccess: true,
      },
    );

    assert.equal(
      privateDeviceAccessProjection("UNKNOWN"),
      null,
    );
  },
);
