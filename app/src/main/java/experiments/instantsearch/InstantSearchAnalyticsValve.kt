package experiments.instantsearch

import com.f2prateek.rx.preferences2.Preference
import java.util.UUID

class InstantSearchAnalyticsValve(
    private val allowAnalytics: Preference<Boolean>,
    private val wrapped: InstantSearchAnalytics
): InstantSearchAnalytics {

  override fun createSession(id: UUID) {
    if(allowAnalytics.get()) {
      wrapped.createSession(id)
    }
  }

  override fun clickedRegisterNewPatient(sessionId: UUID, numberOfCharactersTyped: Int, inputType: InstantSearchAnalytics.InputType) {
    if(allowAnalytics.get()) {
      wrapped.clickedRegisterNewPatient(sessionId, numberOfCharactersTyped, inputType)
    }
  }

  override fun clickedOnSearchResult(sessionId: UUID, numberOfCharactersTyped: Int, inputType: InstantSearchAnalytics.InputType) {
    if(allowAnalytics.get()) {
      wrapped.clickedOnSearchResult(sessionId, numberOfCharactersTyped, inputType)
    }
  }

  override fun exitedTheScreen(sessionId: UUID, numberOfCharactersTyped: Int, inputType: InstantSearchAnalytics.InputType) {
    if(allowAnalytics.get()) {
      wrapped.exitedTheScreen(sessionId, numberOfCharactersTyped, inputType)
    }
  }
}

fun InstantSearchAnalytics.allowEvents(valve: Preference<Boolean>): InstantSearchAnalytics {
  return InstantSearchAnalyticsValve(valve, this)
}
