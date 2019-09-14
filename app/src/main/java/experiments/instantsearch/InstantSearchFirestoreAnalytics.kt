package experiments.instantsearch

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import org.simple.clinic.util.UserClock
import org.simple.clinic.util.UtcClock
import org.threeten.bp.Instant
import org.threeten.bp.LocalDateTime
import org.threeten.bp.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

class InstantSearchFirestoreAnalytics @Inject constructor(
    private val utcClock: UtcClock,
    private val userClock: UserClock,
    firestore: FirebaseFirestore
) : InstantSearchAnalytics {

  private val analyticsCollection = firestore
      .collection("experiments")
      .document("instant_search_v1")
      .collection("sessions")
  private val localTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MMM-dd HH:mm:ss")

  private fun sessionRef(id: UUID): DocumentReference {
    return analyticsCollection.document(id.toString())
  }

  override fun createSession(id: UUID) {
    val sessionTimestamp = Instant.now(utcClock)
    val localTime = localTimeFormatter.format(LocalDateTime.now(userClock))

    val session = mapOf(
        "id" to id.toString(),
        "timestamp" to sessionTimestamp.toString(),
        "local_time" to localTime
    )

    val sessionRef = sessionRef(id)

    sessionRef
        .get()
        .addOnSuccessListener { snapshot ->
          if (!snapshot.exists()) {
            sessionRef.set(session)
          }
        }
        .addOnFailureListener { sessionRef.set(session) }
  }

  override fun clickedRegisterNewPatient(sessionId: UUID) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun clickedOnSearchResult(sessionId: UUID, index: Int, total: Int) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun exitedTheScreen(sessionId: UUID) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }
}
