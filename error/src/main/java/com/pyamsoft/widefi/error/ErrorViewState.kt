package com.pyamsoft.widefi.error

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pyamsoft.pydroid.arch.UiViewState
import com.pyamsoft.widefi.ui.ProxyEvent
import javax.inject.Inject

@Stable
interface ErrorViewState : UiViewState {
  val events: List<ProxyEvent>
}

internal class MutableErrorViewState @Inject internal constructor() : ErrorViewState {
  override var events by mutableStateOf<List<ProxyEvent>>(emptyList())
}