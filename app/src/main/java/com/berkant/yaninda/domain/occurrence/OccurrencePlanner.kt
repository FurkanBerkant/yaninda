package com.berkant.yaninda.domain.occurrence

import com.berkant.yaninda.domain.medication.MedicationConfiguration
import com.berkant.yaninda.domain.medication.MedicationSchedule
import com.berkant.yaninda.domain.medication.MedicationScheduleType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class OccurrencePlanner(
    private val idFactory: OccurrenceIdFactory = StableOccurrenceIdFactory,
) {
    fun plan(
        configurations: List<MedicationConfiguration>,
        window: OccurrencePlanningWindow,
    ): OccurrencePlan {
        val occurrencesById = linkedMapOf<String, PlannedDoseOccurrence>()
        val issues = mutableListOf<OccurrencePlanningIssue>()
        val firstDate = window.startInclusive.atZone(window.zoneId).toLocalDate()
        val lastDate = window.endExclusive.minusNanos(1).atZone(window.zoneId).toLocalDate()

        activeFixedConfigurations(configurations).forEach { configuration ->
            configuration.schedules.sortedBy { it.id }.forEach { schedule ->
                val scheduleFirstDate = maxOf(firstDate, schedule.validFrom)
                val scheduleLastDate = minOf(lastDate, schedule.validUntil ?: lastDate)
                var date = scheduleFirstDate

                while (!date.isAfter(scheduleLastDate)) {
                    if (date.dayOfWeek in schedule.daysOfWeek) {
                        val resolved = resolve(
                            medicationId = configuration.medication.id,
                            schedule = schedule,
                            date = date,
                            zoneId = window.zoneId,
                        )
                        resolved.issue?.let(issues::add)
                        resolved.instant
                            ?.takeIf { it >= window.startInclusive && it < window.endExclusive }
                            ?.let { scheduledAt ->
                                val occurrence = plannedOccurrence(
                                    medicationId = configuration.medication.id,
                                    scheduleId = schedule.id,
                                    scheduledAt = scheduledAt,
                                )
                                val previous = occurrencesById.putIfAbsent(occurrence.id, occurrence)
                                check(previous == null || previous == occurrence) {
                                    "A stable occurrence ID resolved to conflicting occurrence data."
                                }
                            }
                    }
                    date = date.plusDays(1)
                }
            }
        }

        return OccurrencePlan(
            occurrences = occurrencesById.values.sortedWith(
                compareBy<PlannedDoseOccurrence> { it.scheduledAt }
                    .thenBy { it.scheduleId }
                    .thenBy { it.id }
            ),
            issues = issues.sortedWith(
                compareBy<OccurrencePlanningIssue> { it.localDateTime }
                    .thenBy { it.scheduleId }
            ),
        )
    }

    fun nextOccurrence(
        configurations: List<MedicationConfiguration>,
        atOrAfter: Instant,
        zoneId: ZoneId,
    ): NextOccurrenceResult {
        val issues = mutableListOf<OccurrencePlanningIssue>()
        val startDate = atOrAfter.atZone(zoneId).toLocalDate()
        val candidates = activeFixedConfigurations(configurations).flatMap { configuration ->
            configuration.schedules.mapNotNull { schedule ->
                val firstCandidateDate = maxOf(startDate, schedule.validFrom)
                val searchEndDate = minOf(
                    schedule.validUntil ?: firstCandidateDate.plusDays(NEXT_SEARCH_DAYS),
                    firstCandidateDate.plusDays(NEXT_SEARCH_DAYS),
                )
                var date = firstCandidateDate
                var candidate: PlannedDoseOccurrence? = null

                while (candidate == null && !date.isAfter(searchEndDate)) {
                    if (date.dayOfWeek in schedule.daysOfWeek) {
                        val resolved = resolve(
                            medicationId = configuration.medication.id,
                            schedule = schedule,
                            date = date,
                            zoneId = zoneId,
                        )
                        resolved.issue?.let(issues::add)
                        resolved.instant
                            ?.takeIf { it >= atOrAfter }
                            ?.let { scheduledAt ->
                                candidate = plannedOccurrence(
                                    medicationId = configuration.medication.id,
                                    scheduleId = schedule.id,
                                    scheduledAt = scheduledAt,
                                )
                            }
                    }
                    date = date.plusDays(1)
                }
                candidate
            }
        }

        return NextOccurrenceResult(
            occurrence = candidates.minWithOrNull(
                compareBy<PlannedDoseOccurrence> { it.scheduledAt }
                    .thenBy { it.scheduleId }
                    .thenBy { it.id }
            ),
            issues = issues.sortedWith(
                compareBy<OccurrencePlanningIssue> { it.localDateTime }
                    .thenBy { it.scheduleId }
            ),
        )
    }

    private fun activeFixedConfigurations(
        configurations: List<MedicationConfiguration>,
    ): List<MedicationConfiguration> = configurations
        .asSequence()
        .filter { it.medication.active }
        .filter { it.medication.scheduleType == MedicationScheduleType.FIXED_ONLY }
        .sortedBy { it.medication.id }
        .toList()

    private fun resolve(
        medicationId: String,
        schedule: MedicationSchedule,
        date: LocalDate,
        zoneId: ZoneId,
    ): ResolvedScheduleTime {
        val localDateTime = LocalDateTime.of(date, schedule.localTime)
        val validOffsets = zoneId.rules.getValidOffsets(localDateTime)
        return when (validOffsets.size) {
            0 -> ResolvedScheduleTime(
                instant = null,
                issue = OccurrencePlanningIssue(
                    medicationId = medicationId,
                    scheduleId = schedule.id,
                    localDateTime = localDateTime,
                    zoneId = zoneId,
                    type = OccurrencePlanningIssueType.NONEXISTENT_LOCAL_TIME,
                ),
            )

            1 -> ResolvedScheduleTime(
                instant = ZonedDateTime.ofLocal(localDateTime, zoneId, validOffsets.single())
                    .toInstant(),
                issue = null,
            )

            else -> ResolvedScheduleTime(
                // One wall-clock reminder is generated at the earlier offset to avoid a duplicate.
                instant = ZonedDateTime.ofLocal(localDateTime, zoneId, validOffsets.first())
                    .toInstant(),
                issue = OccurrencePlanningIssue(
                    medicationId = medicationId,
                    scheduleId = schedule.id,
                    localDateTime = localDateTime,
                    zoneId = zoneId,
                    type = OccurrencePlanningIssueType.AMBIGUOUS_LOCAL_TIME_EARLIER_OFFSET_USED,
                ),
            )
        }
    }

    private fun plannedOccurrence(
        medicationId: String,
        scheduleId: String,
        scheduledAt: Instant,
    ): PlannedDoseOccurrence = PlannedDoseOccurrence(
        id = idFactory.create(medicationId, scheduleId, scheduledAt),
        medicationId = medicationId,
        scheduleId = scheduleId,
        scheduledAt = scheduledAt,
    )

    private data class ResolvedScheduleTime(
        val instant: Instant?,
        val issue: OccurrencePlanningIssue?,
    )

    companion object {
        // A weekly rule needs at most seven days; a second cycle covers one skipped DST-gap date.
        private const val NEXT_SEARCH_DAYS = 14L
    }
}
