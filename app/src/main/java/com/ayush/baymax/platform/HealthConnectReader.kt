package com.ayush.baymax.platform

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.ayush.baymax.agent.HealthReadings
import com.ayush.baymax.ui.settings.HealthConnectStatus
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Steps, sleep and heart rate from Health Connect, read only after the user grants access (FR-24). */
class HealthConnectReader(private val context: Context) {

    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
    )

    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context)

    private fun client(): HealthConnectClient? =
        if (sdkStatus() == HealthConnectClient.SDK_AVAILABLE) runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull() else null

    suspend fun status(): HealthConnectStatus {
        val c = client() ?: return HealthConnectStatus.Unavailable
        val granted = runCatching { c.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())
        return if (granted.containsAll(permissions)) HealthConnectStatus.Connected else HealthConnectStatus.NeedsPermission
    }

    /** Null when Health Connect is missing or access was not granted. Never throws (NFR-5). */
    suspend fun read(): HealthReadings? = runCatching {
        val c = client() ?: return null
        val granted = c.permissionController.getGrantedPermissions()
        if (granted.isEmpty()) return null
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant()

        val steps = if (HealthPermission.getReadPermission(StepsRecord::class) in granted) {
            c.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(startOfDay, now)))[StepsRecord.COUNT_TOTAL]?.toInt()
        } else null

        val heartRate = if (HealthPermission.getReadPermission(HeartRateRecord::class) in granted) {
            c.readRecords(ReadRecordsRequest(HeartRateRecord::class, TimeRangeFilter.between(now.minus(Duration.ofHours(24)), now)))
                .records.flatMap { it.samples }.maxByOrNull { it.time }?.beatsPerMinute?.toInt()
        } else null

        val sleep = if (HealthPermission.getReadPermission(SleepSessionRecord::class) in granted) {
            c.readRecords(ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(now.minus(Duration.ofHours(30)), now)))
                .records.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }.toInt().takeIf { it > 0 }
        } else null

        HealthReadings(heartRateBpm = heartRate, stepsToday = steps, sleepMinutes = sleep)
    }.getOrNull()
}
