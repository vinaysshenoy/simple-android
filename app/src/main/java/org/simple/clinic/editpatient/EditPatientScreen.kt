package org.simple.clinic.editpatient

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.widget.RadioButton
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.transition.ChangeBounds
import androidx.transition.Fade
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import com.jakewharton.rxbinding2.view.RxView
import com.jakewharton.rxbinding2.widget.RxRadioGroup
import com.jakewharton.rxbinding2.widget.RxTextView
import com.spotify.mobius.Connection
import com.spotify.mobius.MobiusLoop
import com.spotify.mobius.android.MobiusAndroid
import com.spotify.mobius.functions.Consumer
import com.spotify.mobius.rx2.RxMobius
import io.reactivex.Observable
import io.reactivex.ObservableTransformer
import io.reactivex.rxkotlin.cast
import kotlinx.android.synthetic.main.screen_edit_patient.view.*
import org.simple.clinic.R
import org.simple.clinic.ReportAnalyticsEvents
import org.simple.clinic.editpatient.EditPatientValidationError.BOTH_DATEOFBIRTH_AND_AGE_ABSENT
import org.simple.clinic.editpatient.EditPatientValidationError.COLONY_OR_VILLAGE_EMPTY
import org.simple.clinic.editpatient.EditPatientValidationError.DATE_OF_BIRTH_IN_FUTURE
import org.simple.clinic.editpatient.EditPatientValidationError.DISTRICT_EMPTY
import org.simple.clinic.editpatient.EditPatientValidationError.FULL_NAME_EMPTY
import org.simple.clinic.editpatient.EditPatientValidationError.INVALID_DATE_OF_BIRTH
import org.simple.clinic.editpatient.EditPatientValidationError.PHONE_NUMBER_EMPTY
import org.simple.clinic.editpatient.EditPatientValidationError.PHONE_NUMBER_LENGTH_TOO_LONG
import org.simple.clinic.editpatient.EditPatientValidationError.PHONE_NUMBER_LENGTH_TOO_SHORT
import org.simple.clinic.editpatient.EditPatientValidationError.STATE_EMPTY
import org.simple.clinic.main.TheActivity
import org.simple.clinic.patient.Gender
import org.simple.clinic.patient.Gender.Female
import org.simple.clinic.patient.Gender.Male
import org.simple.clinic.patient.Gender.Transgender
import org.simple.clinic.patient.Gender.Unknown
import org.simple.clinic.platform.crash.CrashReporter
import org.simple.clinic.registration.phone.PhoneNumberValidator
import org.simple.clinic.router.screen.BackPressInterceptCallback
import org.simple.clinic.router.screen.BackPressInterceptor
import org.simple.clinic.router.screen.ScreenRouter
import org.simple.clinic.util.exhaustive
import org.simple.clinic.util.unsafeLazy
import org.simple.clinic.widgets.ageanddateofbirth.DateOfBirthAndAgeVisibility
import org.simple.clinic.widgets.ageanddateofbirth.DateOfBirthAndAgeVisibility.AGE_VISIBLE
import org.simple.clinic.widgets.ageanddateofbirth.DateOfBirthAndAgeVisibility.BOTH_VISIBLE
import org.simple.clinic.widgets.ageanddateofbirth.DateOfBirthAndAgeVisibility.DATE_OF_BIRTH_VISIBLE
import org.simple.clinic.widgets.ageanddateofbirth.UserInputDateValidator
import org.simple.clinic.widgets.scrollToChild
import org.simple.clinic.widgets.setTextAndCursor
import org.simple.clinic.widgets.textChanges
import org.threeten.bp.LocalDate
import org.threeten.bp.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Named

class EditPatientScreen(context: Context, attributeSet: AttributeSet) : RelativeLayout(context, attributeSet), EditPatientUi {

  @Inject
  lateinit var screenRouter: ScreenRouter

  @field:[Inject Named("date_for_user_input")]
  lateinit var dateOfBirthFormat: DateTimeFormatter

  @Inject
  lateinit var numberValidator: PhoneNumberValidator

  @Inject
  lateinit var dateOfBirthValidator: UserInputDateValidator

  @Inject
  lateinit var activity: AppCompatActivity

  @Inject
  lateinit var crashReporter: CrashReporter

  @Inject
  lateinit var effectHandlerFactory: EditPatientEffectHandler.Factory

