package org.simple.clinic.bp.entry

import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.rxkotlin.ofType
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.simple.clinic.bp.BloodPressureMeasurement
import org.simple.clinic.bp.entry.BloodPressureEntrySheet.ScreenType.BP_ENTRY
import org.simple.clinic.bp.entry.BpValidator.Validation
import org.simple.clinic.bp.entry.BpValidator.Validation.ErrorDiastolicEmpty
import org.simple.clinic.bp.entry.BpValidator.Validation.ErrorDiastolicTooHigh
import org.simple.clinic.bp.entry.BpValidator.Validation.ErrorDiastolicTooLow
import org.simple.clinic.bp.entry.BpValidator.Validation.ErrorSystolicEmpty
import org.simple.clinic.bp.entry.BpValidator.Validation.ErrorSystolicLessThanDiastolic
import org.simple.clinic.bp.entry.BpValidator.Validation.ErrorSystolicTooHigh
import org.simple.clinic.bp.entry.BpValidator.Validation.ErrorSystolicTooLow
import org.simple.clinic.bp.entry.OpenAs.New
import org.simple.clinic.bp.entry.OpenAs.Update
import org.simple.clinic.functions.Function0
import org.simple.clinic.functions.Function1
import org.simple.clinic.functions.Function2
import org.simple.clinic.functions.Function4
import org.simple.clinic.functions.MockFunctions
import org.simple.clinic.patient.PatientMocker
import org.simple.clinic.util.RxErrorsRule
import org.simple.clinic.util.TestUserClock
import org.simple.clinic.util.UserInputDatePaddingCharacter
import org.simple.clinic.util.scheduler.TrampolineSchedulersProvider
import org.simple.clinic.widgets.UiEvent
import org.simple.clinic.widgets.ageanddateofbirth.UserInputDateValidator
import org.simple.mobius.migration.MobiusTestFixture
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import org.threeten.bp.ZoneOffset.UTC
import org.threeten.bp.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

@RunWith(JUnitParamsRunner::class)
class BloodPressureValidationTest {

  @get:Rule
  val rxErrorsRule = RxErrorsRule()

