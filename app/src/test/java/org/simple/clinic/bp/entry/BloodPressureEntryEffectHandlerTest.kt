package org.simple.clinic.bp.entry

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import org.junit.After
import org.junit.Test
import org.simple.clinic.functions.Function0
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
  private val effectHandler = BloodPressureEntryEffectHandler(
      ui = ui,
      facilityRepository = mock(),
      patientRepository = mock(),
      bloodPressureRepository = mock(),
      appointmentsRepository = mock(),
      userClock = userClock,
      schedulersProvider = TrampolineSchedulersProvider(),
      fetchCurrentUser = Function0 { PatientMocker.loggedInUser(uuid = UUID.fromString("1080d084-9835-4d9c-a279-327c2d52577a")) }
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