  private lateinit var screenModel: EditPatientModel
  private lateinit var controller: MobiusLoop.Controller<EditPatientModel, EditPatientEvent>

  private val screenKey by unsafeLazy {
    screenRouter.key<EditPatientScreenKey>(this)
  }

  override fun onFinishInflate() {
    super.onFinishInflate()
    if (isInEditMode) {
      return
    }

    TheActivity.component.inject(this)

    val (patient, address, phoneNumber) = screenKey
    screenModel = EditPatientModel.from(patient, address, phoneNumber, dateOfBirthFormat)
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    controller = createController(screenModel, effectHandlerFactory.create(this).build())
    controller.connect(::connect)
    controller.start()
  }

  private fun connect(eventSink: Consumer<EditPatientEvent>): Connection<EditPatientModel> {

    // Setup events here
    val events: Observable<EditPatientEvent> = Observable
        .mergeArray(
            saveClicks(),
            nameTextChanges(),
            phoneNumberTextChanges(),
            districtTextChanges(),
            stateTextChanges(),
            colonyTextChanges(),
            genderChanges(),
            dateOfBirthTextChanges(),
            dateOfBirthFocusChanges(),
            ageTextChanges(),
            backClicks()
        )
        .compose(ReportAnalyticsEvents())
        .cast()

    val eventsDisposable = events.subscribe(eventSink::accept)

    return object : Connection<EditPatientModel> {
      val viewRenderer = EditPatientViewRenderer(this@EditPatientScreen)

      override fun accept(value: EditPatientModel) {
        // Update UI here
        viewRenderer.render(value)
      }

      override fun dispose() {
        // Dispose events here
        eventsDisposable.dispose()
      }
    }
  }

  private fun createController(
      model: EditPatientModel,
      effectHandler: ObservableTransformer<EditPatientEffect, EditPatientEvent>
  ): MobiusLoop.Controller<EditPatientModel, EditPatientEvent> {
    val (patient, address, phoneNumber) = screenKey

    val loop = RxMobius
        .loop(EditPatientUpdate(numberValidator, dateOfBirthValidator), effectHandler)
        .init(EditPatientInit(patient, address, phoneNumber))

    return MobiusAndroid.controller(loop, model)
  }

  override fun onDetachedFromWindow() {
    controller.stop()
    controller.disconnect()
    super.onDetachedFromWindow()
  }

  override fun onSaveInstanceState(): Parcelable? {
    val savedState = super.onSaveInstanceState()

    val bundle = Bundle()
    bundle.putParcelable("view_state", savedState)
    bundle.putParcelable("model_key", controller.model)

    return bundle
  }

  override fun onRestoreInstanceState(state: Parcelable?) {
    val bundle = state as Bundle

    val viewState = bundle["view_state"] as Parcelable

    val model = bundle["model_key"] as EditPatientModel
    screenModel = model

    super.onRestoreInstanceState(viewState)
  }

  private fun saveClicks(): Observable<EditPatientEvent> {
    return RxView.clicks(saveButton.button).map { SaveClicked }
  }

  private fun nameTextChanges(): Observable<EditPatientEvent> {
    return RxTextView.textChanges(fullNameEditText).map { NameChanged(it.toString()) }
  }

  private fun phoneNumberTextChanges(): Observable<EditPatientEvent> {
    return RxTextView.textChanges(phoneNumberEditText).map { PhoneNumberChanged(it.toString()) }
  }

  private fun districtTextChanges(): Observable<EditPatientEvent> {
    return RxTextView.textChanges(districtEditText).map { DistrictChanged(it.toString()) }
  }

  private fun stateTextChanges(): Observable<EditPatientEvent> {
    return RxTextView.textChanges(stateEditText).map { StateChanged(it.toString()) }
  }

  private fun colonyTextChanges(): Observable<EditPatientEvent> {
    return RxTextView.textChanges(colonyOrVillageEditText).map { ColonyOrVillageChanged(it.toString()) }
  }

  private fun backClicks(): Observable<EditPatientEvent> {
    val hardwareBackKeyClicks = Observable.create<Any> { emitter ->
      val interceptor = object : BackPressInterceptor {
        override fun onInterceptBackPress(callback: BackPressInterceptCallback) {
          emitter.onNext(Any())
          callback.markBackPressIntercepted()
        }
      }
      emitter.setCancellable { screenRouter.unregisterBackPressInterceptor(interceptor) }
      screenRouter.registerBackPressInterceptor(interceptor)
    }

    return RxView.clicks(backButton)
        .mergeWith(hardwareBackKeyClicks)
        .map { BackClicked }
  }