  private val ui = mock<BloodPressureEntryUi>()
  private val testUserClock = TestUserClock()
  private val dateValidator = UserInputDateValidator(testUserClock, DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH))
  private val bpValidator = BpValidator()

  private val uiEvents = PublishSubject.create<UiEvent>()
  private val patientUuid = UUID.fromString("79145baf-7a5c-4442-ab30-2da564a32944")

  private val user = PatientMocker.loggedInUser(uuid = UUID.fromString("1367a583-12b1-48c6-ae9d-fb34f9aac449"))

  private val facility = PatientMocker.facility(uuid = UUID.fromString("2a70f82e-92c6-4fce-b60e-6f083a8e725b"))

  private val bp = PatientMocker.bp(
      uuid = UUID.fromString("3650a765-353a-4f8b-bdc1-4a24e9093d2a"),
      patientUuid = patientUuid,
      userUuid = user.uuid,
      facilityUuid = facility.uuid
  )

  private val uiRenderer = BloodPressureEntryUiRenderer(ui)
  private lateinit var fixture: MobiusTestFixture<BloodPressureEntryModel, BloodPressureEntryEvent, BloodPressureEntryEffect>

  @Before
  fun setUp() {
    RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
  }

  @Test
  @Parameters(method = "params for bp validation errors and expected ui changes")
  fun `when BP entry is active, and BP readings are invalid then show error`(
      testParams: ValidationErrorsAndUiChangesTestParams
  ) {
    val (systolic, diastolic, error, uiChangeVerification) = testParams
    val recordNewMeasurementEffect = MockFunctions.function4<UUID, Int, Int, Instant, BloodPressureMeasurement>(bp)
    val updateMeasurementEffect = MockFunctions.function1<BloodPressureMeasurement, Unit>(Unit)

    // This assertion is not necessary, it was added to stabilize this test for refactoring.
    assertThat(bpValidator.validate(systolic, diastolic))
        .isEqualTo(error)


    sheetCreatedForNew(patientUuid, recordNewMeasurementEffect, updateMeasurementEffect)
    uiEvents.onNext(ScreenChanged(BP_ENTRY))
    uiEvents.onNext(SystolicChanged(systolic))
    uiEvents.onNext(DiastolicChanged(diastolic))
    uiEvents.onNext(SaveClicked)

    recordNewMeasurementEffect.invocations.assertNeverCalled()
    updateMeasurementEffect.invocations.assertNeverCalled()

    uiChangeVerification(ui)
  }

  @Suppress("unused")
  fun `params for bp validation errors and expected ui changes`(): List<ValidationErrorsAndUiChangesTestParams> {
    return listOf(
        ValidationErrorsAndUiChangesTestParams("", "80", ErrorSystolicEmpty) { ui: Ui -> verify(ui).showSystolicEmptyError() },
        ValidationErrorsAndUiChangesTestParams("120", "", ErrorDiastolicEmpty) { ui: Ui -> verify(ui).showDiastolicEmptyError() },
        ValidationErrorsAndUiChangesTestParams("999", "80", ErrorSystolicTooHigh) { ui: Ui -> verify(ui).showSystolicHighError() },
        ValidationErrorsAndUiChangesTestParams("0", "80", ErrorSystolicTooLow) { ui: Ui -> verify(ui).showSystolicLowError() },
        ValidationErrorsAndUiChangesTestParams("120", "999", ErrorDiastolicTooHigh) { ui: Ui -> verify(ui).showDiastolicHighError() },
        ValidationErrorsAndUiChangesTestParams("120", "0", ErrorDiastolicTooLow) { ui: Ui -> verify(ui).showDiastolicLowError() },
        ValidationErrorsAndUiChangesTestParams("120", "121", ErrorSystolicLessThanDiastolic) { ui: Ui -> verify(ui).showSystolicLessThanDiastolicError() }
    )
  }

  data class ValidationErrorsAndUiChangesTestParams(
      val systolic: String,
      val diastolic: String,
      val error: Validation,
      val uiChangeVerification: (Ui) -> Unit
  )

  @Test
  @Parameters(method = "params for OpenAs and bp validation errors")
  fun `when BP entry is active, BP readings are invalid and next arrow is pressed then date entry should not be shown`(
      testParams: ValidationErrorsAndDoNotGoToDateEntryParams
  ) {
    val (openAs, systolic, diastolic, error) = testParams

    // This assertion is not necessary, it was added to stabilize this test for refactoring.
    assertThat(bpValidator.validate(systolic, diastolic))
        .isEqualTo(error)

    sheetCreated(openAs)
    uiEvents.run {
      onNext(ScreenChanged(BP_ENTRY))
      onNext(SystolicChanged(systolic))
      onNext(DiastolicChanged(diastolic))
      onNext(BloodPressureDateClicked)
    }

    verify(ui, never()).showDateEntryScreen()
  }

  @Suppress("unused")
  fun `params for OpenAs and bp validation errors`(): List<ValidationErrorsAndDoNotGoToDateEntryParams> {
    val bpUuid = UUID.fromString("99fed5e5-19a8-4ece-9d07-6beab70ee77c")
    return listOf(
        ValidationErrorsAndDoNotGoToDateEntryParams(New(patientUuid), "", "80", ErrorSystolicEmpty),
        ValidationErrorsAndDoNotGoToDateEntryParams(New(patientUuid), "120", "", ErrorDiastolicEmpty),
        ValidationErrorsAndDoNotGoToDateEntryParams(New(patientUuid), "999", "80", ErrorSystolicTooHigh),
        ValidationErrorsAndDoNotGoToDateEntryParams(New(patientUuid), "0", "80", ErrorSystolicTooLow),
        ValidationErrorsAndDoNotGoToDateEntryParams(New(patientUuid), "120", "999", ErrorDiastolicTooHigh),
        ValidationErrorsAndDoNotGoToDateEntryParams(New(patientUuid), "120", "0", ErrorDiastolicTooLow),
        ValidationErrorsAndDoNotGoToDateEntryParams(New(patientUuid), "120", "140", ErrorSystolicLessThanDiastolic),

        ValidationErrorsAndDoNotGoToDateEntryParams(Update(bpUuid), "", "80", ErrorSystolicEmpty),
        ValidationErrorsAndDoNotGoToDateEntryParams(Update(bpUuid), "120", "", ErrorDiastolicEmpty),
        ValidationErrorsAndDoNotGoToDateEntryParams(Update(bpUuid), "999", "80", ErrorSystolicTooHigh),
        ValidationErrorsAndDoNotGoToDateEntryParams(Update(bpUuid), "0", "80", ErrorSystolicTooLow),
        ValidationErrorsAndDoNotGoToDateEntryParams(Update(bpUuid), "120", "999", ErrorDiastolicTooHigh),
        ValidationErrorsAndDoNotGoToDateEntryParams(Update(bpUuid), "120", "0", ErrorDiastolicTooLow),
        ValidationErrorsAndDoNotGoToDateEntryParams(Update(bpUuid), "120", "140", ErrorSystolicLessThanDiastolic))
  }

  data class ValidationErrorsAndDoNotGoToDateEntryParams(
      val openAs: OpenAs,
      val systolic: String,
      val diastolic: String,
      val error: Validation
  )

  private fun sheetCreatedForNew(
      patientUuid: UUID,
      recordNewMeasurementEffect: Function4<UUID, Int, Int, Instant, BloodPressureMeasurement> = Function4 { patientUuid, systolic, diastolic, timestamp ->
        PatientMocker.bp(
            uuid = UUID.fromString("1c70ee9c-a5da-49ec-adf4-9259993ec56d"),
            patientUuid = patientUuid,
            facilityUuid = facility.uuid,
            userUuid = user.uuid,
            recordedAt = timestamp,
            systolic = systolic,
            diastolic = diastolic
        )
      },
      updateMeasurementEffect: Function1<BloodPressureMeasurement, Unit> = Function1 { }
  ) {
    val openAsNew = New(patientUuid)
    instantiateFixture(openAsNew, recordNewMeasurementEffect, updateMeasurementEffect)
  }

  private fun sheetCreatedForUpdate(
      existingBpUuid: UUID,
      recordNewMeasurementEffect: Function4<UUID, Int, Int, Instant, BloodPressureMeasurement> = Function4 { patientUuid, systolic, diastolic, timestamp ->
        PatientMocker.bp(
            uuid = UUID.fromString("1c70ee9c-a5da-49ec-adf4-9259993ec56d"),
            patientUuid = patientUuid,
            facilityUuid = facility.uuid,
            userUuid = user.uuid,
            recordedAt = timestamp,
            systolic = systolic,
            diastolic = diastolic
        )
      },
      updateMeasurementEffect: Function1<BloodPressureMeasurement, Unit> = Function1 { }
  ) {
    val openAsUpdate = Update(existingBpUuid)
    instantiateFixture(openAsUpdate, recordNewMeasurementEffect, updateMeasurementEffect)
  }

  private fun sheetCreated(
      openAs: OpenAs,
      recordNewMeasurementEffect: Function4<UUID, Int, Int, Instant, BloodPressureMeasurement> = Function4 { patientUuid, systolic, diastolic, timestamp ->
        PatientMocker.bp(
            uuid = UUID.fromString("1c70ee9c-a5da-49ec-adf4-9259993ec56d"),
            patientUuid = patientUuid,
            facilityUuid = facility.uuid,
            userUuid = user.uuid,
            recordedAt = timestamp,
            systolic = systolic,
            diastolic = diastolic
        )
      },
      updateMeasurementEffect: Function1<BloodPressureMeasurement, Unit> = Function1 { }
  ) {
    when (openAs) {
      is New -> sheetCreatedForNew(openAs.patientUuid, recordNewMeasurementEffect, updateMeasurementEffect)
      is Update -> sheetCreatedForUpdate(openAs.bpUuid, recordNewMeasurementEffect, updateMeasurementEffect)
      else -> throw IllegalStateException("Unknown `openAs`: $openAs")
    }
  }

  private fun instantiateFixture(
      openAs: OpenAs,
      recordNewMeasurementEffect: Function4<UUID, Int, Int, Instant, BloodPressureMeasurement>,
      updateMeasurementEffect: Function1<BloodPressureMeasurement, Unit>
  ) {
    val effectHandler = BloodPressureEntryEffectHandler(
        ui = ui,
        userClock = testUserClock,
        schedulersProvider = TrampolineSchedulersProvider(),
        fetchCurrentUser = Function0 { user },
        fetchCurrentFacility = Function0 { facility },
        updatePatientRecordedEffect = Function2 { _, _ -> },
        markAppointmentsCreatedBeforeTodayAsVisitedEffect = Function1 { },
        fetchExistingBloodPressureMeasurement = Function1 { bp },
        recordNewMeasurementEffect = recordNewMeasurementEffect,
        updateMeasurementEffect = updateMeasurementEffect

    ).build()

    fixture = MobiusTestFixture(
        uiEvents.ofType(),
        BloodPressureEntryModel.create(openAs, LocalDate.now(testUserClock).year),
        BloodPressureEntryInit(),
        BloodPressureEntryUpdate(bpValidator, dateValidator, LocalDate.now(UTC), UserInputDatePaddingCharacter.ZERO),
        effectHandler,
        uiRenderer::render
    ).also { it.start() }
  }
}
