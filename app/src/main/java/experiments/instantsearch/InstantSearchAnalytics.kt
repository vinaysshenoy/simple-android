package experiments.instantsearch

import java.util.UUID

interface InstantSearchAnalytics {
  fun createSession(id: UUID)

  fun clickedRegisterNewPatient(
      sessionId: UUID,
      numberOfCharactersTyped: Int,
      inputType: InputType
  )

  fun clickedOnSearchResult(
      sessionId: UUID,
      numberOfCharactersTyped: Int,
      inputType: InputType
  )

  fun exitedTheScreen(
      sessionId: UUID,
      numberOfCharactersTyped: Int,
      inputType: InputType
  )

  enum class InputType {
    Number, Name, Unknown
  }
}
