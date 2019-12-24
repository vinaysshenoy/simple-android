package org.simple.clinic.bp.entry

import com.spotify.mobius.rx2.RxMobius
import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject
import io.reactivex.Completable
import io.reactivex.ObservableTransformer
import io.reactivex.Scheduler
import io.reactivex.rxkotlin.cast
import org.simple.clinic.ReportAnalyticsEvents
import org.simple.clinic.bp.BloodPressureMeasurement
import org.simple.clinic.bp.BpReading
import org.simple.clinic.bp.entry.BpValidator.Validation
import org.simple.clinic.bp.entry.BpValidator.Validation.ErrorDiastolicEmpty
import org.simple.clinic.bp.entry.BpValidator.Validation.ErrorDiastolicTooHigh
import org.simple.clinic.bp.entry.BpValidator.Validation.ErrorDiastolicTooLow
import org.simple.clinic.bp.entry.BpValidator.Validation.ErrorSystolicEmpty
import org.simple.clinic.bp.entry.BpValidator.Validation.ErrorSystolicLessThanDiastolic
import org.simple.clinic.bp.entry.BpValidator.Validation.ErrorSystolicTooHigh
import org.simple.clinic.bp.entry.BpValidator.Validation.ErrorSystolicTooLow
import org.simple.clinic.bp.entry.BpValidator.Validation.Success
import org.simple.clinic.bp.entry.PrefillDate.PrefillSpecificDate
import org.simple.clinic.facility.Facility
import org.simple.clinic.functions.Function0
import org.simple.clinic.functions.Function1
import org.simple.clinic.functions.Function2
import org.simple.clinic.functions.Function4
import org.simple.clinic.user.User
import org.simple.clinic.util.UserClock
import org.simple.clinic.util.exhaustive
import org.simple.clinic.util.scheduler.SchedulersProvider
import org.simple.clinic.util.toLocalDateAtZone
import org.simple.clinic.util.toUtcInstant
import org.simple.clinic.widgets.ageanddateofbirth.UserInputDateValidator.Result
import org.simple.clinic.widgets.ageanddateofbirth.UserInputDateValidator.Result.Invalid.DateIsInFuture
import org.simple.clinic.widgets.ageanddateofbirth.UserInputDateValidator.Result.Invalid.InvalidPattern
import org.simple.clinic.widgets.ageanddateofbirth.UserInputDateValidator.Result.Valid
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import java.util.UUID

