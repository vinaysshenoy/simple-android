package org.simple.clinic.protocol

import android.annotation.SuppressLint
import androidx.annotation.CheckResult
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.withLatestFrom
import io.reactivex.schedulers.Schedulers.io
import org.simple.clinic.protocol.sync.ProtocolSync
import org.simple.clinic.user.UserSession
import javax.inject.Inject

@SuppressLint("CheckResult")
class SyncProtocolsOnLogin @Inject constructor(
    private val userSession: UserSession,
    private val protocolSync: ProtocolSync,
    private val protocolRepository: ProtocolRepository
) {

  @CheckResult
  fun listen(): Disposable {
    return userSession.loggedInUser()
        .withLatestFrom(protocolRepository.recordCount())
        .subscribeOn(io())
        .filter { (user, drugCount) -> user.isNotEmpty() && drugCount == 0 }
        .flatMapCompletable { protocolSync.sync() }
        .onErrorComplete()
        .subscribe()
  }
}
