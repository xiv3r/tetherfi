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

package com.pyamsoft.tetherfi.server.proxy.session.tcp.http.netty.http

import android.net.Network
import androidx.annotation.CheckResult
import com.pyamsoft.tetherfi.core.Timber
import com.pyamsoft.tetherfi.server.proxy.SocketTagger
import com.pyamsoft.tetherfi.server.proxy.session.tcp.http.netty.NettyProxy
import com.pyamsoft.tetherfi.server.proxy.session.tcp.http.netty.NetworkBoundChannelFactory
import com.pyamsoft.tetherfi.server.proxy.session.tcp.http.netty.RelayHandler
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.timeout.IdleStateEvent
import io.netty.handler.timeout.IdleStateHandler
import io.netty.util.ReferenceCountUtil
import java.net.URI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update

class NettyHttpProxy
internal constructor(
    host: String,
    port: Int,
    onOpened: () -> Unit,
    onClosing: () -> Unit,
    onError: (Throwable) -> Unit,
    private val isDebug: Boolean,
    private val socketTagger: SocketTagger,
    private val androidPreferredNetwork: Network?,
) :
    NettyProxy(
        socketTagger = socketTagger,
        host = host,
        port = port,
        onOpened = onOpened,
        onClosing = onClosing,
        onError = onError,
    ) {

  override fun onChannelInitialized(channel: SocketChannel) {
    val pipeline = channel.pipeline()

    if (isDebug) {
      pipeline.addLast(LoggingHandler(LogLevel.DEBUG))
    }

    // We want our pipeline to deal with HTTP
    pipeline.addLast(HttpServerCodec())

    // And bind our proxy relay handler
    pipeline.addLast(
        Http1ProxyHandler(
            isDebug = isDebug,
            socketTagger = socketTagger,
            androidPreferredNetwork = androidPreferredNetwork,
        )
    )
  }
}

private data class HostAndPort(
    val uri: URI,
    val resolvedHostName: String,
    val resolvedPort: Int,
    val proxyCorrectedFilePath: String,
)

