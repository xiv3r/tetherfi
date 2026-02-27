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

package com.pyamsoft.tetherfi.server.proxy.session.tcp.http

import android.net.Network
import androidx.annotation.CheckResult
import com.pyamsoft.tetherfi.core.Timber
import com.pyamsoft.tetherfi.server.proxy.SocketTagger
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFactory
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.MultiThreadIoEventLoopGroup
import io.netty.channel.nio.NioIoHandler
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

fun interface NettyServerStopper {

  fun stop()
}

/** Run this with a completely new [com.pyamsoft.tetherfi.server.proxy.manager.ProxyManager] */
class SuspendingNettyProxy(
  host: String,
  port: Int,
  socketTagger: SocketTagger,
  androidPreferredNetwork: Network?,
  onOpened: () -> Unit,
  onClosing: () -> Unit,
  onError: (Throwable) -> Unit,
) {

  private val proxy by lazy {
    NettyHttpProxy(
      host = host,
      port = port,
      socketTagger = socketTagger,
      androidPreferredNetwork = androidPreferredNetwork,
      onOpened = onOpened,
      onClosing = onClosing,
      onError = onError,
    )
  }

  suspend fun start() {
    var stopper: NettyServerStopper? = null
    try {
      stopper = proxy.start()
      awaitCancellation()
    } finally {
      stopper?.also { s -> withContext(context = NonCancellable) { s.stop() } }
    }
  }
}

class NettyHttpProxy(
  host: String,
  port: Int,
  onOpened: () -> Unit,
  onClosing: () -> Unit,
  onError: (Throwable) -> Unit,
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

    // We want our pipeline to deal with HTTP
    pipeline.addLast(HttpServerCodec())

    // And bind our proxy relay handler
    pipeline.addLast(HttpProxyHandlder(socketTagger = socketTagger, androidPreferredNetwork = androidPreferredNetwork))
  }
}

abstract class NettyProxy(
  private val socketTagger: SocketTagger,
  private val host: String,
  private val port: Int,
  private val onOpened: () -> Unit,
  private val onClosing: () -> Unit,
  private val onError: (Throwable) -> Unit,
) {

  @CheckResult
  fun start(): NettyServerStopper {
    val bossGroup = MultiThreadIoEventLoopGroup(NioIoHandler.newFactory())
    val workerGroup = MultiThreadIoEventLoopGroup(NioIoHandler.newFactory())

    val bootstrap =
      ServerBootstrap()
        .group(bossGroup, workerGroup)
        .channel(NioServerSocketChannel::class.java)
        .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
        .childHandler(
          object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(ch: SocketChannel) {
              ch.pipeline().addLast(LoggingHandler(LogLevel.DEBUG))
              onChannelInitialized(ch)
            }
          }
        )

    // Tag the server socket
    socketTagger.tagSocket()

    val serverChannel =
      bootstrap
        .bind(host, port)
        .apply {
          addListener { future ->
            if (future.isSuccess) {
              Timber.d { "Netty server started" }
              onOpened()
            } else {
              val err = future.cause()
              Timber.e(err) { "Failed to bind netty server" }
              onError(err)
            }
          }
        }
        .channel()
        .apply {
          closeFuture().addListener {
            Timber.d { "Netty server is closing!" }
            onClosing()
          }
        }

    return {
      Timber.d { "Stopping Netty server gracefully" }
      serverChannel.close()
      bossGroup.shutdownGracefully()
      workerGroup.shutdownGracefully()
    }
  }

  protected abstract fun onChannelInitialized(channel: SocketChannel)
}

