package org.simple.clinic.summary

import dagger.Module
import dagger.Provides
import io.reactivex.Completable
import io.reactivex.Observable
import org.simple.clinic.bp.BloodPressureRepository
import org.simple.clinic.drugs.PrescribedDrug
import org.simple.clinic.drugs.PrescriptionRepository
import org.simple.clinic.functions.Function0
import org.simple.clinic.functions.Function1
import org.simple.clinic.functions.Function2
import org.simple.clinic.medicalhistory.MedicalHistory
import org.simple.clinic.medicalhistory.MedicalHistoryRepository
import org.simple.clinic.overdue.Appointment
import org.simple.clinic.overdue.AppointmentRepository
import org.simple.clinic.summary.addphone.MissingPhoneReminderRepository
import org.simple.clinic.util.UtcClock
import org.simple.clinic.util.filterAndUnwrapJust
import org.threeten.bp.Instant
import java.util.UUID

@Module
class PatientSummaryScreenControllerDependencies {

  @Provides
  fun bindNumberOfBpsToDisplaySupplier(patientSummaryConfig: PatientSummaryConfig): Function0<Int> {
    return Function0 { patientSummaryConfig.numberOfBpsToDisplay }
  }

  @Provides
  fun bindHasShownMissingPhoneReminderProvider(repository: MissingPhoneReminderRepository): Function1<UUID, Observable<Boolean>> {
    return Function1 { repository.hasShownReminderFor(it).toObservable() }
  }

  @Provides
  fun bindMarkReminderAsShownConsumer(repository: MissingPhoneReminderRepository): Function1<UUID, Completable> {
    return Function1 { repository.markReminderAsShownFor(it) }
  }

  @Provides
  fun bindLastCreatedAppointmentProvider(repository: AppointmentRepository): Function1<UUID, Observable<Appointment>> {
    return Function1 { repository.lastCreatedAppointmentForPatient(it).filterAndUnwrapJust() }
  }

  @Provides
  fun bindUpdateMedicalHistory(repository: MedicalHistoryRepository): Function2<MedicalHistory, Instant, Completable> {
    return Function2 { medicalHistory, updatedTime -> repository.save(medicalHistory, updatedTime) }
  }

  @Provides
  fun bindUtcTimestampProvider(clock: UtcClock): Function0<Instant> {
    return Function0 { Instant.now(clock) }
  }

  @Provides
  fun bindMedicalHistoryProvider(repository: MedicalHistoryRepository): Function1<UUID, Observable<MedicalHistory>> {
    return Function1 { repository.historyForPatientOrDefault(it) }
  }

  @Provides
  fun bindPatientPrescriptionProvider(repository: PrescriptionRepository): Function1<UUID, Observable<List<PrescribedDrug>>> {
    return Function1 { repository.newestPrescriptionsForPatient(it) }
  }

  @Provides
  fun bindBloodPressureCountProvider(repository: BloodPressureRepository): Function1<UUID, Int> {
    return Function1 { repository.bloodPressureCount(it) }
  }
}
