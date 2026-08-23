package com.berkant.yaninda.ui.caregiver

internal enum class CaregiverBackAction {
    RETURN_TO_MEDICATION_LIST,
    LOCK_TO_PIN,
    LOCK_TO_GRANDFATHER_HOME,
}

internal fun resolveCaregiverBackAction(
    initialSetup: Boolean,
    destination: CaregiverDestination,
): CaregiverBackAction = when {
    destination != CaregiverDestination.MEDICATION_LIST ->
        CaregiverBackAction.RETURN_TO_MEDICATION_LIST

    initialSetup -> CaregiverBackAction.LOCK_TO_PIN
    else -> CaregiverBackAction.LOCK_TO_GRANDFATHER_HOME
}
