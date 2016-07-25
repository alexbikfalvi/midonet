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

import java.util
import java.util.UUID

import scala.reflect.ClassTag
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

import com.codahale.metrics.MetricRegistry
import com.typesafe.config.{ConfigFactory, ConfigRenderOptions}

import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.zookeeper.KeeperException
import org.rogach.scallop.{ScallopConf, Subcommand}

import rx.Observer

import org.midonet.benchmark.tables._
import org.midonet.cluster.data.storage.StateTable.Update
import org.midonet.cluster.data.storage.model.ArpEntry
import org.midonet.cluster.data.storage.{StateTable, StateTableStorage, ZookeeperObjectMapper}
import org.midonet.cluster.models.Topology.{Network, Port, Router}
import org.midonet.cluster.services.{MidonetBackend, MidonetBackendService}
import org.midonet.cluster.storage.MidonetBackendConfig
import org.midonet.cluster.util.UUIDUtil._
import org.midonet.conf.HostIdGenerator.PropertiesFileNotWritableException
import org.midonet.conf.MidoNodeConfigurator
import org.midonet.packets.{IPv4Addr, MAC}
import org.midonet.util.concurrent._

object StateBenchmarkAgent extends App {

    private val Random = new scala.util.Random()

    System.setProperty("logback.configurationFile", "logback-disabled.xml")

    private val SuccessCode = 0
    private val NoArgumentsErrorCode = 1
    private val PropertiesFileErrorCode = 2
    private val ZooKeeperErrorCode = 3
    private val OtherErrorCode = 4
    private val BenchmarkFailedErrorCode = 5

    private abstract class TableInfo[K,V](val objectClass: Class[_],
                                          val keyClass: Class[K],
                                          val valueClass: Class[V],
                                          val name: String,
                                          val tableClass: Class[_ <: StateTable[K,V]]) {
        def newObject(id: UUID): AnyRef
        def randomKey(): Long
        def randomValue(): Long
        def encodeKey(key: Long): K
        def decodeKey(key: K): Long
        def encodeValue(value: Long): V
        def decodeValue(value: V): Long
    }

    private case class TableOp(table: Int, key: Long, oldValue: Long,
                               newValue: Long, timestamp: Long, op: Byte)

    private case class Entry(key: Long, value: Long,
                             encodedKey: Any, encodedValue: Any,
                             timestamp: Long)

    private class Table(info: TableInfo[Any,Any], index: Int,
                        inner: StateTable[Any, Any])
        extends Observer[Update[Any, Any]] {

        private val entries = new util.HashMap[Long, Entry]

        inner.start()
        val subscription = inner.observable.subscribe(this)

        override def onNext(update: Update[Any, Any]): Unit = update match {
            case Update(k, null, v) =>
                val key = info.decodeKey(k)
                val value = info.decodeValue(v)
            case Update(k, v, null) =>
                val key = info.decodeKey(k)
                val value = info.decodeValue(v)
            case Update(k, ov, nv) =>
                val key = info.decodeKey(k)
                val oldValue = info.decodeValue(ov)
                val newValue = info.decodeValue(nv)
        }

        override def onError(e: Throwable): Unit = {
            System.err.println(s"[bm-agent] Table $index observable failed: " +
                               e.getMessage)
        }

        override def onCompleted(): Unit = {
            System.err.println(s"[bm-agent] Table $index observable completed")
        }

        def close(): Unit = {
            subscription.unsubscribe()
            inner.stop()
        }

        def count = entries.size()

        def add(): Unit = {
            while (true) {
                val key = info.randomKey()
                val value = info.randomValue()
                val entry = Entry(key, value, info.encodeKey(key),
                                  info.encodeValue(value),
                                  System.currentTimeMillis())
                if (!inner.containsLocal(entry.encodedKey)) {
                    entries.put(key, entry)
                    inner.add(entry.encodedKey, entry.encodedValue)
                    return
                }
            }
        }

        def update(): Unit = {
            val oldEntry = randomEntry()
            if (oldEntry eq null) {
                return
            }
            val value = info.randomValue()
            val newEntry = Entry(oldEntry.key, value,
                                 info.encodeKey(oldEntry.key),
                                 info.encodeValue(value),
                                 System.currentTimeMillis())
            entries.put(newEntry.key, newEntry)
            inner.add(newEntry.encodedKey, newEntry.encodedValue)
        }

        def remove(): Unit = {
            val entry = randomEntry()
            if (entry eq null) {
                return
            }
            entries.remove(entry.key)
            inner.remove(entry.encodedKey, entry.encodedValue)
        }

        private def randomEntry(): Entry = {
            val random = Random.nextInt(entries.size())
            val iterator = entries.values().iterator()
            var index = 0
            while (iterator.hasNext) {
                val entry = iterator.next()
                if (index >= random) {
                    return entry
                }
                index += 1
            }
            null
        }
    }

