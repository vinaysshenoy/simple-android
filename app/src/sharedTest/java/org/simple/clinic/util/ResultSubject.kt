package org.simple.clinic.util

import com.google.common.truth.CustomSubjectBuilder
import com.google.common.truth.CustomSubjectBuilder.Factory
import com.google.common.truth.FailureMetadata
import com.google.common.truth.Subject
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertAbout
import org.simple.clinic.util.ResultSubject.ResultSubjectBuilder.Companion.results

class ResultSubject<T>(
    failureMetadata: FailureMetadata,
    private val actual: Result<T>?
) : Subject(failureMetadata, actual) {

  companion object {
    fun <T> assertThat(actual: Result<T>?): ResultSubject<T> {
      return assertAbout(results()).that(actual)
    }
  }

  fun isSuccess() {
    requireNotNull(actual)
    Truth.assertThat(actual.isSuccess).isTrue()
  }

  fun isFailure() {
    requireNotNull(actual)
    Truth.assertThat(actual.isFailure).isTrue()
  }

  fun hasValue(expected: T) {
    requireNotNull(actual)
    Truth.assertThat(actual.getOrNull()).isEqualTo(expected)
  }

  fun hasException(expected: Throwable) {
    requireNotNull(actual)
    Truth.assertThat(actual.exceptionOrNull()).isEqualTo(expected)
  }

  class ResultSubjectBuilder private constructor(failureMetadata: FailureMetadata) : CustomSubjectBuilder(failureMetadata) {

    companion object {
      fun results(): Factory<ResultSubjectBuilder> = Factory(::ResultSubjectBuilder)
    }

    fun <T> that(actual: Result<T>?): ResultSubject<T> = ResultSubject(metadata(), actual)
  }
}
