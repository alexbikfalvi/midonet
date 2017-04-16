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

package org.midonet.fixes

import java.util.{Scanner, UUID}
import java.util.concurrent.TimeUnit

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NonFatal

import com.codahale.metrics.MetricRegistry
import com.google.protobuf.TextFormat
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}

import org.apache.commons.configuration.HierarchicalINIConfiguration
import org.apache.commons.lang.StringUtils
import org.apache.curator.framework.{CuratorFramework, CuratorFrameworkFactory}
import org.apache.curator.retry.RetryOneTime
import org.rogach.scallop.ScallopConf

import rx.Observable

import org.midonet.cluster.data.storage.metrics.StorageMetrics
import org.midonet.cluster.data.storage.{SingleValueKey, ZookeeperObjectMapper}
import org.midonet.cluster.models.Neutron.NeutronPort
import org.midonet.cluster.models.Neutron.NeutronPort.DeviceOwner
import org.midonet.cluster.models.State
import org.midonet.cluster.models.State.HostState
import org.midonet.cluster.models.Topology.{Host, Port}
import org.midonet.cluster.rpc.State.ProxyResponse.Notify.Update
import org.midonet.cluster.services.MidonetBackend
import org.midonet.cluster.services.state.client.StateTableClient.ConnectionState.ConnectionState
import org.midonet.cluster.services.state.client.{StateSubscriptionKey, StateTableClient}
import org.midonet.cluster.storage.MidonetBackendConfig
import org.midonet.cluster.util.UUIDUtil._
import org.midonet.util.concurrent._
import org.midonet.util.eventloop.TryCatchReactor

object BindingFix extends App {

    private class Conf(args: Seq[String]) extends ScallopConf(args) {
        printedName = "mn-fix-bindings"
        footer("Copyright (c) 2017 Midokura SARL, All Rights Reserved.")
    }

    private class IniConf(fileName: String) {
        private val ini: HierarchicalINIConfiguration = new HierarchicalINIConfiguration()
        ini.setDelimiterParsingDisabled(true)
        ini.setFileName(fileName)
        ini.setThrowExceptionOnMissing(false)
        ini.load()

        def get: Config = {
            var config = ConfigFactory.empty()
            for (section <- ini.getSections.asScala;
                 key <- ini.getSection(section).getKeys.asScala) {
                val value = ini.getSection(section).getString(key)
                config = config.withValue(key, ConfigValueFactory.fromAnyRef(value))
            }

            config
        }
    }

    private final val ErrorCodeConnectionFailed = -1
    private final val ErrorCodeStorageFailed = -2

    private final val MidolmanConfLocation = "/etc/midolman/midolman.conf"
    private final val MidonetConfLocations = List("~/.midonetrc",
                                                  "/etc/midonet/midonet.conf",
                                                  MidolmanConfLocation)
    private final val DefaultRootKey = "/midonet"
    private final val StorageTimeout = 600 seconds

    System.setProperty("logback.configurationFile", "logback-disabled.xml")

    private final val conf = new Conf(args)
    private final val config = bootstrapConfig()

    try run()
    catch {
        case NonFatal(e) =>
            System.err.println(s"mn-binding-fix tool has failed ${e.getMessage}")
    }

    private def bootstrapConfig(): Config = {
        val defaultRootConfig =
            ConfigFactory.parseString(s"zk_default_root = $DefaultRootKey")
        val defaults = ConfigFactory.parseString(
            """
              |zookeeper {
              |    zookeeper_hosts = "127.0.0.1:2181"
              |    root_key = ${zk_default_root}
              |    bootstrap_timeout = 30s
              |}
            """.stripMargin).resolveWith(defaultRootConfig)

        val environment = ConfigFactory.parseString(
            """
              |zookeeper.zookeeper_hosts = ${?MIDO_ZOOKEEPER_HOSTS}
              |zookeeper.root_key = ${?MIDO_ZOOKEEPER_ROOT_KEY}
            """.stripMargin)

        def loadConfig =
            (loc: String) => Try(new IniConf(loc).get).getOrElse(ConfigFactory.empty)

        environment.withFallback({ for (l <- MidonetConfLocations) yield loadConfig(l)
                                 } reduce((a, b) => a.withFallback(b))
                                 withFallback(ConfigFactory.systemProperties)
                                 withFallback(defaults)).resolve()
    }