    private val Tables = Map(
        "bridge-mac" -> new TableInfo(classOf[Network], classOf[MAC],
                                      classOf[UUID], MidonetBackend.MacTable,
                                      classOf[MacIdStateTable]) {
            override def newObject(id: UUID): AnyRef = {
                Network.newBuilder().setId(id.asProto).build()
            }
            override def randomKey(): Long = {
                Random.nextLong() & 0xFFFFFFFFFFFFL
            }
            override def randomValue(): Long = {
                Random.nextLong()
            }
            override def encodeKey(key: Long): MAC = {
                new MAC(key)
            }
            override def decodeKey(key: MAC): Long = {
                key.asLong()
            }
            override def encodeValue(value: Long): UUID = {
                new UUID(0L, value)
            }
            override def decodeValue(value: UUID): Long = {
                value.getLeastSignificantBits
            }
        },
        "bridge-arp" -> new TableInfo(classOf[Network], classOf[IPv4Addr],
                                      classOf[MAC], MidonetBackend.Ip4MacTable,
                                      classOf[Ip4MacStateTable]) {
            override def newObject(id: UUID): AnyRef = {
                Network.newBuilder().setId(id.asProto).build()
            }
            override def randomKey(): Long = {
                Random.nextLong() & 0xFFFFFFFFL
            }
            override def randomValue(): Long = {
                Random.nextLong() & 0xFFFFFFFFFFFFL
            }
            override def encodeKey(key: Long): IPv4Addr = {
                new IPv4Addr(key.toInt)
            }
            override def decodeKey(key: IPv4Addr): Long = {
                key.addr
            }
            override def encodeValue(value: Long): MAC = {
                new MAC(value)
            }
            override def decodeValue(value: MAC): Long = {
                value.asLong()
            }
        },
        "router-arp" -> new TableInfo(classOf[Router], classOf[IPv4Addr],
                                      classOf[ArpEntry], MidonetBackend.ArpTable,
                                      classOf[ArpStateTable]) {
            override def newObject(id: UUID): AnyRef = {
                Router.newBuilder().setId(id.asProto).build()
            }
            override def randomKey(): Long = {
                Random.nextLong() & 0xFFFFFFFFL
            }
            override def randomValue(): Long = {
                Random.nextLong()
            }
            override def encodeKey(key: Long): IPv4Addr = {
                new IPv4Addr(key.toInt)
            }
            override def decodeKey(key: IPv4Addr): Long = {
                key.addr
            }
            override def encodeValue(value: Long): ArpEntry = {
                new ArpEntry(new MAC(value), value, value, value)
            }
            override def decodeValue(value: ArpEntry): Long = {
                value.lastArp
            }
        },
        "router-peer" -> new TableInfo(classOf[Port], classOf[MAC],
                                       classOf[IPv4Addr], MidonetBackend.PeeringTable,
                                       classOf[MacIp4StateTable]) {
            override def newObject(id: UUID): AnyRef = {
                Port.newBuilder().setId(id.asProto).build()
            }
            override def randomKey(): Long = {
                Random.nextLong() & 0xFFFFFFFFFFFFL
            }
            override def randomValue(): Long = {
                Random.nextLong() & 0xFFFFFFFFL
            }
            override def encodeKey(key: Long): MAC = {
                new MAC(key)
            }
            override def decodeKey(key: MAC): Long = {
                key.asLong()
            }
            override def encodeValue(value: Long): IPv4Addr = {
                new IPv4Addr(value.toInt)
            }
            override def decodeValue(value: IPv4Addr): Long = {
                value.addr
            }
        })

    trait BenchmarkCommand {
        def run(configurator: MidoNodeConfigurator): Int
    }

    object Config extends Subcommand("config") with BenchmarkCommand {
        descr("Prints the current configuration")

        val renderOptions = ConfigRenderOptions.defaults()
            .setOriginComments(false)
            .setComments(true)
            .setJson(true)
            .setFormatted(true)

