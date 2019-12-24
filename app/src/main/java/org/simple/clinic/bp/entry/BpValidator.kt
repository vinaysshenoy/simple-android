package org.simple.clinic.bp.entry

import org.simple.clinic.bp.entry.BpValidator.Validation.ErrorDiastolicTooHigh
import org.simple.clinic.bp.entry.BpValidator.Validation.ErrorDiastolicTooLow
import org.simple.clinic.bp.entry.BpValidator.Validation.ErrorSystolicLessThanDiastolic
import org.simple.clinic.bp.entry.BpValidator.Validation.ErrorSystolicTooHigh
import org.simple.clinic.bp.entry.BpValidator.Validation.ErrorSystolicTooLow
import org.simple.clinic.bp.entry.BpValidator.Validation.Success
import javax.inject.Inject

class BpValidator @Inject constructor() {

  // TODO: Rename to "Result".
  sealed class Validation {
    // TODO: Rename to "Valid".
    data class Success(
        val systolic: Int,
        val diastolic: Int
    ) : Validation()

    object ErrorSystolicTooHigh : Validation()
    object ErrorSystolicTooLow : Validation()
    object ErrorDiastolicTooHigh : Validation()
    object ErrorDiastolicTooLow : Validation()
    object ErrorSystolicLessThanDiastolic : Validation()
  }

  fun validate(systolic: Int, diastolic: Int): Validation {
    return when {
      systolic < 70 -> ErrorSystolicTooLow
      systolic > 300 -> ErrorSystolicTooHigh
      diastolic < 40 -> ErrorDiastolicTooLow
      diastolic > 180 -> ErrorDiastolicTooHigh
      systolic < diastolic -> ErrorSystolicLessThanDiastolic
      else -> Success(systolic, diastolic)
    }
  }
}