    private def run(): Unit = {
        val zookeeperHosts = config.getString("zookeeper.zookeeper_hosts")
        val rootKey = config.getString("zookeeper.root_key")

        System.out.println("MidoNet BINDING-FIX tool")
        System.out.println()
        System.out.println("Copyright (c) 2017 Midokura SARL, All Rights Reserved.")
        System.out.println("Use --help for configuration options")
        System.out.println("------------------------------------------------------")
        System.out.println()

        val curator = createBackend(zookeeperHosts)
        try {
            val storage = createStorage(curator, rootKey)
            var neutronPorts = listNeutronPorts(3, storage)
            var ports = listPorts(4, storage)
            var hosts = listHosts(5, storage)
            var interfaces = listInterfaces(6, hosts.keySet, storage)
            var (notFound, notBound) = analyzePorts(7, neutronPorts, ports)


            val scanner = new Scanner(System.in)
            do {
                System.out.println()
                System.out.println("Select an option to continue:")
                System.out.println("(U) View unbound ports")
                System.out.println("(F) View Neutron ports that are missing from MidoNet")
                System.out.println("(B) Bind unbound ports")
                System.out.println("(Q) Quit")
                System.out.print("Type option and press ENTER > ")

                if (scanner.hasNextLine) {
                    scanner.nextLine().trim.toUpperCase match {
                        case "U" => viewUnbound(neutronPorts, ports, notBound)
                        case "F" => viewMissing(neutronPorts, notFound)
                        case "B" =>
                            if (bind(storage, scanner, neutronPorts, ports, hosts,
                                     interfaces, notBound)) {
                                System.out.println("\nUpdating port information...")
                                neutronPorts = listNeutronPorts(1, storage)
                                ports = listPorts(2, storage)
                                hosts = listHosts(3, storage)
                                interfaces =
                                    listInterfaces(4, hosts.keySet, storage)
                                val result = analyzePorts(5, neutronPorts, ports)
                                notFound = result._1
                                notBound = result._2
                            }

                        case "Q" => return
                        case o => System.err.println(s"Unknown option: $o")
                    }
                } else return
            } while (true)
        } finally {
            curator.close()
        }
    }

    private def createBackend(hosts: String): CuratorFramework = {
        System.out.print(s"[1] Connecting to ZooKeeper at $hosts... ")
        val curator = CuratorFrameworkFactory.newClient(hosts, new RetryOneTime(60000))
        curator.start()
        curator.blockUntilConnected(60, TimeUnit.SECONDS)
        if (!curator.getZookeeperClient.isConnected) {
            System.out.println("failed!")
            System.exit(ErrorCodeConnectionFailed)
        }
        System.out.println("done.")
        curator
    }

    private def createStorage(curator: CuratorFramework,
                              rootKey: String): ZookeeperObjectMapper = {
        val stateTableClient = new StateTableClient {
            override def start(): Unit = {}

            override def stop(): Boolean = false

            override def observable(table: StateSubscriptionKey): Observable[Update] =
                Observable.never()

            override def connection: Observable[ConnectionState] =
                Observable.never()
        }

        System.out.print(s"[2] Setting up storage with root path $rootKey... ")
        try {
            val config = new MidonetBackendConfig(ConfigFactory.parseString(
                s"""
                  |zookeeper.root_key:$rootKey
                """.stripMargin))
            val storage = new ZookeeperObjectMapper(
                config,
                MidonetBackend.ClusterNamespaceId.toString,
                curator, curator, stateTableClient,
                new TryCatchReactor("nsdb", 1),
                new StorageMetrics(new MetricRegistry()))
            MidonetBackend.setupBindings(storage, storage, () => {})
            System.out.println("done.")
            storage
        } catch {
            case NonFatal(e) =>
                System.out.println(s"failed (${e.getMessage})!")
                System.exit(ErrorCodeStorageFailed)
                throw e
        }
    }