        override def run(configurator: MidoNodeConfigurator): Int = {
            println(configurator.dropSchema(configurator.runtimeConfig,
                                            showPasswords = true)
                                .root().render(renderOptions))
            SuccessCode
        }
    }

    object Simple extends Subcommand("simple") with BenchmarkCommand {
        descr("Simple benchmark where the benchmark writes at a given average " +
              "rate to a number of state tables, and reads the updates from " +
              "all tables. The write operations follow an exponential " +
              "distribution. The benchmark begins with a warm-up interval " +
              "during which the test adds an initial number of entries to " +
              "the table. Following the warm-up the test enters a steady state " +
              "interval during which the benchmark randomly chooses one of " +
              "the following operations: (i) updating an entry, (ii) removing " +
              "and adding a new entry. The specified benchmark duration " +
              "refers to the steady-state interval.")

        val table =
            opt[String]("table", short = 't', default = Some("bridge-mac"), descr =
                        "The state table class, it can be one of the following: " +
                        "bridge-mac, bridge-arp, router-arp, router-peer")
        val duration =
            opt[Int]("duration", short = 'd', default = Some(600), descr =
                     "The test duration in seconds")
        val tableCount =
            opt[Int]("table-count", short = 'n', default = Some(10), descr =
                     "The number of tables to which the benchmark writes")
        val entryCount =
            opt[Int]("entry-count", short = 'e', default = Some(100), descr =
                     "The initial number of entries added by the benchmark")
        val writeRate =
            opt[Int]("write-rate", short = 'w', default = Some(60), descr =
                     "The number of writes per minute to a table.")
        val output =
            opt[String]("output", short = 'o', default = Some("benchmark.out"), descr =
                "The output statistics data file.")
        val dbUrl =
            opt[String]("db-url", descr = "The InfluxDB database URL.")
        val dbUser =
            opt[String]("db-user", descr = "The InfluxDB database user name.")
        val dbPassword =
            opt[String]("db-password", descr = "The InfluxDB database password.")
        val dbName =
            opt[String]("db-name", descr = "The InfluxDB database name.")

        private var tables: Array[Table] = null

        override def run(configurator: MidoNodeConfigurator): Int = {
            println("Starting simple benchmark...")

            val config = new MidonetBackendConfig(configurator.runtimeConfig)
            val curator = CuratorFrameworkFactory.newClient(
                config.hosts,
                new ExponentialBackoffRetry(config.retryMs.toInt, config.maxRetries))
            val registry = new MetricRegistry
            val backend = new MidonetBackendService(config, curator, curator,
                                                    registry, None) {
                protected override def setup(storage: StateTableStorage): Unit = {
                    for (info <- Tables.values) {
                        storage.registerTable(
                            info.objectClass,
                            info.keyClass.asInstanceOf[Class[Object]],
                            info.valueClass.asInstanceOf[Class[Object]],
                            info.name,
                            info.tableClass.asInstanceOf[Class[StateTable[Object, Object]]])
                    }
                }
            }

            StateTableMetrics.writer =
                if (dbUrl.isDefined && dbUser.isDefined && dbPassword.isDefined &&
                    dbName.isDefined) {
                    new InfluxDbBenchmarkWriter(dbUrl.get.get,
                                                dbUser.get.get,
                                                dbPassword.get.get,
                                                dbName.get.get)
                } else {
                    new FileBenchmarkWriter(output.get.get)
                }

            try {
                backend.startAsync().awaitRunning()

                val zoom = backend.store.asInstanceOf[ZookeeperObjectMapper]

                pre(config, backend, zoom)
                warmUp(config, backend)
                steadyState(config, backend)
                post(config, backend)

                println("Simple benchmark completed successfully")
                SuccessCode
            } catch {
                case NonFatal(e) =>
                    System.err.println("[bm-agent] Simple benchmark failed: " +
                                       e.getMessage)
                    BenchmarkFailedErrorCode
            } finally {
                StateTableMetrics.writer.close()
                backend.stopAsync().awaitTerminated()
                curator.close()
            }
        }

        private def pre(config: MidonetBackendConfig,
                        backend: MidonetBackendService,
                        zoom: ZookeeperObjectMapper): Unit = {
            println("[Step 1 of 4] Creating objects and tables...")

            val count = tableCount.get.get
            val info = Tables.getOrElse(table.get.get,
                                        throw new IllegalArgumentException("No such table"))
                .asInstanceOf[TableInfo[Any, Any]]
            tables = new Array[Table](count)

            for (index <- 0 until count) {
                val id = new UUID(0L, index)
                if (!backend.store.exists(info.objectClass, id).await()) {
                    try backend.store.create(info.newObject(id))
                    catch { case NonFatal(e) => }
                }
                val table = backend.stateTableStore
                    .getTable(info.objectClass, new UUID(0L, index), info.name)(
                        ClassTag(info.keyClass), ClassTag(info.valueClass))
                tables(index) = new Table(info, index,
                                          table.asInstanceOf[StateTable[Any, Any]])
            }
        }

