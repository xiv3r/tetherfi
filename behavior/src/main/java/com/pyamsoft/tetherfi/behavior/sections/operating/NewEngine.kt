/*
 * Copyright 2025 pyamsoft
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pyamsoft.tetherfi.behavior.sections.operating

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pyamsoft.pydroid.ui.haptics.LocalHapticManager
import com.pyamsoft.tetherfi.behavior.R
import com.pyamsoft.tetherfi.ui.ServerViewState
import com.pyamsoft.tetherfi.ui.checkable.CheckableCard

@Composable
internal fun NewEngine(
    modifier: Modifier = Modifier,
    isEditable: Boolean,
    appName: String,
    serverViewState: ServerViewState,
    onToggleNewEngine: () -> Unit,
) {
  val hapticManager = LocalHapticManager.current
  val isNewEngineEnabled by serverViewState.isNewEngine.collectAsStateWithLifecycle()

  CheckableCard(
      modifier = modifier,
      isEditable = isEditable,
      condition = isNewEngineEnabled,
      title = stringResource(R.string.operating_new_engine_title),
      description = stringResource(R.string.operating_new_engine_description, appName),
      onClick = {
        hapticManager?.confirmButtonPress()
        onToggleNewEngine()
      },
  )
}