    private def listNeutronPorts(step: Int, storage: ZookeeperObjectMapper)
    : Map[UUID, NeutronPort] = {
        System.out.print(s"[$step] Reading Neutron ports... ")
        val ports = try {
            storage.getAll(classOf[NeutronPort]).await(StorageTimeout)
        } catch {
            case NonFatal(e) =>
                System.out.println(s"failed (${e.getMessage})!")
                System.exit(ErrorCodeStorageFailed)
                throw e
        }
        System.out.println("done.")
        System.out.println(s"    > Found ${ports.size} Neutron port(s).")

        val computePorts = ports.filter(_.getDeviceOwner == DeviceOwner.COMPUTE)
        System.out.println(s"    > Found ${computePorts.size} Neutron compute " +
                           "port(s).")

        computePorts.map(port => (port.getId.asJava, port)).toMap
    }

    private def listPorts(step: Int,
                          storage: ZookeeperObjectMapper): Map[UUID, Port] = {
        System.out.print(s"[$step] Reading Neutron ports... ")
        val ports = try {
            storage.getAll(classOf[Port]).await(StorageTimeout)
        } catch {
            case NonFatal(e) =>
                System.out.println(s"failed (${e.getMessage})!")
                System.exit(ErrorCodeStorageFailed)
                throw e
        }
        System.out.println("done.")
        System.out.println(s"    > Found ${ports.size} MidoNet port(s).")

        ports.map(port => (port.getId.asJava, port)).toMap
    }

    private def listHosts(step: Int,
                          storage: ZookeeperObjectMapper): Map[UUID, Host] = {
        System.out.print(s"[$step] Reading compute hosts... ")
        val hosts = try {
            storage.getAll(classOf[Host]).await(StorageTimeout)
        } catch {
            case NonFatal(e) =>
                System.out.println(s"failed (${e.getMessage})!")
                System.exit(ErrorCodeStorageFailed)
                throw e
        }
        System.out.println("done.")
        System.out.println(s"    > Found ${hosts.size} compute host(s).")

        hosts.map(host => (host.getId.asJava, host)).toMap
    }

    private def listInterfaces(step: Int,
                               hosts: Set[UUID], storage: ZookeeperObjectMapper)
    : Map[UUID, Map[String, HostState.Interface]] = {
        System.out.print(s"[$step] Reading compute interfaces... ")

        var count = 0
        val interfaces =
            try {
                hosts.map(id => {
                    storage.getKey(id.toString, classOf[Host], id,
                                   MidonetBackend.HostKey)
                        .toBlocking
                        .first() match {
                        case SingleValueKey(_, Some(value), _) =>
                            val builder = State.HostState.newBuilder()
                            TextFormat.merge(value, builder)
                            val state = builder.build()
                            val interfaces = state.getInterfacesList.asScala
                                .map(interface => {
                                    (interface.getName, interface)
                                }).toMap
                            count += interfaces.size
                            (id, interfaces)
                        case _ =>
                            (id, Map.empty[String, HostState.Interface])
                    }
                }).toMap
            } catch {
                case NonFatal(e) =>
                    System.out.println(s"failed (${e.getMessage})!")
                    System.exit(ErrorCodeStorageFailed)
                    throw e
            }

        System.out.println("done.")
        System.out.println(s"    > Found $count compute interface(s).")
        interfaces
    }

    private def analyzePorts(step: Int,
                             neutronPorts: Map[UUID, NeutronPort],
                             ports: Map[UUID, Port]): (Set[UUID], Set[UUID]) = {
        System.out.print(s"[$step] Searching unbound compute ports... ")

        var notFound = new mutable.HashSet[UUID]()
        val notBound = new mutable.HashSet[UUID]()
        for ((id, neutronPort) <- neutronPorts) {
            ports.get(id) match {
                case Some(port) =>
                    if (!port.hasHostId || !port.hasInterfaceName)
                        notBound += id
                case None => notFound += id
            }
        }

        System.out.println("done.")
        System.out.println(s"    > Found ${notBound.size} unbound compute " +
                           "port(s).")
        if (notFound.nonEmpty) {
            System.out.println(s"    > Found ${notFound.size} Neutron " +
                               "port(s) that are missing in MidoNet.")
        }

        (notFound.toSet, notBound.toSet)
    }

