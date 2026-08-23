package com.berkant.yaninda.domain.occurrence

import com.berkant.yaninda.domain.medication.Medication
import com.berkant.yaninda.domain.medication.MedicationConfiguration
import com.berkant.yaninda.domain.medication.MedicationSchedule
import com.berkant.yaninda.domain.medication.MedicationScheduleType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OccurrencePlannerTest {
    private val planner = OccurrencePlanner()
    private val istanbul = ZoneId.of("Europe/Istanbul")

    @Test
    fun recurrencePlan_includesEveryMatchingLocalDayWithinWindow() {
        val configuration = configuration(
            days = setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY),
            localTime = LocalTime.of(20, 0),
        )
        val plan = planner.plan(
            configurations = listOf(configuration),
            window = OccurrencePlanningWindow(
                startInclusive = Instant.parse("2026-08-21T16:30:00Z"),
                endExclusive = Instant.parse("2026-08-22T18:00:00Z"),
                zoneId = istanbul,
            ),
        )

        assertEquals(
            listOf(
                Instant.parse("2026-08-21T17:00:00Z"),
                Instant.parse("2026-08-22T17:00:00Z"),
            ),
            plan.occurrences.map { it.scheduledAt },
        )
        assertTrue(plan.issues.isEmpty())
    }

    @Test
    fun nextOccurrence_atExactScheduledInstant_includesCurrentOccurrence() {
        val scheduledAt = Instant.parse("2026-08-21T17:00:00Z")

        val result = planner.nextOccurrence(
            configurations = listOf(configuration()),
            atOrAfter = scheduledAt,
            zoneId = istanbul,
        )

        assertEquals(scheduledAt, result.occurrence?.scheduledAt)
    }

    @Test
    fun nextOccurrence_afterScheduledInstant_movesToNextMatchingDay() {
        val result = planner.nextOccurrence(
            configurations = listOf(configuration()),
            atOrAfter = Instant.parse("2026-08-21T17:00:01Z"),
            zoneId = istanbul,
        )

        assertEquals(
            Instant.parse("2026-08-22T17:00:00Z"),
            result.occurrence?.scheduledAt,
        )
    }

    @Test
    fun inactiveMedication_doesNotProduceOccurrence() {
        val result = planner.nextOccurrence(
            configurations = listOf(configuration(active = false)),
            atOrAfter = Instant.parse("2026-08-21T00:00:00Z"),
            zoneId = istanbul,
        )

        assertNull(result.occurrence)
    }

    @Test
    fun validityBounds_areInclusiveLocalDates() {
        val configuration = configuration(
            validFrom = LocalDate.of(2026, 8, 22),
            validUntil = LocalDate.of(2026, 8, 22),
        )
        val plan = planner.plan(
            configurations = listOf(configuration),
            window = OccurrencePlanningWindow(
                startInclusive = Instant.parse("2026-08-21T00:00:00Z"),
                endExclusive = Instant.parse("2026-08-23T21:00:00Z"),
                zoneId = istanbul,
            ),
        )

        assertEquals(
            listOf(Instant.parse("2026-08-22T17:00:00Z")),
            plan.occurrences.map { it.scheduledAt },
        )
    }

    @Test
    fun futureValidFrom_isUsedWithoutAnArbitraryNearTermHorizon() {
        val configuration = configuration(
            days = setOf(DayOfWeek.TUESDAY),
            validFrom = LocalDate.of(2026, 12, 1),
        )

        val result = planner.nextOccurrence(
            configurations = listOf(configuration),
            atOrAfter = Instant.parse("2026-01-01T00:00:00Z"),
            zoneId = istanbul,
        )

        assertEquals(
            Instant.parse("2026-12-01T17:00:00Z"),
            result.occurrence?.scheduledAt,
        )
    }

    @Test
    fun localWallTime_isResolvedUsingRequestedTimeZone() {
        val configuration = configuration(localTime = LocalTime.of(20, 0))
        val atOrAfter = Instant.parse("2026-08-21T00:00:00Z")

        val istanbulResult = planner.nextOccurrence(
            listOf(configuration),
            atOrAfter,
            ZoneId.of("Europe/Istanbul"),
        )
        val utcResult = planner.nextOccurrence(
            listOf(configuration),
            atOrAfter,
            ZoneId.of("UTC"),
        )

        assertEquals(Instant.parse("2026-08-21T17:00:00Z"), istanbulResult.occurrence?.scheduledAt)
        assertEquals(Instant.parse("2026-08-21T20:00:00Z"), utcResult.occurrence?.scheduledAt)
    }

    @Test
    fun dstGap_doesNotSilentlyMoveMedicationTime() {
        val berlin = ZoneId.of("Europe/Berlin")
        val configuration = configuration(
            days = setOf(DayOfWeek.SUNDAY),
            localTime = LocalTime.of(2, 30),
            validFrom = LocalDate.of(2026, 3, 29),
            validUntil = LocalDate.of(2026, 3, 29),
        )

        val plan = planner.plan(
            listOf(configuration),
            OccurrencePlanningWindow(
                startInclusive = Instant.parse("2026-03-28T23:00:00Z"),
                endExclusive = Instant.parse("2026-03-29T22:00:00Z"),
                zoneId = berlin,
            ),
        )

        assertTrue(plan.occurrences.isEmpty())
        assertEquals(
            listOf(OccurrencePlanningIssueType.NONEXISTENT_LOCAL_TIME),
            plan.issues.map { it.type },
        )
    }

    @Test
    fun dstOverlap_createsOneOccurrenceAtEarlierOffsetAndReportsIssue() {
        val berlin = ZoneId.of("Europe/Berlin")
        val configuration = configuration(
            days = setOf(DayOfWeek.SUNDAY),
            localTime = LocalTime.of(2, 30),
            validFrom = LocalDate.of(2026, 10, 25),
            validUntil = LocalDate.of(2026, 10, 25),
        )

        val plan = planner.plan(
            listOf(configuration),
            OccurrencePlanningWindow(
                startInclusive = Instant.parse("2026-10-24T22:00:00Z"),
                endExclusive = Instant.parse("2026-10-25T23:00:00Z"),
                zoneId = berlin,
            ),
        )

        assertEquals(
            listOf(Instant.parse("2026-10-25T00:30:00Z")),
            plan.occurrences.map { it.scheduledAt },
        )
        assertEquals(
            listOf(OccurrencePlanningIssueType.AMBIGUOUS_LOCAL_TIME_EARLIER_OFFSET_USED),
            plan.issues.map { it.type },
        )
    }

    @Test
    fun repeatedPlanning_producesSameOccurrenceId() {
        val configuration = configuration()
        val window = OccurrencePlanningWindow(
            startInclusive = Instant.parse("2026-08-21T00:00:00Z"),
            endExclusive = Instant.parse("2026-08-22T00:00:00Z"),
            zoneId = istanbul,
        )

        val first = planner.plan(listOf(configuration), window)
        val second = planner.plan(listOf(configuration), window)

        assertEquals(first.occurrences.single().id, second.occurrences.single().id)
    }

    private fun configuration(
        active: Boolean = true,
        days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
        localTime: LocalTime = LocalTime.of(20, 0),
        validFrom: LocalDate = LocalDate.of(2026, 1, 1),
        validUntil: LocalDate? = null,
    ): MedicationConfiguration {
        val instant = Instant.parse("2026-01-01T00:00:00Z")
        val medication = Medication(
            id = "medication-1",
            displayName = "Test ilacı",
            dosageText = "Yazılı doz",
            instructionText = "Yazılı talimat",
            photoUri = null,
            scheduleType = MedicationScheduleType.FIXED_ONLY,
            active = active,
            createdAt = instant,
            updatedAt = instant,
            version = 1L,
        )
        return MedicationConfiguration(
            medication = medication,
            schedules = listOf(
                MedicationSchedule(
                    id = "schedule-1",
                    medicationId = medication.id,
                    localTime = localTime,
                    daysOfWeek = days,
                    validFrom = validFrom,
                    validUntil = validUntil,
                    snoozeEnabled = false,
                    snoozeMinutes = 10,
                    maxSnoozes = 1,
                    createdAt = instant,
                    updatedAt = instant,
                    version = 1L,
                )
            ),
        )
    }
}
