/*
 * Copyright 2023 pyamsoft
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pyamsoft.tetherfi.ui

import androidx.annotation.CheckResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.pyamsoft.tetherfi.server.broadcast.BroadcastNetworkStatus

@Composable
@CheckResult
fun rememberServerSSID(group: BroadcastNetworkStatus.GroupInfo): String {
  return remember(group) {
    when (group) {
      is BroadcastNetworkStatus.GroupInfo.Connected -> group.ssid
      is BroadcastNetworkStatus.GroupInfo.Empty -> "NO NAME"
      is BroadcastNetworkStatus.GroupInfo.Error -> "ERROR"
      is BroadcastNetworkStatus.GroupInfo.Unchanged -> {
        throw IllegalStateException(
            "GroupInfo.Unchanged should never escape the server-module internals.")
      }
    }
  }
}

@Composable
@CheckResult
fun rememberServerRawPassword(group: BroadcastNetworkStatus.GroupInfo): String {
  return remember(
      group,
  ) {
    when (group) {
      is BroadcastNetworkStatus.GroupInfo.Connected -> group.password
      is BroadcastNetworkStatus.GroupInfo.Empty -> ""
      is BroadcastNetworkStatus.GroupInfo.Error -> ""
      is BroadcastNetworkStatus.GroupInfo.Unchanged -> {
        throw IllegalStateException(
            "GroupInfo.Unchanged should never escape the server-module internals.")
      }
    }
  }
}

@Composable
@CheckResult
fun rememberServerPassword(
    group: BroadcastNetworkStatus.GroupInfo,
    isPasswordVisible: Boolean,
): String {
  return remember(
      group,
      isPasswordVisible,
  ) {
    when (group) {
      is BroadcastNetworkStatus.GroupInfo.Connected -> {
        val rawPassword = group.password
        // If hidden password, map each char to the password star
        return@remember if (isPasswordVisible) {
          rawPassword
        } else {
          rawPassword.map { '\u2022' }.joinToString("")
        }
      }
      is BroadcastNetworkStatus.GroupInfo.Empty -> {
        "NO PASSWORD"
      }
      is BroadcastNetworkStatus.GroupInfo.Error -> {
        "ERROR"
      }
      is BroadcastNetworkStatus.GroupInfo.Unchanged -> {
        throw IllegalStateException(
            "GroupInfo.Unchanged should never escape the server-module internals.")
      }
    }
  }
}

@Composable
@CheckResult
fun rememberServerHostname(connection: BroadcastNetworkStatus.ConnectionInfo): String {
  return remember(connection) {
    when (connection) {
      is BroadcastNetworkStatus.ConnectionInfo.Connected -> connection.hostName
      is BroadcastNetworkStatus.ConnectionInfo.Empty -> "NO HOST"
      is BroadcastNetworkStatus.ConnectionInfo.Error -> "ERROR"
      is BroadcastNetworkStatus.ConnectionInfo.Unchanged -> {
        throw IllegalStateException(
            "ConnectionInfo.Unchanged should never escape the server-module internals.")
      }
    }
  }
}
