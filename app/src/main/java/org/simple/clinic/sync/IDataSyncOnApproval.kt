package org.simple.clinic.sync

import androidx.annotation.CheckResult
import io.reactivex.disposables.Disposable

interface IDataSyncOnApproval {
  @CheckResult
  fun sync(): Disposable
}
