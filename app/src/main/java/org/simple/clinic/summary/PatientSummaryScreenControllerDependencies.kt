package org.simple.clinic.summary

import dagger.Module
import dagger.Provides
import io.reactivex.Observable
import org.simple.clinic.functions.Function0
import org.simple.clinic.functions.Function1
import org.simple.clinic.summary.addphone.MissingPhoneReminderRepository
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
}
