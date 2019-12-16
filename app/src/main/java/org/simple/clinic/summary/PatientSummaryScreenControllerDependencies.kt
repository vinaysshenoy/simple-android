package org.simple.clinic.summary

import dagger.Module
import dagger.Provides
import io.reactivex.Observable
import org.simple.clinic.bp.BloodPressureMeasurement
import org.simple.clinic.bp.BloodPressureRepository
import org.simple.clinic.drugs.PrescribedDrug
import org.simple.clinic.drugs.PrescriptionRepository
import org.simple.clinic.functions.Function1
import org.simple.clinic.functions.Function2
import org.simple.clinic.medicalhistory.MedicalHistory
import org.simple.clinic.medicalhistory.MedicalHistoryRepository
import org.simple.clinic.overdue.Appointment
import org.simple.clinic.overdue.AppointmentRepository
import org.simple.clinic.patient.Patient
import org.simple.clinic.patient.PatientAddress
import org.simple.clinic.patient.PatientPhoneNumber
import org.simple.clinic.patient.PatientRepository
import org.simple.clinic.patient.businessid.BusinessId
import org.simple.clinic.summary.addphone.MissingPhoneReminderRepository
import org.simple.clinic.util.Optional
import org.simple.clinic.util.filterAndUnwrapJust
import org.threeten.bp.Instant
import java.util.UUID

@Module
class PatientSummaryScreenControllerDependencies {

  @Provides
  fun bindHasShownMissingPhoneReminder(repository: MissingPhoneReminderRepository): Function1<UUID, Boolean> {
    return Function1 { repository.hasShownReminderFor(it).blockingGet() }
  }

  @Provides
  fun bindMarkReminderAsShownEffect(repository: MissingPhoneReminderRepository): Function1<UUID, Result<Unit>> {
    return Function1(repository::markReminderAsShownFor2)
  }

  @Provides
  fun bindFetchLastCreatedAppointment(repository: AppointmentRepository): Function1<UUID, Optional<Appointment>> {
    return Function1 { repository.lastCreatedAppointmentForPatient(it).blockingFirst() }
  }

  @Provides
  fun bindUpdateMedicalHistoryEffect(repository: MedicalHistoryRepository): Function1<MedicalHistory, Result<Unit>> {
    return Function1(repository::update)
  }

  @Provides
  fun bindFetchMedicalHistory(repository: MedicalHistoryRepository): Function1<UUID, MedicalHistory> {
    return Function1 { repository.historyForPatientOrDefault(it).blockingFirst() }
  }

  @Provides
  fun bindPatientPrescriptionProvider(repository: PrescriptionRepository): Function1<UUID, Observable<List<PrescribedDrug>>> {
    return Function1 { repository.newestPrescriptionsForPatient(it) }
  }

  @Provides
  fun bindBloodPressureCountProvider(repository: BloodPressureRepository): Function1<UUID, Int> {
    return Function1 { repository.bloodPressureCount(it) }
  }

  @Provides
  fun bindBloodPressuresProvider(
      repository: BloodPressureRepository,
      patientSummaryConfig: PatientSummaryConfig
  ): Function1<UUID, Observable<List<BloodPressureMeasurement>>> {
    return Function1 { patientUuid -> repository.newestMeasurementsForPatient(patientUuid, patientSummaryConfig.numberOfBpsToDisplay) }
  }

  @Provides
  fun bindPatientDataChangedSinceProvider(repository: PatientRepository): Function2<UUID, Instant, Boolean> {
    return Function2 { patientUuid, instant -> repository.hasPatientDataChangedSince(patientUuid, instant) }
  }

  @Provides
  fun bindFetchPatientPhoneNumber(repository: PatientRepository): Function1<UUID, Optional<PatientPhoneNumber>> {
    return Function1 { repository.phoneNumber(it).blockingFirst() }
  }

  @Provides
  fun bindFetchBpPassport(repository: PatientRepository): Function1<UUID, Optional<BusinessId>> {
    return Function1 { repository.bpPassportForPatient(it).blockingFirst() }
  }

  @Provides
  fun bindFetchPatientAddress(repository: PatientRepository): Function1<UUID, PatientAddress> {
    return Function1 { repository.address(it).filterAndUnwrapJust().blockingFirst() }
  }

  @Provides
  fun bindFetchPatient(repository: PatientRepository): Function1<UUID, Patient> {
    return Function1 { repository.patient(it).filterAndUnwrapJust().blockingFirst() }
  }
}
