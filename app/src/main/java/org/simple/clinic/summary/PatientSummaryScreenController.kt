package org.simple.clinic.summary

import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject
import io.reactivex.Observable
import io.reactivex.ObservableSource
import io.reactivex.ObservableTransformer
import io.reactivex.rxkotlin.Observables
import io.reactivex.rxkotlin.cast
import io.reactivex.rxkotlin.ofType
import io.reactivex.rxkotlin.withLatestFrom
import org.simple.clinic.ReplayUntilScreenIsDestroyed
import org.simple.clinic.ReportAnalyticsEvents
import org.simple.clinic.analytics.Analytics
import org.simple.clinic.bp.BloodPressureMeasurement
import org.simple.clinic.drugs.PrescribedDrug
import org.simple.clinic.functions.Function1
import org.simple.clinic.functions.Function2
import org.simple.clinic.medicalhistory.Answer
import org.simple.clinic.medicalhistory.MedicalHistory
import org.simple.clinic.medicalhistory.MedicalHistoryQuestion
import org.simple.clinic.medicalhistory.MedicalHistoryQuestion.DIAGNOSED_WITH_HYPERTENSION
import org.simple.clinic.medicalhistory.MedicalHistoryQuestion.HAS_DIABETES
import org.simple.clinic.medicalhistory.MedicalHistoryQuestion.HAS_HAD_A_HEART_ATTACK
import org.simple.clinic.medicalhistory.MedicalHistoryQuestion.HAS_HAD_A_KIDNEY_DISEASE
import org.simple.clinic.medicalhistory.MedicalHistoryQuestion.HAS_HAD_A_STROKE
import org.simple.clinic.medicalhistory.MedicalHistoryQuestion.IS_ON_TREATMENT_FOR_HYPERTENSION
import org.simple.clinic.overdue.Appointment
import org.simple.clinic.overdue.Appointment.Status.Cancelled
import org.simple.clinic.overdue.AppointmentCancelReason.InvalidPhoneNumber
import org.simple.clinic.patient.Patient
import org.simple.clinic.patient.PatientAddress
import org.simple.clinic.patient.PatientPhoneNumber
import org.simple.clinic.patient.businessid.BusinessId
import org.simple.clinic.summary.OpenIntention.LinkIdWithPatient
import org.simple.clinic.summary.OpenIntention.ViewExistingPatient
import org.simple.clinic.summary.OpenIntention.ViewNewPatient
import org.simple.clinic.util.None
import org.simple.clinic.util.Optional
import org.simple.clinic.util.exhaustive
import org.simple.clinic.util.filterTrue
import org.simple.clinic.util.zipWith
import org.simple.clinic.widgets.UiEvent
import org.threeten.bp.Instant
import java.util.UUID

typealias Ui = PatientSummaryScreenUi
typealias UiChange = (Ui) -> Unit

