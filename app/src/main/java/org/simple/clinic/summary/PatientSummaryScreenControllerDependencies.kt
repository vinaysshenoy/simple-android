package org.simple.clinic.summary

import dagger.Module
import dagger.Provides
import io.reactivex.Completable
import io.reactivex.Observable
import org.simple.clinic.functions.Function0
import org.simple.clinic.functions.Function1
import org.simple.clinic.overdue.Appointment
import org.simple.clinic.overdue.AppointmentRepository
import org.simple.clinic.summary.addphone.MissingPhoneReminderRepository
import org.simple.clinic.util.filterAndUnwrapJust
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
}
