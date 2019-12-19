package org.simple.clinic.bp.entry

import dagger.Module
import dagger.Provides
import org.simple.clinic.facility.Facility
import org.simple.clinic.functions.Function0
import org.simple.clinic.user.LoggedInUserFacilityMapping
import org.simple.clinic.user.User

@Module
class BloodPressureEntryEffectHandlerDependencies {

  @Provides
  fun bindFetchCurrentUser(userDao: User.RoomDao): Function0<User> {
    return Function0<User> {
      val user = userDao.userImmediate()
      requireNotNull(user)

      user
    }
  }

  @Provides
  fun bindFetchCurrentFacility(fetchCurrentUser: Function0<User>, facilityMapping: LoggedInUserFacilityMapping.RoomDao): Function0<Facility> {
    return Function0 {
      val user = fetchCurrentUser.call()

      facilityMapping.currentFacilityImmediate(user.uuid)
    }
  }
}