class HttpProxyHandlder(private val socketTagger: SocketTagger, private val androidPreferredNetwork: Network?) :
  ChannelInboundHandlerAdapter() {

  private val messageQueue = MutableStateFlow<List<Any>>(emptyList())

  private var outboundChannel: Channel? = null

  private data class HostAndPort(val host: String, val port: Int)

  @CheckResult
  private fun parseHostAndPort(uri: String, defaultPort: Int = 80): HostAndPort {
    val hostPort = uri.split(":")
    val hostname = hostPort[0]
    val port = hostPort.getOrNull(1)?.toIntOrNull() ?: defaultPort
    return HostAndPort(hostname, port)
  }

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
    val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_GATEWAY, Unpooled.EMPTY_BUFFER)
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
        NetworkBoundChannelFactory(socketTagger = socketTagger, androidPreferredNetwork = androidPreferredNetwork)
      )
      .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
      .handler(
        object : ChannelInitializer<Channel>() {
          override fun initChannel(ch: Channel) {
            onChannelOpened(ch)

            val pipeline = ch.pipeline()
            pipeline.addLast(LoggingHandler(LogLevel.DEBUG))

            // Once our connection to the internet is made, relay data in tunnel
            pipeline.addLast(RelayHandler(hostName, port, channel))
          }
        }
      )
      .connect(hostName, port)
  }

  private fun handleHttpsConnect(ctx: ChannelHandlerContext, msg: HttpRequest) {
    val parsed = parseHostAndPort(msg.uri())
    val clientChannel = ctx.channel()

    val future = newOutboundConnection(clientChannel, parsed.host, parsed.port)
    val outbound = future.channel()
    future.addListener { future ->
      if (!future.isSuccess) {
        sendErrorAndClose(ctx)
        return@addListener
      }

      // Enable auto-read once connection is established
      clientChannel.config().isAutoRead = true

      assignOutboundChannel(outbound)

      // Tell proxy we've established connection
      val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
      // Must flush here or the connection does not complete
      Timber.d { "Write and flush CONNECT: $response" }
      ctx.writeAndFlush(response)

      // And then tell the client its "the end"
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
      pipeline.addLast(RelayHandler(parsed.host, parsed.port, outbound))
    }
  }

  private fun handleHttpForward(ctx: ChannelHandlerContext, msg: HttpRequest) {
    val uri = URI(msg.uri())
    val port = if (uri.port <= 0) 80 else uri.port

    val hostname = "${uri.host}"
    val clientChannel = ctx.channel()

    val future = newOutboundConnection(clientChannel, hostname, port) { it.pipeline().addLast(HttpClientCodec()) }
    val outbound = future.channel()
    future.addListener { future ->
      if (!future.isSuccess) {
        sendErrorAndClose(ctx)
        return@addListener
      }

      // Enable auto-read once connection is established
      clientChannel.config().isAutoRead = true

      assignOutboundChannel(outbound)

      // Adjust the URL to be relative to the new host
      var query = uri.rawQuery.orEmpty()
      query = if (query.isNotBlank()) "?${query}" else ""

      val newUri = "${uri.rawPath}${query}".ifBlank { "/" }
      msg.uri = newUri

      outbound.writeAndFlush(msg)

      // And then tell the client its "the end"
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
      pipeline.addLast(RelayHandler(hostname, port, outbound))
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
        Timber.w { "MSG was not http content: $msg" }
        sendErrorAndClose(ctx)
      }
    } finally {
      ReferenceCountUtil.release(msg)
    }
  }
}

class NetworkBoundChannelFactory(
  private val socketTagger: SocketTagger,
  private val androidPreferredNetwork: Network?,
) : ChannelFactory<NioSocketChannel> {

  override fun newChannel(): NioSocketChannel {
    socketTagger.tagSocket()

    val outboundSocketChannel = java.nio.channels.SocketChannel.open().apply { configureBlocking(false) }
    val socket = outboundSocketChannel.socket()

    androidPreferredNetwork?.bindSocket(socket)

    return NioSocketChannel(outboundSocketChannel)
  }
}

class RelayHandler(private val hostName: String, private val port: Int, private val clientChannel: Channel) :
  ChannelInboundHandlerAdapter() {

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