        private def warmUp(config: MidonetBackendConfig,
                           backend: MidonetBackendService): Unit = {
            val count = entryCount.get.get
            println(s"[Step 2 of 4] Warming up by adding $count entries " +
                    s"to ${tables.length} tables...")

            for (table <- tables) {
                for (index <- 0 until count) {
                    table.add()
                }
            }

            println("[Step 2 of 4] Warming up completed")
        }

        private def steadyState(config: MidonetBackendConfig,
                                backend: MidonetBackendService): Unit = {
            println("[Step 3 of 4] Steady state benchmark for " +
                    s"${duration.get.get} seconds...")

            val startTime = System.currentTimeMillis()
            val finishTime = startTime + duration.get.get * 1000
            val meanSleepTime = 60000 / (writeRate.get.get * tables.length)

            def sleepTime(): Long = {
                (-meanSleepTime * Math.log(1 - Random.nextDouble())).toLong
            }

            println(s"[Step 3 of 4] Average inter-op interval is $meanSleepTime " +
                    "milliseconds")
            var additions = 0
            var updates = 0
            var removals = 0
            while (System.currentTimeMillis() < finishTime) {
                Thread.sleep(sleepTime())
                Random.nextInt(3) match {
                    case 0 =>
                        tables(Random.nextInt(tables.length)).add()
                        additions += 1
                    case 1 =>
                        tables(Random.nextInt(tables.length)).update()
                        updates += 1
                    case 2 =>
                        tables(Random.nextInt(tables.length)).remove()
                        removals += 1
                }
            }

            println(s"[Step 3 of 4] Steady state completed with $additions " +
                    s"additions $updates updates $removals removals")
        }

        private def post(config: MidonetBackendConfig,
                         backend: MidonetBackendService): Unit = {
            Thread.sleep(10000)
            println("[Step 4 of 4] Cleaning up...")

            val info = Tables.getOrElse(table.get.get,
                                        throw new IllegalArgumentException("No such table"))
                .asInstanceOf[TableInfo[Any, Any]]

            for (index <- tables.indices) {
                tables(index).close()
                try backend.store.delete(info.objectClass, new UUID(0L, index))
                catch { case NonFatal(e) => }
            }
        }

    }

    val bootstrapConfig =
        ConfigFactory.parseString("zookeeper.bootstrap_timeout : 1s")
    val options = new ScallopConf(args) {
        val config = Config
        val simple = Simple

        printedName = "bm-agent"
        footer("Copyright (c) 2016 Midokura SARL, All Rights Reserved.")
    }

    def invalidException = {
        new Exception("invalid arguments, use --help for usage information")
    }

    val returnCode = options.subcommand map {
        case command: BenchmarkCommand =>
            Try(command.run(MidoNodeConfigurator(bootstrapConfig)))
        case _ =>
            Failure(invalidException)
    } getOrElse Failure(invalidException) match {
        case Success(code) => code
        case Failure(e) => e match {
            case prop: PropertiesFileNotWritableException =>
                System.err.println("[bm-agent] Failed: this host does not yet " +
                                   "have a host id assigned and bm-agent " +
                                   "failed to generate one.")
                System.err.println("[bm-agent] Failed to store a new host id " +
                                   s"at ${e.getMessage}.")
                System.err.println("[bm-agent] Please retry as root.")
                PropertiesFileErrorCode
            case e: KeeperException
                if e.code() == KeeperException.Code.CONNECTIONLOSS =>
                val server =
                    MidoNodeConfigurator.bootstrapConfig()
                                        .getString("zookeeper.zookeeper_hosts")
                System.err.println("[bm-agent] Could not connect to ZooKeeper " +
                                   s"at $server.")
                ZooKeeperErrorCode
            case _ if args.length == 0 =>
                options.printHelp()
                NoArgumentsErrorCode
            case _ =>
                System.err.println ("[bm-agent] Failed: " + e.getMessage)
                OtherErrorCode
        }
    }

    System.exit(returnCode)
}