private class Http1ProxyHandler(
    private val socketTagger: SocketTagger,
    private val androidPreferredNetwork: Network?,
    private val isDebug: Boolean,
) : ChannelInboundHandlerAdapter() {

  private val messageQueue = MutableStateFlow<List<Any>>(emptyList())

  private var outboundChannel: Channel? = null

  private fun assignOutboundChannel(channel: Channel) {
    outboundChannel?.let { old ->
      Timber.d { "Re-assigning outbound channel $old -> $channel" }
      if (old.isActive) {
        Timber.d { "Close old outbound channel $old" }
        old.close()
      }
    }

    outboundChannel = channel
  }

  private fun sendErrorAndClose(ctx: ChannelHandlerContext) {
    val response =
        DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.BAD_GATEWAY,
            Unpooled.EMPTY_BUFFER,
        )
    ctx.writeAndFlush(response).addListener { closeChannels(ctx) }
  }

  private fun closeChannels(ctx: ChannelHandlerContext) {
    Timber.d { "close outbound channel" }
    outboundChannel?.close()

    Timber.d { "close owner channel" }
    ctx.close()
  }

  override fun channelRegistered(ctx: ChannelHandlerContext) {
    Timber.d { "Add idle timeout handler" }
    ctx.pipeline().addFirst("idle", IdleStateHandler(60, 60, 60))
  }

  override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
    if (evt is IdleStateEvent) {
      Timber.d { "Closing idle connection: $ctx $evt" }
      closeChannels(ctx)
    }
  }

  override fun channelWritabilityChanged(ctx: ChannelHandlerContext) {
    try {
      val isWritable = ctx.channel().isWritable
      Timber.d { "Owner write changed: $ctx $isWritable" }
      outboundChannel?.config()?.isAutoRead = isWritable
    } finally {
      ctx.fireChannelWritabilityChanged()
    }
  }

  override fun channelInactive(ctx: ChannelHandlerContext) {
    Timber.d { "Close inactive outbound channels: $ctx" }
    closeChannels(ctx)
  }

  override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
    try {
      Timber.e(cause) { "ProxyServer exception caught $ctx" }
    } finally {
      closeChannels(ctx)
    }
  }

  @CheckResult
  private fun newOutboundConnection(
      channel: Channel,
      hostName: String,
      port: Int,
      onChannelOpened: (Channel) -> Unit = {},
  ): ChannelFuture {
    return Bootstrap()
        .group(channel.eventLoop())
        .channelFactory(
            NetworkBoundChannelFactory(
                socketTagger = socketTagger,
                androidPreferredNetwork = androidPreferredNetwork,
            )
        )
        .handler(
            object : ChannelInitializer<Channel>() {
              override fun initChannel(ch: Channel) {
                val pipeline = ch.pipeline()
                if (isDebug) {
                  pipeline.addLast(LoggingHandler(LogLevel.DEBUG))
                }

                onChannelOpened(ch)

                // Once our connection to the internet is made, relay data in tunnel
                pipeline.addLast(RelayHandler(hostName, port, channel))
              }
            }
        )
        .connect(hostName, port)
  }

  private fun handleHttpsConnect(ctx: ChannelHandlerContext, msg: HttpRequest) {
    val parsed = parseUriAndPort(msg.uri(), 443)
    val clientChannel = ctx.channel()

    val future = newOutboundConnection(clientChannel, parsed.resolvedHostName, parsed.resolvedPort)
    val outbound = future.channel()
    future.addListener { future ->
      if (!future.isSuccess) {
        sendErrorAndClose(ctx)
        return@addListener
      }

      // Tell proxy we've established connection
      val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)

      Timber.d { "Write and flush CONNECT: $response" }
      ctx.writeAndFlush(response)

      // Enable auto-read once connection is established
      clientChannel.config().isAutoRead = true

      // Hold onto this channel for future requests to immediately fire off to it
      assignOutboundChannel(outbound)

      // And then replay any previously seen messages that arrived BEFORE we were set up
      // any future messages will go directly to the outbound now that the channel is held
      replayQueuedMessages(outbound)

      // Drop down to raw TCP
      val pipeline = ctx.pipeline()
      // Remove the http server codec
      if (pipeline.get(HttpServerCodec::class.java) != null) {
        pipeline.remove(HttpServerCodec::class.java)
      }

      // Remove our own handler
      if (pipeline.get(this::class.java) != null) {
        pipeline.remove(this::class.java)
      }

      // Add a relay for the internet outbound
      pipeline.addLast(RelayHandler(parsed.resolvedHostName, parsed.resolvedPort, outbound))
    }
  }

  private fun handleHttpForward(ctx: ChannelHandlerContext, msg: HttpRequest) {
    val parsed = parseUriAndPort(msg.uri(), 80)
    val clientChannel = ctx.channel()

    val future =
        newOutboundConnection(clientChannel, parsed.resolvedHostName, parsed.resolvedPort) { ch ->
          // Our outbound client MUST speak HTTP
          ch.pipeline().addLast(HttpClientCodec())
        }

    val outbound = future.channel()
    future.addListener { future ->
      if (!future.isSuccess) {
        sendErrorAndClose(ctx)
        return@addListener
      }

      // Adjust the URL to be relative to the new host
      msg.uri = parsed.proxyCorrectedFilePath

      // Replay the initial request
      outbound.writeAndFlush(msg)

      // Enable auto-read once connection is established
      clientChannel.config().isAutoRead = true

      // Hold onto this channel for future requests to immediately fire off to it
      assignOutboundChannel(outbound)

      // And then replay any previously seen messages that arrived BEFORE we were set up
      // any future messages will go directly to the outbound now that the channel is held
      replayQueuedMessages(outbound)

      // Drop down to raw TCP
      val pipeline = ctx.pipeline()
      // Remove the http server codec
      if (pipeline.get(HttpClientCodec::class.java) != null) {
        pipeline.remove(HttpClientCodec::class.java)
      }

      // Remove our own handler
      if (pipeline.get(this::class.java) != null) {
        pipeline.remove(this::class.java)
      }

      // Add a relay for the internet outbound
      pipeline.addLast(RelayHandler(parsed.resolvedHostName, parsed.resolvedPort, outbound))
    }
  }

  private fun replayQueuedMessages(channel: Channel) {
    var needsFlush = false
    try {
      val queued = messageQueue.getAndUpdate { emptyList() }
      needsFlush = queued.isNotEmpty()
      if (needsFlush) {
        for (q in queued) {
          channel.write(q)
        }
      }
    } finally {
      if (needsFlush) {
        channel.flush()
      }
    }
  }

  override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
    try {
      if (msg is HttpRequest) {
        if (msg.method() == HttpMethod.CONNECT) {
          handleHttpsConnect(ctx, msg)
        } else {
          handleHttpForward(ctx, msg)
        }
      } else if (msg is HttpContent) {
        val outbound = outboundChannel
        if (outbound == null) {
          messageQueue.update { it + msg }
        } else {
          outbound.writeAndFlush(msg)
        }
      } else {
        Timber.w { "MSG was not HTTP based: $msg" }
        sendErrorAndClose(ctx)
      }
    } finally {
      ReferenceCountUtil.release(msg)
    }
  }

  companion object {

    @CheckResult
    private fun parseUriAndPort(uri: String, defaultPort: Int): HostAndPort {
      val parsed = URI(uri)
      val port = if (parsed.port <= 0) defaultPort else parsed.port

      // Adjust the file path relative to the proxy host
      // For example if the request is http://example.com/
      // and the host is http://example.com
      //
      // The correct upstream file path expected is
      // GET / HTTP1.1
      // Host: http://example.com
      var query = parsed.rawQuery.orEmpty()
      query = if (query.isNotBlank()) "?${query}" else ""
      val proxyCorrectedFilePath = "${parsed.rawPath}${query}".ifBlank { "/" }

      return HostAndPort(
          uri = parsed,
          resolvedHostName = parsed.host,
          resolvedPort = port,
          proxyCorrectedFilePath = proxyCorrectedFilePath,
      )
    }
  }
}
