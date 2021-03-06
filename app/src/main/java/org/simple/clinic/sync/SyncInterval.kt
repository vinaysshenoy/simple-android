package org.simple.clinic.sync

import org.threeten.bp.Duration

enum class SyncInterval(val frequency: Duration, val backOffDelay: Duration) {
  FREQUENT(frequency = Duration.ofMinutes(16L), backOffDelay = Duration.ofMinutes(5L)),
  DAILY(frequency = Duration.ofDays(1L), backOffDelay = Duration.ofMinutes(5L))
}
