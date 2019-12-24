package org.simple.clinic.bp.sync

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import org.simple.clinic.bp.BloodPressureMeasurement
import org.simple.clinic.bp.BpReading
import org.simple.clinic.patient.SyncStatus
import org.simple.clinic.util.generateEncounterUuid
import org.simple.clinic.util.toLocalDateAtZone
import org.threeten.bp.Instant
import org.threeten.bp.ZoneOffset
import java.util.UUID

@JsonClass(generateAdapter = true)
data class BloodPressureMeasurementPayload(
    @Json(name = "id")
    val uuid: UUID,

    @Json(name = "patient_id")
    val patientUuid: UUID,

    @Json(name = "systolic")
    val systolic: Int,

    @Json(name = "diastolic")
    val diastolic: Int,

    @Json(name = "facility_id")
    val facilityUuid: UUID,

    @Json(name = "user_id")
    val userUuid: UUID,

    @Json(name = "created_at")
    val createdAt: Instant,

    @Json(name = "updated_at")
    val updatedAt: Instant,

    @Json(name = "deleted_at")
    val deletedAt: Instant?,

    @Json(name = "recorded_at")
    val recordedAt: Instant
) {

  fun toDatabaseModel(syncStatus: SyncStatus): BloodPressureMeasurement {
    return BloodPressureMeasurement(
        uuid = uuid,
        reading = BpReading(systolic, diastolic),
        syncStatus = syncStatus,
        userUuid = userUuid,
        facilityUuid = facilityUuid,
        patientUuid = patientUuid,
        //FIXME: [Encounter] This code is throw-away. Once the Encounter API is implemented, this should be fixed.
        encounterUuid = generateEncounterUuid(facilityUuid, patientUuid, recordedAt.toLocalDateAtZone(ZoneOffset.UTC)),
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        recordedAt = recordedAt
    )
  }

  fun toDatabaseModel(syncStatus: SyncStatus, encounterUuid: UUID): BloodPressureMeasurement {
    return BloodPressureMeasurement(
        uuid = uuid,
        reading = BpReading(systolic, diastolic),
        syncStatus = syncStatus,
        userUuid = userUuid,
        facilityUuid = facilityUuid,
        patientUuid = patientUuid,
        encounterUuid = encounterUuid,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        recordedAt = recordedAt
    )
  }

}
