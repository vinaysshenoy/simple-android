package org.simple.clinic.bp.entry

import org.simple.clinic.bp.entry.BpValidator.Result.ErrorDiastolicTooHigh
import org.simple.clinic.bp.entry.BpValidator.Result.ErrorDiastolicTooLow
import org.simple.clinic.bp.entry.BpValidator.Result.ErrorSystolicLessThanDiastolic
import org.simple.clinic.bp.entry.BpValidator.Result.ErrorSystolicTooHigh
import org.simple.clinic.bp.entry.BpValidator.Result.ErrorSystolicTooLow
import org.simple.clinic.bp.entry.BpValidator.Result.Valid
import javax.inject.Inject

class BpValidator @Inject constructor() {

  sealed class Result {
    object Valid : Result()

    object ErrorSystolicTooHigh : Result()
    object ErrorSystolicTooLow : Result()
    object ErrorDiastolicTooHigh : Result()
    object ErrorDiastolicTooLow : Result()
    object ErrorSystolicLessThanDiastolic : Result()
  }

  fun validate(systolic: Int, diastolic: Int): Result {
    return when {
      systolic < 70 -> ErrorSystolicTooLow
      systolic > 300 -> ErrorSystolicTooHigh
      diastolic < 40 -> ErrorDiastolicTooLow
      diastolic > 180 -> ErrorDiastolicTooHigh
      systolic < diastolic -> ErrorSystolicLessThanDiastolic
      else -> Valid
    }
  }
}
