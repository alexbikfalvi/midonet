/*
 * Copyright 2016 Midokura SARL
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

package org.midonet.benchmark

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.{FileSystems, StandardOpenOption}
import java.util.concurrent.{Executors, TimeUnit}

import scala.util.control.NonFatal

import org.midonet.benchmark.BenchmarkWriter.StateTableMetric
import org.midonet.util.functors._

class FileBenchmarkWriter(fileName: String) extends BenchmarkWriter {

    private val path = FileSystems.getDefault.getPath(fileName)
    private val channel = FileChannel.open(
        path, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
        StandardOpenOption.TRUNCATE_EXISTING)
    val executor = Executors.newSingleThreadExecutor()

    override def latency(metric: StateTableMetric): Unit = {
        executor.submit(makeRunnable {
            val buffer = ByteBuffer.allocate(52)
            buffer.putLong(metric.time)
            buffer.putInt(metric.operation)
            buffer.putLong(metric.key)
            buffer.putLong(metric.value)
            buffer.putLong(metric.callbackLatency)
            buffer.putLong(metric.storageLatency)
            buffer.putLong(metric.proxyLatency)
            buffer.rewind()
            try channel.write(buffer)
            catch { case NonFatal(e) => }
        })
    }

    override def close(): Unit = {
        executor.shutdown()
        while (!executor.awaitTermination(600000, TimeUnit.MILLISECONDS)) {
            System.err.println(s"[bm-writer] Closing the file writer " +
                               s"$fileName timed out after 10 minutes")
        }
        channel.close()
    }
}
