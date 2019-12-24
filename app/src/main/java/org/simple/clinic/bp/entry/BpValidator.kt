package org.simple.clinic.bp.entry

import org.simple.clinic.bp.BpReading
import org.simple.clinic.bp.BpReading.ValidationResult.ErrorDiastolicTooHigh
import org.simple.clinic.bp.BpReading.ValidationResult.ErrorDiastolicTooLow
import org.simple.clinic.bp.BpReading.ValidationResult.ErrorSystolicLessThanDiastolic
import org.simple.clinic.bp.BpReading.ValidationResult.ErrorSystolicTooHigh
import org.simple.clinic.bp.BpReading.ValidationResult.ErrorSystolicTooLow
import org.simple.clinic.bp.BpReading.ValidationResult.Valid
import javax.inject.Inject

class BpValidator @Inject constructor() {

  fun validate(systolic: Int, diastolic: Int): BpReading.ValidationResult {
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
