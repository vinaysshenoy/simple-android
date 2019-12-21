package org.simple.clinic.bp.entry

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import org.junit.After
import org.junit.Test
import org.simple.clinic.functions.Function0
import org.simple.clinic.functions.Function1
import org.simple.clinic.functions.Function2
import org.simple.clinic.functions.Function4
import org.simple.clinic.mobius.EffectHandlerTestCase
import org.simple.clinic.patient.PatientMocker
import org.simple.clinic.util.TestUserClock
import org.simple.clinic.util.scheduler.TrampolineSchedulersProvider
import org.threeten.bp.LocalDate
import org.threeten.bp.ZoneOffset.UTC
import java.util.UUID

class BloodPressureEntryEffectHandlerTest {
  private val ui = mock<BloodPressureEntryUi>()
  private val userClock = TestUserClock()
  private val userUuid = UUID.fromString("1080d084-9835-4d9c-a279-327c2d52577a")
  private val facilityUuid = UUID.fromString("4ac91d66-b805-4342-8f0b-94cdbb6ae5ae")
  private val existingBpUuid = UUID.fromString("93f40be0-01c1-4139-98fb-0818132ff184")
  private val newBpUuid = UUID.fromString("ce38fb46-3a30-4d2a-baed-a9295350c481")
  private val patientUuid = UUID.fromString("a619a655-544b-4eec-80c9-0a5efc264ec1")

  private val effectHandler = BloodPressureEntryEffectHandler(
      ui = ui,
      bloodPressureRepository = mock(),
      userClock = userClock,
      schedulersProvider = TrampolineSchedulersProvider(),
      fetchCurrentUser = Function0 { PatientMocker.loggedInUser(uuid = userUuid) },
      fetchCurrentFacility = Function0 { PatientMocker.facility(uuid = facilityUuid) },
      updatePatientRecordedEffect = Function2 { _, _ -> },
      markAppointmentsCreatedBeforeTodayAsVisitedEffect = Function1 { },
      fetchExistingBloodPressureMeasurement = Function1 { PatientMocker.bp(uuid = existingBpUuid, patientUuid = patientUuid, facilityUuid = facilityUuid, userUuid = userUuid) },
      recordNewMeasurementEffect = Function4 { _, _, _, _ -> PatientMocker.bp(uuid = newBpUuid, patientUuid = patientUuid, facilityUuid = facilityUuid, userUuid = userUuid) }
  ).build()
  private val testCase = EffectHandlerTestCase(effectHandler)

  @After
  fun teardown() {
    testCase.dispose()
  }

  @Test
  fun `when prefill date is dispatched, then populate date button and date input fields`() {
    // when
    val entryDate = LocalDate.of(1992, 6, 7)
    userClock.setDate(LocalDate.of(1992, 6, 7), UTC)
    testCase.dispatch(PrefillDate.forNewEntry())

    // then
    verify(ui).setDateOnInputFields("7", "6", "92")
    verify(ui).showDateOnDateButton(entryDate)
    testCase.assertOutgoingEvents(DatePrefilled(entryDate))
  }
}
