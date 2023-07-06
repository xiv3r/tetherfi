package com.pyamsoft.tetherfi.status

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.pyamsoft.pydroid.theme.keylines
import com.pyamsoft.pydroid.ui.haptics.HapticManager
import com.pyamsoft.pydroid.ui.theme.HairlineSize
import com.pyamsoft.tetherfi.server.ServerNetworkBand
import com.pyamsoft.tetherfi.server.status.RunningStatus
import com.pyamsoft.tetherfi.ui.ServerViewState

internal fun LazyListScope.renderNetworkInformation(
    itemModifier: Modifier = Modifier,
    hapticManager: HapticManager,
    state: StatusViewState,
    serverViewState: ServerViewState,
    appName: String,

    // Running
    isEditable: Boolean,
    wiDiStatus: RunningStatus,
    proxyStatus: RunningStatus,

    // Network config
    onSsidChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onPortChanged: (String) -> Unit,
    onSelectBand: (ServerNetworkBand) -> Unit,
    onTogglePasswordVisibility: () -> Unit,

    // Connections
    onShowQRCode: () -> Unit,
    onRefreshConnection: () -> Unit,

    // Errors
    onShowNetworkError: () -> Unit,
    onShowHotspotError: () -> Unit,
) {
  item(
      contentType = StatusScreenContentTypes.NETWORK_ERROR,
  ) {
    val isWifiDirectError = remember(wiDiStatus) { wiDiStatus is RunningStatus.Error }
    val isProxyError = remember(proxyStatus) { proxyStatus is RunningStatus.Error }
    val showErrorHintMessage =
        remember(
            isWifiDirectError,
            isProxyError,
        ) {
          isWifiDirectError || isProxyError
        }

    AnimatedVisibility(
        visible = showErrorHintMessage,
    ) {
      Box(
          modifier =
              itemModifier
                  .fillMaxWidth()
                  .padding(horizontal = MaterialTheme.keylines.content)
                  .padding(bottom = MaterialTheme.keylines.content * 2)
                  .border(
                      width = HairlineSize,
                      color = MaterialTheme.colors.error,
                      shape = MaterialTheme.shapes.medium,
                  )
                  .padding(vertical = MaterialTheme.keylines.content),
      ) {
        TroubleshootUnableToStart(
            modifier = Modifier.fillMaxWidth(),
            appName = appName,
            isWifiDirectError = isWifiDirectError,
            isProxyError = isProxyError,
        )
      }
    }
  }

  item(
      contentType = StatusScreenContentTypes.PERMISSIONS,
  ) {
    val requiresPermissions by state.requiresPermissions.collectAsState()

    AnimatedVisibility(
        visible = requiresPermissions,
    ) {
      Box(
          modifier =
              itemModifier
                  .padding(bottom = MaterialTheme.keylines.content)
                  .padding(horizontal = MaterialTheme.keylines.content),
      ) {
        Text(
            text = "$appName requires permissions: Click the button and grant permissions",
            style =
                MaterialTheme.typography.caption.copy(
                    color = MaterialTheme.colors.error,
                ),
        )
      }
    }
  }

  if (isEditable) {
    renderEditableItems(
        modifier = itemModifier,
        hapticManager = hapticManager,
        state = state,
        onSsidChanged = onSsidChanged,
        onPasswordChanged = onPasswordChanged,
        onPortChanged = onPortChanged,
        onTogglePasswordVisibility = onTogglePasswordVisibility,
    )
  } else {
    renderRunningItems(
        modifier = itemModifier,
        hapticManager = hapticManager,
        state = state,
        serverViewState = serverViewState,
        onTogglePasswordVisibility = onTogglePasswordVisibility,
        onShowQRCode = onShowQRCode,
        onRefreshConnection = onRefreshConnection,
        onShowHotspotError = onShowHotspotError,
        onShowNetworkError = onShowNetworkError,
    )
  }

  item(
      contentType = StatusScreenContentTypes.BANDS,
  ) {
    NetworkBands(
        modifier =
            itemModifier
                .padding(top = MaterialTheme.keylines.content)
                .padding(horizontal = MaterialTheme.keylines.content),
        hapticManager = hapticManager,
        isEditable = isEditable,
        state = state,
        onSelectBand = onSelectBand,
    )
  }
}

private fun LazyListScope.renderRunningItems(
    modifier: Modifier = Modifier,
    hapticManager: HapticManager,
    state: StatusViewState,
    serverViewState: ServerViewState,
    onTogglePasswordVisibility: () -> Unit,
    onShowQRCode: () -> Unit,
    onRefreshConnection: () -> Unit,
    onShowHotspotError: () -> Unit,
    onShowNetworkError: () -> Unit,
) {
  item(
      contentType = StatusScreenContentTypes.VIEW_SSID,
  ) {
    ViewSsid(
        modifier =
            modifier
                .padding(bottom = MaterialTheme.keylines.baseline)
                .padding(horizontal = MaterialTheme.keylines.content),
        serverViewState = serverViewState,
    )
  }

  item(
      contentType = StatusScreenContentTypes.VIEW_PASSWD,
  ) {
    ViewPassword(
        modifier =
            modifier
                .padding(bottom = MaterialTheme.keylines.baseline)
                .padding(horizontal = MaterialTheme.keylines.content),
        hapticManager = hapticManager,
        state = state,
        serverViewState = serverViewState,
        onTogglePasswordVisibility = onTogglePasswordVisibility,
    )
  }

  item(
      contentType = StatusScreenContentTypes.VIEW_PROXY,
  ) {
    ViewProxy(
        modifier =
            modifier
                .padding(bottom = MaterialTheme.keylines.baseline)
                .padding(horizontal = MaterialTheme.keylines.content),
        serverViewState = serverViewState,
    )
  }

  item(
      contentType = StatusScreenContentTypes.TILES,
  ) {
    RunningTiles(
        hapticManager = hapticManager,
        serverViewState = serverViewState,
        onShowQRCode = onShowQRCode,
        onRefreshConnection = onRefreshConnection,
        onShowHotspotError = onShowHotspotError,
        onShowNetworkError = onShowNetworkError,
    )
  }
}

private fun LazyListScope.renderEditableItems(
    modifier: Modifier = Modifier,
    hapticManager: HapticManager,
    state: StatusViewState,
    onSsidChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onPortChanged: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
) {
  item(
      contentType = StatusScreenContentTypes.EDIT_SSID,
  ) {
    EditSsid(
        modifier =
            modifier
                .padding(bottom = MaterialTheme.keylines.baseline)
                .padding(horizontal = MaterialTheme.keylines.content),
        state = state,
        onSsidChanged = onSsidChanged,
    )
  }

  item(
      contentType = StatusScreenContentTypes.EDIT_PASSWD,
  ) {
    EditPassword(
        modifier =
            modifier
                .padding(bottom = MaterialTheme.keylines.baseline)
                .padding(horizontal = MaterialTheme.keylines.content),
        hapticManager = hapticManager,
        state = state,
        onTogglePasswordVisibility = onTogglePasswordVisibility,
        onPasswordChanged = onPasswordChanged,
    )
  }

  item(
      contentType = StatusScreenContentTypes.EDIT_PORT,
  ) {
    EditPort(
        modifier =
            modifier
                .padding(bottom = MaterialTheme.keylines.baseline)
                .padding(horizontal = MaterialTheme.keylines.content),
        state = state,
        onPortChanged = onPortChanged,
    )
  }
}