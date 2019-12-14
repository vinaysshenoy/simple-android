package org.simple.clinic.summary

import dagger.Module
import dagger.Provides
import org.simple.clinic.functions.Function0

@Module
class PatientSummaryScreenControllerDependencies {

  @Provides
  fun supplyNumberOfBpsToDisplay(patientSummaryConfig: PatientSummaryConfig): Function0<Int> {
    return Function0 { patientSummaryConfig.numberOfBpsToDisplay }
  }
}
