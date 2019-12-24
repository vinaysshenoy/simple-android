package org.simple.clinic.bp.entry

import com.spotify.mobius.test.NextMatchers.hasEffects
import com.spotify.mobius.test.NextMatchers.hasNoModel
import com.spotify.mobius.test.UpdateSpec
import com.spotify.mobius.test.UpdateSpec.assertThatNext
import org.junit.Test
import org.simple.clinic.util.TestUserClock
import org.simple.clinic.util.UserInputDatePaddingCharacter
import org.simple.clinic.widgets.ageanddateofbirth.UserInputDateValidator
import org.threeten.bp.LocalDate
import org.threeten.bp.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

class BloodPressureEntryUpdateTest {

  private val localDate = LocalDate.parse("2018-01-01")
  private val testUserClock = TestUserClock(localDate)
  private val dateValidator = UserInputDateValidator(testUserClock, DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH))
  private val bpValidator = BpValidator()
  private val patientUuid = UUID.fromString("d37b134b-64c3-40d6-8f95-5eb88d075d14")

  private val spec = UpdateSpec(
      BloodPressureEntryUpdate(
          bpValidator = bpValidator,
          dateValidator = dateValidator,
          inputDatePaddingCharacter = UserInputDatePaddingCharacter.ZERO,
          dateInUserTimeZone = localDate
      )
  )

  @Test
  fun `when the entered systolic is blank and save is clicked, the empty systolic error message must be shown`() {
    val model = BloodPressureEntryModel.create(OpenAs.New(patientUuid), localDate.year)
        .systolicChanged("120")
        .diastolicChanged("80")
        .dayChanged(localDate.dayOfMonth.toString())
        .monthChanged(localDate.monthValue.toString())
        .yearChanged(localDate.year.toString().takeLast(2))
        .systolicChanged("")

    spec
        .given(model)
        .whenEvent(SaveClicked)
        .then(
            assertThatNext(
                hasNoModel(),
                hasEffects(ShowEmptySystolicError as BloodPressureEntryEffect)
            )
        )
  }

  @Test
  fun `when the entered systolic is blank and date is clicked, the empty systolic error message must be shown`() {
    val model = BloodPressureEntryModel.create(OpenAs.New(patientUuid), localDate.year)
        .systolicChanged("120")
        .diastolicChanged("80")
        .dayChanged(localDate.dayOfMonth.toString())
        .monthChanged(localDate.monthValue.toString())
        .yearChanged(localDate.year.toString().takeLast(2))
        .systolicChanged("")

    spec
        .given(model)
        .whenEvent(BloodPressureDateClicked)
        .then(
            assertThatNext(
                hasNoModel(),
                hasEffects(ShowEmptySystolicError as BloodPressureEntryEffect)
            )
        )
  }

  @Test
  fun `when the entered diastolic is blank and save is clicked, the empty diastolic error message must be shown`() {
    val model = BloodPressureEntryModel.create(OpenAs.New(patientUuid), localDate.year)
        .systolicChanged("120")
        .diastolicChanged("80")
        .dayChanged(localDate.dayOfMonth.toString())
        .monthChanged(localDate.monthValue.toString())
        .yearChanged(localDate.year.toString().takeLast(2))
        .diastolicChanged("")

    spec
        .given(model)
        .whenEvent(SaveClicked)
        .then(
            assertThatNext(
                hasNoModel(),
                hasEffects(ShowEmptyDiastolicError as BloodPressureEntryEffect)
            )
        )
  }

  @Test
  fun `when the entered diastolic is blank and date is clicked, the empty diastolic error message must be shown`() {
    val model = BloodPressureEntryModel.create(OpenAs.New(patientUuid), localDate.year)
        .systolicChanged("120")
        .diastolicChanged("80")
        .dayChanged(localDate.dayOfMonth.toString())
        .monthChanged(localDate.monthValue.toString())
        .yearChanged(localDate.year.toString().takeLast(2))
        .diastolicChanged("")

    spec
        .given(model)
        .whenEvent(BloodPressureDateClicked)
        .then(
            assertThatNext(
                hasNoModel(),
                hasEffects(ShowEmptyDiastolicError as BloodPressureEntryEffect)
            )
        )
  }
}
