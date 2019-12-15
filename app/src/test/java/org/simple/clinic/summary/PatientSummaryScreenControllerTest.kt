package org.simple.clinic.summary

import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.PublishSubject
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.simple.clinic.analytics.Analytics
import org.simple.clinic.analytics.MockAnalyticsReporter
import org.simple.clinic.bp.BloodPressureMeasurement
import org.simple.clinic.drugs.PrescribedDrug
import org.simple.clinic.functions.Function0
import org.simple.clinic.functions.Function1
import org.simple.clinic.functions.Function2
import org.simple.clinic.functions.MockFunctions
import org.simple.clinic.medicalhistory.Answer
import org.simple.clinic.medicalhistory.Answer.Unanswered
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
import org.simple.clinic.overdue.Appointment.Status.Scheduled
import org.simple.clinic.overdue.AppointmentCancelReason
import org.simple.clinic.overdue.AppointmentCancelReason.InvalidPhoneNumber
import org.simple.clinic.patient.Patient
import org.simple.clinic.patient.PatientAddress
import org.simple.clinic.patient.PatientMocker
import org.simple.clinic.patient.PatientPhoneNumber
import org.simple.clinic.patient.PatientRepository
import org.simple.clinic.patient.businessid.BusinessId
import org.simple.clinic.patient.businessid.Identifier
import org.simple.clinic.patient.businessid.Identifier.IdentifierType.BpPassport
import org.simple.clinic.summary.PatientSummaryScreenControllerTest.GoBackToScreen.HOME
import org.simple.clinic.summary.PatientSummaryScreenControllerTest.GoBackToScreen.PREVIOUS
import org.simple.clinic.util.Just
import org.simple.clinic.util.None
import org.simple.clinic.util.Optional
import org.simple.clinic.util.RxErrorsRule
import org.simple.clinic.util.TestUtcClock
import org.simple.clinic.util.randomMedicalHistoryAnswer
import org.simple.clinic.util.toOptional
import org.simple.clinic.widgets.UiEvent
import org.threeten.bp.Duration
import org.threeten.bp.Instant
import java.util.UUID

@RunWith(JUnitParamsRunner::class)
class PatientSummaryScreenControllerTest {

  @get:Rule
  val rxErrorsRule = RxErrorsRule()

  private val ui = mock<PatientSummaryScreenUi>()
  private val patientRepository = mock<PatientRepository>()
  private val patientUuid = UUID.fromString("d2fe1916-b76a-4bb6-b7e5-e107f00c3163")
  private val utcClock = TestUtcClock()

  private val uiEvents = PublishSubject.create<UiEvent>()
  private val reporter = MockAnalyticsReporter()
  private val bpDisplayLimit = 100
  private val medicalHistory = PatientMocker.medicalHistory(
      uuid = UUID.fromString("30a58b92-d5d9-420c-b591-7ea79cf94ee7"),
      patientUuid = patientUuid
  )
  private val phoneNumber = PatientMocker.phoneNumber(
      uuid = UUID.fromString("cf468b9c-4b66-478c-b15b-785b193dc7c9"),
      patientUuid = patientUuid
  )
  private val bpPassport = PatientMocker.businessId(
      uuid = UUID.fromString("a9c01550-dd07-4cc3-a7c8-35c0d72ad014"),
      patientUuid = patientUuid,
      identifier = Identifier(value = "8c3fdfc8-370c-4756-a215-9d453b846f77", type = BpPassport)
  )

  private lateinit var controllerSubscription: Disposable

  @Before
  fun setUp() {
    whenever(patientRepository.patient(patientUuid)).doReturn(Observable.never())

    Analytics.addReporter(reporter)
  }

  @After
  fun tearDown() {
    Analytics.clearReporters()
    reporter.clear()
    controllerSubscription.dispose()
  }

