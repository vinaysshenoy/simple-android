package org.simple.clinic.editpatient

import com.spotify.mobius.rx2.RxMobius
import io.reactivex.Observable
import io.reactivex.ObservableTransformer
import io.reactivex.Single
import org.simple.clinic.editpatient.OngoingEditPatientEntry.EitherAgeOrDateOfBirth.EntryWithAge
import org.simple.clinic.editpatient.OngoingEditPatientEntry.EitherAgeOrDateOfBirth.EntryWithDateOfBirth
import org.simple.clinic.patient.Age
import org.simple.clinic.patient.DateOfBirth
import org.simple.clinic.patient.DateOfBirth.Type.EXACT
import org.simple.clinic.patient.DateOfBirth.Type.FROM_AGE
import org.simple.clinic.patient.Patient
import org.simple.clinic.patient.PatientAddress
import org.simple.clinic.patient.PatientPhoneNumber
import org.simple.clinic.patient.PatientPhoneNumberType.Mobile
import org.simple.clinic.patient.PatientRepository
import org.simple.clinic.util.UserClock
import org.simple.clinic.util.UtcClock
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import org.threeten.bp.format.DateTimeFormatter
import java.util.UUID

object EditPatientEffectHandler {
  fun createEffectHandler(
      ui: EditPatientUi,
      userClock: UserClock,
      patientRepository: PatientRepository,
      utcClock: UtcClock,
      dateOfBirthFormatter: DateTimeFormatter
  ): ObservableTransformer<EditPatientEffect, EditPatientEvent> {
    return RxMobius
        .subtypeEffectHandler<EditPatientEffect, EditPatientEvent>()
        .addConsumer(PrefillFormEffect::class.java) { prefillFormFields(it, ui, userClock) }
        .addConsumer(ShowValidationErrorsEffect::class.java) { showValidationErrors(it, ui) }
        .addConsumer(HideValidationErrorsEffect::class.java) { ui.hideValidationErrors(it.validationErrors) }
        .addAction(ShowDatePatternInDateOfBirthLabelEffect::class.java) { ui.showDatePatternInDateOfBirthLabel() }
        .addAction(HideDatePatternInDateOfBirthLabelEffect::class.java) { ui.hideDatePatternInDateOfBirthLabel() }
        .addAction(GoBackEffect::class.java) { ui.goBack() }
        .addAction(ShowDiscardChangesAlertEffect::class.java) { ui.showDiscardChangesAlert() }
        .addTransformer(SavePatientEffect::class.java, savePatientTransformer(patientRepository, dateOfBirthFormatter, utcClock, ui))
        .build()
  }

  private fun prefillFormFields(
      prefillFormFieldsEffect: PrefillFormEffect,
      ui: EditPatientUi,
      userClock: UserClock
  ) {
    val (patient, address, phoneNumber) = prefillFormFieldsEffect

    ui.setPatientName(patient.fullName)
    ui.setGender(patient.gender)
    ui.setState(address.state)
    ui.setDistrict(address.district)

    if (address.colonyOrVillage.isNullOrBlank().not()) {
      ui.setColonyOrVillage(address.colonyOrVillage!!)
    }

    if (phoneNumber != null) {
      ui.setPatientPhoneNumber(phoneNumber.number)
    }

    val dateOfBirth = DateOfBirth.fromPatient(patient, userClock)
    when (dateOfBirth.type) {
      EXACT -> ui.setPatientDateOfBirth(dateOfBirth.date)
      FROM_AGE -> ui.setPatientAge(dateOfBirth.estimateAge(userClock))
    }
  }

  private fun showValidationErrors(
      effect: ShowValidationErrorsEffect,
      ui: EditPatientUi
  ) {
    with(ui) {
      showValidationErrors(effect.validationErrors)
      scrollToFirstFieldWithError()
    }
  }

  private fun savePatientTransformer(
      patientRepository: PatientRepository,
      dateOfBirthFormatter: DateTimeFormatter,
      utcClock: UtcClock,
      ui: EditPatientUi
  ): ObservableTransformer<SavePatientEffect, EditPatientEvent> {
    return ObservableTransformer { savePatientEffects ->
      val sharedSavePatientEffects = savePatientEffects
          .share()

      Observable.merge(
          createOrUpdatePhoneNumber(sharedSavePatientEffects, patientRepository),
          savePatient(sharedSavePatientEffects, dateOfBirthFormatter, utcClock, patientRepository, ui)
      )
    }
  }

  private fun savePatient(
      savePatientEffects: Observable<SavePatientEffect>,
      dateOfBirthFormatter: DateTimeFormatter,
      utcClock: UtcClock,
      patientRepository: PatientRepository,
      ui: EditPatientUi
  ): Observable<EditPatientEvent>? {
    return savePatientEffects
        .map { (ongoingEditPatientEntry, patient, patientAddress, _) ->
          getUpdatedPatientAndAddress(patient, patientAddress, ongoingEditPatientEntry, utcClock, dateOfBirthFormatter)
        }.flatMapSingle { (updatedPatient, updatedAddress) ->
          savePatientAndAddress(patientRepository, updatedPatient, updatedAddress)
        }
        .doOnNext { ui.goBack() }
        .flatMap { Observable.never<EditPatientEvent>() }
  }

