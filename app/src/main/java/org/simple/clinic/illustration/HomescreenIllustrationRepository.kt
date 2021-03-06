package org.simple.clinic.illustration

import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.rxkotlin.ofType
import org.simple.clinic.storage.files.FileStorage
import org.simple.clinic.storage.files.GetFileResult
import org.simple.clinic.util.Just
import org.simple.clinic.util.Optional
import org.simple.clinic.util.UserClock
import org.simple.clinic.util.filterAndUnwrapJust
import org.simple.clinic.util.mapType
import org.simple.clinic.util.toOptional
import org.threeten.bp.LocalDate
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Named

class HomescreenIllustrationRepository @Inject constructor(
    private val userClock: UserClock,
    private val fileStorage: FileStorage,
    private val illustrationConfigParser: IllustrationConfigParser,
    @Named("homescreen-illustration-folder") private val illustrationsFolder: String
) {

  fun illustrations(): Observable<List<HomescreenIllustration>> =
      Observable.fromCallable { illustrationConfigParser.illustrations() }

  fun illustrationImageToShow(): Observable<File> =
      illustrations()
          .map { pickIllustration(it) }
          .filterAndUnwrapJust()
          .flatMapMaybe { illustrationFile(it.eventId) }

  private fun pickIllustration(illustrations: List<HomescreenIllustration>): Optional<HomescreenIllustration> {
    val today = LocalDate.now(userClock)
    return illustrations
        .firstOrNull { illustration ->
          val showIllustrationFrom = toLocalDate(illustration.from)
          val showIllustrationTill = toLocalDate(illustration.to)

          today in showIllustrationFrom..showIllustrationTill
        }
        .toOptional()
  }

  private fun toLocalDate(dayOfMonth: DayOfMonth): LocalDate =
      LocalDate.now(userClock)
          .withMonth(dayOfMonth.month.value)
          .withDayOfMonth(dayOfMonth.day)

  fun saveIllustration(illustrationFileName: String, responseStream: InputStream): Completable {
    return illustrationFile(illustrationFileName)
        .flatMapCompletable { illustrationsFile ->
          Completable.fromAction {
            fileStorage.writeStreamToFile(
                inputStream = responseStream,
                file = illustrationsFile
            )
          }
        }
  }

  private fun illustrationFile(illustrationFileName: String): Maybe<File> {
    return Single
        .fromCallable { fileStorage.getFile("$illustrationsFolder/$illustrationFileName") }
        .mapType<GetFileResult.Success, File> { it.file }
  }
}