  @Test
  @Parameters(method = "params for patient summary populating profile")
  fun `patient's profile should be populated`(intention: OpenIntention, bpPassport: BusinessId?) {
    val addressUuid = UUID.fromString("471253db-11d7-42ae-9e92-1415abd7a418")
    val patient = PatientMocker.patient(uuid = patientUuid, addressUuid = addressUuid)
    val address = PatientMocker.address(uuid = addressUuid)
    val phoneNumber = None

    whenever(patientRepository.patient(patientUuid)).doReturn(Observable.just<Optional<Patient>>(Just(patient)))
    whenever(patientRepository.address(addressUuid)).doReturn(Observable.just<Optional<PatientAddress>>(Just(address)))

    setupControllerWithScreenCreated(
        openIntention = intention,
        patientPhoneNumber = null,
        patientBpPassport = bpPassport
    )

    verify(ui).populatePatientProfile(PatientSummaryProfile(patient, address, phoneNumber, bpPassport.toOptional()))
    verify(ui).showEditButton()
  }

  @Suppress("Unused")
  private fun `params for patient summary populating profile`() = listOf(
      listOf(OpenIntention.ViewExistingPatient, PatientMocker.businessId(patientUuid = patientUuid, identifier = Identifier("bp-pass", BpPassport))),
      listOf(OpenIntention.ViewExistingPatient, null),
      listOf(OpenIntention.ViewNewPatient, PatientMocker.businessId(patientUuid = patientUuid)),
      listOf(OpenIntention.ViewNewPatient, null),
      listOf(OpenIntention.LinkIdWithPatient(Identifier("06293b71-0f56-45dc-845e-c05ee4d74153", BpPassport)), PatientMocker.businessId(patientUuid = patientUuid)),
      listOf(OpenIntention.LinkIdWithPatient(Identifier("06293b71-0f56-45dc-845e-c05ee4d74153", BpPassport)), null)
  )

  @Test
  @Parameters(method = "patient summary open intentions")
  fun `patient's prescription summary should be populated`(intention: OpenIntention) {
    val prescriptions = listOf(
        PatientMocker.prescription(name = "Amlodipine", dosage = "10mg"),
        PatientMocker.prescription(name = "Telmisartan", dosage = "9000mg"),
        PatientMocker.prescription(name = "Randomzole", dosage = "2 packets"))

    setupControllerWithScreenCreated(intention, prescription = prescriptions)

    verify(ui).populateList(prescriptions, emptyList(), medicalHistory)
  }

  @Test
  @Parameters(method = "patient summary open intentions")
  fun `patient's blood pressure history should be populated`(intention: OpenIntention) {
    val bloodPressureMeasurements = listOf(
        PatientMocker.bp(patientUuid, systolic = 120, diastolic = 85, recordedAt = Instant.now(utcClock).minusSeconds(15L)),
        PatientMocker.bp(patientUuid, systolic = 164, diastolic = 95, recordedAt = Instant.now(utcClock).minusSeconds(30L)),
        PatientMocker.bp(patientUuid, systolic = 144, diastolic = 90, recordedAt = Instant.now(utcClock).minusSeconds(45L)))

    setupControllerWithScreenCreated(intention, bps = bloodPressureMeasurements)

    verify(ui).populateList(emptyList(), bloodPressureMeasurements, medicalHistory)
  }

  @Test
  @Parameters(method = "patient summary open intentions")
  fun `patient's medical history should be populated`(openIntention: OpenIntention) {
    val medicalHistory = PatientMocker.medicalHistory(updatedAt = Instant.now(utcClock))

    setupControllerWithScreenCreated(openIntention, medicalHistory = medicalHistory)

    verify(ui).populateList(emptyList(), emptyList(), medicalHistory)
  }

  @Test
  @Parameters(method = "patient summary open intentions")
  fun `when new-BP is clicked then BP entry sheet should be shown`(openIntention: OpenIntention) {
    setupControllerWithScreenCreated(openIntention)
    uiEvents.onNext(PatientSummaryNewBpClicked())

    verify(ui).showBloodPressureEntrySheet(patientUuid)
  }

