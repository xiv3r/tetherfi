package com.pyamsoft.tetherfi.service.lock

import android.content.Context
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.WifiLock
import android.os.Build
import androidx.annotation.CheckResult
import androidx.core.content.getSystemService
import com.pyamsoft.pydroid.core.ThreadEnforcer
import com.pyamsoft.pydroid.core.requireNotNull
import com.pyamsoft.tetherfi.service.ServicePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class WiFiLocker
@Inject
internal constructor(
    enforcer: ThreadEnforcer,
    context: Context,
    private val preferences: ServicePreferences,
) : AbstractLocker() {

  private val mutex = Mutex()
  private val tag = createTag(context.packageName)

  private val lock by lazy {
    enforcer.assertOffMainThread()

    val wifiManager = context.getSystemService<WifiManager>().requireNotNull()
    return@lazy wifiManager.createLock(tag)
  }

  // Double check because we are also wrapped in a mutex
  private val wakeAcquired = MutableStateFlow(false)

  override suspend fun acquireLock() =
      withContext(context = Dispatchers.Default + NonCancellable) {
        mutex.withLock {
          if (wakeAcquired.compareAndSet(expect = false, update = true)) {
            lock.acquire()
            Timber.d("####################################")
            Timber.d("Acquire WiFi wakelock: $tag")
            Timber.d("####################################")
          }
        }
      }

  override suspend fun releaseLock() =
      withContext(context = Dispatchers.Default + NonCancellable) {
        mutex.withLock {
          if (wakeAcquired.compareAndSet(expect = true, update = false)) {
            Timber.d("####################################")
            Timber.d("Release WIFI wakelock: $tag")
            Timber.d("####################################")
            lock.release()
          }
        }
      }

  override suspend fun isEnabled(): Boolean =
      withContext(context = Dispatchers.Default) { preferences.listenForWiFiLockChanges().first() }

  companion object {

    @JvmStatic
    @CheckResult
    private fun createTag(name: String): String {
      return "${name}:PROXY_WIFI_LOCK"
    }

    @JvmStatic
    @CheckResult
    private fun WifiManager.createLock(tag: String): WifiLock {
      return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        this.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, tag)
      } else {
        @Suppress("DEPRECATION") this.createWifiLock(tag)
      }
    }
  }
}
