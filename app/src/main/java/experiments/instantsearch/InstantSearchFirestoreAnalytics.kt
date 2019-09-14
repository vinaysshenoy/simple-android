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

private val localTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MMM-dd HH:mm:ss")

class InstantSearchFirestoreAnalytics @Inject constructor(
    private val utcClock: UtcClock,
    private val userClock: UserClock,
    firestore: FirebaseFirestore
) : InstantSearchAnalytics {

  private val analyticsCollection = firestore
      .collection("experiments")
      .document("instant_search_v1")
      .collection("sessions")

  private fun sessionRef(id: UUID): DocumentReference {
    return analyticsCollection.document(id.toString())
  }

  override fun createSession(id: UUID) {
    val session = Session.create(id, utcClock, userClock)

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
    val event = Event.registerPatientClicked(utcClock, userClock)

    sessionRef(sessionId)
        .get()
        .addOnSuccessListener { snapShot ->
          val session = snapShot.toObject(Session::class.java)

          if(session != null) {
            session.events.add(event)
            snapShot.reference.set(session)
          }
        }
  }

  override fun clickedOnSearchResult(sessionId: UUID, index: Int, total: Int) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun exitedTheScreen(sessionId: UUID) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  private class Event {

    var type: String? = null

    var timestamp: String? = null

    var localTime: String? = null

    var props: MutableMap<String, Any> = mutableMapOf()

    companion object {

      fun registerPatientClicked(utcClock: UtcClock, userClock: UserClock): Event {
        return Event().apply {
          type = "register_patient_clicked"
          timestamp = Instant.now(utcClock).toString()
          localTime = localTimeFormatter.format(LocalDateTime.now(userClock))
        }
      }
    }
  }

  private class Session {
    var id: String? = null

    var timestamp: String? = null

    var localTime: String? = null

    var events: MutableList<Event> = mutableListOf()

    companion object {
      fun create(id: UUID, utcClock: UtcClock, userClock: UserClock): Session {
        return Session().apply {
          this.id = id.toString()
          timestamp = Instant.now(utcClock).toString()
          localTime = localTimeFormatter.format(LocalDateTime.now(userClock))
        }
      }
    }
  }
}