  @Test
  @Parameters(method = "patient summary open intentions")
  fun `when update medicines is clicked then BP medicines screen should be shown`(openIntention: OpenIntention) {
    setupControllerWithScreenCreated(openIntention)
    uiEvents.onNext(PatientSummaryUpdateDrugsClicked())

    verify(ui).showUpdatePrescribedDrugsScreen(patientUuid)
  }

  @Test
  @Parameters(method = "patient summary open intentions")
  fun `when the screen is opened, the viewed patient analytics event must be sent`(openIntention: OpenIntention) {
    setupControllerWithScreenCreated(openIntention)

    val expectedEvent = MockAnalyticsReporter.Event("ViewedPatient", mapOf(
        "patientId" to patientUuid.toString(),
        "from" to openIntention.analyticsName()
    ))
    assertThat(reporter.receivedEvents).contains(expectedEvent)
  }

  @Test
  @Parameters(method = "medicalHistoryQuestionsAndAnswers")
  fun `when answers for medical history questions are toggled, then the updated medical history should be saved`(
      openIntention: OpenIntention,
      question: MedicalHistoryQuestion,
      newAnswer: Answer
  ) {
    val medicalHistory = PatientMocker.medicalHistory(
        diagnosedWithHypertension = Unanswered,
        isOnTreatmentForHypertension = Unanswered,
        hasHadHeartAttack = Unanswered,
        hasHadStroke = Unanswered,
        hasHadKidneyDisease = Unanswered,
        hasDiabetes = Unanswered,
        updatedAt = Instant.now(utcClock))

    val now = Instant.now(utcClock)
    val updateMedicalHistory = MockFunctions.function2<MedicalHistory, Instant, Completable>(Completable.complete())

    setupControllerWithScreenCreated(
        openIntention,
        updateMedicalHistory = updateMedicalHistory,
        medicalHistory = medicalHistory
    )

    uiEvents.onNext(SummaryMedicalHistoryAnswerToggled(question, answer = newAnswer))

    val updatedMedicalHistory = medicalHistory.copy(
        diagnosedWithHypertension = if (question == DIAGNOSED_WITH_HYPERTENSION) newAnswer else Unanswered,
        isOnTreatmentForHypertension = if (question == IS_ON_TREATMENT_FOR_HYPERTENSION) newAnswer else Unanswered,
        hasHadHeartAttack = if (question == HAS_HAD_A_HEART_ATTACK) newAnswer else Unanswered,
        hasHadStroke = if (question == HAS_HAD_A_STROKE) newAnswer else Unanswered,
        hasHadKidneyDisease = if (question == HAS_HAD_A_KIDNEY_DISEASE) newAnswer else Unanswered,
        hasDiabetes = if (question == HAS_DIABETES) newAnswer else Unanswered)
    updateMedicalHistory.invocations.assertCalledWithParameters(updatedMedicalHistory, now)
  }

  @Suppress("unused")
  fun medicalHistoryQuestionsAndAnswers(): List<List<Any>> {
    val questions = MedicalHistoryQuestion.values().asList()
    return questions
        .asSequence()
        .map { question ->
          listOf(
              randomPatientSummaryOpenIntention(),
              question,
              randomMedicalHistoryAnswer()
          )
        }
        .toList()
  }

  @Test
  fun `when blood pressure is clicked for editing, blood pressure update sheet should show up`() {
    setupControllerWithoutScreenCreated()

    val bloodPressureMeasurement = PatientMocker.bp()
    uiEvents.onNext(PatientSummaryBpClicked(bloodPressureMeasurement))

    verify(ui).showBloodPressureUpdateSheet(bloodPressureMeasurement.uuid)
  }

