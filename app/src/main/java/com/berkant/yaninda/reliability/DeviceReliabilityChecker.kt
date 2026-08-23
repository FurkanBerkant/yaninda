package com.berkant.yaninda.reliability

import android.content.Context
import android.os.Build
import android.os.PowerManager

enum class ReliabilityCheckState {
    NOT_CHECKED,
    ENABLED,
    DISABLED,
    CHECK_FAILED,
}

enum class BatteryOptimizationState {
    NOT_CHECKED,
    EXEMPT,
    SYSTEM_MANAGED,
    CHECK_FAILED,
}

data class DeviceReliabilityStatus(
    val isSamsungDevice: Boolean? = null,
    val powerSaveMode: ReliabilityCheckState = ReliabilityCheckState.NOT_CHECKED,
    val batteryOptimization: BatteryOptimizationState =
        BatteryOptimizationState.NOT_CHECKED,
)

interface DeviceReliabilityChecker {
    fun snapshot(): DeviceReliabilityStatus
}

class AndroidDeviceReliabilityChecker(
    context: Context,
) : DeviceReliabilityChecker {
    private val applicationContext = context.applicationContext
    private val powerManager = applicationContext.getSystemService(PowerManager::class.java)

    override fun snapshot(): DeviceReliabilityStatus {
        val powerSaveMode = try {
            if (powerManager.isPowerSaveMode) {
                ReliabilityCheckState.ENABLED
            } else {
                ReliabilityCheckState.DISABLED
            }
        } catch (_: RuntimeException) {
            ReliabilityCheckState.CHECK_FAILED
        }
        val batteryOptimization = try {
            if (powerManager.isIgnoringBatteryOptimizations(applicationContext.packageName)) {
                BatteryOptimizationState.EXEMPT
            } else {
                BatteryOptimizationState.SYSTEM_MANAGED
            }
        } catch (_: RuntimeException) {
            BatteryOptimizationState.CHECK_FAILED
        }
        return DeviceReliabilityStatus(
            isSamsungDevice = Build.MANUFACTURER.equals(SAMSUNG_MANUFACTURER, ignoreCase = true),
            powerSaveMode = powerSaveMode,
            batteryOptimization = batteryOptimization,
        )
    }

    companion object {
        private const val SAMSUNG_MANUFACTURER = "samsung"
    }
}
