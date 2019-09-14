package experiments.instantsearch

import java.util.UUID

interface InstantSearchAnalytics {
  fun createSession(id: UUID)
  fun clickedRegisterNewPatient(sessionId: UUID)
  fun clickedOnSearchResult(sessionId: UUID, index: Int, total: Int)
  fun exitedTheScreen(sessionId: UUID)
}