  @Test
  @Parameters(method = "appointment cancelation reasons")
  fun `when patient's phone was marked as invalid after the phone number was last updated then update phone dialog should be shown`(
      openIntention: OpenIntention,
      cancelReason: AppointmentCancelReason
  ) {
    val canceledAppointment = PatientMocker.appointment(status = Cancelled, cancelReason = cancelReason)
    val phoneNumber = this.phoneNumber.copy(updatedAt = canceledAppointment.updatedAt - Duration.ofHours(2))

    setupControllerWithScreenCreated(
        openIntention = openIntention,
        lastCreatedAppointment = canceledAppointment,
        patientPhoneNumber = phoneNumber
    )

    if (cancelReason == InvalidPhoneNumber) {
      verify(ui).showUpdatePhoneDialog(patientUuid)
    } else {
      verify(ui, never()).showUpdatePhoneDialog(patientUuid)
    }
  }

  @Test
  @Parameters(method = "appointment cancelation reasons")
  fun `when patient's phone was marked as invalid before the phone number was last updated then update phone dialog should not be shown`(
      openIntention: OpenIntention,
      cancelReason: AppointmentCancelReason
  ) {
    val canceledAppointment = PatientMocker.appointment(status = Cancelled, cancelReason = cancelReason)
    val phoneNumber = this.phoneNumber.copy(updatedAt = canceledAppointment.updatedAt + Duration.ofHours(2))

    setupControllerWithScreenCreated(
        openIntention = openIntention,
        lastCreatedAppointment = canceledAppointment,
        patientPhoneNumber = phoneNumber
    )

    verify(ui, never()).showUpdatePhoneDialog(patientUuid)
  }

  @Test
  @Parameters(method = "appointment cancelation reasons")
  fun `when update phone dialog feature is disabled then it should never be shown`(
      openIntention: OpenIntention,
      cancelReason: AppointmentCancelReason
  ) {
    setupControllerWithScreenCreated(openIntention, lastCreatedAppointment = PatientMocker.appointment(cancelReason = cancelReason))

    verify(ui, never()).showUpdatePhoneDialog(patientUuid)
  }

  @Suppress("unused")
  fun `appointment cancelation reasons`() =
      AppointmentCancelReason
          .values()
          .map { listOf(randomPatientSummaryOpenIntention(), it) }

  @Test
  @Parameters(method = "patient summary open intentions")
  fun `when a canceled appointment with the patient does not exist then update phone dialog should not be shown`(
      openIntention: OpenIntention
  ) {
    setupControllerWithScreenCreated(openIntention, lastCreatedAppointment = PatientMocker.appointment(status = Scheduled, cancelReason = null))

    verify(ui, never()).showUpdatePhoneDialog(patientUuid)
  }

  @Test
  fun `when a new patient is missing a phone number, then avoid showing update phone dialog`() {
    setupControllerWithScreenCreated(
        openIntention = OpenIntention.ViewNewPatient,
        lastCreatedAppointment = null,
        patientPhoneNumber = null
    )

    verify(ui, never()).showUpdatePhoneDialog(patientUuid)
  }

  @Test
  @Parameters(method = "patient summary open intentions except new patient")
  fun `when an existing patient is missing a phone number, a BP is recorded, and the user has never been reminded, then add phone dialog should be shown`(
      openIntention: OpenIntention
  ) {
    val markReminderAsShownCompletable = MockFunctions.function1<UUID, Completable>(Completable.complete())

    setupControllerWithScreenCreated(
        openIntention,
        hasShownMissingPhoneReminder = false,
        markReminderAsShownCompletable = markReminderAsShownCompletable,
        patientPhoneNumber = null
    )
    uiEvents.onNext(PatientSummaryBloodPressureSaved)

    verify(ui).showAddPhoneDialog(patientUuid)
    markReminderAsShownCompletable.invocations.assertCalledWithParameters(patientUuid)
  }

  @Test
  @Parameters(method = "patient summary open intentions except new patient")
  fun `when an existing patient is missing a phone number, a BP hasn't been recorded yet, and the user has never been reminded, then add phone dialog should not be shown`(
      openIntention: OpenIntention
  ) {
    val markReminderAsShownCompletable = MockFunctions.function1<UUID, Completable>(Completable.complete())
    setupControllerWithScreenCreated(openIntention, markReminderAsShownCompletable = markReminderAsShownCompletable, patientPhoneNumber = null)

    verify(ui, never()).showAddPhoneDialog(patientUuid)
    markReminderAsShownCompletable.invocations.assertNeverCalled()
  }