  private fun getUpdatedPatientAndAddress(
      patient: Patient,
      patientAddress: PatientAddress,
      ongoingEditPatientEntry: OngoingEditPatientEntry,
      utcClock: UtcClock,
      dateOfBirthFormatter: DateTimeFormatter
  ): Pair<Patient, PatientAddress> {
    val updatedPatient = updatePatient(patient, ongoingEditPatientEntry, dateOfBirthFormatter, utcClock)
    val updatedAddress = updateAddress(patientAddress, ongoingEditPatientEntry)
    return updatedPatient to updatedAddress
  }

  private fun updatePatient(
      patient: Patient,
      ongoingEditPatientEntry: OngoingEditPatientEntry,
      dateOfBirthFormatter: DateTimeFormatter,
      utcClock: UtcClock
  ): Patient {
    val patientWithoutAgeOrDateOfBirth = patient.copy(
        fullName = ongoingEditPatientEntry.name,
        gender = ongoingEditPatientEntry.gender,
        dateOfBirth = null,
        age = null
    )

    return when (ongoingEditPatientEntry.ageOrDateOfBirth) {
      is EntryWithAge -> {
        val age = coerceAgeFrom(patient.age, ongoingEditPatientEntry.ageOrDateOfBirth.age, utcClock)
        patientWithoutAgeOrDateOfBirth.copy(age = age)
      }

      is EntryWithDateOfBirth -> {
        val dateOfBirth = LocalDate.parse(ongoingEditPatientEntry.ageOrDateOfBirth.dateOfBirth, dateOfBirthFormatter)
        patientWithoutAgeOrDateOfBirth.copy(dateOfBirth = dateOfBirth)
      }
    }
  }

  private fun coerceAgeFrom(alreadySavedAge: Age?, enteredAge: String, utcClock: UtcClock): Age {
    val enteredAgeValue = enteredAge.toInt()
    return when {
      alreadySavedAge != null && alreadySavedAge.value == enteredAgeValue -> alreadySavedAge
      else -> Age(enteredAgeValue, Instant.now(utcClock))
    }
  }

  private fun updateAddress(
      patientAddress: PatientAddress,
      ongoingEditPatientEntry: OngoingEditPatientEntry
  ): PatientAddress {
    return patientAddress.copy(
        colonyOrVillage = ongoingEditPatientEntry.colonyOrVillage,
        district = ongoingEditPatientEntry.district,
        state = ongoingEditPatientEntry.state
    )
  }

  private fun createOrUpdatePhoneNumber(
      savePatientEffects: Observable<SavePatientEffect>,
      patientRepository: PatientRepository
  ): Observable<EditPatientEvent> {
    fun isPhoneNumberPresent(existingPhoneNumber: PatientPhoneNumber?, enteredPhoneNumber: String): Boolean =
        existingPhoneNumber != null || enteredPhoneNumber.isNotBlank()

    val effectsWithPhoneNumber = savePatientEffects
        .map { (entry, patient, _, phoneNumber) -> Triple(patient.uuid, phoneNumber, entry.phoneNumber) }
        .filter { (_, existingPhoneNumber, enteredPhoneNumber) ->
          isPhoneNumberPresent(existingPhoneNumber, enteredPhoneNumber)
        }
        .share()

    return Observable.merge(
        createPhoneNumber(effectsWithPhoneNumber, patientRepository),
        updatePhoneNumber(effectsWithPhoneNumber, patientRepository)
    )
  }

  private fun updatePhoneNumber(
      phoneNumbers: Observable<Triple<UUID, PatientPhoneNumber?, String>>,
      patientRepository: PatientRepository
  ): Observable<EditPatientEvent> {
    fun hasExistingPhoneNumber(existingPhoneNumber: PatientPhoneNumber?, enteredPhoneNumber: String): Boolean =
        existingPhoneNumber != null && enteredPhoneNumber.isNotBlank()

    return phoneNumbers
        .filter { (_, existingPhoneNumber, enteredPhoneNumber) ->
          hasExistingPhoneNumber(existingPhoneNumber, enteredPhoneNumber)
        }
        .flatMapCompletable { (patientUuid, existingPhoneNumber, enteredPhoneNumber) ->
          patientRepository.updatePhoneNumberForPatient(patientUuid, existingPhoneNumber!!.copy(number = enteredPhoneNumber))
        }
        .toObservable()
  }

  private fun createPhoneNumber(
      phoneNumbers: Observable<Triple<UUID, PatientPhoneNumber?, String>>,
      patientRepository: PatientRepository
  ): Observable<EditPatientEvent> {
    fun noExistingPhoneNumberButHasEnteredPhoneNumber(existingPhoneNumber: PatientPhoneNumber?, enteredPhoneNumber: String): Boolean =
        existingPhoneNumber == null && enteredPhoneNumber.isNotBlank()

    return phoneNumbers
        .filter { (_, existingPhoneNumber, enteredPhoneNumber) ->
          noExistingPhoneNumberButHasEnteredPhoneNumber(existingPhoneNumber, enteredPhoneNumber)
        }
        .flatMapCompletable { (patientUuid, _, enteredPhoneNumber) ->
          patientRepository.createPhoneNumberForPatient(patientUuid, enteredPhoneNumber, Mobile, true)
        }.toObservable()
  }

  private fun savePatientAndAddress(
      patientRepository: PatientRepository,
      updatedPatient: Patient,
      updatedAddress: PatientAddress
  ): Single<Boolean> {
    return patientRepository
        .updatePatient(updatedPatient)
        .andThen(patientRepository.updateAddressForPatient(updatedPatient.uuid, updatedAddress))
        .toSingleDefault(true)
  }
}