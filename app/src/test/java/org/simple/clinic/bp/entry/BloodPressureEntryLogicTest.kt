package org.simple.clinic.bp.entry

import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.atLeastOnce
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
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
import org.simple.clinic.bp.entry.BloodPressureEntrySheet.ScreenType.DATE_ENTRY
import org.simple.clinic.bp.entry.BpValidator.Validation.ErrorDiastolicEmpty
import org.simple.clinic.bp.entry.BpValidator.Validation.ErrorDiastolicTooHigh
import org.simple.clinic.bp.entry.BpValidator.Validation.ErrorDiastolicTooLow
import org.simple.clinic.bp.entry.BpValidator.Validation.ErrorSystolicEmpty
import org.simple.clinic.bp.entry.BpValidator.Validation.ErrorSystolicLessThanDiastolic
import org.simple.clinic.bp.entry.BpValidator.Validation.ErrorSystolicTooHigh
import org.simple.clinic.bp.entry.BpValidator.Validation.ErrorSystolicTooLow
import org.simple.clinic.bp.entry.OpenAs.New
import org.simple.clinic.bp.entry.OpenAs.Update
import org.simple.clinic.facility.Facility
import org.simple.clinic.functions.Function0
import org.simple.clinic.functions.Function1
import org.simple.clinic.functions.Function2
import org.simple.clinic.functions.Function4
import org.simple.clinic.functions.MockFunctions
import org.simple.clinic.patient.PatientMocker
import org.simple.clinic.user.User
import org.simple.clinic.util.RxErrorsRule
import org.simple.clinic.util.TestUserClock
import org.simple.clinic.util.UserInputDatePaddingCharacter
import org.simple.clinic.util.scheduler.TrampolineSchedulersProvider
import org.simple.clinic.util.toLocalDateAtZone
import org.simple.clinic.util.toUtcInstant
import org.simple.clinic.widgets.UiEvent
import org.simple.clinic.widgets.ageanddateofbirth.UserInputDateValidator
import org.simple.clinic.widgets.ageanddateofbirth.UserInputDateValidator.Result.Invalid.DateIsInFuture
import org.simple.clinic.widgets.ageanddateofbirth.UserInputDateValidator.Result.Invalid.InvalidPattern
import org.simple.mobius.migration.MobiusTestFixture
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import org.threeten.bp.ZoneOffset.UTC
import org.threeten.bp.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

typealias Ui = BloodPressureEntryUi
typealias UiChange = (Ui) -> Unit

@RunWith(JUnitParamsRunner::class)
class BloodPressureEntrySheetLogicTest {

  @get:Rule
  val rxErrorsRule = RxErrorsRule()