  @Test
  @Parameters(method = "patient summary open intentions except new patient")
  fun `when an existing patient is missing a phone number, and the user has been reminded before, then add phone dialog should not be shown`(
      openIntention: OpenIntention
  ) {
    val markReminderAsShownCompletable = MockFunctions.function1<UUID, Completable>(Completable.complete())
    setupControllerWithScreenCreated(openIntention, markReminderAsShownCompletable = markReminderAsShownCompletable, patientPhoneNumber = null)

    verify(ui, never()).showAddPhoneDialog(patientUuid)
    markReminderAsShownCompletable.invocations.assertNeverCalled()
  }

  @Test
  @Parameters(method = "patient summary open intentions except new patient")
  fun `when an existing patient has a phone number, then add phone dialog should not be shown`(openIntention: OpenIntention) {
    val markReminderAsShownCompletable = MockFunctions.function1<UUID, Completable>(Completable.complete())
    setupControllerWithScreenCreated(openIntention, markReminderAsShownCompletable = markReminderAsShownCompletable)

    verify(ui, never()).showAddPhoneDialog(patientUuid)
    markReminderAsShownCompletable.invocations.assertNeverCalled()
  }

  @Test
  @Parameters(method = "patient summary open intentions except new patient")
  fun `when a new patient has a phone number, then add phone dialog should not be shown`(openIntention: OpenIntention) {
    val markReminderAsShownCompletable = MockFunctions.function1<UUID, Completable>(Completable.complete())
    setupControllerWithScreenCreated(openIntention, markReminderAsShownCompletable = markReminderAsShownCompletable)

    verify(ui, never()).showAddPhoneDialog(patientUuid)
    markReminderAsShownCompletable.invocations.assertNeverCalled()
  }

  @Test
  @Parameters(method = "patient summary open intentions except new patient")
  fun `when a new patient is missing a phone number, then add phone dialog should not be shown`(openIntention: OpenIntention) {
    val markReminderAsShownCompletable = MockFunctions.function1<UUID, Completable>(Completable.complete())
    setupControllerWithScreenCreated(
        openIntention,
        markReminderAsShownCompletable = markReminderAsShownCompletable,
        patientPhoneNumber = null
    )

    verify(ui, never()).showAddPhoneDialog(patientUuid)
    markReminderAsShownCompletable.invocations.assertNeverCalled()
  }

  private fun randomPatientSummaryOpenIntention() = `patient summary open intentions`().shuffled().first()

  @Suppress("Unused")
  private fun `patient summary open intentions`() = listOf(
      OpenIntention.ViewExistingPatient,
      OpenIntention.ViewNewPatient,
      OpenIntention.LinkIdWithPatient(Identifier("06293b71-0f56-45dc-845e-c05ee4d74153", BpPassport))
  )

  @Suppress("Unused")
  private fun `patient summary open intentions and screen to go back`(): List<List<Any>> {
    fun testCase(openIntention: OpenIntention, goBackToScreen: GoBackToScreen): List<Any> {
      return listOf(openIntention, goBackToScreen)
    }

    return listOf(
        testCase(openIntention = OpenIntention.ViewExistingPatient, goBackToScreen = PREVIOUS),
        testCase(openIntention = OpenIntention.ViewNewPatient, goBackToScreen = HOME),
        testCase(openIntention = OpenIntention.LinkIdWithPatient(Identifier("06293b71-0f56-45dc-845e-c05ee4d74153", BpPassport)), goBackToScreen = HOME)
    )
  }

  @Suppress("Unused")
  private fun `patient summary open intentions except new patient`() = listOf(
      OpenIntention.ViewExistingPatient,
      OpenIntention.LinkIdWithPatient(Identifier("06293b71-0f56-45dc-845e-c05ee4d74153", BpPassport))
  )

