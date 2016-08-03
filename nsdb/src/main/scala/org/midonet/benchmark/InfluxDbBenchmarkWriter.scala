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

import java.util.concurrent.{Executors, TimeUnit}

import scala.util.control.NonFatal

import org.influxdb.InfluxDBFactory
import org.influxdb.dto.Point

import org.midonet.benchmark.BenchmarkWriter.StateTableMetric
import org.midonet.util.functors._

class InfluxDbBenchmarkWriter(url: String, user: String, password: String,
                              database: String)
    extends BenchmarkWriter {

    val db = InfluxDBFactory.connect(url, user, password)
    db.enableBatch(1000, 100, TimeUnit.MILLISECONDS)

    val executor = Executors.newSingleThreadExecutor()

    override def latency(metric: StateTableMetric): Unit = {
        executor.submit(makeRunnable {
            val point = Point.measurement("latency")
                .time(metric.time, TimeUnit.MILLISECONDS)
                .addField("callback", metric.callbackLatency)
                .addField("storage", metric.storageLatency)
                .addField("proxy", metric.proxyLatency)
                .build()
            try db.write(database, "default", point)
            catch { case NonFatal(_) => }
        })
    }

    def proxyTableSize(value: Long): Unit = {
        executor.submit(makeRunnable {
            val point = Point.measurement("proxy")
                .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                .addField("table-size", value)
                .build()
            try db.write(database, "default", point)
            catch { case NonFatal(_) => }
        })
    }

    def proxyRoundTripLatency(time: Long, value: Long): Unit = {
        executor.submit(makeRunnable {
            val point = Point.measurement("proxy")
                .time(time, TimeUnit.MILLISECONDS)
                .addField("round-trip-latency", value)
                .build()
            try db.write(database, "default", point)
            catch { case NonFatal(_) => }
        })
    }

    def proxyReadLatency(time: Long, value: Long): Unit = {
        executor.submit(makeRunnable {
            val point = Point.measurement("proxy")
                .time(time, TimeUnit.MILLISECONDS)
                .addField("read-latency", value)
                .build()
            try db.write(database, "default", point)
            catch { case NonFatal(_) => }
        })
    }

    def proxyNotifyLatency(time: Long, value: Long): Unit = {
        executor.submit(makeRunnable {
            val point = Point.measurement("proxy")
                .time(time, TimeUnit.MILLISECONDS)
                .addField("notify-latency", value)
                .build()
            try db.write(database, "default", point)
            catch { case NonFatal(_) => }
        })
    }

    def proxyProcessLatency(start: Long, queue: Long, end: Long): Unit = {
        executor.submit(makeRunnable {
            val point = Point.measurement("proxy")
                .time(end, TimeUnit.MILLISECONDS)
                .addField("process-queue-latency", queue - start)
                .addField("process-execute-latency", end - queue)
                .addField("process-total-latency", end - start)
                .build()
            try db.write(database, "default", point)
            catch { case NonFatal(_) => }
        })
    }

    def proxyNotify(queueDelay: Long, updateCount: Int): Unit = {
        executor.submit(makeRunnable {
            val point = Point.measurement("proxy")
                .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                .addField("notify-queue-delay", queueDelay)
                .addField("notify-update-count", updateCount)
                .build()
            try db.write(database, "default", point)
            catch { case NonFatal(_) => }
        })
    }

    def proxyRefreshRequest(value: Long): Unit = {
        executor.submit(makeRunnable {
            val point = Point.measurement("proxy")
                .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                .addField("refresh-request", value)
                .build()
            try db.write(database, "default", point)
            catch { case NonFatal(_) => }
        })
    }

    def proxyRefreshComplete(count: Long, latency: Long): Unit = {
        executor.submit(makeRunnable {
            val point = Point.measurement("proxy")
                .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                .addField("refresh-complete", count)
                .addField("refresh-latency", latency)
                .build()
            try db.write(database, "default", point)
            catch { case NonFatal(_) => }
        })
    }

    def proxyNettyLatency(value: Long): Unit = {
        executor.submit(makeRunnable {
            val point = Point.measurement("proxy")
                .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                .addField("netty-latency", value)
                .build()
            try db.write(database, "default", point)
            catch { case NonFatal(_) => }
        })
    }

    def proxySendQueueLatency(value: Long): Unit = {
        executor.submit(makeRunnable {
            val point = Point.measurement("proxy")
                .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                .addField("send-queue-latency", value)
                .build()
            try db.write(database, "default", point)
            catch { case NonFatal(_) => }
        })
    }

    def proxyQueuedTasks(total: Long, send: Long, callback: Long, process: Long,
                         refresh: Long): Unit = {
        executor.submit(makeRunnable {
            val point = Point.measurement("proxy")
                .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                .addField("queue-size", total)
                .addField("queue-send-size", send)
                .addField("queue-callback-size", callback)
                .addField("queue-process-size", process)
                .addField("queue-refresh-size", refresh)
                .build()
            try db.write(database, "default", point)
            catch { case NonFatal(_) => }
        })
    }

    override def close(): Unit = {
        executor.shutdown()
        while (!executor.awaitTermination(600000, TimeUnit.MILLISECONDS)) {
            System.err.println(s"[bm-writer] Closing the InfluxDB writer " +
                               s"$url timed out after 10 minutes")
        }
    }
}
