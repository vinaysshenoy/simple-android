package org.simple.clinic.bp.entry

import com.spotify.mobius.Next
import com.spotify.mobius.Update
import org.simple.clinic.bp.BpReading
import org.simple.clinic.bp.entry.BloodPressureEntrySheet.ScreenType.BP_ENTRY
import org.simple.clinic.bp.entry.BloodPressureEntrySheet.ScreenType.DATE_ENTRY
import org.simple.clinic.mobius.dispatch
import org.simple.clinic.mobius.next
import org.simple.clinic.util.UserInputDatePaddingCharacter
import org.simple.clinic.widgets.ageanddateofbirth.UserInputDateValidator
import org.threeten.bp.LocalDate
import org.simple.clinic.bp.BpReading.ValidationResult as BpValidationResult
import org.simple.clinic.widgets.ageanddateofbirth.UserInputDateValidator.Result as DateValidationResult

class BloodPressureEntryUpdate(
    private val dateValidator: UserInputDateValidator,
    private val dateInUserTimeZone: LocalDate,
    private val inputDatePaddingCharacter: UserInputDatePaddingCharacter
) : Update<BloodPressureEntryModel, BloodPressureEntryEvent, BloodPressureEntryEffect> {
  override fun update(
      model: BloodPressureEntryModel,
      event: BloodPressureEntryEvent
  ): Next<BloodPressureEntryModel, BloodPressureEntryEffect> {
    return when (event) {
      is ScreenChanged -> next(model.screenChanged(event.type))
      is SystolicChanged -> onSystolicChanged(model, event)
      is DiastolicChanged -> next(model.diastolicChanged(event.diastolic), HideBpErrorMessage)
      is DiastolicBackspaceClicked -> onDiastolicBackSpaceClicked(model)
      is BloodPressureMeasurementFetched -> onBloodPressureMeasurementFetched(model, event)
      is RemoveBloodPressureClicked -> dispatch(ShowConfirmRemoveBloodPressureDialog((model.openAs as OpenAs.Update).bpUuid))
      is BackPressed -> onBackPressed(model)
      is DayChanged -> onDateChanged(model.dayChanged(event.day))
      is MonthChanged -> onDateChanged(model.monthChanged(event.month))
      is YearChanged -> onDateChanged(model.yearChanged(event.twoDigitYear))
      is BloodPressureDateClicked -> onBloodPressureDateClicked(model)
      is SaveClicked -> onSaveClicked(model)
      is ShowBpClicked -> showBpClicked(model)
      is BloodPressureSaved -> dispatch(SetBpSavedResultAndFinish)
      is DatePrefilled -> next(model.datePrefilled(event.prefilledDate))
    }
  }

  private fun onSystolicChanged(
      model: BloodPressureEntryModel,
      event: SystolicChanged
  ): Next<BloodPressureEntryModel, BloodPressureEntryEffect> {
    val updatedSystolicModel = model.systolicChanged(event.systolic)
    val effects = if (isSystolicValueComplete(event.systolic)) {
      setOf(HideBpErrorMessage, ChangeFocusToDiastolic)
    } else {
      setOf(HideBpErrorMessage)
    }
    return next(updatedSystolicModel, *effects.toTypedArray())
  }

  private fun onDiastolicBackSpaceClicked(
      model: BloodPressureEntryModel
  ): Next<BloodPressureEntryModel, BloodPressureEntryEffect> {
    return if (model.diastolic.isNotEmpty()) {
      next(model.deleteDiastolicLastDigit())
    } else {
      val deleteSystolicLastDigitModel = model.deleteSystolicLastDigit()
      next(deleteSystolicLastDigitModel, ChangeFocusToSystolic, SetSystolic(deleteSystolicLastDigitModel.systolic))
    }
  }

  private fun onBloodPressureMeasurementFetched(
      model: BloodPressureEntryModel,
      event: BloodPressureMeasurementFetched
  ): Next<BloodPressureEntryModel, BloodPressureEntryEffect> {
    val (systolic, diastolic, recordedAt) = event
    val modelWithSystolicAndDiastolic = model
        .systolicChanged(systolic.toString())
        .diastolicChanged(diastolic.toString())

    return next(
        modelWithSystolicAndDiastolic,
        SetSystolic(systolic.toString()),
        SetDiastolic(diastolic.toString()),
        PrefillDate.forUpdateEntry(recordedAt)
    )
  }

  private fun onBackPressed(
      model: BloodPressureEntryModel
  ): Next<BloodPressureEntryModel, BloodPressureEntryEffect> {
    return when (model.activeScreen) {
      BP_ENTRY -> dispatch(Dismiss as BloodPressureEntryEffect)
      DATE_ENTRY -> showBpClicked(model)
    }
  }

  private fun onDateChanged(
      updatedModel: BloodPressureEntryModel
  ): Next<BloodPressureEntryModel, BloodPressureEntryEffect> =
      next(updatedModel, HideDateErrorMessage)

  private fun onBloodPressureDateClicked(
      model: BloodPressureEntryModel
  ): Next<BloodPressureEntryModel, BloodPressureEntryEffect> {
    val isSystolicBlank = model.systolic.isBlank()
    val isDiastolicBlank = model.diastolic.isBlank()

    val effect = when {
      isSystolicBlank -> ShowEmptySystolicError
      isDiastolicBlank -> ShowEmptyDiastolicError
      else -> {
        val bpReading = BpReading(model.systolic.trim().toInt(), model.diastolic.trim().toInt())

        when (bpReading.validate()) {
          BpValidationResult.Valid -> ShowDateEntryScreen
          else -> ShowBpValidationError(bpReading.validate())
        }
      }
    }
    return dispatch(effect)
  }

  private fun onSaveClicked(
      model: BloodPressureEntryModel
  ): Next<BloodPressureEntryModel, BloodPressureEntryEffect> {
    val isSystolicBlank = model.systolic.isBlank()
    val isDiastolicBlank = model.diastolic.isBlank()
    val bpValidationResult = if (!isSystolicBlank && !isDiastolicBlank) BpReading(model.systolic.trim().toInt(), model.diastolic.trim().toInt()).validate() else null
    val dateValidationResult = dateValidator.validate(getDateText(model), dateInUserTimeZone)

    return when {
      isSystolicBlank && dateValidationResult is DateValidationResult.Valid -> dispatch(ShowEmptySystolicError)
      isSystolicBlank && dateValidationResult !is DateValidationResult.Valid -> dispatch(ShowEmptySystolicError, ShowDateValidationError(dateValidationResult))
      isDiastolicBlank && dateValidationResult is DateValidationResult.Valid -> dispatch(ShowEmptyDiastolicError)
      isDiastolicBlank && dateValidationResult !is DateValidationResult.Valid -> dispatch(ShowEmptyDiastolicError, ShowDateValidationError(dateValidationResult))
      bpValidationResult != null && bpValidationResult !is BpValidationResult.Valid && dateValidationResult is DateValidationResult.Valid -> dispatch(ShowBpValidationError(bpValidationResult))
      bpValidationResult != null && bpValidationResult is BpValidationResult.Valid && dateValidationResult !is DateValidationResult.Valid -> dispatch(ShowDateValidationError(dateValidationResult))
      bpValidationResult != null && bpValidationResult !is BpValidationResult.Valid && dateValidationResult !is DateValidationResult.Valid -> dispatch(ShowBpValidationError(bpValidationResult), ShowDateValidationError(dateValidationResult))
      else -> dispatch(getCreateOrUpdateEntryEffect(model, dateValidationResult))
    }
  }

  private fun showBpClicked(
      model: BloodPressureEntryModel
  ): Next<BloodPressureEntryModel, BloodPressureEntryEffect> {
    val result = dateValidator.validate(getDateText(model), dateInUserTimeZone)
    val effect = if (result is DateValidationResult.Valid) {
      ShowBpEntryScreen(result.parsedDate)
    } else {
      ShowDateValidationError(result)
    }
    return dispatch(effect)
  }

  private fun getCreateOrUpdateEntryEffect(
      model: BloodPressureEntryModel,
      dateValidationResult: DateValidationResult
  ): BloodPressureEntryEffect {
    val systolic = model.systolic.toInt()
    val diastolic = model.diastolic.toInt()
    val userEnteredDate = (dateValidationResult as DateValidationResult.Valid).parsedDate
    val prefilledDate = model.prefilledDate!!

    return when (val openAs = model.openAs) {
      is OpenAs.New -> CreateNewBpEntry(openAs.patientUuid, systolic, diastolic, userEnteredDate, prefilledDate)
      is OpenAs.Update -> UpdateBpEntry(openAs.bpUuid, systolic, diastolic, userEnteredDate, prefilledDate)
    }
  }

  private fun getDateText(model: BloodPressureEntryModel) =
      formatToPaddedDate(model.day, model.month, model.twoDigitYear, model.year)

  private fun isSystolicValueComplete(systolicText: String): Boolean {
    return (systolicText.length == 3 && systolicText.matches("^[123].*$".toRegex()))
        || (systolicText.length == 2 && systolicText.matches("^[789].*$".toRegex()))
  }

  private fun formatToPaddedDate(day: String, month: String, twoDigitYear: String, fourDigitYear: String): String {
    val paddedDd = day.padStart(length = 2, padChar = inputDatePaddingCharacter.value)
    val paddedMm = month.padStart(length = 2, padChar = inputDatePaddingCharacter.value)
    val paddedYy = twoDigitYear.padStart(length = 2, padChar = inputDatePaddingCharacter.value)

    val firstTwoDigitsOfYear = fourDigitYear.substring(0, 2)
    val paddedYyyy = firstTwoDigitsOfYear + paddedYy
    return "$paddedDd/$paddedMm/$paddedYyyy"
  }
}