  @Suppress("Unused")
  private fun `patient summary open intentions and summary item changed`(): List<List<Any>> {
    val identifier = Identifier("06293b71-0f56-45dc-845e-c05ee4d74153", BpPassport)

    return listOf(
        listOf(OpenIntention.ViewExistingPatient, true),
        listOf(OpenIntention.ViewExistingPatient, false),
        listOf(OpenIntention.ViewNewPatient, true),
        listOf(OpenIntention.ViewNewPatient, false),
        listOf(OpenIntention.LinkIdWithPatient(identifier), true),
        listOf(OpenIntention.LinkIdWithPatient(identifier), false))
  }


  @Test
  @Parameters(method = "params for testing link id with patient bottom sheet")
  fun `link id with patient bottom sheet should only open when patient summary is created with link id intent`(
      openIntention: OpenIntention,
      shouldShowLinkIdSheet: Boolean,
      identifier: Identifier?
  ) {
    setupControllerWithScreenCreated(openIntention)

    if (shouldShowLinkIdSheet) {
      verify(ui).showLinkIdWithPatientView(patientUuid, identifier!!)
    } else {
      verify(ui, never()).showLinkIdWithPatientView(any(), any())
    }
  }

  @Suppress("Unused")
  private fun `params for testing link id with patient bottom sheet`(): List<Any> {
    val identifier = Identifier("1f79f976-f1bc-4c8a-8a53-ad646ce09fdb", BpPassport)

    return listOf(
        listOf(OpenIntention.LinkIdWithPatient(identifier), true, identifier),
        listOf(OpenIntention.ViewExistingPatient, false, null),
        listOf(OpenIntention.ViewNewPatient, false, null)
    )
  }

  @Test
  fun `when the link id with patient is cancelled, the patient summary screen must be closed`() {
    val openIntention = OpenIntention.LinkIdWithPatient(identifier = Identifier("id", BpPassport))
    setupControllerWithScreenCreated(openIntention)

    uiEvents.onNext(PatientSummaryLinkIdCancelled)

    verify(ui).goToPreviousScreen()
  }

  @Test
  fun `when the link id with patient is completed, the link id screen must be closed`() {
    val openIntention = OpenIntention.LinkIdWithPatient(identifier = Identifier("id", BpPassport))
    setupControllerWithScreenCreated(openIntention)

    uiEvents.onNext(PatientSummaryLinkIdCompleted)

    verify(ui).hideLinkIdWithPatientView()
    verify(ui, never()).goToPreviousScreen()
  }

  enum class GoBackToScreen {
    HOME,
    PREVIOUS
  }

  @Test
  @Parameters(method = "patient summary open intentions")
  fun `when there are patient summary changes and at least one BP is present, clicking on back must show the schedule appointment sheet`(
      openIntention: OpenIntention
  ) {
    setupControllerWithScreenCreated(openIntention, bpCount = 1, hasPatientDataChanged = true)
    uiEvents.onNext(PatientSummaryBackClicked())

    verify(ui, never()).goToPreviousScreen()
    verify(ui, never()).goToHomeScreen()
    verify(ui).showScheduleAppointmentSheet(patientUuid)
  }

  @Test
  @Parameters(method = "params for going back or home when clicking back when there are no BPs")
  fun `when there are patient summary changes and all bps are deleted, clicking on back must go back`(
      openIntention: OpenIntention,
      goBackToScreen: GoBackToScreen
  ) {
    setupControllerWithScreenCreated(openIntention)
    uiEvents.onNext(PatientSummaryBackClicked())

    verify(ui, never()).showScheduleAppointmentSheet(patientUuid)
    if (goBackToScreen == HOME) {
      verify(ui).goToHomeScreen()
    } else {
      verify(ui).goToPreviousScreen()
    }
  }