    private def viewUnbound(neutronPorts: Map[UUID, NeutronPort],
                            ports: Map[UUID, Port],
                            unbound: Set[UUID]): Unit = {
        var index = 0
        val values = unbound.toSeq.map(id => {
            val neutronPort = neutronPorts(id)
            val port = ports(id)
            index += 1
            Seq(index, id, neutronPort.getHostId,
                if (port.hasHostId) port.getHostId else "",
                if (port.hasInterfaceName) port.getInterfaceName else "")
        })
        printTable(Seq("Index","Port ID", "Host", "Host ID", "Interface"),
                   values)
    }

    private def viewMissing(neutronPorts: Map[UUID, NeutronPort],
                            missing: Set[UUID]): Unit = {
        var index = 0
        val values = missing.toSeq.map(id => {
            val port = neutronPorts(id)
            index += 1
            Seq(index, id, port.getHostId, port.getDeviceOwner,
                port.getNetworkId.asJava, port.getTenantId)
        })
        printTable(Seq("Index", "Port ID", "Host", "Device owner",
                       "Network ID", "Tenant ID"), values)
    }

    private def bind(storage: ZookeeperObjectMapper,
                     scanner: Scanner,
                     neutronPorts: Map[UUID, NeutronPort],
                     ports: Map[UUID, Port],
                     hosts: Map[UUID, Host],
                     interfaces: Map[UUID, Map[String, HostState.Interface]],
                     unbound: Set[UUID]): Boolean = {
        val confirmedHostAndInterface =
            new mutable.HashMap[UUID, (Host, String)]()
        val confirmedHost =
            new mutable.HashMap[UUID, (Host, String)]()
        val unidentified = new mutable.HashSet[UUID]()

        for (id <- unbound) {
            val neutronPort = neutronPorts(id)
            if (neutronPort.hasHostId &&
                StringUtils.isNotBlank(neutronPort.getHostId)) {
                val host = findHost(hosts, neutronPort.getHostId)
                if (host.nonEmpty) {
                    val hostId = host.get.getId.asJava
                    val interface = findInterface(interfaces(hostId), id)
                    if (interface.nonEmpty) {
                        confirmedHostAndInterface += id -> (host.get, interface.get)
                    } else {
                        confirmedHost += id -> (host.get,
                            "tap" + id.toString.substring(0, 11))
                    }
                } else {
                    unidentified += id
                }
            } else {
                unidentified += id
            }
        }

        System.out.println()
        System.out.println("Ports with confirmed host and interface:    " +
                           s"           ${confirmedHostAndInterface.size}")
        System.out.println("Ports with confirmed host but unconfirmed " +
                           s"interface:   ${confirmedHost.size}")
        System.out.println("Ports with unidentified or missing host and interface: " +
                           s"${unidentified.size}")
        System.out.println("-" * 60)
        System.out.println("Total unbound ports:                        " +
                           s"           ${unbound.size}")

        do {
            System.out.println()
            System.out.println("Select an option to continue:")
            if (confirmedHostAndInterface.nonEmpty)
                System.out.println("(I) Bind ports with confirmed host and interface")
            if (confirmedHost.nonEmpty)
                System.out.println("(D) Bind ports with confirmed host only")
            if (unidentified.nonEmpty)
                System.out.println("(N) View ports with unidentified or missing host and interface")
            System.out.println("(L) Return to previous menu")
            System.out.println("(Q) Quit")
            System.out.print("Type option and press ENTER > ")

            if (scanner.hasNextLine) {
                scanner.nextLine().trim.toUpperCase match {
                    case "I" if confirmedHostAndInterface.nonEmpty =>
                        if (bindConfirmed(storage, scanner, neutronPorts, ports,
                                          hosts, confirmedHostAndInterface.toMap))
                            return true

                    case "D" if confirmedHost.nonEmpty =>
                        if (bindHalfConfirmed(storage, scanner, neutronPorts,
                                              ports, hosts, confirmedHost.toMap))
                            return true

                    case "N" if unidentified.nonEmpty =>
                        viewUnidentified(neutronPorts, ports, hosts,
                                         unidentified.toSet)

                    case "L" => return false
                    case "Q" => System.exit(0)
                    case o => System.err.println(s"Unknown option: $o")
                }
            } else return false
        } while (true)
        false
    }

