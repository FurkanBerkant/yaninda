package com.berkant.yaninda.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

interface TimeProvider {
    fun now(): Instant

    fun currentZoneId(): ZoneId

    fun today(): LocalDate = now().atZone(currentZoneId()).toLocalDate()
}

class SystemTimeProvider(
    private val instantProvider: () -> Instant = { Instant.now() },
    private val zoneProvider: () -> ZoneId = { ZoneId.systemDefault() },
) : TimeProvider {
    override fun now(): Instant = instantProvider()

    override fun currentZoneId(): ZoneId = zoneProvider()
}
