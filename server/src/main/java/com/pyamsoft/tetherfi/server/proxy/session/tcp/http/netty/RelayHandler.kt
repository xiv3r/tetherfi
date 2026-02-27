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

package com.pyamsoft.tetherfi.server.proxy.session.tcp.http.netty

import com.pyamsoft.tetherfi.core.Timber
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter

internal class RelayHandler
internal constructor(
    private val hostName: String,
    private val port: Int,
    private val clientChannel: Channel,
) : ChannelInboundHandlerAdapter() {

  private fun flushAndClose(channel: Channel) {
    if (channel.isActive) {
      channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
    }
  }

  override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
    if (!clientChannel.isActive) {
      return
    }

    if (msg is ByteBuf) {
      val byteCount = msg.readableBytes().toLong()
      // TODO Record amount consumed
      //      Timber.d { "(${hostName}:${port}) Read $byteCount bytes" }
      // TODO bandwidth limit enforcement
    }

    clientChannel.writeAndFlush(msg)
  }

  override fun channelWritabilityChanged(ctx: ChannelHandlerContext) {
    try {
      val isWritable = ctx.channel().isWritable
      Timber.d { "(${hostName}:${port}) Relay write changed: $ctx $isWritable" }
      clientChannel.config().isAutoRead = isWritable
    } finally {
      ctx.fireChannelWritabilityChanged()
    }
  }

  override fun channelInactive(ctx: ChannelHandlerContext) {
    Timber.d { "(${hostName}:${port}) Close inactive relay channel: $ctx" }
    flushAndClose(ctx.channel())
    flushAndClose(clientChannel)
  }

  override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
    Timber.e(cause) { "(${hostName}:${port}) RelayChannel exception caught $ctx" }
    flushAndClose(ctx.channel())
  }
}
