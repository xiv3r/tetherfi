package com.pyamsoft.tetherfi.info

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pyamsoft.pydroid.theme.ZeroSize
import com.pyamsoft.pydroid.theme.keylines
import com.pyamsoft.pydroid.ui.defaults.ImageDefaults
import com.pyamsoft.tetherfi.ui.icons.Devices
import com.pyamsoft.tetherfi.ui.icons.PhoneAndroid

internal fun LazyListScope.renderConnectionInstructions(
    itemModifier: Modifier = Modifier,
    appName: String,
    state: InfoViewState,
) {
  item {
    Column(
        modifier = itemModifier.padding(bottom = MaterialTheme.keylines.content),
    ) {
      ThisInstruction(
          small = true,
      ) {
        Text(
            text = "This Device",
            style =
                MaterialTheme.typography.caption.copy(
                    color =
                        MaterialTheme.colors.onSurface.copy(
                            alpha = ContentAlpha.medium,
                        ),
                ),
        )
      }

      OtherInstruction(
          small = true,
      ) {
        Text(
            text = "Other Device",
            style =
                MaterialTheme.typography.caption.copy(
                    color =
                        MaterialTheme.colors.onSurface.copy(
                            alpha = ContentAlpha.medium,
                        ),
                ),
        )
      }
    }
  }

  item {
    ThisInstruction(
        modifier = itemModifier,
    ) {
      Text(
          text = "Connect to the Internet.",
          style = MaterialTheme.typography.body1,
      )
    }
  }

  item {
    ThisInstruction(
        modifier = itemModifier,
        small = true,
    ) {
      Text(
          text =
              "Optionally configure the $appName proxy by setting the network name, password, port, and band",
          style =
              MaterialTheme.typography.body2.copy(
                  color =
                      MaterialTheme.colors.onSurface.copy(
                          alpha = ContentAlpha.medium,
                      ),
              ),
      )
    }
  }

  item {
    ThisInstruction(
        modifier = itemModifier,
        small = true,
    ) {
      Column {
        Text(
            text = "Optionally disable Battery Optimizations for full proxy performance",
            style =
                MaterialTheme.typography.body2.copy(
                    color =
                        MaterialTheme.colors.onBackground.copy(
                            alpha = ContentAlpha.medium,
                        ),
                ),
        )

        Text(
            text = "Optionally keep the CPU awake for full proxy performance",
            style =
                MaterialTheme.typography.body2.copy(
                    color =
                        MaterialTheme.colors.onBackground.copy(
                            alpha = ContentAlpha.medium,
                        ),
                ),
        )
      }
    }
  }

  item {
    ThisInstruction(
        modifier = itemModifier,
    ) {
      Text(
          text = "Start the $appName proxy by clicking the button at the top",
          style = MaterialTheme.typography.body1,
      )
    }
  }

  item {
    OtherInstruction(
        modifier = itemModifier,
    ) {
      Text(
          text = "Open the Wi-Fi settings page",
          style = MaterialTheme.typography.body1,
      )
    }
  }

  item {
    OtherInstruction(
        modifier = itemModifier,
    ) {
      Column {
        Text(
            text = "Connect to the $appName network:",
            style = MaterialTheme.typography.body2,
        )

        Row {
          Text(
              text = "Name",
              style =
                  MaterialTheme.typography.body1.copy(
                      color =
                          MaterialTheme.colors.onBackground.copy(
                              alpha = ContentAlpha.medium,
                          ),
                  ),
          )
          Text(
              modifier = Modifier.padding(start = MaterialTheme.keylines.typography),
              text = state.ssid,
              style =
                  MaterialTheme.typography.body1.copy(
                      fontWeight = FontWeight.W700,
                  ),
          )
        }

        Row {
          Text(
              text = "Password",
              style =
                  MaterialTheme.typography.body1.copy(
                      color =
                          MaterialTheme.colors.onBackground.copy(
                              alpha = ContentAlpha.medium,
                          ),
                  ),
          )
          Text(
              modifier = Modifier.padding(start = MaterialTheme.keylines.typography),
              text = state.password,
              style =
                  MaterialTheme.typography.body1.copy(
                      fontWeight = FontWeight.W700,
                  ),
          )
        }

        Row {
          Text(
              text = "URL/Hostname",
              style =
                  MaterialTheme.typography.body1.copy(
                      color =
                          MaterialTheme.colors.onBackground.copy(
                              alpha = ContentAlpha.medium,
                          ),
                  ),
          )
          Text(
              modifier = Modifier.padding(start = MaterialTheme.keylines.typography),
              text = state.ip,
              style =
                  MaterialTheme.typography.body1.copy(
                      fontWeight = FontWeight.W700,
                  ),
          )
        }

        Row {
          Text(
              text = "Port",
              style =
                  MaterialTheme.typography.body1.copy(
                      color =
                          MaterialTheme.colors.onBackground.copy(
                              alpha = ContentAlpha.medium,
                          ),
                  ),
          )
          Text(
              modifier = Modifier.padding(start = MaterialTheme.keylines.typography),
              text = "${state.port}",
              style =
                  MaterialTheme.typography.body1.copy(
                      fontWeight = FontWeight.W700,
                  ),
          )
        }

        Text(
            modifier = Modifier.padding(top = MaterialTheme.keylines.baseline),
            text = "Leave all other proxy options blank!",
            style =
                MaterialTheme.typography.caption.copy(
                    color =
                        MaterialTheme.colors.onBackground.copy(
                            alpha = ContentAlpha.medium,
                        ),
                ),
        )
      }
    }
  }

  item {
    OtherInstruction(
        modifier = itemModifier,
    ) {
      Text(
          text =
              "Turn the Wi-Fi off and back on again. It should automatically connect to the $appName network proxy",
          style = MaterialTheme.typography.body1,
      )
    }
  }

  item {
    ThisInstruction(
        modifier = itemModifier,
    ) {
      Text(
          text = "Your device should now be sharing its Internet connection!",
          style = MaterialTheme.typography.body1,
      )
    }
  }

  item {
    ThisInstruction(
        modifier = itemModifier,
    ) {
      Text(
          text = "To stop the $appName proxy, click the button at the top again.",
          style = MaterialTheme.typography.body1,
      )
    }
  }
}