class PatientSummaryScreenController @AssistedInject constructor(
    @Assisted private val patientUuid: UUID,
    @Assisted private val openIntention: OpenIntention,
    @Assisted private val screenCreatedTimestamp: Instant,
    private val hasShownMissingPhoneReminder: Function1<UUID, Boolean>,
    private val fetchLastCreatedAppointment: Function1<UUID, Optional<Appointment>>,
    private val fetchMedicalHistory: Function1<UUID, MedicalHistory>,
    private val patientPrescriptionProvider: Function1<UUID, Observable<List<PrescribedDrug>>>,
    private val bloodPressureCountProvider: Function1<UUID, Int>,
    private val bloodPressuresProvider: Function1<UUID, Observable<List<BloodPressureMeasurement>>>,
    private val patientDataChangedSinceProvider: Function2<UUID, Instant, Boolean>,
    private val fetchPatientPhoneNumber: Function1<UUID, Optional<PatientPhoneNumber>>,
    private val patientBpPassportProvider: Function1<UUID, Observable<Optional<BusinessId>>>,
    private val patientAddressProvider: Function1<UUID, Observable<PatientAddress>>,
    private val patientProvider: Function1<UUID, Observable<Patient>>,
    private val markReminderAsShownEffect: Function1<UUID, Result<Unit>>,
    private val updateMedicalHistoryEffect: Function1<MedicalHistory, Result<Unit>>
) : ObservableTransformer<UiEvent, UiChange> {

  @AssistedInject.Factory
  interface Factory {
    fun create(patientUuid: UUID, openIntention: OpenIntention, screenCreatedTimestamp: Instant): PatientSummaryScreenController
  }

  override fun apply(events: Observable<UiEvent>): ObservableSource<UiChange> {
    val replayedEvents = ReplayUntilScreenIsDestroyed(events)
        .compose(mergeWithPatientSummaryChanges())
        .compose(ReportAnalyticsEvents())
        .replay()

    return Observable.mergeArray(
        populateList(replayedEvents),
        reportViewedPatientEvent(replayedEvents),
        populatePatientProfile(),
        updateMedicalHistory(replayedEvents),
        openBloodPressureBottomSheet(replayedEvents),
        openPrescribedDrugsScreen(replayedEvents),
        exitScreenAfterSchedulingAppointment(replayedEvents),
        openBloodPressureUpdateSheet(replayedEvents),
        openLinkIdWithPatientSheet(replayedEvents),
        showUpdatePhoneDialogIfRequired(replayedEvents),
        showScheduleAppointmentSheet(replayedEvents),
        goBackWhenBackClicked(replayedEvents),
        goToHomeOnDoneClick(replayedEvents),
        exitScreenIfLinkIdWithPatientIsCancelled(replayedEvents),
        hideLinkIdWithPatientSheet(replayedEvents)
    )
  }

  private fun reportViewedPatientEvent(events: Observable<UiEvent>): Observable<UiChange> {
    return events.ofType<PatientSummaryScreenCreated>()
        .take(1L)
        .doOnNext { Analytics.reportViewedPatient(patientUuid, openIntention.analyticsName()) }
        .flatMap { Observable.empty<UiChange>() }
  }

  private fun populatePatientProfile(): Observable<UiChange> {
    val sharedPatients = patientProvider.call(patientUuid)
        .replay(1)
        .refCount()

    val addresses = sharedPatients
        .map { it.addressUuid }
        .flatMap(patientAddressProvider::call)

    val bpPassports = patientBpPassportProvider.call(patientUuid)

    return Observables
        .combineLatest(sharedPatients, addresses, bpPassports) { patient, address, bpPassport ->
          PatientSummaryProfile(
              patient = patient,
              address = address,
              phoneNumber = fetchPatientPhoneNumber.call(patientUuid),
              bpPassport = bpPassport
          )
        }
        .map { patientSummaryProfile -> { ui: Ui -> showPatientSummaryProfile(ui, patientSummaryProfile) } }
  }

  private fun showPatientSummaryProfile(ui: Ui, patientSummaryProfile: PatientSummaryProfile) {
    with(ui) {
      populatePatientProfile(patientSummaryProfile)
      showEditButton()
    }
  }

  private fun mergeWithPatientSummaryChanges(): ObservableTransformer<UiEvent, UiEvent> {
    return ObservableTransformer { events ->
      val prescribedDrugsStream = patientPrescriptionProvider.call(patientUuid)
      val bloodPressures = bloodPressuresProvider.call(patientUuid)

      // combineLatest() is important here so that the first data-set for the list
      // is dispatched in one go instead of them appearing one after another on the UI.
      val summaryItemChanges = Observables
          .combineLatest(prescribedDrugsStream, bloodPressures) { prescribedDrugs, bloodPressureMeasurements ->
            PatientSummaryItemChanged(PatientSummaryItems(
                prescription = prescribedDrugs,
                bloodPressures = bloodPressureMeasurements,
                medicalHistory = fetchMedicalHistory.call(patientUuid)
            ))
          }
          .distinctUntilChanged()

      events.mergeWith(summaryItemChanges)
    }
  }

  private fun populateList(events: Observable<UiEvent>): Observable<UiChange> {
    return events
        .ofType<PatientSummaryItemChanged>()
        .map { it.patientSummaryItems }
        .map { patientSummary ->
          { ui: Ui -> ui.populateList(patientSummary.prescription, patientSummary.bloodPressures, patientSummary.medicalHistory) }
        }
  }

  private fun updateMedicalHistory(events: Observable<UiEvent>): Observable<UiChange> {
    val updateMedicalHistory = { question: MedicalHistoryQuestion, answer: Answer ->
      val currentMedicalHistory = fetchMedicalHistory.call(patientUuid)
      when (question) {
        DIAGNOSED_WITH_HYPERTENSION -> currentMedicalHistory.copy(diagnosedWithHypertension = answer)
        IS_ON_TREATMENT_FOR_HYPERTENSION -> currentMedicalHistory.copy(isOnTreatmentForHypertension = answer)
        HAS_HAD_A_HEART_ATTACK -> currentMedicalHistory.copy(hasHadHeartAttack = answer)
        HAS_HAD_A_STROKE -> currentMedicalHistory.copy(hasHadStroke = answer)
        HAS_HAD_A_KIDNEY_DISEASE -> currentMedicalHistory.copy(hasHadKidneyDisease = answer)
        HAS_DIABETES -> currentMedicalHistory.copy(hasDiabetes = answer)
      }
    }

    return events.ofType<SummaryMedicalHistoryAnswerToggled>()
        .map { toggleEvent -> updateMedicalHistory(toggleEvent.question, toggleEvent.answer) }
        .map(updateMedicalHistoryEffect::call)
        .flatMap { Observable.never<UiChange>() }
  }

  private fun openBloodPressureBottomSheet(events: Observable<UiEvent>): Observable<UiChange> {
    return events
        .ofType<PatientSummaryNewBpClicked>()
        .map { { ui: Ui -> ui.showBloodPressureEntrySheet(patientUuid) } }
  }

  private fun openPrescribedDrugsScreen(events: Observable<UiEvent>): Observable<UiChange> {
    return events
        .ofType<PatientSummaryUpdateDrugsClicked>()
        .map { { ui: Ui -> ui.showUpdatePrescribedDrugsScreen(patientUuid) } }
  }

  private fun showScheduleAppointmentSheet(events: Observable<UiEvent>): Observable<UiChange> {
    val backClicks = events.ofType<PatientSummaryBackClicked>()
    val doneClicks = events.ofType<PatientSummaryDoneClicked>()

    val hasSummaryItemChangedStream = backClicks
        .map { patientDataChangedSinceProvider.call(patientUuid, screenCreatedTimestamp) }

    val allBpsForPatientDeletedStream = backClicks
        .cast<UiEvent>()
        .mergeWith(doneClicks.cast())
        .map { doesNotHaveBloodPressures() }

    val shouldShowScheduleAppointmentSheetOnBackClicksStream = Observables
        .combineLatest(hasSummaryItemChangedStream, allBpsForPatientDeletedStream)
        .map { (hasSummaryItemChanged, allBpsForPatientDeleted) ->
          if (allBpsForPatientDeleted) false else hasSummaryItemChanged
        }

    val showScheduleAppointmentSheetOnBackClicks = backClicks
        .withLatestFrom(shouldShowScheduleAppointmentSheetOnBackClicksStream)
        .filter { (_, shouldShowScheduleAppointmentSheet) -> shouldShowScheduleAppointmentSheet }
        .map { (_, _) -> { ui: Ui -> ui.showScheduleAppointmentSheet(patientUuid) } }

    val showScheduleAppointmentSheetOnDoneClicks = doneClicks
        .withLatestFrom(allBpsForPatientDeletedStream)
        .filter { (_, allBpsForPatientDeleted) -> allBpsForPatientDeleted.not() }
        .map { (_, _) -> { ui: Ui -> ui.showScheduleAppointmentSheet(patientUuid) } }

    return showScheduleAppointmentSheetOnBackClicks
        .mergeWith(showScheduleAppointmentSheetOnDoneClicks)
  }

  private fun openLinkIdWithPatientSheet(events: Observable<UiEvent>): Observable<UiChange> {
    return events
        .ofType<PatientSummaryScreenCreated>()
        .map {
          { ui: Ui ->
            if (openIntention is LinkIdWithPatient) {
              ui.showLinkIdWithPatientView(patientUuid, openIntention.identifier)
            }
          }
        }
  }

  private fun goBackWhenBackClicked(events: Observable<UiEvent>): Observable<UiChange> {
    return events
        .ofType<PatientSummaryBackClicked>()
        .filter { shouldGoBack() }
        .map {
          { ui: Ui ->
            when (openIntention) {
              ViewNewPatient, is LinkIdWithPatient -> ui.goToHomeScreen()
              ViewExistingPatient -> ui.goToPreviousScreen()
            }
          }
        }
  }

  private fun shouldGoBack(): Boolean {
    val hasPatientDataChanged = patientDataChangedSinceProvider.call(patientUuid, screenCreatedTimestamp)
    val doesNotHaveBloodPressures = doesNotHaveBloodPressures()

    return if (doesNotHaveBloodPressures) true else hasPatientDataChanged.not()
  }

  private fun goToHomeOnDoneClick(events: Observable<UiEvent>): Observable<UiChange> {
    val allBpsForPatientDeletedStream = events.ofType<PatientSummaryDoneClicked>()
        .map { doesNotHaveBloodPressures() }

    return events
        .ofType<PatientSummaryDoneClicked>()
        .withLatestFrom(allBpsForPatientDeletedStream)
        .filter { (_, allBpsForPatientDeleted) -> allBpsForPatientDeleted }
        .map { { ui: Ui -> ui.goToHomeScreen() } }
  }

  private fun exitScreenAfterSchedulingAppointment(events: Observable<UiEvent>): Observable<UiChange> {
    val scheduleAppointmentCloses = events
        .ofType<ScheduleAppointmentSheetClosed>()

    val backClicks = events
        .ofType<PatientSummaryBackClicked>()

    val doneClicks = events
        .ofType<PatientSummaryDoneClicked>()

    val afterBackClicks = scheduleAppointmentCloses.withLatestFrom(backClicks)
        .map { (_, _) ->
          { ui: Ui ->
            when (openIntention) {
              ViewExistingPatient -> ui.goToPreviousScreen()
              ViewNewPatient, is LinkIdWithPatient -> ui.goToHomeScreen()
            }.exhaustive()
          }
        }

    val afterDoneClicks = scheduleAppointmentCloses
        .withLatestFrom(doneClicks)
        .map { { ui: Ui -> ui.goToHomeScreen() } }

    return afterBackClicks.mergeWith(afterDoneClicks)
  }

  private fun openBloodPressureUpdateSheet(events: Observable<UiEvent>): Observable<UiChange> {
    return events.ofType<PatientSummaryBpClicked>()
        .map { it.bloodPressureMeasurement }
        .map { bp -> { ui: Ui -> ui.showBloodPressureUpdateSheet(bp.uuid) } }
  }

  private fun showUpdatePhoneDialogIfRequired(events: Observable<UiEvent>): Observable<UiChange> {
    val showForInvalidPhone = Observable.fromCallable { hasInvalidPhone() }
        .take(1)
        .filterTrue()
        .map { { ui: Ui -> ui.showUpdatePhoneDialog(patientUuid) } }

    val waitTillABpIsRecorded = events
        .ofType<PatientSummaryBloodPressureSaved>()
        .take(1)

    val showForMissingPhone = if (openIntention == ViewNewPatient) {
      Observable.empty<UiChange>()
    } else {
      waitTillABpIsRecorded
          .filter { isMissingPhoneAndShouldBeReminded() }
          .doOnNext { markReminderAsShownEffect.call(patientUuid) }
          .flatMap { Observable.just { ui: Ui -> ui.showAddPhoneDialog(patientUuid) } }
    }

    return showForInvalidPhone.mergeWith(showForMissingPhone)
  }

  private fun hasInvalidPhone(): Boolean {
    val phoneNumberOptional = fetchPatientPhoneNumber.call(patientUuid)
    val appointmentOptional = fetchLastCreatedAppointment.call(patientUuid)

    return phoneNumberOptional.zipWith(
        other = appointmentOptional,
        zipper = { phoneNumber, appointment ->
          val wasAppointmentCancelledForInvalidPhone = appointment.status == Cancelled && appointment.cancelReason == InvalidPhoneNumber
          val hasPhoneNumberBeenUpdatedAfterAppointment = appointment.updatedAt > phoneNumber.updatedAt

          wasAppointmentCancelledForInvalidPhone && hasPhoneNumberBeenUpdatedAfterAppointment
        },
        fallback = { _, _ -> false }
    )
  }

  private fun isMissingPhoneAndShouldBeReminded(): Boolean {
    val phoneNumber = fetchPatientPhoneNumber.call(patientUuid)
    val reminderShown = hasShownMissingPhoneReminder.call(patientUuid)

    return phoneNumber is None && reminderShown.not()
  }

  private fun exitScreenIfLinkIdWithPatientIsCancelled(events: Observable<UiEvent>): Observable<UiChange> {
    val screenCreates = events.ofType<PatientSummaryScreenCreated>()
    val linkIdCancelled = events.ofType<PatientSummaryLinkIdCancelled>()

    return Observables.combineLatest(screenCreates, linkIdCancelled)
        .take(1)
        .map { { ui: Ui -> ui.goToPreviousScreen() } }
  }

  private fun hideLinkIdWithPatientSheet(events: Observable<UiEvent>): Observable<UiChange> {
    return events
        .ofType<PatientSummaryLinkIdCompleted>()
        .map { Ui::hideLinkIdWithPatientView }
  }

  private fun doesNotHaveBloodPressures(): Boolean {
    return bloodPressureCountProvider.call(patientUuid) == 0
  }
}
