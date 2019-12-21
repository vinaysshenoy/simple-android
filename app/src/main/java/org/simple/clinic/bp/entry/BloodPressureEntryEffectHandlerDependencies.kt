package org.simple.clinic.bp.entry

import dagger.Module
import dagger.Provides
import org.simple.clinic.facility.Facility
import org.simple.clinic.functions.CachedFunction0.cached
import org.simple.clinic.functions.Function0
import org.simple.clinic.functions.Function1
import org.simple.clinic.functions.Function2
import org.simple.clinic.functions.SynchronisedFunction0.synchronised
import org.simple.clinic.overdue.AppointmentRepository
import org.simple.clinic.patient.PatientRepository
import org.simple.clinic.user.LoggedInUserFacilityMapping
import org.simple.clinic.user.User
import org.threeten.bp.Instant
import java.util.UUID

@Module
class BloodPressureEntryEffectHandlerDependencies {

  @Provides
  fun bindFetchCurrentUser(userDao: User.RoomDao): Function0<User> {
    val supplier = Function0<User> {
      val user = userDao.userImmediate()
      requireNotNull(user)

      user
    }

    return synchronised(cached(supplier))
  }

  @Provides
  fun bindFetchCurrentFacility(fetchCurrentUser: Function0<User>, facilityMapping: LoggedInUserFacilityMapping.RoomDao): Function0<Facility> {
    val supplier = Function0 {
      val user = fetchCurrentUser.call()

      facilityMapping.currentFacilityImmediate(user.uuid)
    }

    return synchronised(cached(supplier))
  }

  @Provides
  fun bindUpdatePatientRecordedEffect(patientRepository: PatientRepository): Function2<UUID, Instant, Unit> {
    return Function2 { patientUuid, instant -> patientRepository.compareAndUpdateRecordedAt(patientUuid, instant).blockingAwait() }
  }

  @Provides
  fun bindMarkAppointmentsCreatedBeforeTodayAsVisitedEffect(appointmentRepository: AppointmentRepository): Function1<UUID, Unit> {
    return Function1 { appointmentRepository.markAppointmentsCreatedBeforeTodayAsVisited(it).blockingAwait() }
  }
}
