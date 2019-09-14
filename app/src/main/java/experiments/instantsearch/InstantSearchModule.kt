package experiments.instantsearch

import dagger.Binds
import dagger.Module

@Module
abstract class InstantSearchModule {

  @Binds
  abstract fun bindInstantSearchAnalytics(instantSearchFirestoreAnalytics: InstantSearchFirestoreAnalytics): InstantSearchAnalytics
}