class BloodPressureEntryEffectHandler @AssistedInject constructor(
    @Assisted private val ui: BloodPressureEntryUi,
    private val userClock: UserClock,
    private val schedulersProvider: SchedulersProvider,
    private val fetchCurrentUser: Function0<User>,
    private val fetchCurrentFacility: Function0<Facility>,
    private val updatePatientRecordedEffect: Function2<UUID, Instant, Unit>,
    private val markAppointmentsCreatedBeforeTodayAsVisitedEffect: Function1<UUID, Unit>,
    private val fetchExistingBloodPressureMeasurement: Function1<UUID, BloodPressureMeasurement>,
    private val recordNewMeasurementEffect: Function4<UUID, Int, Int, Instant, BloodPressureMeasurement>,
    private val updateMeasurementEffect: Function1<BloodPressureMeasurement, Unit>
) {

  @AssistedInject.Factory
  interface Factory {
    fun create(ui: BloodPressureEntryUi): BloodPressureEntryEffectHandler
  }

  private val reportAnalyticsEvents = ReportAnalyticsEvents()

  fun build(): ObservableTransformer<BloodPressureEntryEffect, BloodPressureEntryEvent> {
    return RxMobius
        .subtypeEffectHandler<BloodPressureEntryEffect, BloodPressureEntryEvent>()
        .addTransformer(PrefillDate::class.java, prefillDate(schedulersProvider.ui()))
        .addAction(HideBpErrorMessage::class.java, ui::hideBpErrorMessage, schedulersProvider.ui())
        .addAction(ChangeFocusToDiastolic::class.java, ui::changeFocusToDiastolic, schedulersProvider.ui())
        .addAction(ChangeFocusToSystolic::class.java, ui::changeFocusToSystolic, schedulersProvider.ui())
        .addConsumer(SetSystolic::class.java, { ui.setSystolic(it.systolic) }, schedulersProvider.ui())
        .addTransformer(FetchBloodPressureMeasurement::class.java, fetchBloodPressureMeasurement(schedulersProvider.io()))
        .addConsumer(SetDiastolic::class.java, { ui.setDiastolic(it.diastolic) }, schedulersProvider.ui())
        .addConsumer(ShowConfirmRemoveBloodPressureDialog::class.java, { ui.showConfirmRemoveBloodPressureDialog(it.bpUuid) }, schedulersProvider.ui())
        .addAction(Dismiss::class.java, ui::dismiss, schedulersProvider.ui())
        .addAction(HideDateErrorMessage::class.java, ui::hideDateErrorMessage, schedulersProvider.ui())
        .addConsumer(ShowBpValidationError::class.java, { showBpValidationError(it.result) }, schedulersProvider.ui())
        .addAction(ShowDateEntryScreen::class.java, ui::showDateEntryScreen, schedulersProvider.ui())
        .addConsumer(ShowBpEntryScreen::class.java, { showBpEntryScreen(it.date) }, schedulersProvider.ui())
        .addConsumer(ShowDateValidationError::class.java, { showDateValidationError(it.result) }, schedulersProvider.ui())
        .addTransformer(CreateNewBpEntry::class.java, createNewBpEntryTransformer())
        .addAction(SetBpSavedResultAndFinish::class.java, ui::setBpSavedResultAndFinish, schedulersProvider.ui())
        .addTransformer(UpdateBpEntry::class.java, updateBpEntryTransformer())
        .addAction(ShowEmptySystolicError::class.java, ui::showSystolicEmptyError, schedulersProvider.ui())
        .addAction(ShowEmptyDiastolicError::class.java, ui::showDiastolicEmptyError, schedulersProvider.ui())
        .build()
  }

  private fun prefillDate(scheduler: Scheduler): ObservableTransformer<PrefillDate, BloodPressureEntryEvent> {
    return ObservableTransformer { prefillDates ->
      prefillDates
          .map(::convertToLocalDate)
          .observeOn(scheduler)
          .doOnNext { setDateOnInputFields(it) }
          .doOnNext { ui.showDateOnDateButton(it) }
          .map { DatePrefilled(it) }
    }
  }

  private fun convertToLocalDate(prefillDate: PrefillDate): LocalDate {
    val instant = if (prefillDate is PrefillSpecificDate) prefillDate.date else Instant.now(userClock)
    return instant.toLocalDateAtZone(userClock.zone)
  }

  private fun setDateOnInputFields(dateToSet: LocalDate) {
    ui.setDateOnInputFields(
        dateToSet.dayOfMonth.toString(),
        dateToSet.monthValue.toString(),
        getYear(dateToSet)
    )
  }

  private fun getYear(date: LocalDate): String =
      date.year.toString().substring(startIndex = 2, endIndex = 4)

  private fun fetchBloodPressureMeasurement(
      scheduler: Scheduler
  ): ObservableTransformer<FetchBloodPressureMeasurement, BloodPressureEntryEvent> {
    return ObservableTransformer { fetchBloodPressureMeasurement ->
      fetchBloodPressureMeasurement
          .observeOn(scheduler)
          .map { fetchExistingBloodPressureMeasurement.call(it.bpUuid) }
          .map { BloodPressureMeasurementFetched(it.reading.systolic, it.reading.diastolic, it.recordedAt) }
    }
  }

  private fun showBpValidationError(bpValidation: Validation) {
    when (bpValidation) {
      is ErrorSystolicLessThanDiastolic -> ui.showSystolicLessThanDiastolicError()
      is ErrorSystolicTooHigh -> ui.showSystolicHighError()
      is ErrorSystolicTooLow -> ui.showSystolicLowError()
      is ErrorDiastolicTooHigh -> ui.showDiastolicHighError()
      is ErrorDiastolicTooLow -> ui.showDiastolicLowError()
      is ErrorSystolicEmpty -> ui.showSystolicEmptyError()
      is ErrorDiastolicEmpty -> ui.showDiastolicEmptyError()
      is Success -> {
        /* Nothing to do here. */
      }
    }.exhaustive()
  }

  private fun showBpEntryScreen(entryDate: LocalDate) {
    with(ui) {
      showBpEntryScreen()
      showDateOnDateButton(entryDate)
    }
  }

  private fun showDateValidationError(result: Result) {
    when (result) {
      is InvalidPattern -> ui.showInvalidDateError()
      is DateIsInFuture -> ui.showDateIsInFutureError()
      is Valid -> throw IllegalStateException("Date validation error cannot be $result")
    }.exhaustive()
  }

  private fun createNewBpEntryTransformer(): ObservableTransformer<CreateNewBpEntry, BloodPressureEntryEvent> {
    return ObservableTransformer { createNewBpEntries ->
      createNewBpEntries
          .map { createNewBpEntry ->
            val (patientUuid, systolic, diastolic, date) = createNewBpEntry
            val recordedBp = recordNewMeasurementEffect.call(patientUuid, systolic, diastolic, date.toUtcInstant(userClock))
            updateAppointmentsAsVisited(createNewBpEntry, recordedBp)
          }
          .compose(reportAnalyticsEvents)
          .cast()
    }
  }

  private fun updateAppointmentsAsVisited(
      createNewBpEntry: CreateNewBpEntry,
      bloodPressureMeasurement: BloodPressureMeasurement
  ): BloodPressureSaved {
    markAppointmentsCreatedBeforeTodayAsVisitedEffect.call(bloodPressureMeasurement.patientUuid)

    val entryDate = createNewBpEntry.userEnteredDate.toUtcInstant(userClock)
    updatePatientRecordedEffect.call(bloodPressureMeasurement.patientUuid, entryDate)

    return BloodPressureSaved(createNewBpEntry.wasDateChanged)
  }

  private fun updateBpEntryTransformer(): ObservableTransformer<UpdateBpEntry, BloodPressureEntryEvent> {
    return ObservableTransformer { updateBpEntries ->
      updateBpEntries
          .map { updateBpEntry ->
            val existingMeasurement = fetchExistingBloodPressureMeasurement.call(updateBpEntry.bpUuid)
            val updatedMeasurement = updateBloodPressureMeasurementValues(existingMeasurement, updateBpEntry)
            updatedMeasurement to updateBpEntry.wasDateChanged
          }
          .doOnNext { (bloodPressureMeasurement, _) -> storeUpdateBloodPressureMeasurement(bloodPressureMeasurement) }
          .map { (_, wasDateChanged) -> BloodPressureSaved(wasDateChanged) }
          .compose(reportAnalyticsEvents)
          .cast()
    }
  }

  private fun storeUpdateBloodPressureMeasurement(bloodPressureMeasurement: BloodPressureMeasurement) {
    updateMeasurementEffect.call(bloodPressureMeasurement)
    updatePatientRecordedEffect.call(bloodPressureMeasurement.patientUuid, bloodPressureMeasurement.recordedAt)
  }

  private fun updateBloodPressureMeasurementValues(
      existingMeasurement: BloodPressureMeasurement,
      updateBpEntry: UpdateBpEntry
  ): BloodPressureMeasurement {
    val (_, systolic, diastolic, parsedDateFromForm, _) = updateBpEntry
    val user = fetchCurrentUser.call()
    val facility = fetchCurrentFacility.call()

    return existingMeasurement.updated(
        userUuid = user.uuid,
        facilityUuid = facility.uuid,
        reading = BpReading(systolic, diastolic),
        recordedAt = parsedDateFromForm.toUtcInstant(userClock)
    )
  }

}
