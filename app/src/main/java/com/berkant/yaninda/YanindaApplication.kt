package com.berkant.yaninda

import android.app.Application
import androidx.room.Room
import com.berkant.yaninda.core.time.SystemTimeProvider
import com.berkant.yaninda.data.contact.DataStoreCaregiverContactRepository
import com.berkant.yaninda.data.contact.caregiverContactDataStore
import com.berkant.yaninda.data.diagnostics.DataStoreReminderDiagnosticsRepository
import com.berkant.yaninda.data.diagnostics.reminderDiagnosticsDataStore
import com.berkant.yaninda.data.device.DataStoreDeviceIdentityRepository
import com.berkant.yaninda.data.device.deviceIdentityDataStore
import com.berkant.yaninda.data.local.MIGRATION_1_2
import com.berkant.yaninda.data.local.MIGRATION_2_3
import com.berkant.yaninda.data.local.MIGRATION_3_4
import com.berkant.yaninda.data.local.MIGRATION_4_5
import com.berkant.yaninda.data.local.YanindaDatabase
import com.berkant.yaninda.data.repository.RoomDoseOccurrenceRepository
import com.berkant.yaninda.data.repository.RoomMedicationRepository
import com.berkant.yaninda.data.repository.RoomSyncOutboxRepository
import com.berkant.yaninda.data.security.DataStoreCaregiverPinRepository
import com.berkant.yaninda.data.security.PinHasher
import com.berkant.yaninda.data.security.caregiverSecurityDataStore
import com.berkant.yaninda.data.setup.DataStorePrimarySetupRepository
import com.berkant.yaninda.data.setup.primarySetupDataStore
import com.berkant.yaninda.domain.occurrence.DoseOccurrenceStateMachine
import com.berkant.yaninda.domain.occurrence.OccurrencePlanner
import com.berkant.yaninda.auth.FirebaseFamilyAuthRepository
import com.berkant.yaninda.auth.FamilyAuthRepository
import com.berkant.yaninda.auth.UnavailableFamilyAuthRepository
import com.berkant.yaninda.family.DevicePairingService
import com.berkant.yaninda.family.FirestoreFamilyRepository
import com.berkant.yaninda.family.FamilyRepository
import com.berkant.yaninda.family.UnavailableFamilyRepository
import com.berkant.yaninda.firebase.FirebaseRuntimeFactory
import com.berkant.yaninda.notification.MedicationNotificationManager
import com.berkant.yaninda.push.FamilyPushNotificationManager
import com.berkant.yaninda.push.FamilyPushRegistrationRepository
import com.berkant.yaninda.push.FirestoreFamilyPushRegistrationRepository
import com.berkant.yaninda.push.UnavailableFamilyPushRegistrationRepository
import com.berkant.yaninda.reminder.AlarmManagerReminderScheduler
import com.berkant.yaninda.reminder.ReminderCoordinator
import com.berkant.yaninda.secondary.AlarmManagerSecondaryReminderScheduler
import com.berkant.yaninda.secondary.DataStoreSecondaryReminderSettingsRepository
import com.berkant.yaninda.secondary.RoomSecondaryReminderCacheRepository
import com.berkant.yaninda.secondary.SecondaryReminderCoordinator
import com.berkant.yaninda.secondary.SecondaryReminderNotificationManager
import com.berkant.yaninda.secondary.secondaryReminderDataStore
import com.berkant.yaninda.reliability.AndroidDeviceReliabilityChecker
import com.berkant.yaninda.sync.RemoteSyncDataSource
import com.berkant.yaninda.sync.FirestoreRemoteSyncDataSource
import com.berkant.yaninda.sync.SyncOutboxProcessor
import com.berkant.yaninda.sync.UnavailableRemoteSyncDataSource
import com.berkant.yaninda.sync.WorkManagerSyncWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.berkant.yaninda.schedule.AdminScheduleRepository
import com.berkant.yaninda.schedule.FirestoreAdminScheduleRepository
import com.berkant.yaninda.schedule.UnavailableAdminScheduleRepository
import com.berkant.yaninda.data.schedule.DataStoreAlarmScheduleStateRepository
import com.berkant.yaninda.data.schedule.alarmScheduleStateDataStore
import com.berkant.yaninda.schedule.AlarmScheduleLocalApplier
import com.berkant.yaninda.schedule.AlarmScheduleSyncCoordinator
import com.berkant.yaninda.schedule.FirestoreAlarmScheduleRemoteRepository
class YanindaApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: YanindaDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            YanindaDatabase::class.java,
            DATABASE_NAME,
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()
    }

    val timeProvider by lazy { SystemTimeProvider() }

    val medicationRepository by lazy {
        RoomMedicationRepository(
            medicationDao = database.medicationDao(),
            timeProvider = timeProvider,
        )
    }

    val doseOccurrenceRepository by lazy {
        RoomDoseOccurrenceRepository(
            database = database,
            medicationDao = database.medicationDao(),
            occurrenceDao = database.doseOccurrenceDao(),
            outboxDao = database.syncOutboxDao(),
            planner = OccurrencePlanner(),
            stateMachine = DoseOccurrenceStateMachine(),
            timeProvider = timeProvider,
        )
    }

    val caregiverPinRepository by lazy {
        DataStoreCaregiverPinRepository(
            dataStore = applicationContext.caregiverSecurityDataStore,
            pinHasher = PinHasher(),
        )
    }

    val caregiverContactRepository by lazy {
        DataStoreCaregiverContactRepository(applicationContext.caregiverContactDataStore)
    }

    val reminderDiagnosticsRepository by lazy {
        DataStoreReminderDiagnosticsRepository(applicationContext.reminderDiagnosticsDataStore)
    }

    val deviceIdentityRepository by lazy {
        DataStoreDeviceIdentityRepository(applicationContext.deviceIdentityDataStore)
    }

    val primarySetupRepository by lazy {
        DataStorePrimarySetupRepository(applicationContext.primarySetupDataStore)
    }

    val firebaseRuntime by lazy {
        FirebaseRuntimeFactory.create(applicationContext)
    }

    val familyAuthRepository: FamilyAuthRepository by lazy {
        firebaseRuntime?.let { runtime -> FirebaseFamilyAuthRepository(runtime.auth) }
            ?: UnavailableFamilyAuthRepository
    }

    val familyRepository: FamilyRepository by lazy {
        firebaseRuntime?.let { runtime ->
            FirestoreFamilyRepository(
                firestore = runtime.firestore,
                auth = runtime.auth,
            )
        } ?: UnavailableFamilyRepository
    }

    val adminScheduleRepository: AdminScheduleRepository by lazy {
        firebaseRuntime?.let { runtime ->
            FirestoreAdminScheduleRepository(
                firestore = runtime.firestore,
                auth = runtime.auth,
            )
        } ?: UnavailableAdminScheduleRepository
    }

    val alarmScheduleStateRepository by lazy {
        DataStoreAlarmScheduleStateRepository(
            applicationContext
                .alarmScheduleStateDataStore
        )
    }

    val alarmScheduleLocalApplier by lazy {
        AlarmScheduleLocalApplier(
            medicationDao =
                database.medicationDao(),
            timeProvider =
                timeProvider,
        )
    }

    val alarmScheduleSyncCoordinator:
            AlarmScheduleSyncCoordinator? by lazy {

        firebaseRuntime?.let { runtime ->

            val remoteRepository =
                FirestoreAlarmScheduleRemoteRepository(
                    firestore =
                        runtime.firestore,
                    auth =
                        runtime.auth,
                    appVersion =
                        BuildConfig.VERSION_NAME,
                )

            AlarmScheduleSyncCoordinator(
                remoteRepository =
                    remoteRepository,
                localApplier =
                    alarmScheduleLocalApplier,
                stateRepository =
                    alarmScheduleStateRepository,
                deviceIdentityRepository =
                    deviceIdentityRepository,
                reminderCoordinator =
                    reminderCoordinator,
            )
        }
    }

    val devicePairingService by lazy {
        DevicePairingService(
            authRepository = familyAuthRepository,
            familyRepository = familyRepository,
            deviceIdentityRepository = deviceIdentityRepository,
            appVersion = BuildConfig.VERSION_NAME,
        )
    }

    val familyPushRegistrationRepository: FamilyPushRegistrationRepository by lazy {
        firebaseRuntime?.takeUnless { it.usesLocalEmulators }?.let { runtime ->
            FirestoreFamilyPushRegistrationRepository(
                firebaseApp = runtime.app,
                firestore = runtime.firestore,
                auth = runtime.auth,
                deviceIdentityRepository = deviceIdentityRepository,
                appVersion = BuildConfig.VERSION_NAME,
            )
        } ?: UnavailableFamilyPushRegistrationRepository
    }

    val familyPushNotificationManager by lazy {
        FamilyPushNotificationManager(applicationContext)
    }

    val secondaryReminderCacheRepository by lazy {
        RoomSecondaryReminderCacheRepository(
            database = database,
            dao = database.secondaryReminderCacheDao(),
        )
    }

    val secondaryReminderSettingsRepository by lazy {
        DataStoreSecondaryReminderSettingsRepository(
            applicationContext.secondaryReminderDataStore
        )
    }

    val secondaryReminderNotifier by lazy {
        SecondaryReminderNotificationManager(applicationContext)
    }

    val secondaryReminderScheduler by lazy {
        AlarmManagerSecondaryReminderScheduler(applicationContext)
    }

    val secondaryReminderCoordinator by lazy {
        SecondaryReminderCoordinator(
            cacheRepository = secondaryReminderCacheRepository,
            settingsRepository = secondaryReminderSettingsRepository,
            deviceIdentityRepository = deviceIdentityRepository,
            scheduler = secondaryReminderScheduler,
            notifier = secondaryReminderNotifier,
            timeProvider = timeProvider,
        )
    }

    val deviceReliabilityChecker by lazy {
        AndroidDeviceReliabilityChecker(applicationContext)
    }

    val syncOutboxRepository by lazy {
        RoomSyncOutboxRepository(database.syncOutboxDao())
    }

    val remoteSyncDataSource: RemoteSyncDataSource by lazy {
        firebaseRuntime?.let { runtime ->
            FirestoreRemoteSyncDataSource(
                firestore = runtime.firestore,
                auth = runtime.auth,
                authRepository = familyAuthRepository,
                deviceIdentityRepository = deviceIdentityRepository,
                occurrenceRepository = doseOccurrenceRepository,
                medicationRepository = medicationRepository,
                timeProvider = timeProvider,
                appVersion = BuildConfig.VERSION_NAME,
            )
        } ?: UnavailableRemoteSyncDataSource
    }

    val syncOutboxProcessor by lazy {
        SyncOutboxProcessor(
            outboxRepository = syncOutboxRepository,
            remoteDataSource = remoteSyncDataSource,
            timeProvider = timeProvider,
        )
    }

    val syncWorkScheduler by lazy {
        WorkManagerSyncWorkScheduler(applicationContext)
    }

    val reminderNotifier by lazy {
        MedicationNotificationManager(applicationContext)
    }

    val reminderScheduler by lazy {
        AlarmManagerReminderScheduler(
            context = applicationContext,
            timeProvider = timeProvider,
        )
    }

    val reminderCoordinator by lazy {
        ReminderCoordinator(
            occurrenceRepository = doseOccurrenceRepository,
            scheduler = reminderScheduler,
            notifier = reminderNotifier,
            diagnosticsRepository = reminderDiagnosticsRepository,
            timeProvider = timeProvider,
            syncWorkScheduler = syncWorkScheduler,
        )
    }

    override fun onCreate() {
        super.onCreate()

        reminderNotifier.ensureChannel()
        familyPushNotificationManager.ensureChannel()

        /*
         * V2:
         *
         * Grandfather and grandmother phones are both ALARM_DEVICE.
         * Therefore the old "secondary reminder" architecture must not
         * automatically restore reminder alarms on application startup.
         *
         * Exact medication alarms are restored/scheduled only through
         * the normal ALARM_DEVICE reminder pipeline.
         */
        syncWorkScheduler.requestSync()
    }

    companion object {
        private const val DATABASE_NAME = "yaninda.db"
    }
}