    private def bindConfirmed(storage: ZookeeperObjectMapper,
                              scanner: Scanner,
                              neutronPorts: Map[UUID, NeutronPort],
                              ports: Map[UUID, Port],
                              hosts: Map[UUID, Host],
                              bindings: Map[UUID, (Host, String)]): Boolean = {
        System.out.println(
            s"\nBinding ${bindings.size} ports that have " +
            s"a confirmed host and network interface.")

        confirmAndBind(storage, scanner, neutronPorts, ports, hosts,
                       bindings)
    }

    private def bindHalfConfirmed(storage: ZookeeperObjectMapper,
                                  scanner: Scanner,
                                  neutronPorts: Map[UUID, NeutronPort],
                                  ports: Map[UUID, Port],
                                  hosts: Map[UUID, Host],
                                  bindings: Map[UUID, (Host, String)])
    : Boolean = {
        System.out.println(
            s"\nBinding ${bindings.size} ports that have " +
            s"only a confirmed host.")

        System.out.println(
            "\nCAUTION: This tool was unable to detect the network " +
            "interfaces\ncorresponding to these unbound ports. " +
            "This may happen if the\nMidoNet Agent is not running on the " +
            "interface host or if the\ninstance interface no longer " +
            "exists. You may proceed with the\nbinding, however the port " +
            "will remain unplugged if the interface\ndoes not exist.")

        confirmAndBind(storage, scanner, neutronPorts, ports, hosts,
                       bindings)
    }

    private def viewUnidentified(neutronPorts: Map[UUID, NeutronPort],
                                 ports: Map[UUID, Port],
                                 hosts: Map[UUID, Host],
                                 unidentified: Set[UUID]): Unit = {
        System.out.println(
            "\nUnidentified ports are compute ports that do not specify a host" +
            "\nin their Neutron binding profile, or their specified host no longer" +
            "\nexists in MidoNet, or the host name has been changed.")

        System.out.println(
            "\nYou should assess these ports individually on whether they should" +
            "\nbe bound to an instance interface.\n")

        var index = 0
        val values = unidentified.toSeq.map(id => {
            val neutronPort = neutronPorts(id)
            val port = ports(id)
            index += 1
            Seq(index, id, neutronPort.getHostId, neutronPort.getDeviceOwner,
                neutronPort.getNetworkId.asJava, neutronPort.getTenantId,
                if (port.hasHostId) port.getHostId.asJava else "",
                if (port.hasInterfaceName) port.getInterfaceName else "")
        })
        printTable(Seq("Index", "Port ID", "Host", "Device owner",
                       "Network ID", "Tenant ID", "Host ID",
                       "Interface"), values)
    }

    private def confirmAndBind(storage: ZookeeperObjectMapper,
                               scanner: Scanner,
                               neutronPorts: Map[UUID, NeutronPort],
                               ports: Map[UUID, Port],
                               hosts: Map[UUID, Host],
                               bindings: Map[UUID, (Host, String)])
    : Boolean = {

        var confirmed = false
        do {
            System.out.println()
            System.out.println("Select an option to continue:")
            System.out.println("(F) View and confirm the new port bindings")
            if (confirmed)
                System.out.println("(P) Apply the confirmed port bindings")
            System.out.println("(L) Return to previous menu")
            System.out.println("(Q) Quit")
            System.out.print("Type option and press ENTER > ")

            if (scanner.hasNextLine) {
                scanner.nextLine().trim.toUpperCase match {
                    case "F" =>
                        confirmed = viewAndConfirmBindings(scanner, ports,
                                                           bindings)
                    case "P" if confirmed =>
                        bindPorts(storage, ports, bindings)
                        return true
                    case "L" => return false
                    case "Q" => System.exit(0)
                    case o => System.err.println(s"Unknown option: $o")
                }
            } else return false
        } while (true)
        false
    }

