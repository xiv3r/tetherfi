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

package com.pyamsoft.tetherfi.server.proxy.session.netty.handler.pool

import androidx.annotation.CheckResult
import com.pyamsoft.tetherfi.server.proxy.session.netty.handler.flushAndClose
import java.util.concurrent.ConcurrentHashMap

internal abstract class AbstractSocketPooler<Key : Any, KnownSocket : SocketPooler.Entry>
protected constructor() : SocketPooler<Key> {

  private val knownSockets: MutableSet<KnownSocket> = ConcurrentHashMap.newKeySet()

  override fun register(key: Key): SocketPooler.Lease {
    var existing: KnownSocket? = null
    for (known in knownSockets) {
      if (claimExistingLease(known, key)) {
        // We took over this socket
        existing = known
        break
      }
    }

    val known = existing ?: createNewSocket(key).also { knownSockets.add(it) }
    return object : SocketPooler.Lease, SocketPooler.Entry by known {

      override fun unregister() {}
    }
  }

  @CheckResult protected abstract fun claimExistingLease(known: KnownSocket, key: Key): Boolean

  @CheckResult protected abstract fun createNewSocket(key: Key): KnownSocket

  final override fun close() {
    knownSockets.forEach { it.socket.flushAndClose() }
    knownSockets.clear()
  }
}
