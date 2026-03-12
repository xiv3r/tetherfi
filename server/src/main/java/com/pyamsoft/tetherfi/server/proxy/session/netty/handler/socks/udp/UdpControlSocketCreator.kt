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

package com.pyamsoft.tetherfi.server.proxy.session.netty.handler.socks.udp

import androidx.annotation.CheckResult
import com.pyamsoft.tetherfi.core.Timber
import com.pyamsoft.tetherfi.server.ServerSocketTimeout
import com.pyamsoft.tetherfi.server.proxy.session.netty.handler.channel.ChannelCreator
import com.pyamsoft.tetherfi.server.proxy.session.netty.handler.pool.AbstractSocketPooler
import com.pyamsoft.tetherfi.server.proxy.session.netty.handler.pool.SocketPooler
import io.netty.channel.ChannelFuture
import java.net.InetSocketAddress

internal class UdpControlSocketCreator
internal constructor(
    private val creator: ChannelCreator,
    private val serverSocketTimeout: ServerSocketTimeout,
) : AbstractSocketPooler<InetSocketAddress, UdpControlSocketCreator.UdpInfo>() {

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
                serverSocketTimeout = serverSocketTimeout,
                getTcpControl = tcpControl,
                backToClient = backToClient,
                unregister = { unregister(tcpControl, backToClient, tcpControlAddress) },
            )
        val pipeline = ch.pipeline()
        pipeline.addLast(handler)
      }

  override fun claimExistingLease(known: UdpInfo, key: InetSocketAddress): Boolean {
    return known.tcpControl.compareAndSet(null, key)
  }

  override fun createNewSocket(key: InetSocketAddress): UdpInfo {
    val tcpControl = AtomicSocketAddressHolder.create(key)
    val backToClient = AtomicSocketAddressHolder.create()

    val socket =
        newSocket(
            tcpControlAddress = key,
            tcpControl = tcpControl,
            backToClient = backToClient,
        )

    return UdpInfo(
        socket = socket,
        tcpControl = tcpControl,
        backToClient = backToClient,
    )
  }

  @ConsistentCopyVisibility
  internal data class UdpInfo
  internal constructor(
      override val socket: ChannelFuture,
      val tcpControl: MutableSocketAddressHolder,
      val backToClient: MutableSocketAddressHolder,
  ) : SocketPooler.Entry
}