    private def viewAndConfirmBindings(scanner: Scanner,
                                       ports: Map[UUID, Port],
                                       bindings: Map[UUID, (Host, String)])
    : Boolean = {
        var index = 0
        val values = for ((portId, (host, interface)) <- bindings) yield {
            index += 1
            Seq(index, portId, host.getName, host.getId.asJava, interface)
        }
        printTable(Seq("Index","Port ID", "Host", "Host ID", "Interface"),
                   values.toSeq)

        System.out.print(
            "\nDo you confirm these changes?" +
            "\nType 'yes' to acknowledge and press ENTER > ")

        if (scanner.hasNext)
            scanner.nextLine().toUpperCase.trim == "YES"
        else
            false
    }

    private def bindPorts(storage: ZookeeperObjectMapper,
                          ports: Map[UUID, Port],
                          bindings: Map[UUID, (Host, String)]): Unit = {
        System.out.println("\nBinding confirmed ports...")
        System.out.println(
            "\nNOTE: If a port has changed since the last port scan, it will " +
            "\nbe skipped automatically.\n")

        for ((portId, (host, interface)) <- bindings) {
            bindPort(storage, portId, ports(portId), host, interface)
        }
    }

    private def bindPort(storage: ZookeeperObjectMapper,
                         portId: UUID, port: Port, host: Host,
                         interface: String): Unit = {
        System.out.print(s"Binding port $portId to host ${host.getId.asJava} " +
                         s"(${host.getName}) interface $interface... ")

        try {
            val transaction = storage.transaction()
            val current = transaction.get(classOf[Port], portId)
            if (current != port) {
                System.out.println("SKIPPED (port changed)")
                return
            }
            val changed = port.toBuilder
                .setHostId(host.getId)
                .setInterfaceName(interface)
                .build()
            transaction.update(changed)
            transaction.commit()
            System.out.println("OK")
        } catch {
            case NonFatal(e) =>
                System.out.println(s"FAILED (${e.getMessage})")
        }
    }

    private def findHost(hosts: Map[UUID, Host], name: String): Option[Host] = {
        hosts.values.find(h => name.startsWith(h.getName))
    }

    private def findInterface(interfaces: Map[String, HostState.Interface],
                              id: UUID): Option[String] = {
        val tap = "tap" + id.toString.substring(0, 10)
        interfaces.keys.find(_.startsWith(tap))
    }

    private def printTable(header: Seq[String], values: Seq[Seq[Any]]): Unit = {
        val width = new Array[Int](header.size)
        for (i <- header.indices) {
            width(i) = header(i).length + 2
            for (j <- values.indices)
                width(i) = Math.max(width(i), values(j)(i).toString.length + 2)
        }
        var line = "+"
        for (i <- header.indices) {
            line += "-" * width(i)
            line += "+"
        }
        System.out.println(line)
        line = "|"
        for (i <- header.indices) {
            line += " "
            line += header(i)
            line += " " * (width(i) - header(i).length - 1)
            line += "|"
        }
        System.out.println(line)
        line = "+"
        for (i <- header.indices) {
            line += "-" * width(i)
            line += "+"
        }
        System.out.println(line)
        for (j <- values.indices) {
            line = "|"
            for (i <- header.indices) {
                line += " "
                line += values(j)(i).toString
                line += " " * (width(i) - values(j)(i).toString.length - 1)
                line += "|"
            }
            System.out.println(line)
        }
        if (values.nonEmpty) {
            line = "+"
            for (i <- header.indices) {
                line += "-" * width(i)
                line += "+"
            }
            System.out.println(line)
        }
    }

}
