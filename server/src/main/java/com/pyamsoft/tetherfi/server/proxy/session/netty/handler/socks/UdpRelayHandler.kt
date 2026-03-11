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

import com.pyamsoft.tetherfi.core.Timber
import com.pyamsoft.tetherfi.server.proxy.session.netty.handler.ProxyHandler
import io.ktor.util.network.address
import io.ktor.util.network.port
import io.netty.buffer.ByteBufUtil
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.socket.DatagramPacket
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse
import io.netty.handler.codec.socksx.v5.Socks5AddressType
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus
import java.net.InetSocketAddress

internal class UdpRelayHandler
internal constructor() : ProxyHandler() {

  private var clientAddress: InetSocketAddress? = null

  private fun unwrapUdpResponse(
    ctx: ChannelHandlerContext,
    msg: DatagramPacket,
  ) {
    val serverChannel = ctx.channel()
    UDP.unwrap(
      channelId = channelId,
      msg = msg,
      executor = ctx.executor(),
      onError = {
        sendErrorAndClose(ctx, msg)
      },
      onUnwrapped = { data, destination ->
        Timber.d { "Go upstream ${serverChannel.localAddress()} -> $destination" }
        val packet = DatagramPacket(data, destination)
        ctx.writeAndFlush(packet).addListener { packet.release() }
      }
    )
  }

  override fun channelActive(ctx: ChannelHandlerContext) {
    try {
      val addr = ctx.channel().localAddress()
      setChannelId("UDP-RELAY-${addr.address}:${addr.port}")
    } finally {
      super.channelActive(ctx)
    }
  }

  override fun sendErrorAndClose(ctx: ChannelHandlerContext, msg: Any) {
    // Write a "0" response back to the UDP control channel
    val response =
      DefaultSocks5CommandResponse(
        Socks5CommandStatus.FAILURE,
        Socks5AddressType.IPv4,
        "0.0.0.0",
        0,
      )

    ctx.writeAndFlush(response).addListener { closeChannels(ctx) }
  }

  override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
    if (msg is DatagramPacket) {
      val sender = msg.sender()
      if (sender == null) {
        Timber.w { "(${channelId}) DROP: Null sender in packet" }
        sendErrorAndClose(ctx, msg)
        return
      }

      if (clientAddress == null) {
        clientAddress = sender
      }

      val client = clientAddress
      if (client == null || client == sender) {
        clientAddress = sender
        unwrapUdpResponse(ctx, msg)
      } else {
        val content = msg.retain().content()
        val response = UDP.wrap(alloc = ctx.alloc(), sender = sender, content = content)

        Timber.d { "Back to client: $client -> ${ByteBufUtil.hexDump(response)}" }
        val packet = DatagramPacket(response, client)
        ctx.writeAndFlush(packet).addListener { msg.release() }
      }

    } else {
      Timber.w { "(${channelId}): Invalid message seen: $msg" }
      super.channelRead(ctx, msg)
    }
  }
}