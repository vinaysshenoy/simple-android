package experiments.instantsearch

import com.f2prateek.rx.preferences2.Preference
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import org.simple.clinic.util.UserClock
import org.simple.clinic.util.UtcClock
import javax.inject.Named

@Module
class InstantSearchModule {

  @Provides
  fun bindInstantSearchAnalytics(
      utcClock: UtcClock,
      userClock: UserClock,
      firestore: FirebaseFirestore,
      @Named("experiment_instantsearch_v1_toggle") experimentEnabled: Preference<Boolean>
  ): InstantSearchAnalytics {
    return InstantSearchFirestoreAnalytics(utcClock, userClock, firestore)
        .allowEvents(experimentEnabled)
  }
}