  @Suppress("Unused")
  private fun `params for going back or home when clicking back when there are no BPs`(): List<List<Any>> {
    return listOf(
        listOf(
            OpenIntention.ViewExistingPatient,
            PREVIOUS
        ),
        listOf(
            OpenIntention.ViewNewPatient,
            HOME
        ),
        listOf(
            OpenIntention.LinkIdWithPatient(Identifier("1f79f976-f1bc-4c8a-8a53-ad646ce09fdb", BpPassport)),
            HOME
        )
    )
  }

  @Test
  @Parameters(method = "patient summary open intentions and screen to go back")
  fun `when there are no patient summary changes and all bps are not deleted, clicking on back must go back`(
      openIntention: OpenIntention,
      goBackToScreen: GoBackToScreen
  ) {
    setupControllerWithScreenCreated(openIntention, bpCount = 1)
    uiEvents.onNext(PatientSummaryBackClicked())

    verify(ui, never()).showScheduleAppointmentSheet(patientUuid)
    if (goBackToScreen == HOME) {
      verify(ui).goToHomeScreen()
    } else {
      verify(ui).goToPreviousScreen()
    }
  }

  @Test
  @Parameters(method = "patient summary open intentions and screen to go back")
  fun `when there are no patient summary changes and all bps are deleted, clicking on back must go back`(
      openIntention: OpenIntention,
      goBackToScreen: GoBackToScreen
  ) {
    setupControllerWithScreenCreated(openIntention)
    uiEvents.onNext(PatientSummaryBackClicked())

    verify(ui, never()).showScheduleAppointmentSheet(patientUuid)
    if (goBackToScreen == HOME) {
      verify(ui).goToHomeScreen()
    } else {
      verify(ui).goToPreviousScreen()
    }
  }

  @Test
  @Parameters(method = "patient summary open intentions")
  fun `when all bps are not deleted, clicking on save must show the schedule appointment sheet regardless of summary changes`(
      openIntention: OpenIntention
  ) {
    setupControllerWithScreenCreated(openIntention, bpCount = 1)
    uiEvents.onNext(PatientSummaryDoneClicked())

    verify(ui).showScheduleAppointmentSheet(patientUuid)
    verify(ui, never()).goToHomeScreen()
    verify(ui, never()).goToPreviousScreen()
  }

  @Test
  @Parameters(method = "patient summary open intentions")
  fun `when all bps are deleted, clicking on save must go to the home screen regardless of summary changes`(
      openIntention: OpenIntention
  ) {
    setupControllerWithScreenCreated(openIntention)
    uiEvents.onNext(PatientSummaryDoneClicked())

    verify(ui, never()).showScheduleAppointmentSheet(patientUuid)
    verify(ui, never()).goToPreviousScreen()
    verify(ui).goToHomeScreen()
  }

  private fun setupControllerWithScreenCreated(
      openIntention: OpenIntention,
      patientUuid: UUID = this.patientUuid,
      screenCreatedTimestamp: Instant = Instant.now(utcClock),
      numberOfBpsToDisplay: Int = this.bpDisplayLimit,
      hasShownMissingPhoneReminder: Boolean = true,
      markReminderAsShownCompletable: Function1<UUID, Completable> = Function1 { Completable.complete() },
      lastCreatedAppointment: Appointment? = null,
      updateMedicalHistory: Function2<MedicalHistory, Instant, Completable> = Function2 { _, _ -> Completable.complete() },
      medicalHistory: MedicalHistory = this.medicalHistory,
      prescription: List<PrescribedDrug> = emptyList(),
      bpCount: Int = 0,
      bps: List<BloodPressureMeasurement> = emptyList(),
      hasPatientDataChanged: Boolean = false,
      patientPhoneNumber: PatientPhoneNumber? = this.phoneNumber,
      patientBpPassport: BusinessId? = this.bpPassport
  ) {
    setupControllerWithoutScreenCreated(
        numberOfBpsToDisplay = numberOfBpsToDisplay,
        hasShownMissingPhoneReminder = hasShownMissingPhoneReminder,
        markReminderAsShownCompletable = markReminderAsShownCompletable,
        lastCreatedAppointment = lastCreatedAppointment,
        updateMedicalHistory = updateMedicalHistory,
        medicalHistory = medicalHistory,
        prescription = prescription,
        bpCount = bpCount,
        bps = bps,
        hasPatientDataChanged = hasPatientDataChanged,
        patientPhoneNumber = patientPhoneNumber,
        patientBpPassport = patientBpPassport
    )
    uiEvents.onNext(PatientSummaryScreenCreated(patientUuid, openIntention, screenCreatedTimestamp))
  }

