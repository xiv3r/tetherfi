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
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

@JvmInline
internal value class AtomicSocketAddressHolder private constructor(
  private val atomic: AtomicReference<InetSocketAddress> = AtomicReference(null),
) : MutableSocketAddressHolder {

  override fun set(address: InetSocketAddress?) {
    atomic.set(address)
  }

  override fun get(): InetSocketAddress? {
    return atomic.get()
  }

  override fun compareAndSet(expected: InetSocketAddress?, update: InetSocketAddress?): Boolean {
    return atomic.compareAndSet(expected, update)
  }

  companion object {

    @JvmStatic
    @CheckResult
    fun create(): MutableSocketAddressHolder {
      return AtomicSocketAddressHolder()
    }

  }
}