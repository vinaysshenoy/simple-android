package experiments

import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import experiments.instantsearch.InstantPatientSearchExperimentsDao
import experiments.instantsearch.InstantSearchModule
import org.simple.clinic.AppDatabase

@Module(includes = [InstantSearchModule::class])
class ExperimentsModule {

  @Provides
  fun providePatientSearchResultsDao(appDatabase: AppDatabase): InstantPatientSearchExperimentsDao {
    return InstantPatientSearchExperimentsDao(appDatabase)
  }

  @Provides
  fun firebaseStore(): FirebaseFirestore = FirebaseFirestore.getInstance()
}
