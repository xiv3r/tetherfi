/*
 * Copyright 2026 pyamsoft
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

package com.pyamsoft.tetherfi.server.proxy.session

import androidx.annotation.CheckResult
import com.pyamsoft.pydroid.core.cast
import java.net.InetSocketAddress
import java.net.SocketAddress

@CheckResult
private fun SocketAddress.inet(): InetSocketAddress? {
  return this.cast()
}

val SocketAddress.hostname: String
   @CheckResult get() {
    val inet = this.inet()
    val hostname = inet?.hostname ?: inet?.address?.hostName
    return hostname.orEmpty()
  }

val SocketAddress.address: String
  @CheckResult get() = this.inet()?.hostString.orEmpty()

val SocketAddress.port: Int
  @CheckResult get() = this.inet()?.port ?: 0