  private val ui = mock<BloodPressureEntryUi>()
  private val testUserClock = TestUserClock(LocalDate.parse("2018-01-01"))
  private val dateValidator = UserInputDateValidator(testUserClock, DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH))
  private val bpValidator = BpValidator()

  private val uiEvents = PublishSubject.create<UiEvent>()
  private val patientUuid = UUID.fromString("79145baf-7a5c-4442-ab30-2da564a32944")

  private val user = PatientMocker.loggedInUser(uuid = UUID.fromString("1367a583-12b1-48c6-ae9d-fb34f9aac449"))

  private val facility = PatientMocker.facility(uuid = UUID.fromString("2a70f82e-92c6-4fce-b60e-6f083a8e725b"))

  private val bp = PatientMocker.bp(
      uuid = UUID.fromString("b52fdf2c-54d5-4297-b49a-5ce816dfeb5b"),
      userUuid = user.uuid,
      patientUuid = patientUuid,
      facilityUuid = facility.uuid
  )

  private val uiRenderer = BloodPressureEntryUiRenderer(ui)
  private lateinit var fixture: MobiusTestFixture<BloodPressureEntryModel, BloodPressureEntryEvent, BloodPressureEntryEffect>

  @Before
  fun setUp() {
    RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
  }

  @Test
  @Parameters(value = ["90", "120", "300"])
  fun `when valid systolic value is entered, move cursor to diastolic field`(sampleSystolicBp: String) {
    sheetCreatedForNew(patientUuid)
    uiEvents.onNext(SystolicChanged(sampleSystolicBp))
    uiEvents.onNext(SystolicChanged(""))
    uiEvents.onNext(SystolicChanged(sampleSystolicBp))

    verify(ui, times(2)).changeFocusToDiastolic()
  }

  @Test
  @Parameters(value = ["66", "44"])
  fun `when invalid systolic value is entered, don't move cursor to diastolic field`(sampleSystolicBp: String) {
    sheetCreatedForNew(patientUuid)
    uiEvents.onNext(SystolicChanged(sampleSystolicBp))
    uiEvents.onNext(SystolicChanged(""))
    uiEvents.onNext(SystolicChanged(sampleSystolicBp))

    verify(ui, never()).changeFocusToDiastolic()
  }

  @Test
  @Parameters(value = [
    "170, 17",
    "17, 1",
    "1,",
    ","])
  fun `when backspace is pressed in diastolic field and it's text is empty, move cursor to systolic and delete last digit`(
      existingSystolic: String,
      systolicAfterBackspace: String
  ) {
    sheetCreatedForNew(patientUuid)
    reset(ui)

    uiEvents.onNext(SystolicChanged(existingSystolic))
    uiEvents.onNext(DiastolicChanged("142"))

    uiEvents.onNext(DiastolicBackspaceClicked)
    uiEvents.onNext(DiastolicChanged("14"))

    uiEvents.onNext(DiastolicBackspaceClicked)
    uiEvents.onNext(DiastolicChanged("1"))

    uiEvents.onNext(DiastolicBackspaceClicked)
    uiEvents.onNext(DiastolicChanged(""))

    uiEvents.onNext(DiastolicBackspaceClicked)

    verify(ui).changeFocusToSystolic()
    if (systolicAfterBackspace.isNotEmpty()) {
      verify(ui).setSystolic(systolicAfterBackspace)
    }
  }

  @Test
  fun `when systolic or diastolic values change, hide any error message`() {
    sheetCreatedForNew(patientUuid)
    uiEvents.onNext(SystolicChanged("12"))
    uiEvents.onNext(SystolicChanged("120"))
    uiEvents.onNext(SystolicChanged("130"))
    uiEvents.onNext(DiastolicChanged("90"))
    uiEvents.onNext(DiastolicChanged("99"))

    verify(ui, times(5)).hideBpErrorMessage()
  }

  @Test
  @Parameters(value = [
    ",",
    "1,1"
  ])
  fun `when save is clicked, BP entry is active, but input is invalid then blood pressure measurement should not be saved`(
      systolic: String,
      diastolic: String
  ) {
    val recordNewMeasurementEffect = MockFunctions.function4<UUID, Int, Int, Instant, BloodPressureMeasurement>(PatientMocker.bp())

    sheetCreatedForNew(patientUuid, recordNewMeasurementEffect = recordNewMeasurementEffect)
    uiEvents.onNext(SystolicChanged(systolic))
    uiEvents.onNext(DiastolicChanged(diastolic))
    uiEvents.onNext(SaveClicked)

    recordNewMeasurementEffect.invocations.assertNeverCalled()
    verify(ui, never()).setBpSavedResultAndFinish()
  }

  @Test
  @Parameters(method = "params for validating BP when save is clicked")
  fun `when BP entry is active, BP readings are invalid and save is clicked then date entry should not be shown`(
      openAs: OpenAs,
      error: BpValidator.Validation
  ) {
    sheetCreated(openAs)
    uiEvents.run {
      onNext(ScreenChanged(BP_ENTRY))
      onNext(SystolicChanged(""))
      onNext(DiastolicChanged(""))
      onNext(SaveClicked)
    }

    verify(ui, never()).showDateEntryScreen()
  }

  @Suppress("unused")
  fun `params for validating BP when save is clicked`(): List<Any> {
    val bpUuid = UUID.fromString("99fed5e5-19a8-4ece-9d07-6beab70ee77c")
    return listOf(
        listOf(New(patientUuid), ErrorSystolicEmpty),
        listOf(New(patientUuid), ErrorDiastolicEmpty),
        listOf(New(patientUuid), ErrorSystolicTooHigh),
        listOf(New(patientUuid), ErrorSystolicTooLow),
        listOf(New(patientUuid), ErrorDiastolicTooHigh),
        listOf(New(patientUuid), ErrorDiastolicTooLow),
        listOf(New(patientUuid), ErrorSystolicLessThanDiastolic),
        listOf(Update(bpUuid), ErrorSystolicEmpty),
        listOf(Update(bpUuid), ErrorDiastolicEmpty),
        listOf(Update(bpUuid), ErrorSystolicTooHigh),
        listOf(Update(bpUuid), ErrorSystolicTooLow),
        listOf(Update(bpUuid), ErrorDiastolicTooHigh),
        listOf(Update(bpUuid), ErrorDiastolicTooLow),
        listOf(Update(bpUuid), ErrorSystolicLessThanDiastolic))
  }

  @Test
  @Parameters(method = "params for prefilling bp measurements")
  fun `when screen is opened to update a blood pressure, the blood pressure must be prefilled`(
      openAs: OpenAs,
      bloodPressureMeasurement: BloodPressureMeasurement?
  ) {
    if (openAs is Update) {
      sheetCreated(openAs, fetchExistingBloodPressureMeasurement = Function1 { bloodPressureMeasurement })
    } else {
      sheetCreated(openAs)
    }

    if (openAs is Update) {
      val verify = verify(ui)
      verify.setSystolic(bloodPressureMeasurement!!.systolic.toString())
      verify.setDiastolic(bloodPressureMeasurement.diastolic.toString())
    } else {
      val verify = verify(ui, never())
      verify.setSystolic(any())
      verify.setDiastolic(any())
    }
  }

  @Suppress("Unused")
  private fun `params for prefilling bp measurements`(): List<List<Any?>> {
    val bpUuid = UUID.fromString("8ddd7237-f331-4d34-9ce6-a3cd0c4c8133")
    return listOf(
        listOf(New(patientUuid), null),
        listOf(Update(bpUuid), PatientMocker.bp(uuid = bpUuid, patientUuid = patientUuid))
    )
  }

  @Test
  @Parameters(method = "params for showing remove button")
  fun `the remove BP button must be shown when the sheet is opened for update`(
      openAs: OpenAs,
      shouldShowRemoveBpButton: Boolean
  ) {
    sheetCreated(openAs)

    if (shouldShowRemoveBpButton) {
      verify(ui).showRemoveBpButton()
    } else {
      verify(ui).hideRemoveBpButton()
    }
  }

  @Suppress("Unused")
  private fun `params for showing remove button`(): List<List<Any>> {
    return listOf(
        listOf(New(UUID.fromString("3127284e-8db4-4359-9386-57b8837573e9")), false),
        listOf(Update(UUID.fromString("baac5893-3670-4d9c-a5ff-12405cbb1ad5")), true))
  }

  @Test
  @Parameters(method = "params for setting the title of the sheet")
  fun `the correct title should be shown when the sheet is opened`(
      openAs: OpenAs,
      showEntryTitle: Boolean
  ) {
    sheetCreated(openAs)

    if (showEntryTitle) {
      verify(ui).showEnterNewBloodPressureTitle()
    } else {
      verify(ui).showEditBloodPressureTitle()
    }
  }

  @Suppress("Unused")
  private fun `params for setting the title of the sheet`(): List<List<Any>> {
    return listOf(
        listOf(New(UUID.fromString("1526b550-c7a1-440a-a916-043f959bc6c5")), true),
        listOf(Update(UUID.fromString("66f89f1a-4d13-4491-970d-0d09c9ce4043")), false))
  }

  @Test
  fun `when the remove button is clicked, the confirmation alert must be shown`() {
    val bloodPressure = PatientMocker.bp()

    sheetCreatedForUpdate(bloodPressure.uuid)
    uiEvents.onNext(RemoveBloodPressureClicked)

    verify(ui).showConfirmRemoveBloodPressureDialog(bloodPressure.uuid)
  }

  @Test
  @Parameters(method = "params for checking valid date input")
  fun `when save is clicked, date entry is active, but input is invalid then BP measurement should not be saved`(
      params: DoNotSaveBpWithInvalidDateTestParams
  ) {
    val (openAs, day, month, twoDigitYear, _, verifications) = params

    val recordNewMeasurementEffect = MockFunctions.function4<UUID, Int, Int, Instant, BloodPressureMeasurement>(PatientMocker.bp())
    val updateMeasurementEffect = MockFunctions.function1<BloodPressureMeasurement, Unit>(Unit)

    sheetCreated(openAs, recordNewMeasurementEffect = recordNewMeasurementEffect, updateMeasurementEffect = updateMeasurementEffect)

    uiEvents.onNext(ScreenChanged(DATE_ENTRY))
    uiEvents.onNext(DayChanged(day))
    uiEvents.onNext(MonthChanged(month))
    uiEvents.onNext(YearChanged(twoDigitYear))
    uiEvents.onNext(SaveClicked)

    when (openAs) {
      is New -> recordNewMeasurementEffect.invocations.assertNeverCalled()
      is Update -> updateMeasurementEffect.invocations.assertNeverCalled()
      else -> throw AssertionError()
    }

    verifications.invoke(ui)
    verify(ui, never()).setBpSavedResultAndFinish()
  }

  @Suppress("Unused")
  private fun `params for checking valid date input`(): List<DoNotSaveBpWithInvalidDateTestParams> {
    return listOf(
        DoNotSaveBpWithInvalidDateTestParams(New(patientUuid), "invalid", "4", "9", InvalidPattern) { ui ->
          verify(ui).showInvalidDateError()
        },
        DoNotSaveBpWithInvalidDateTestParams(Update(UUID.fromString("cd4c2957-19f5-4dbb-839e-17507649046f")), "invalid", "4", "9", InvalidPattern) { ui ->
          verify(ui).showInvalidDateError()
        },
        DoNotSaveBpWithInvalidDateTestParams(New(patientUuid), "05", "04", "20", DateIsInFuture) { ui ->
          verify(ui).showDateIsInFutureError()
        },
        DoNotSaveBpWithInvalidDateTestParams(Update(UUID.fromString("cd4c2957-19f5-4dbb-839e-17507649046f")), "05", "04", "20", DateIsInFuture) { ui ->
          verify(ui).showDateIsInFutureError()
        }
    )
  }

  data class DoNotSaveBpWithInvalidDateTestParams(
      val openAs: OpenAs,
      val day: String,
      val month: String,
      val twoDigitYear: String,
      val result: UserInputDateValidator.Result,
      val verifications: (Ui) -> Unit
  ) {
    constructor(
        openAs: OpenAs,
        date: LocalDate,
        result: UserInputDateValidator.Result,
        verifications: (Ui) -> Unit
    ) : this(
        openAs,
        day = date.dayOfMonth.toString(),
        month = date.monthValue.toString(),
        twoDigitYear = date.year.toString().substring(2),
        result = result,
        verifications = verifications
    )
  }

  @Test
  fun `when save is clicked for a new BP, date entry is active and input is valid then a BP measurement should be saved`() {
    val inputDate = LocalDate.of(2000, 2, 13)
    val recordNewMeasurementEffect = MockFunctions.function4<UUID, Int, Int, Instant, BloodPressureMeasurement>(PatientMocker.bp(patientUuid = patientUuid))
    val updatePatientRecordedEffect = MockFunctions.function2<UUID, Instant, Unit>(Unit)
    val markAppointmentsCreatedBeforeTodayAsVisitedEffect = MockFunctions.function1<UUID, Unit>(Unit)
    val updateMeasurementEffect = MockFunctions.function1<BloodPressureMeasurement, Unit>(Unit)

    sheetCreatedForNew(
        patientUuid = patientUuid,
        updatePatientRecordedEffect = updatePatientRecordedEffect,
        markAppointmentsCreatedBeforeTodayAsVisitedEffect = markAppointmentsCreatedBeforeTodayAsVisitedEffect,
        recordNewMeasurementEffect = recordNewMeasurementEffect,
        updateMeasurementEffect = updateMeasurementEffect
    )
    uiEvents.run {
      onNext(ScreenChanged(BP_ENTRY))
      onNext(SystolicChanged("13"))
      onNext(DiastolicChanged("11"))
      onNext(SystolicChanged("130"))
      onNext(DiastolicChanged("110"))
      onNext(SaveClicked)
      onNext(ScreenChanged(DATE_ENTRY))
      onNext(DayChanged("13"))
      onNext(MonthChanged("02"))
      onNext(YearChanged("00"))
      onNext(SaveClicked)
    }

    updateMeasurementEffect.invocations.assertNeverCalled()
    val entryDateAsInstant = inputDate.toUtcInstant(testUserClock)
    recordNewMeasurementEffect.invocations.assertCalledWithParameters(patientUuid, 130, 110, entryDateAsInstant)
    markAppointmentsCreatedBeforeTodayAsVisitedEffect.invocations.assertCalledWithParameters(patientUuid)
    updatePatientRecordedEffect.invocations.assertCalledWithParameters(patientUuid, entryDateAsInstant)
    verify(ui).setBpSavedResultAndFinish()
  }

  @Test
  fun `when save is clicked while updating a BP, date entry is active and input is valid then the updated BP measurement should be saved`() {
    val oldCreatedAt = LocalDate.of(1990, 1, 13).toUtcInstant(testUserClock)
    val existingBp = PatientMocker.bp(
        userUuid = user.uuid,
        facilityUuid = facility.uuid,
        systolic = 9000,
        diastolic = 8999,
        createdAt = oldCreatedAt,
        updatedAt = oldCreatedAt,
        recordedAt = oldCreatedAt
    )

    val newInputDate = LocalDate.of(2000, 2, 14)
    val updateMeasurementEffect = MockFunctions.function1<BloodPressureMeasurement, Unit>(Unit)
    val updatePatientRecordedEffect = MockFunctions.function2<UUID, Instant, Unit>(Unit)
    val markAppointmentsCreatedBeforeTodayAsVisitedEffect = MockFunctions.function1<UUID, Unit>(Unit)
    val recordNewMeasurementEffect = MockFunctions.function4<UUID, Int, Int, Instant, BloodPressureMeasurement>(PatientMocker.bp())

    sheetCreatedForUpdate(
        existingBpUuid = existingBp.uuid,
        updatePatientRecordedEffect = updatePatientRecordedEffect,
        markAppointmentsCreatedBeforeTodayAsVisitedEffect = markAppointmentsCreatedBeforeTodayAsVisitedEffect,
        fetchExistingBloodPressureMeasurement = Function1 { existingBp },
        updateMeasurementEffect = updateMeasurementEffect,
        recordNewMeasurementEffect = recordNewMeasurementEffect
    )
    uiEvents.run {
      onNext(ScreenChanged(BP_ENTRY))
      onNext(SystolicChanged("120"))
      onNext(DiastolicChanged("110"))
      onNext(SaveClicked)
    }

    uiEvents.run {
      onNext(ScreenChanged(DATE_ENTRY))
      onNext(DayChanged("14"))
      onNext(MonthChanged("02"))
      onNext(YearChanged("00"))
      onNext(SaveClicked)
    }

    val newInputDateAsInstant = newInputDate.toUtcInstant(testUserClock)
    val updatedBp = existingBp.copy(
        systolic = 120,
        diastolic = 110,
        updatedAt = oldCreatedAt,
        recordedAt = newInputDateAsInstant
    )
    updateMeasurementEffect.invocations.assertCalledWithParameters(updatedBp)
    updatePatientRecordedEffect.invocations.assertCalledWithParameters(updatedBp.patientUuid, updatedBp.recordedAt)

    recordNewMeasurementEffect.invocations.assertNeverCalled()
    markAppointmentsCreatedBeforeTodayAsVisitedEffect.invocations.assertNeverCalled()
    verify(ui).setBpSavedResultAndFinish()
  }

  @Test
  @Parameters(method = "params for showing date validation errors")
  fun `when save is clicked, date entry is active and input is invalid then validation errors should be shown`(
      testParams: InvalidDateTestParams
  ) {
    val (openAs, day, month, year, errorResult, uiChangeVerification) = testParams
    val recordNewMeasurementEffect = MockFunctions.function4<UUID, Int, Int, Instant, BloodPressureMeasurement>(PatientMocker.bp())
    val updateMeasurementEffect = MockFunctions.function1<BloodPressureMeasurement, Unit>(Unit)

    // This assertion was written only to stabilize the test by verifying incoming parameters
    assertThat(dateValidator.validate("$day/$month/$year"))
        .isEqualTo(errorResult)

    sheetCreated(openAs, recordNewMeasurementEffect = recordNewMeasurementEffect, updateMeasurementEffect = updateMeasurementEffect)
    uiEvents.run {
      onNext(ScreenChanged(DATE_ENTRY))
      onNext(DayChanged(day))
      onNext(MonthChanged(month))
      onNext(YearChanged(year.takeLast(2)))
      onNext(SaveClicked)
    }

    recordNewMeasurementEffect.invocations.assertNeverCalled()
    updateMeasurementEffect.invocations.assertNeverCalled()
    verify(ui, never()).setBpSavedResultAndFinish()

    uiChangeVerification(ui)
  }

  @Suppress("unused")
  fun `params for showing date validation errors`(): List<InvalidDateTestParams> {
    val existingBpUuid = UUID.fromString("2c4eccbb-d1bc-4c7c-b1ec-60a13acfeea4")
    return listOf(
        InvalidDateTestParams(New(patientUuid), "-invalid-", "-invalid-", "-invalid-", InvalidPattern) { ui: Ui -> verify(ui).showInvalidDateError() },
        InvalidDateTestParams(Update(existingBpUuid), "-invalid-", "-invalid-", "-invalid-", InvalidPattern) { ui: Ui -> verify(ui).showInvalidDateError() },
        InvalidDateTestParams(New(patientUuid), "01", "01", "2099", DateIsInFuture) { ui: Ui -> verify(ui).showDateIsInFutureError() },
        InvalidDateTestParams(Update(existingBpUuid), "01", "01", "2099", DateIsInFuture) { ui: Ui -> verify(ui).showDateIsInFutureError() }
    )
  }

  data class InvalidDateTestParams(
      val openAs: OpenAs,
      val day: String,
      val month: String,
      val year: String,
      val errorResult: UserInputDateValidator.Result,
      val uiChangeVerification: UiChange
  )

  @Test
  fun `when date values change, hide any error message`() {
    sheetCreatedForNew(patientUuid)

    uiEvents.run {
      onNext(DayChanged("14"))
      onNext(MonthChanged("02"))
      onNext(YearChanged("1991"))
    }

    verify(ui, atLeastOnce()).hideDateErrorMessage()
  }

  @Test
  @Parameters(method = "params for OpenAs types")
  fun `when BP entry is active, BP readings are valid and next arrow is pressed then date entry should be shown`(
      openAs: OpenAs
  ) {
    sheetCreated(openAs)
    uiEvents.run {
      onNext(ScreenChanged(BP_ENTRY))
      onNext(SystolicChanged("120"))
      onNext(DiastolicChanged("110"))
      onNext(BloodPressureDateClicked)
    }

    verify(ui).showDateEntryScreen()
  }

  @Test
  fun `when BP entry is active and back is pressed then the sheet should be closed`() {
    sheetCreatedForNew(patientUuid)

    uiEvents.run {
      onNext(ScreenChanged(BP_ENTRY))
      onNext(BackPressed)
    }

    verify(ui).dismiss()
  }

  @Test
  fun `when BP entry is active and BP readings are invalid and blood pressure date is clicked, then show BP validation errors`() {
    sheetCreatedForNew(patientUuid)
    uiEvents.run {
      onNext(ScreenChanged(BP_ENTRY))
      onNext(SystolicChanged(""))
      onNext(DiastolicChanged("80"))
      onNext(BloodPressureDateClicked)
    }

    verify(ui).showSystolicEmptyError()
  }

  @Test
  fun `when screen is opened for a new BP, then the date should be prefilled with the current date`() {
    val currentDate = LocalDate.of(2018, 4, 23)
    testUserClock.setDate(currentDate, UTC)

    sheetCreatedForNew(patientUuid)

    verify(ui).setDateOnInputFields(
        dayOfMonth = "23",
        month = "4",
        twoDigitYear = "18")
  }

  @Test
  fun `when screen is opened for updating an existing BP, then the date should be prefilled with the BP's recorded date`() {
    val recordedAtDate = LocalDate.of(2018, 4, 23)
    val recordedAtDateAsInstant = recordedAtDate.atStartOfDay().toInstant(UTC)
    val existingBp = PatientMocker.bp(recordedAt = recordedAtDateAsInstant)

    sheetCreatedForUpdate(existingBp.uuid, fetchExistingBloodPressureMeasurement = Function1 { existingBp })

    verify(ui, times(1)).setDateOnInputFields(
        dayOfMonth = "23",
        month = "4",
        twoDigitYear = "18")
  }

  @Suppress("Unused")
  private fun `params for OpenAs types`(): List<OpenAs> {
    return listOf(
        New(patientUuid),
        Update(UUID.fromString("d9d0050a-7fa1-4293-868a-7640ebc93cd8"))
    )
  }

  @Test
  fun `whenever the BP sheet is shown to add a new BP, then show today's date`() {
    val today = LocalDate.now(testUserClock)

    sheetCreatedForNew(patientUuid)
    uiEvents.onNext(ScreenChanged(BP_ENTRY))

    verify(ui).showDateOnDateButton(today)

    verify(ui).setDateOnInputFields(
        today.dayOfMonth.toString(),
        today.month.value.toString(),
        today.year.toString().takeLast(2)
    )
    verify(ui).hideRemoveBpButton()
    verify(ui).showEnterNewBloodPressureTitle()
    verifyNoMoreInteractions(ui)
  }

  @Test
  fun `whenever the BP sheet is shown to update an existing BP, then show the BP date`() {
    val bp = PatientMocker.bp(patientUuid = patientUuid)
    val recordedDate = bp.recordedAt.toLocalDateAtZone(testUserClock.zone)

    sheetCreatedForUpdate(bp.uuid, fetchExistingBloodPressureMeasurement = Function1 { bp })
    uiEvents.onNext(ScreenChanged(BP_ENTRY))

    verify(ui).showDateOnDateButton(recordedDate)

    verify(ui).setDateOnInputFields(
        recordedDate.dayOfMonth.toString(),
        recordedDate.month.value.toString(),
        recordedDate.year.toString().takeLast(2)
    )
    verify(ui).setSystolic(bp.systolic.toString())
    verify(ui).setDiastolic(bp.diastolic.toString())
    verify(ui).showRemoveBpButton()
    verify(ui).showEditBloodPressureTitle()
    verifyNoMoreInteractions(ui)
  }

  @Test
  fun `whenever the BP sheet has an invalid date (new BP) and show BP button is pressed, then show date validation errors`() {
    val systolic = 120.toString()
    val diastolic = 110.toString()

    sheetCreatedForNew(patientUuid)
    with(uiEvents) {
      onNext(ScreenChanged(BP_ENTRY))
      onNext(SystolicChanged(systolic))
      onNext(DiastolicChanged(diastolic))
      onNext(BloodPressureDateClicked)
      onNext(ScreenChanged(DATE_ENTRY))
      onNext(DayChanged("1"))
      onNext(MonthChanged("24"))
      onNext(YearChanged("91"))

      reset(ui)
      onNext(ShowBpClicked)
    }

    verify(ui).showInvalidDateError()
    verifyNoMoreInteractions(ui)
  }

  @Test
  fun `whenever the BP sheet has a valid date (new BP) and show BP button is pressed, then show bp entry sheet`() {
    val systolic = 120.toString()
    val diastolic = 110.toString()
    val bpDate = LocalDate.of(2000, 12, 1)

    sheetCreatedForNew(patientUuid)
    with(uiEvents) {
      onNext(ScreenChanged(BP_ENTRY))
      onNext(SystolicChanged(systolic))
      onNext(DiastolicChanged(diastolic))
      onNext(BloodPressureDateClicked)
      onNext(ScreenChanged(DATE_ENTRY))
      onNext(DayChanged(bpDate.dayOfMonth.toString()))
      onNext(MonthChanged(bpDate.monthValue.toString()))
      onNext(YearChanged(bpDate.year.toString().substring(2)))

      reset(ui)
      onNext(ShowBpClicked)
    }

    verify(ui).showBpEntryScreen()
    verify(ui).showDateOnDateButton(bpDate)
    verifyNoMoreInteractions(ui)
  }

  @Test
  fun `whenever the BP sheet has an invalid date (new BP) and back key is pressed, then show date validation errors`() {
    val systolic = 120.toString()
    val diastolic = 110.toString()

    sheetCreatedForNew(patientUuid)
    with(uiEvents) {
      onNext(ScreenChanged(BP_ENTRY))
      onNext(SystolicChanged(systolic))
      onNext(DiastolicChanged(diastolic))
      onNext(BloodPressureDateClicked)
      onNext(ScreenChanged(DATE_ENTRY))
      onNext(DayChanged("1"))
      onNext(MonthChanged("24"))
      onNext(YearChanged("91"))

      reset(ui)
      onNext(BackPressed)
    }

    verify(ui).showInvalidDateError()
    verifyNoMoreInteractions(ui)
  }

  @Test
  fun `when the update BP sheet has an invalid date and show BP button is pressed, then show date validation errors`() {
    val systolic = 120.toString()
    val diastolic = 110.toString()

    val bp = PatientMocker.bp(patientUuid = patientUuid)

    sheetCreatedForUpdate(bp.uuid)
    with(uiEvents) {
      onNext(ScreenChanged(BP_ENTRY))
      onNext(SystolicChanged(systolic))
      onNext(DiastolicChanged(diastolic))
      onNext(BloodPressureDateClicked)
      onNext(ScreenChanged(DATE_ENTRY))
      onNext(DayChanged("1"))
      onNext(MonthChanged("24"))
      onNext(YearChanged("91"))

      reset(ui)
      onNext(ShowBpClicked)
    }

    verify(ui).showInvalidDateError()
    verifyNoMoreInteractions(ui)
  }

  @Test
  fun `when the update BP sheet has an invalid date and back key is pressed, then show date validation errors`() {
    val systolic = 120.toString()
    val diastolic = 110.toString()

    val bp = PatientMocker.bp(patientUuid = patientUuid)

    sheetCreatedForUpdate(bp.uuid)
    with(uiEvents) {
      onNext(ScreenChanged(BP_ENTRY))
      onNext(SystolicChanged(systolic))
      onNext(DiastolicChanged(diastolic))
      onNext(BloodPressureDateClicked)
      onNext(ScreenChanged(DATE_ENTRY))
      onNext(DayChanged("1"))
      onNext(MonthChanged("24"))
      onNext(YearChanged("91"))

      reset(ui)
      onNext(BackPressed)
    }

    verify(ui).showInvalidDateError()
    verifyNoMoreInteractions(ui)
  }

  @Test
  fun `when the data entry sheet changes date and back button is pressed, then update date button in BP entry`() {
    val systolic = 120.toString()
    val diastolic = 110.toString()
    val localDate = LocalDate.of(2000, 5, 10)

    sheetCreatedForNew(patientUuid)
    with(uiEvents) {
      onNext(ScreenChanged(BP_ENTRY))
      onNext(SystolicChanged(systolic))
      onNext(DiastolicChanged(diastolic))
      onNext(BloodPressureDateClicked)
      onNext(ScreenChanged(DATE_ENTRY))
      onNext(DayChanged(localDate.dayOfMonth.toString()))
      onNext(MonthChanged(localDate.monthValue.toString()))
      onNext(YearChanged(localDate.year.toString().takeLast(2)))

      reset(ui)
      onNext(BackPressed)
    }

    verify(ui).showDateOnDateButton(localDate)
    verify(ui).showBpEntryScreen()
    verifyNoMoreInteractions(ui)
  }

  @Test
  fun `when the data entry sheet changes date and show BP button is pressed, then update date button in BP entry`() {
    val systolic = 120.toString()
    val diastolic = 110.toString()
    val localDate = LocalDate.of(2000, 5, 10)

    sheetCreatedForNew(patientUuid)
    with(uiEvents) {
      onNext(ScreenChanged(BP_ENTRY))
      onNext(SystolicChanged(systolic))
      onNext(DiastolicChanged(diastolic))
      onNext(BloodPressureDateClicked)
      onNext(ScreenChanged(DATE_ENTRY))
      onNext(DayChanged(localDate.dayOfMonth.toString()))
      onNext(MonthChanged(localDate.monthValue.toString()))
      onNext(YearChanged(localDate.year.toString().takeLast(2)))

      reset(ui)
      onNext(ShowBpClicked)
    }

    verify(ui).showDateOnDateButton(localDate)
    verify(ui).showBpEntryScreen()
    verifyNoMoreInteractions(ui)
  }

  @Test
  fun `when done button is clicked in new BP entry, then save BP with entered date immediately`() {
    val systolic = 120.toString()
    val diastolic = 110.toString()
    val inputDate = LocalDate.of(2000, 5, 10)

    val recordNewMeasurementEffect = MockFunctions.function4<UUID, Int, Int, Instant, BloodPressureMeasurement>(PatientMocker.bp(patientUuid = patientUuid))
    val markAppointmentsCreatedBeforeTodayAsVisitedEffect = MockFunctions.function1<UUID, Unit>(Unit)
    val updatePatientRecordedEffect = MockFunctions.function2<UUID, Instant, Unit>(Unit)

    sheetCreatedForNew(
        patientUuid = patientUuid,
        updatePatientRecordedEffect = updatePatientRecordedEffect,
        markAppointmentsCreatedBeforeTodayAsVisitedEffect = markAppointmentsCreatedBeforeTodayAsVisitedEffect,
        recordNewMeasurementEffect = recordNewMeasurementEffect
    )
    with(uiEvents) {
      onNext(ScreenChanged(BP_ENTRY))
      onNext(SystolicChanged(systolic))
      onNext(DiastolicChanged(diastolic))
      onNext(DayChanged(inputDate.dayOfMonth.toString()))
      onNext(MonthChanged(inputDate.monthValue.toString()))
      onNext(YearChanged(inputDate.year.toString().takeLast(2)))

      reset(ui)
      onNext(SaveClicked)
    }

    val entryDateAsInstant = inputDate.toUtcInstant(testUserClock)
    recordNewMeasurementEffect.invocations.assertCalledWithParameters(patientUuid, systolic.toInt(), diastolic.toInt(), entryDateAsInstant)
    markAppointmentsCreatedBeforeTodayAsVisitedEffect.invocations.assertCalledWithParameters(patientUuid)
    updatePatientRecordedEffect.invocations.assertCalledWithParameters(patientUuid, entryDateAsInstant)
    verify(ui).setBpSavedResultAndFinish()

    verifyNoMoreInteractions(ui)
  }

  @Test
  fun `when done button is clicked in update BP entry, then save BP with entered date immediately`() {
    val systolic = 120.toString()
    val diastolic = 110.toString()
    val createdAt = LocalDate.of(1990, 1, 13).toUtcInstant(testUserClock)
    val existingBp = PatientMocker.bp(
        userUuid = user.uuid,
        facilityUuid = facility.uuid,
        patientUuid = patientUuid,
        systolic = 9000,
        diastolic = 8999,
        createdAt = createdAt,
        updatedAt = createdAt,
        recordedAt = createdAt
    )

    val newInputDate = LocalDate.of(2000, 2, 14)

    val updateMeasurementEffect = MockFunctions.function1<BloodPressureMeasurement, Unit>(Unit)
    val updatePatientRecordedEffect = MockFunctions.function2<UUID, Instant, Unit>(Unit)
    val markAppointmentsCreatedBeforeTodayAsVisitedEffect = MockFunctions.function1<UUID, Unit>(Unit)
    val recordNewMeasurementEffect = MockFunctions.function4<UUID, Int, Int, Instant, BloodPressureMeasurement>(PatientMocker.bp())

    sheetCreatedForUpdate(
        existingBpUuid = existingBp.uuid,
        updatePatientRecordedEffect = updatePatientRecordedEffect,
        markAppointmentsCreatedBeforeTodayAsVisitedEffect = markAppointmentsCreatedBeforeTodayAsVisitedEffect,
        fetchExistingBloodPressureMeasurement = Function1 { existingBp },
        updateMeasurementEffect = updateMeasurementEffect,
        recordNewMeasurementEffect = recordNewMeasurementEffect
    )
    with(uiEvents) {
      onNext(ScreenChanged(BP_ENTRY))
      onNext(SystolicChanged(systolic))
      onNext(DiastolicChanged(diastolic))
      onNext(DayChanged(newInputDate.dayOfMonth.toString()))
      onNext(MonthChanged(newInputDate.monthValue.toString()))
      onNext(YearChanged(newInputDate.year.toString().takeLast(2)))

      reset(ui)
      onNext(SaveClicked)
    }

    val entryDateAsInstant = newInputDate.toUtcInstant(testUserClock)
    val newInputDateAsInstant = newInputDate.toUtcInstant(testUserClock)
    val updatedBp = existingBp.copy(
        systolic = systolic.toInt(),
        diastolic = diastolic.toInt(),
        updatedAt = createdAt,
        recordedAt = newInputDateAsInstant
    )
    updateMeasurementEffect.invocations.assertCalledWithParameters(updatedBp)
    recordNewMeasurementEffect.invocations.assertNeverCalled()
    markAppointmentsCreatedBeforeTodayAsVisitedEffect.invocations.assertNeverCalled()

    updatePatientRecordedEffect.invocations.assertCalledWithParameters(patientUuid, entryDateAsInstant)
    verify(ui).setBpSavedResultAndFinish()

    verifyNoMoreInteractions(ui)
  }

  @Test
  fun `when a different user clicks on save while updating a BP, then the updated BP measurement should be saved with the user's ID and the corresponding facility ID`() {
    // given
    val userFromDifferentFacility = PatientMocker.loggedInUser(uuid = UUID.fromString("4844b826-a162-49fe-b92c-962da172e86c"))
    val differentFacility = PatientMocker.facility(uuid = UUID.fromString("f895b54f-ee32-4471-bc0c-a91b80368778"))

    val oldCreatedAt = Instant.parse("1990-01-13T00:00:00Z")
    val patientUuid = UUID.fromString("af92b081-0131-4f91-9c28-98da5737945b")
    val existingBp = PatientMocker.bp(
        uuid = patientUuid,
        userUuid = user.uuid,
        facilityUuid = facility.uuid,
        systolic = 9000,
        diastolic = 8999,
        createdAt = oldCreatedAt,
        updatedAt = oldCreatedAt,
        recordedAt = oldCreatedAt
    )

    val updateMeasurementEffect = MockFunctions.function1<BloodPressureMeasurement, Unit>(Unit)
    val updatePatientRecordedEffect = MockFunctions.function2<UUID, Instant, Unit>(Unit)
    val markAppointmentsCreatedBeforeTodayAsVisitedEffect = MockFunctions.function1<UUID, Unit>(Unit)
    val recordNewMeasurementEffect = MockFunctions.function4<UUID, Int, Int, Instant, BloodPressureMeasurement>(PatientMocker.bp())

    sheetCreatedForUpdate(
        existingBpUuid = existingBp.uuid,
        fetchCurrentUser = Function0 { userFromDifferentFacility },
        fetchCurrentFacility = Function0 { differentFacility },
        updatePatientRecordedEffect = updatePatientRecordedEffect,
        markAppointmentsCreatedBeforeTodayAsVisitedEffect = markAppointmentsCreatedBeforeTodayAsVisitedEffect,
        fetchExistingBloodPressureMeasurement = Function1 { existingBp },
        updateMeasurementEffect = updateMeasurementEffect,
        recordNewMeasurementEffect = recordNewMeasurementEffect
    )
    uiEvents.run {
      onNext(ScreenChanged(BP_ENTRY))
      onNext(SystolicChanged("120"))
      onNext(DiastolicChanged("110"))
      onNext(SaveClicked)
    }

    uiEvents.run {
      onNext(ScreenChanged(DATE_ENTRY))
      onNext(DayChanged("14"))
      onNext(MonthChanged("02"))
      onNext(YearChanged("01"))
      onNext(SaveClicked)
    }

    val newInputDate = LocalDate.parse("2001-02-14")
    val newInputDateAsInstant = newInputDate.toUtcInstant(testUserClock)
    val updatedBp = existingBp.copy(
        systolic = 120,
        diastolic = 110,
        updatedAt = oldCreatedAt,
        recordedAt = newInputDateAsInstant,
        userUuid = userFromDifferentFacility.uuid,
        facilityUuid = differentFacility.uuid
    )
    updateMeasurementEffect.invocations.assertCalledWithParameters(updatedBp)
    updatePatientRecordedEffect.invocations.assertCalledWithParameters(updatedBp.patientUuid, updatedBp.recordedAt)

    recordNewMeasurementEffect.invocations.assertNeverCalled()
    markAppointmentsCreatedBeforeTodayAsVisitedEffect.invocations.assertNeverCalled()
    verify(ui).setBpSavedResultAndFinish()
  }

  @Test
  fun `when done button is clicked by a user from a different facility in update BP entry, then save BP with entered date immediately`() {
    // given
    val userFromDifferentFacility = PatientMocker.loggedInUser(uuid = UUID.fromString("e246c4fb-5a8d-418a-b80a-9e9d12ca1a8c"))
    val differentFacility = PatientMocker.facility(uuid = UUID.fromString("d9ea6458-fbe2-4d59-b1ac-7dc77b234486"))

    val systolic = 120.toString()
    val diastolic = 110.toString()
    val createdAt = LocalDate.of(1990, 1, 13).toUtcInstant(testUserClock)
    val existingBp = PatientMocker.bp(
        uuid = UUID.fromString("20d6aba0-80a9-4ab1-b0b6-5261a53f5fe5"),
        userUuid = user.uuid,
        facilityUuid = facility.uuid,
        patientUuid = patientUuid,
        systolic = 9000,
        diastolic = 8999,
        createdAt = createdAt,
        updatedAt = createdAt,
        recordedAt = createdAt
    )

    val newInputDate = LocalDate.of(2001, 2, 14)
    val newInputDateAsInstant = newInputDate.toUtcInstant(testUserClock)
    val updatedBp = existingBp.copy(
        systolic = systolic.toInt(),
        diastolic = diastolic.toInt(),
        userUuid = userFromDifferentFacility.uuid,
        facilityUuid = differentFacility.uuid,
        recordedAt = newInputDateAsInstant
    )

    val markAppointmentsCreatedBeforeTodayAsVisitedEffect = MockFunctions.function1<UUID, Unit>(Unit)
    val updateMeasurementEffect = MockFunctions.function1<BloodPressureMeasurement, Unit>(Unit)
    val updatePatientRecordedEffect = MockFunctions.function2<UUID, Instant, Unit>(Unit)
    val recordNewMeasurementEffect = MockFunctions.function4<UUID, Int, Int, Instant, BloodPressureMeasurement>(PatientMocker.bp())

    sheetCreatedForUpdate(
        existingBpUuid = existingBp.uuid,
        fetchCurrentUser = Function0 { userFromDifferentFacility },
        fetchCurrentFacility = Function0 { differentFacility },
        updatePatientRecordedEffect = updatePatientRecordedEffect,
        markAppointmentsCreatedBeforeTodayAsVisitedEffect = markAppointmentsCreatedBeforeTodayAsVisitedEffect,
        fetchExistingBloodPressureMeasurement = Function1 { existingBp },
        updateMeasurementEffect = updateMeasurementEffect,
        recordNewMeasurementEffect = recordNewMeasurementEffect
    )
    with(uiEvents) {
      onNext(ScreenChanged(BP_ENTRY))
      onNext(SystolicChanged(systolic))
      onNext(DiastolicChanged(diastolic))
      onNext(DayChanged(newInputDate.dayOfMonth.toString()))
      onNext(MonthChanged(newInputDate.monthValue.toString()))
      onNext(YearChanged(newInputDate.year.toString().takeLast(2)))

      reset(ui)
      onNext(SaveClicked)
    }

    val entryDateAsInstant = newInputDate.toUtcInstant(testUserClock)
    updateMeasurementEffect.invocations.assertCalledWithParameters(updatedBp)

    recordNewMeasurementEffect.invocations.assertNeverCalled()
    markAppointmentsCreatedBeforeTodayAsVisitedEffect.invocations.assertNeverCalled()
    updatePatientRecordedEffect.invocations.assertCalledWithParameters(patientUuid, entryDateAsInstant)
    verify(ui).setBpSavedResultAndFinish()

    verifyNoMoreInteractions(ui)
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


    sheetCreatedForNew(patientUuid, recordNewMeasurementEffect = recordNewMeasurementEffect, updateMeasurementEffect = updateMeasurementEffect)
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
      val error: BpValidator.Validation,
      val uiChangeVerification: (Ui) -> Unit
  )

  @Test
  @Parameters(method = "params for validating BP when date is clicked")
  fun `when BP entry is active, BP readings are invalid and date is clicked then date entry should not be shown`(
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
  fun `params for validating BP when date is clicked`(): List<ValidationErrorsAndDoNotGoToDateEntryParams> {
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
      val error: BpValidator.Validation
  )

  private fun sheetCreatedForNew(
      patientUuid: UUID,
      fetchCurrentUser: Function0<User> = Function0 { user },
      fetchCurrentFacility: Function0<Facility> = Function0 { facility },
      updatePatientRecordedEffect: Function2<UUID, Instant, Unit> = Function2 { _, _ -> },
      markAppointmentsCreatedBeforeTodayAsVisitedEffect: Function1<UUID, Unit> = Function1 { },
      fetchExistingBloodPressureMeasurement: Function1<UUID, BloodPressureMeasurement> = Function1 { bp },
      recordNewMeasurementEffect: Function4<UUID, Int, Int, Instant, BloodPressureMeasurement> = Function4 { _, systolic, diastolic, timestamp ->
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
    instantiateFixture(
        openAs = openAsNew,
        fetchCurrentUser = fetchCurrentUser,
        fetchCurrentFacility = fetchCurrentFacility,
        updatePatientRecordedEffect = updatePatientRecordedEffect,
        markAppointmentsCreatedBeforeTodayAsVisitedEffect = markAppointmentsCreatedBeforeTodayAsVisitedEffect,
        fetchExistingBloodPressureMeasurement = fetchExistingBloodPressureMeasurement,
        recordNewMeasurementEffect = recordNewMeasurementEffect,
        updateMeasurementEffect = updateMeasurementEffect
    )
  }

  private fun sheetCreatedForUpdate(
      existingBpUuid: UUID,
      fetchCurrentUser: Function0<User> = Function0 { user },
      fetchCurrentFacility: Function0<Facility> = Function0 { facility },
      updatePatientRecordedEffect: Function2<UUID, Instant, Unit> = Function2 { _, _ -> },
      markAppointmentsCreatedBeforeTodayAsVisitedEffect: Function1<UUID, Unit> = Function1 { },
      fetchExistingBloodPressureMeasurement: Function1<UUID, BloodPressureMeasurement> = Function1 { bp },
      recordNewMeasurementEffect: Function4<UUID, Int, Int, Instant, BloodPressureMeasurement> = Function4 { _, systolic, diastolic, timestamp ->
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
    instantiateFixture(
        openAs = openAsUpdate,
        fetchCurrentUser = fetchCurrentUser,
        fetchCurrentFacility = fetchCurrentFacility,
        updatePatientRecordedEffect = updatePatientRecordedEffect,
        markAppointmentsCreatedBeforeTodayAsVisitedEffect = markAppointmentsCreatedBeforeTodayAsVisitedEffect,
        fetchExistingBloodPressureMeasurement = fetchExistingBloodPressureMeasurement,
        recordNewMeasurementEffect = recordNewMeasurementEffect,
        updateMeasurementEffect = updateMeasurementEffect
    )
  }

  private fun sheetCreated(
      openAs: OpenAs,
      fetchCurrentUser: Function0<User> = Function0 { user },
      fetchCurrentFacility: Function0<Facility> = Function0 { facility },
      updatePatientRecordedEffect: Function2<UUID, Instant, Unit> = Function2 { _, _ -> },
      markAppointmentsCreatedBeforeTodayAsVisitedEffect: Function1<UUID, Unit> = Function1 { },
      fetchExistingBloodPressureMeasurement: Function1<UUID, BloodPressureMeasurement> = Function1 { bp },
      recordNewMeasurementEffect: Function4<UUID, Int, Int, Instant, BloodPressureMeasurement> = Function4 { _, systolic, diastolic, timestamp ->
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
      is New -> sheetCreatedForNew(
          patientUuid = openAs.patientUuid,
          fetchCurrentUser = fetchCurrentUser,
          fetchCurrentFacility = fetchCurrentFacility,
          updatePatientRecordedEffect = updatePatientRecordedEffect,
          markAppointmentsCreatedBeforeTodayAsVisitedEffect = markAppointmentsCreatedBeforeTodayAsVisitedEffect,
          fetchExistingBloodPressureMeasurement = fetchExistingBloodPressureMeasurement,
          recordNewMeasurementEffect = recordNewMeasurementEffect,
          updateMeasurementEffect = updateMeasurementEffect
      )
      is Update -> sheetCreatedForUpdate(
          existingBpUuid = openAs.bpUuid,
          fetchCurrentUser = fetchCurrentUser,
          fetchCurrentFacility = fetchCurrentFacility,
          updatePatientRecordedEffect = updatePatientRecordedEffect,
          markAppointmentsCreatedBeforeTodayAsVisitedEffect = markAppointmentsCreatedBeforeTodayAsVisitedEffect,
          fetchExistingBloodPressureMeasurement = fetchExistingBloodPressureMeasurement,
          recordNewMeasurementEffect = recordNewMeasurementEffect,
          updateMeasurementEffect = updateMeasurementEffect
      )
      else -> throw IllegalStateException("Unknown `openAs`: $openAs")
    }
  }

  private fun instantiateFixture(
      openAs: OpenAs,
      fetchCurrentUser: Function0<User>,
      fetchCurrentFacility: Function0<Facility>,
      updatePatientRecordedEffect: Function2<UUID, Instant, Unit>,
      markAppointmentsCreatedBeforeTodayAsVisitedEffect: Function1<UUID, Unit>,
      fetchExistingBloodPressureMeasurement: Function1<UUID, BloodPressureMeasurement>,
      recordNewMeasurementEffect: Function4<UUID, Int, Int, Instant, BloodPressureMeasurement>,
      updateMeasurementEffect: Function1<BloodPressureMeasurement, Unit>
  ) {
    val effectHandler = BloodPressureEntryEffectHandler(
        ui = ui,
        userClock = testUserClock,
        schedulersProvider = TrampolineSchedulersProvider(),
        fetchCurrentUser = fetchCurrentUser,
        fetchCurrentFacility = fetchCurrentFacility,
        updatePatientRecordedEffect = updatePatientRecordedEffect,
        markAppointmentsCreatedBeforeTodayAsVisitedEffect = markAppointmentsCreatedBeforeTodayAsVisitedEffect,
        fetchExistingBloodPressureMeasurement = fetchExistingBloodPressureMeasurement,
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