  private fun setupControllerWithoutScreenCreated(
      numberOfBpsToDisplay: Int = this.bpDisplayLimit,
      hasShownMissingPhoneReminder: Boolean = true,
      markReminderAsShownCompletable: Function1<UUID, Completable> = Function1 { Completable.complete() },
      lastCreatedAppointment: Appointment? = null,
      updateMedicalHistory: Function2<MedicalHistory, Instant, Completable> = Function2 { _, _ -> Completable.complete() },
      medicalHistory: MedicalHistory = this.medicalHistory,
      prescription: List<PrescribedDrug> = emptyList(),
      bpCount: Int = 0,
      bps: List<BloodPressureMeasurement> = emptyList(),
      hasPatientDataChanged: Boolean = false,
      patientPhoneNumber: PatientPhoneNumber? = this.phoneNumber,
      patientBpPassport: BusinessId? = this.bpPassport
  ) {
    createController(
        numberOfBpsToDisplay = numberOfBpsToDisplay,
        hasShownMissingPhoneReminder = hasShownMissingPhoneReminder,
        markReminderAsShownCompletable = markReminderAsShownCompletable,
        lastCreatedAppointment = lastCreatedAppointment,
        updateMedicalHistory = updateMedicalHistory,
        medicalHistory = medicalHistory,
        prescription = prescription,
        bpCount = bpCount,
        bps = bps,
        hasPatientDataChanged = hasPatientDataChanged,
        patientPhoneNumber = patientPhoneNumber,
        patientBpPassport = patientBpPassport
    )
  }

  private fun createController(
      numberOfBpsToDisplay: Int,
      hasShownMissingPhoneReminder: Boolean,
      markReminderAsShownCompletable: Function1<UUID, Completable>,
      lastCreatedAppointment: Appointment?,
      updateMedicalHistory: Function2<MedicalHistory, Instant, Completable>,
      medicalHistory: MedicalHistory,
      prescription: List<PrescribedDrug>,
      bpCount: Int,
      bps: List<BloodPressureMeasurement>,
      hasPatientDataChanged: Boolean,
      patientPhoneNumber: PatientPhoneNumber?,
      patientBpPassport: BusinessId?
  ) {
    val controller = PatientSummaryScreenController(
        patientRepository = patientRepository,
        numberOfBpsToDisplaySupplier = Function0 { numberOfBpsToDisplay },
        hasShownMissingPhoneReminderProvider = Function1 { Observable.just(hasShownMissingPhoneReminder) },
        markReminderAsShownConsumer = markReminderAsShownCompletable,
        lastCreatedAppointmentProvider = Function1 { if (lastCreatedAppointment == null) Observable.never() else Observable.just(lastCreatedAppointment) },
        updateMedicalHistory = updateMedicalHistory,
        utcTimestampProvider = Function0 { Instant.now(utcClock) },
        medicalHistoryProvider = Function1 { Observable.just(medicalHistory) },
        patientPrescriptionProvider = Function1 { Observable.just(prescription) },
        bloodPressureCountProvider = Function1 { bpCount },
        bloodPressuresProvider = Function2 { _, _ -> Observable.just(bps) },
        patientDataChangedSinceProvider = Function2 { _, _ -> hasPatientDataChanged },
        patientPhoneNumberProvider = Function1 { Observable.just(patientPhoneNumber.toOptional()) },
        patientBpPassportProvider = Function1 { Observable.just(patientBpPassport.toOptional()) }
    )

    controllerSubscription = uiEvents
        .compose(controller)
        .subscribe { uiChange -> uiChange(ui) }
  }
}
