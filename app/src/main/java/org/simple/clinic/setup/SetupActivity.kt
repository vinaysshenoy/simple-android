package org.simple.clinic.setup

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.spotify.mobius.Connection
import com.spotify.mobius.MobiusLoop
import com.spotify.mobius.android.MobiusAndroid
import com.spotify.mobius.functions.Consumer
import com.spotify.mobius.rx2.RxMobius
import io.github.inflationx.viewpump.ViewPumpContextWrapper
import io.reactivex.ObservableTransformer
import org.simple.clinic.BuildConfig
import org.simple.clinic.ClinicApp
import org.simple.clinic.R
import org.simple.clinic.activity.placeholder.PlaceholderScreenKey
import org.simple.clinic.analytics.Analytics
import org.simple.clinic.di.InjectorProviderContextWrapper
import org.simple.clinic.main.TheActivity
import org.simple.clinic.onboarding.OnboardingScreenKey
import org.simple.clinic.platform.crash.CrashReporter
import org.simple.clinic.router.ScreenResultBus
import org.simple.clinic.router.screen.ActivityPermissionResult
import org.simple.clinic.router.screen.ActivityResult
import org.simple.clinic.router.screen.FullScreenKey
import org.simple.clinic.router.screen.FullScreenKeyChanger
import org.simple.clinic.router.screen.NestedKeyChanger
import org.simple.clinic.router.screen.RouterDirection
import org.simple.clinic.router.screen.ScreenRouter
import org.simple.clinic.selectcountry.SelectCountryScreenKey
import org.simple.clinic.util.LocaleOverrideContextWrapper
import org.simple.clinic.util.unsafeLazy
import org.simple.clinic.util.wrap
import java.util.Locale
import javax.inject.Inject

class SetupActivity : AppCompatActivity(), UiActions {

  @Inject
  lateinit var locale: Locale

  @Inject
  lateinit var crashReporter: CrashReporter

  private lateinit var component: SetupActivityComponent

  @Inject
  lateinit var effectHandlerFactory: SetupActivityEffectHandler.Factory

  private val screenResults = ScreenResultBus()

  private val screenRouter by unsafeLazy {
    ScreenRouter.create(this, NestedKeyChanger(), screenResults)
  }

  private lateinit var controller: MobiusLoop.Controller<SetupActivityModel, SetupActivityEvent>


  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    @Suppress("ConstantConditionIf")
    if (BuildConfig.DISABLE_SCREENSHOT) {
      window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    }

    val model = if (savedInstanceState == null) SetupActivityModel.SETTING_UP else savedInstanceState["model_key"] as SetupActivityModel
    controller = createController(model, effectHandlerFactory.create(this).build())
    controller.connect(::connect)
  }

  private fun createController(
      model: SetupActivityModel,
      effectHandler: ObservableTransformer<SetupActivityEffect, SetupActivityEvent>
  ): MobiusLoop.Controller<SetupActivityModel, SetupActivityEvent> {
    val loop = RxMobius
        .loop(SetupActivityUpdate(), effectHandler)
        .init(SetupActivityInit())

    return MobiusAndroid.controller(loop, model)
  }

  private fun connect(eventSink: Consumer<SetupActivityEvent>): Connection<SetupActivityModel> {

    // Setup events here

    return object : Connection<SetupActivityModel> {
      override fun accept(value: SetupActivityModel) {
        // Update UI here
      }

      override fun dispose() {
        // Dispose events here
      }
    }
  }

  override fun onStart() {
    super.onStart()
    controller.start()
  }

  override fun onStop() {
    controller.stop()
    super.onStop()
  }

  override fun onDestroy() {
    controller.disconnect()
    super.onDestroy()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    outState.putParcelable("model_key", controller.model)
    super.onSaveInstanceState(outState)
  }

  override fun attachBaseContext(baseContext: Context) {
    setupDiGraph()

    val wrappedContext = baseContext
        .wrap { LocaleOverrideContextWrapper.wrap(it, locale) }
        .wrap { wrapContextWithRouter(it) }
        .wrap { InjectorProviderContextWrapper.wrap(it, component) }
        .wrap { ViewPumpContextWrapper.wrap(it) }

    super.attachBaseContext(wrappedContext)
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    screenResults.send(ActivityResult(requestCode, resultCode, data))
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    screenResults.send(ActivityPermissionResult(requestCode))
  }

  override fun onBackPressed() {
    val interceptCallback = screenRouter.offerBackPressToInterceptors()
    if (interceptCallback.intercepted) {
      return
    }
    val popCallback = screenRouter.pop()
    if (popCallback.popped) {
      return
    }
    super.onBackPressed()
  }

  override fun goToMainActivity() {
    val intent = TheActivity.newIntent(this).apply {
      flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
    }
    startActivity(intent)
    overridePendingTransition(0, 0)
  }

  override fun showOnboardingScreen() {
    screenRouter.popAndPush(OnboardingScreenKey(), RouterDirection.FORWARD)
  }

  override fun showCountrySelectionScreen() {
    screenRouter.popAndPush(SelectCountryScreenKey(), RouterDirection.FORWARD)
  }

  private fun wrapContextWithRouter(baseContext: Context): Context {
    screenRouter.registerKeyChanger(FullScreenKeyChanger(
        activity = this,
        screenLayoutContainerRes = android.R.id.content,
        screenBackgroundRes = R.color.window_background,
        onKeyChange = this::onScreenChanged
    ))
    return screenRouter.installInContext(baseContext, PlaceholderScreenKey())
  }

  private fun onScreenChanged(outgoing: FullScreenKey?, incoming: FullScreenKey) {
    val outgoingScreenName = outgoing?.analyticsName ?: ""
    val incomingScreenName = incoming.analyticsName
    Analytics.reportScreenChange(outgoingScreenName, incomingScreenName)
  }

  private fun setupDiGraph() {
    component = ClinicApp.appComponent
        .setupActivityComponentBuilder()
        .activity(this)
        .screenRouter(screenRouter)
        .build()
    component.inject(this)
  }
}