  private fun genderChanges(): Observable<EditPatientEvent> {
    val radioIdToGenders = mapOf(
        R.id.femaleRadioButton to Female,
        R.id.maleRadioButton to Male,
        R.id.transgenderRadioButton to Transgender)

    return RxRadioGroup.checkedChanges(genderRadioGroup)
        .filter { it != -1 }
        .map { checkedId ->
          val gender = radioIdToGenders[checkedId]!!
          GenderChanged(gender)
        }
  }

  private fun dateOfBirthTextChanges(): Observable<EditPatientEvent> = dateOfBirthEditText.textChanges(::DateOfBirthChanged)

  private fun dateOfBirthFocusChanges(): Observable<EditPatientEvent> = dateOfBirthEditText.focusChanges.map(::DateOfBirthFocusChanged)

  private fun ageTextChanges(): Observable<EditPatientEvent> = ageEditText.textChanges(::AgeChanged)

  override fun setPatientName(name: String) {
    fullNameEditText.setTextAndCursor(name)
  }

  override fun setPatientPhoneNumber(number: String) {
    phoneNumberEditText.setTextAndCursor(number)
  }

  override fun setColonyOrVillage(colonyOrVillage: String) {
    colonyOrVillageEditText.setTextAndCursor(colonyOrVillage)
  }

  override fun setDistrict(district: String) {
    districtEditText.setTextAndCursor(district)
  }

  override fun setState(state: String) {
    stateEditText.setTextAndCursor(state)
  }

  override fun setGender(gender: Gender) {
    val genderButton: RadioButton? = when (gender) {
      Male -> maleRadioButton
      Female -> femaleRadioButton
      Transgender -> transgenderRadioButton
      is Unknown -> {
        crashReporter.report(IllegalStateException("Heads-up: unknown gender ${gender.actualValue} found in ${EditPatientScreen::class.java.name}"))
        null
      }
    }

    genderButton?.isChecked = true
  }

  override fun setPatientAge(age: Int) {
    ageEditText.setTextAndCursor(age.toString())
  }

  override fun setPatientDateOfBirth(dateOfBirth: LocalDate) {
    dateOfBirthEditText.setTextAndCursor(dateOfBirthFormat.format(dateOfBirth))
  }

  override fun showValidationErrors(errors: Set<EditPatientValidationError>) {
    errors.forEach {
      when (it) {
        FULL_NAME_EMPTY -> {
          showEmptyFullNameError(true)
        }

        PHONE_NUMBER_EMPTY,
        PHONE_NUMBER_LENGTH_TOO_SHORT -> {
          showLengthTooShortPhoneNumberError()
        }

        PHONE_NUMBER_LENGTH_TOO_LONG -> {
          showLengthTooLongPhoneNumberError()
        }

        COLONY_OR_VILLAGE_EMPTY -> {
          showEmptyColonyOrVillageError(true)
        }

        DISTRICT_EMPTY -> {
          showEmptyDistrictError(true)
        }

        STATE_EMPTY -> {
          showEmptyStateError(true)
        }

        BOTH_DATEOFBIRTH_AND_AGE_ABSENT -> {
          showAgeEmptyError(true)
        }

        INVALID_DATE_OF_BIRTH -> {
          showInvalidaDateOfBithError()
        }

        DATE_OF_BIRTH_IN_FUTURE -> {
          showDateOfBirthIsInFutureError()
        }
      }.exhaustive()
    }
  }

  override fun hideValidationErrors(errors: Set<EditPatientValidationError>) {
    errors.forEach {
      when (it) {
        FULL_NAME_EMPTY -> {
          showEmptyFullNameError(false)
        }

        PHONE_NUMBER_EMPTY,
        PHONE_NUMBER_LENGTH_TOO_SHORT,
        PHONE_NUMBER_LENGTH_TOO_LONG -> {
          hidePhoneNumberError()
        }

        COLONY_OR_VILLAGE_EMPTY -> {
          showEmptyColonyOrVillageError(false)
        }

        DISTRICT_EMPTY -> {
          showEmptyDistrictError(false)
        }

        STATE_EMPTY -> {
          showEmptyStateError(false)
        }

        BOTH_DATEOFBIRTH_AND_AGE_ABSENT -> {
          showAgeEmptyError(false)
        }

        INVALID_DATE_OF_BIRTH, DATE_OF_BIRTH_IN_FUTURE -> {
          hideDateOfBirthError()
        }
      }.exhaustive()
    }
  }

