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
import com.pyamsoft.tetherfi.server.ServerSocketTimeout
import com.pyamsoft.tetherfi.server.proxy.session.netty.handler.flushAndClose
import io.netty.channel.ChannelFuture
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

internal class UdpControlSocketCreator
internal constructor(
  private val creator: ChannelCreator,
) {

  private val knownSockets: MutableSet<UdpInfo> = ConcurrentHashMap.newKeySet()

  private fun unregister(
    tcpControl: MutableSocketAddressHolder,
    backToClient: MutableSocketAddressHolder,
    tcpControlAddress: InetSocketAddress,
  ) {
    if (tcpControl.compareAndSet(expected = tcpControlAddress, update = null)) {
      Timber.d { "Unregistered client from socket: $tcpControlAddress" }
      backToClient.set(null)
    }
  }

  @CheckResult
  private fun newSocket(
    tcpControlAddress: InetSocketAddress,
    tcpControl: MutableSocketAddressHolder,
    backToClient: MutableSocketAddressHolder,
  ): ChannelFuture =
    creator.bind { ch ->
      val handler =
        UdpRelayHandler(
          serverSocketTimeout = UDP_RELAY_TIMEOUT,
          getTcpControl = tcpControl,
          backToClient = backToClient,
          unregister = { unregister(tcpControl, backToClient, tcpControlAddress) },
        )
      val pipeline = ch.pipeline()
      pipeline.addLast(handler)
    }

  @CheckResult
  fun register(tcpControlClient: InetSocketAddress): UdpControl {
    var existing: UdpInfo? = null
    for (known in knownSockets) {
      if (known.tcpControl.compareAndSet(null, tcpControlClient)) {
        // We took over this socket
        existing = known
        break
      }
    }

    val info: UdpInfo
    if (existing == null) {
      Timber.d { "Create new socket: $tcpControlClient" }
      val tcpControl = AtomicSocketAddressHolder.create()
      val backToClient = AtomicSocketAddressHolder.create()

      val socket =
        newSocket(
          tcpControlAddress = tcpControlClient,
          tcpControl = tcpControl,
          backToClient = backToClient,
        )
      info =
        UdpInfo(
          socket = socket,
          tcpControl = tcpControl,
          backToClient = backToClient,
        )
          .also { knownSockets.add(it) }
    } else {
      Timber.d { "Re-use existing socket: $existing" }
      info = existing
    }

    return UdpControl(socket = info.socket) {
      unregister(info.tcpControl, info.backToClient, tcpControlClient)
    }
  }

  fun close() {
    knownSockets.forEach { it.socket.flushAndClose() }
    knownSockets.clear()
  }

  @ConsistentCopyVisibility
  internal data class UdpInfo
  internal constructor(
    val socket: ChannelFuture,
    val tcpControl: MutableSocketAddressHolder,
    val backToClient: MutableSocketAddressHolder,
  )

  @ConsistentCopyVisibility
  internal data class UdpControl
  internal constructor(
    val socket: ChannelFuture,
    val unregister: () -> Unit,
  )

  companion object {

    private val UDP_RELAY_TIMEOUT = ServerSocketTimeout.create(10)
  }
}