private val THIS_DEVICE_COLOR = Color(0xFF4CAF50)
private val OTHER_DEVICE_COLOR = Color(0xFF2196F3)

@Composable
private fun ThisDevice(
    modifier: Modifier = Modifier,
    small: Boolean,
) {
  val keylines = MaterialTheme.keylines
  val sizeAdjustment = remember(small, keylines) { if (small) keylines.typography else ZeroSize }

  Icon(
      modifier =
          modifier
              .padding(start = if (small) 2.dp else ZeroSize)
              .size(ImageDefaults.IconSize - sizeAdjustment)
              .padding(end = if (small) 2.dp else ZeroSize),
      imageVector = Icons.Filled.PhoneAndroid,
      contentDescription = "This Device",
      tint = THIS_DEVICE_COLOR,
  )
}

@Composable
private fun OtherDevice(
    modifier: Modifier = Modifier,
    small: Boolean,
) {
  val keylines = MaterialTheme.keylines
  val sizeAdjustment = remember(small, keylines) { if (small) keylines.typography else ZeroSize }

  Icon(
      modifier =
          modifier
              .padding(start = if (small) 2.dp else ZeroSize)
              .size(ImageDefaults.IconSize - sizeAdjustment)
              .padding(end = if (small) 2.dp else ZeroSize),
      imageVector = Icons.Filled.Devices,
      contentDescription = "Other Devices",
      tint = OTHER_DEVICE_COLOR,
  )
}

@Composable
private fun ThisInstruction(
    modifier: Modifier = Modifier,
    small: Boolean = false,
    content: @Composable () -> Unit,
) {
  Row(
      modifier = modifier,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    ThisDevice(small = small)

    Box(
        modifier = Modifier.weight(1F).padding(start = MaterialTheme.keylines.content),
    ) {
      content()
    }
  }
}

@Composable
private fun OtherInstruction(
    modifier: Modifier = Modifier,
    small: Boolean = false,
    content: @Composable () -> Unit,
) {
  Row(
      modifier = modifier,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    OtherDevice(small = small)

    Box(
        modifier = Modifier.weight(1F).padding(start = MaterialTheme.keylines.content),
    ) {
      content()
    }
  }
}

@Preview
@Composable
private fun PreviewConnectionInstructions() {
  LazyColumn {
    renderConnectionInstructions(
        appName = "TEST",
        state =
            MutableInfoViewState().apply {
              ip = "192.168.0.1"
              ssid = "TEST NETWORK"
              password = "TEST PASSWORD"
              port = 8228
            },
    )
  }
}