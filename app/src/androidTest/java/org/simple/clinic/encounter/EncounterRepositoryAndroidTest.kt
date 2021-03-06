package org.simple.clinic.encounter

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.simple.clinic.AppDatabase
import org.simple.clinic.TestClinicApp
import org.simple.clinic.TestData
import org.simple.clinic.patient.SyncStatus
import org.simple.clinic.rules.LocalAuthenticationRule
import org.simple.clinic.util.RxErrorsRule
import org.threeten.bp.Instant
import java.util.UUID
import javax.inject.Inject


class EncounterRepositoryAndroidTest {

  @Inject
  lateinit var appDatabase: AppDatabase

  @Inject
  lateinit var repository: EncounterRepository

  @Inject
  lateinit var testData: TestData

  private val authenticationRule = LocalAuthenticationRule()

  private val rxErrorsRule = RxErrorsRule()

  @get:Rule
  val ruleChain = RuleChain
      .outerRule(authenticationRule)
      .around(rxErrorsRule)!!

  @Before
  fun setUp() {
    TestClinicApp.appComponent().inject(this)
  }

  @Test
  fun record_count_should_return_the_count_of_no_of_encounters() {
    //given
    repository.save(listOf(testData.encounter(
        uuid = UUID.fromString("306b0298-a04d-4680-a55f-9c8696512aa1"),
        patientUuid = UUID.fromString("2ffd2254-4a22-4b1a-9abf-841f5ae5bea3"),
        facilityUuid = UUID.fromString("05e105d8-8a62-4b85-ba3a-319ad742ceb0")
    ))).blockingAwait()

    //when
    val count = repository.recordCount().blockingFirst()

    //then
    assertThat(count).isEqualTo(1)
  }

  @Test
  fun encounter_payload_should_merge_correctly_with_encounter_database_model() {
    //given
    val patientUuid = UUID.fromString("c2273c6c-036b-41f0-a128-84d5551be3ce")
    val encounterUuid1 = UUID.fromString("0b144e4a-ce13-431d-93f1-1d10d0639c09")
    val encounterUuid2 = UUID.fromString("0799e067-6b14-4e45-8171-e6a9d84626fb")

    val encountersPayload1 = testData.encounterPayload(uuid = encounterUuid1, patientUuid = patientUuid, updatedAt = Instant.parse("2018-02-11T00:00:00Z"))
    val encountersPayload2 = testData.encounterPayload(uuid = encounterUuid2, patientUuid = patientUuid)

    repository.save(listOf(testData.encounter(uuid = encounterUuid1, patientUuid = patientUuid, syncStatus = SyncStatus.PENDING, updatedAt = Instant.parse("2018-02-13T00:00:00Z")))).blockingAwait()

    //when
    repository.mergeWithLocalData(listOf(encountersPayload1, encountersPayload2)).blockingAwait()
    val encounter = repository.recordsWithSyncStatus(SyncStatus.DONE).blockingGet()
    val encounterPending = repository.recordsWithSyncStatus(SyncStatus.PENDING).blockingGet()

    //then
    assertThat(encounter.size).isEqualTo(1)
    assertThat(encounter.first().uuid).isEqualTo(encounterUuid2)

    with(encounterPending.first()) {
      assertThat(uuid).isEqualTo(encounterUuid1)
      assertThat(updatedAt).isEqualTo(Instant.parse("2018-02-13T00:00:00Z"))
    }
  }
}