  private fun showEmptyColonyOrVillageError(showError: Boolean) {
    colonyOrVillageInputLayout.error = when {
      showError -> resources.getString(R.string.patientedit_error_empty_colony_or_village)
      else -> null
    }
  }

  private fun showEmptyDistrictError(show: Boolean) {
    districtInputLayout.error = when {
      show -> resources.getString(R.string.patientedit_error_empty_district)
      else -> null
    }
  }

  private fun showEmptyStateError(show: Boolean) {
    stateInputLayout.error = when {
      show -> resources.getString(R.string.patientedit_error_state_empty)
      else -> null
    }
  }

  private fun showEmptyFullNameError(show: Boolean) {
    fullNameInputLayout.error = when {
      show -> resources.getString(R.string.patientedit_error_empty_fullname)
      else -> null
    }
  }

  private fun showAgeEmptyError(show: Boolean) {
    ageInputLayout.error = when {
      show -> resources.getString(R.string.patientedit_error_both_dateofbirth_and_age_empty)
      else -> null
    }
  }

  private fun showLengthTooShortPhoneNumberError() {
    phoneNumberInputLayout.error = context.getString(R.string.patientedit_error_phonenumber_length_less)
  }

  private fun showLengthTooLongPhoneNumberError() {
    phoneNumberInputLayout.error = context.getString(R.string.patientedit_error_phonenumber_length_more)
  }

  private fun showInvalidaDateOfBithError() {
    dateOfBirthInputLayout.error = context.getString(R.string.patientedit_error_invalid_dateofbirth)
  }

  private fun showDateOfBirthIsInFutureError() {
    dateOfBirthInputLayout.error = context.getString(R.string.patientedit_error_dateofbirth_is_in_future)
  }

  private fun hideDateOfBirthError() {
    dateOfBirthInputLayout.error = null
  }

  private fun hidePhoneNumberError() {
    phoneNumberInputLayout.error = null
  }

  override fun scrollToFirstFieldWithError() {
    val views = arrayOf(
        fullNameInputLayout,
        phoneNumberInputLayout,
        colonyOrVillageInputLayout,
        districtInputLayout,
        stateInputLayout,
        ageInputLayout,
        dateOfBirthInputLayout)

    val firstFieldWithError = views.firstOrNull { it.error.isNullOrBlank().not() }

    firstFieldWithError?.let {
      formScrollView.scrollToChild(it, onScrollComplete = { it.requestFocus() })
    }
  }

  override fun goBack() {
    screenRouter.pop()
  }

  override fun showDatePatternInDateOfBirthLabel() {
    dateOfBirthInputLayout.hint = resources.getString(R.string.patientedit_date_of_birth_focused)
  }

  override fun hideDatePatternInDateOfBirthLabel() {
    dateOfBirthInputLayout.hint = resources.getString(R.string.patientedit_date_of_birth_unfocused)
  }

  override fun setDateOfBirthAndAgeVisibility(visibility: DateOfBirthAndAgeVisibility) {
    val transition = TransitionSet()
        .addTransition(ChangeBounds())
        .addTransition(Fade())
        .setOrdering(TransitionSet.ORDERING_TOGETHER)
        .setDuration(250)
        .setInterpolator(FastOutSlowInInterpolator())
    TransitionManager.beginDelayedTransition(this, transition)

    dateOfBirthEditTextContainer.visibility = when (visibility) {
      DATE_OF_BIRTH_VISIBLE, BOTH_VISIBLE -> VISIBLE
      else -> GONE
    }

    dateOfBirthAndAgeSeparator.visibility = when (visibility) {
      BOTH_VISIBLE -> VISIBLE
      else -> GONE
    }

    ageEditTextContainer.visibility = when (visibility) {
      AGE_VISIBLE, BOTH_VISIBLE -> VISIBLE
      else -> GONE
    }
  }

  override fun showDiscardChangesAlert() {
    ConfirmDiscardChangesDialog.show(activity.supportFragmentManager)
  }
}
