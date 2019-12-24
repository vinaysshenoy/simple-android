package org.simple.clinic.bp

import org.simple.clinic.bp.BpReading.ValidationResult.ErrorDiastolicTooHigh
import org.simple.clinic.bp.BpReading.ValidationResult.ErrorDiastolicTooLow
import org.simple.clinic.bp.BpReading.ValidationResult.ErrorSystolicLessThanDiastolic
import org.simple.clinic.bp.BpReading.ValidationResult.ErrorSystolicTooHigh
import org.simple.clinic.bp.BpReading.ValidationResult.ErrorSystolicTooLow
import org.simple.clinic.bp.BpReading.ValidationResult.Valid

data class BpReading(val systolic: Int, val diastolic: Int) {
  sealed class ValidationResult {
    object Valid : ValidationResult()

    object ErrorSystolicTooHigh : ValidationResult()
    object ErrorSystolicTooLow : ValidationResult()
    object ErrorDiastolicTooHigh : ValidationResult()
    object ErrorDiastolicTooLow : ValidationResult()
    object ErrorSystolicLessThanDiastolic : ValidationResult()
  }

  fun validate(): ValidationResult {
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
