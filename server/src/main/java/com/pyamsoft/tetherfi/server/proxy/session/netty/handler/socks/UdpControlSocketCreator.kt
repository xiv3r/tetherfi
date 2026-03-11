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

package com.pyamsoft.tetherfi.server.proxy.session.netty.handler.socks

import androidx.annotation.CheckResult
import com.pyamsoft.tetherfi.core.Timber
import com.pyamsoft.tetherfi.server.proxy.session.netty.handler.flushAndClose
import io.netty.channel.ChannelFuture
import java.net.InetSocketAddress

internal class UdpControlSocketCreator internal constructor(
  private val creator: ChannelCreator,
) {

  @CheckResult
  private fun newSocket(): ChannelFuture = creator.bind { ch ->
    Timber.d { "Bound UDP CONTROL SOCKET: ${ch.localAddress()}" }

    val handler = UdpRelayHandler()
    val pipeline = ch.pipeline()
    pipeline.addLast(handler)
  }

  @CheckResult
  fun register(client: InetSocketAddress): UdpControl {
    // TODO register
    Timber.d { "Register UDP control: $client" }

    val socket = newSocket()
    return UdpControl(
      channelFuture = socket,
    ) {
      // TODO unregister
      Timber.d { "Unregister UDP control: $client" }
      socket.channel().flushAndClose()
    }
  }

  @ConsistentCopyVisibility
  internal data class UdpControl internal constructor(
    val channelFuture: ChannelFuture,
    val unregister: () -> Unit,
  )
}