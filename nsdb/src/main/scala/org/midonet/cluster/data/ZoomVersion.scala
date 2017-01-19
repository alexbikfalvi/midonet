/*
 * Copyright 2017 Midokura SARL
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.midonet.cluster.data

object ZoomVersion {

    object ZoomOwner extends Enumeration {
        type ZoomOwner = Value
        val None = Value(0)
        val ClusterApi = Value(0x101)
        val ClusterNeutron = Value(0x102)
        val ClusterContainers = Value(0x103)
        val AgentBinding = Value(0x201)
        val AgentHaProxy = Value(0x202)
    }

    object ZoomChange extends Enumeration {
        type ZoomChange = Value
        val Direct = Value(0x1)
        val Backreference = Value(0x2)
    }

    final val Size = 8

    def writeTo(data: Array[Byte], version: Int, owner: ZoomOwner.ZoomOwner,
                change: Int): Unit = {
        data(0) = ((version >>> 24) & 0xFF).toByte
        data(1) = ((version >>> 16) & 0xFF).toByte
        data(2) = ((version >>> 8) & 0xFF).toByte
        data(3) = (version & 0xFF).toByte
        data(4) = ((owner.id >>> 8) & 0xFF).toByte
        data(5) = (owner.id & 0xFF).toByte
        data(6) = ((change >>> 8) & 0xFF).toByte
        data(7) = (change & 0xFF).toByte
    }
}
