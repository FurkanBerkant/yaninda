export function parseUidAllowList(value) {
  if (typeof value !== "string") {
    return new Set();
  }

  return new Set(
    value
      .split(",")
      .map((uid) => uid.trim())
      .filter(Boolean),
  );
}

export function isValidPrivateDeviceId(value) {
  return typeof value === "string" &&
    /^[A-Za-z0-9_-]{1,128}$/.test(value);
}

export function isPrivateProvisioningAuthorized({
  uid,
  role,
  isEmulator,
  adminUids,
  alarmUids,
}) {
  if (isEmulator) {
    return true;
  }

  if (role === "ADMIN_DEVICE") {
    return adminUids.has(uid);
  }

  if (role === "ALARM_DEVICE") {
    return alarmUids.has(uid);
  }

  return false;
}

export function privateDeviceAccessProjection(role) {
  if (role === "ADMIN_DEVICE") {
    return {
      grantsFamilyMembership: true,
      familyMemberRole: "ADMIN",
      grantsAlarmScheduleAccess: false,
    };
  }

  if (role === "ALARM_DEVICE") {
    return {
      grantsFamilyMembership: false,
      familyMemberRole: null,
      grantsAlarmScheduleAccess: true,
    };
  }

  return null;
}
