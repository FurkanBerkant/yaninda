package com.berkant.yaninda.domain.occurrence

import java.time.Instant

data class DoseGroupItem(
    val medicationId: String,
    val medicationDisplayName: String,
    val dosageText: String,
    val instructionText: String,
)

data class DoseGroup(
    /*
     * Aynı cihazda aynı mantıksal ilaç saati
     * her refresh sırasında aynı ID'yi üretmeli.
     *
     * Bir sonraki adımda bunu familyId +
     * scheduledAt üzerinden deterministic
     * oluşturacağız.
     */
    val groupId: String,

    /*
     * Bu grubun gerçek alarm zamanı.
     */
    val scheduledAt: Instant,

    /*
     * Aynı anda alınacak tüm ilaçlar.
     */
    val items: List<DoseGroupItem>,
) {

    init {
        require(groupId.isNotBlank()) {
            "Dose group id must not be blank."
        }

        require(items.isNotEmpty()) {
            "Dose group must contain at least one medication."
        }

        require(
            items
                .map { it.medicationId }
                .distinct()
                .size == items.size
        ) {
            "Dose group must not contain duplicate medications."
        }
    }

    val medicationCount: Int
        get() = items.size

    val displayNames: List<String>
        get() =
            items.map {
                it.medicationDisplayName
            }
}
data class NextDoseGroupResult(
    val group: DoseGroup?,
    val issues: List<OccurrencePlanningIssue>,
)