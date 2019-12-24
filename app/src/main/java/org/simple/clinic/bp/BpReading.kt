package org.simple.clinic.bp

data class BpReading(val systolic: Int, val diastolic: Int) {
  sealed class ValidationResult {
    object Valid : ValidationResult()

    object ErrorSystolicTooHigh : ValidationResult()
    object ErrorSystolicTooLow : ValidationResult()
    object ErrorDiastolicTooHigh : ValidationResult()
    object ErrorDiastolicTooLow : ValidationResult()
    object ErrorSystolicLessThanDiastolic : ValidationResult()
  }
}
