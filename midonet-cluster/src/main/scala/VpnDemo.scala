/*
 * Copyright 2015 Midokura SARL
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

import java.util._

import scala.collection.JavaConverters._

import com.codahale.metrics.MetricRegistry

import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.ExponentialBackoffRetry
import org.rogach.scallop._

import org.midonet.cluster.data.storage._
import org.midonet.cluster.models.Commons.{UUID => PUUID}
import org.midonet.cluster.models.Neutron._
import org.midonet.cluster.models.Topology._
import org.midonet.cluster.services._
import org.midonet.cluster.storage.MidonetBackendConfig
import org.midonet.cluster.util.UUIDUtil._
import org.midonet.cluster.util.{IPAddressUtil, IPSubnetUtil}
import org.midonet.conf.MidoNodeConfigurator
import org.midonet.packets.MAC
import org.midonet.util.concurrent._

object VpnDemo extends App {

    def getStorage: Storage = {
        val configurator = MidoNodeConfigurator.apply("/etc/midonet/midonet.conf")
        val config = new MidonetBackendConfig(configurator.runtimeConfig)

        val curator = CuratorFrameworkFactory.newClient(
            config.hosts, new ExponentialBackoffRetry(1000, 3))
        val metrics = new MetricRegistry()

        val backend = new MidonetBackendService(config, curator, metrics)
        backend.startAsync()
        backend.awaitRunning()

        backend.store
    }

    def clearIPSec(storage: Storage, routerId: UUID): Unit = {
        val nameBase = s"Demo124${routerId.toString.substring(0,8)}"
        storage.getAll(classOf[IPSecSiteConnection]).await()
               .find(_.getName == nameBase) match {
            case Some(x) => storage.delete(classOf[IPSecSiteConnection], x.getId)
            case None =>
        }
        storage.getAll(classOf[VpnService]).await()
               .find(_.getName == s"${nameBase}VPNService") match {
            case Some(x) => storage.delete(classOf[VpnService], x.getId)
            case None =>
        }
        storage.getAll(classOf[IkePolicy]).await()
               .find(_.getName == s"${nameBase}IKEPolicy") match {
            case Some(x) => storage.delete(classOf[IkePolicy], x.getId)
            case None =>
        }
        storage.getAll(classOf[IPSecPolicy]).await()
               .find(_.getName == s"${nameBase}IPSecPolicy") match {
            case Some(x) => storage.delete(classOf[IPSecPolicy], x.getId)
            case None =>
        }
    }

    def setupIPSec(storage: Storage,
                   routerId: UUID,
                   localEndpoint: String,
                   remoteEndpoint: String,
                   localCidr: String,
                   remoteCidr: String): UUID = {
        val nameBase = s"Demo124${routerId.toString.substring(0,8)}"
        val connections = storage.getAll(classOf[IPSecSiteConnection]).await()
        connections.find(_.getName == nameBase) match {
            case Some(conn) => conn.getId
            case None =>
                // vpn service
                val vpnService = VpnService.newBuilder()
                    .setId(UUID.randomUUID())
                    .setName(s"${nameBase}VPNService")
                    .setAdminStateUp(true) // TODO: we need all these admin state ups?
                    .setTenantId("foobar")
                    .setSubnetId(UUID.randomUUID())
                    .setRouterId(routerId)
                    .setExternalV4Ip(IPAddressUtil.toProto(localEndpoint))
                    .setStatus("Foobar").build()
                storage.create(vpnService)

                // ike policy
                val ikePolicy = IkePolicy.newBuilder()
                    .setId(UUID.randomUUID())
                    .setName(s"${nameBase}IKEPolicy")
                    .setTenantId("foobar")
                    .setAuthAlgorithm(IPSecAuthAlgorithm.SHA1)
                    .setEncryptionAlgorithm(IPSecEncryptionAlgorithm.AES_128)
                    .setPhase1NegotiationMode(IkePolicy.Phase1NegotiationMode.MAIN)
                    .setLifetimeValue(6000) // not sure of proper format
                    .setIkeVersion(IkePolicy.IkeVersion.V2)
                    .build()
                // TODO .setPfs // not sure how to translate
                storage.create(ikePolicy)

                // ipsec policy
                val ipsecPolicy = IPSecPolicy.newBuilder()
                    .setId(UUID.randomUUID())
                    .setName(s"${nameBase}IPSecPolicy")
                    .setTransformProtocol(IPSecPolicy.TransformProtocol.ESP) // AH, AH_ESP supported?
                    .setAuthAlgorithm(IPSecAuthAlgorithm.SHA1)
                    .setEncryptionAlgorithm(IPSecEncryptionAlgorithm.AES_128)
                    .setEncapsulationMode(IPSecPolicy.EncapsulationMode.TUNNEL) // Transport not supported
                    .setLifetimeValue(6000)
                    .build() // again, not sure of pfs and lifetime
                storage.create(ipsecPolicy)

                val ipSecCon = IPSecSiteConnection.newBuilder()
                    .setId(UUID.randomUUID())
                    .setName(nameBase)
                    .setPeerAddress(remoteEndpoint)
                    .setPeerId(remoteEndpoint)
                    //.setLocalAddress(local_endpoint)
                    //.setLocalId(local_endpoint)
                    .setRouteMode(IPSecSiteConnection.RouteMode.STATIC)
                    .setMtu(1400)
                    .setInitiator(IPSecSiteConnection.Initiator.BI_DIRECTIONAL)
                    .setAuthMode(IPSecSiteConnection.AuthMode.PSK)
                    .setPsk("Foobar")
                    .setAdminStateUp(true)
                    .setVpnServiceId(vpnService.getId)
                    .setIkePolicyId(ikePolicy.getId)
                    .setIpsecPolicyId(ipsecPolicy.getId)
                    .addPeerCidrs(IPSubnetUtil.toProto(remoteCidr))
                    .addLocalCidrs(IPSubnetUtil.toProto(localCidr))
                    .build()
                // TODO: peer cidrs shouldn't be plural
                storage.create(ipSecCon)

                ipSecCon.getId
        }
    }

    def setupContainer(storage: Storage, routerId: UUID,
                       local_endpoint: String,
                       remote_endpoint: String,
                       local_cidr: String,
                       remote_cidr: String,
                       vpnConfigId: UUID): Unit = {
        val router = storage.get(classOf[Router], routerId).await()

        val portAddress = IPAddressUtil.toProto("169.254.1.1")
        val currentPort = router.getPortIdsList.asScala.map {
            (id: PUUID) => storage.get(classOf[Port], id).await()
        } find(_.getPortAddress == portAddress)

        val host = storage.getAll(classOf[Host]).await().last
        currentPort match {
            case Some(p) =>
                println("Port for container already exists")
            case None =>
                val portId = UUID.randomUUID
                val name = s"vpn-${portId.toString.substring(0,8)}"
                val port = Port.newBuilder()
                    .setId(portId)
                    .setRouterId(routerId)
                    .setHostId(host.getId)
                    .setInterfaceName(name)
                    .setPortSubnet(IPSubnetUtil.toProto("169.254.1.0/30")) // TODO, make sure the range is available on router
                    .setPortAddress(IPAddressUtil.toProto("169.254.1.1"))
                    .setPortMac(MAC.random().toString).build()
                storage.create(port)

                val route = Route.newBuilder()
                    .setId(UUID.randomUUID)
                    .setSrcSubnet(IPSubnetUtil.toProto("0.0.0.0/0"))
                    .setDstSubnet(IPSubnetUtil.toProto("169.254.1.0/30"))
                    .setNextHop(Route.NextHop.PORT)
                    .setNextHopPortId(portId)
                    .build()
                storage.create(route)

                val route2 = Route.newBuilder()
                    .setId(UUID.randomUUID)
                    .setSrcSubnet(IPSubnetUtil.toProto("0.0.0.0/0"))
                    .setDstSubnet(IPSubnetUtil.toProto("169.254.1.1/32"))
                    .setNextHopGateway(IPAddressUtil.toProto("255.255.255.255"))
                    .setNextHop(Route.NextHop.LOCAL)
                    .setNextHopPortId(portId)
                    .build()
                storage.create(route2)

                val route3 = Route.newBuilder()
                    .setId(UUID.randomUUID)
                    .setSrcSubnet(IPSubnetUtil.toProto(local_cidr))
                    .setDstSubnet(IPSubnetUtil.toProto(remote_cidr))
                    .setNextHopGateway(IPAddressUtil.toProto("169.254.1.2"))
                    .setNextHop(Route.NextHop.PORT)
                    .setNextHopPortId(portId)
                    .build()
                storage.create(route3)

                val chain = Chain.newBuilder()
                    .setId(UUID.randomUUID)
                    .setName("VPNRedirect")
                    .build()
                storage.create(chain)

                val router2 =storage.get(classOf[Router], routerId).await()
                storage.update(router2.toBuilder.setLocalRedirectChainId(chain.getId).build())

                val redirectRuleBuilder = Rule.newBuilder()
                    .setId(UUID.randomUUID)
                    .setType(Rule.Type.L2TRANSFORM_RULE)
                    .setChainId(chain.getId)
                    .setAction(Rule.Action.REDIRECT)
                redirectRuleBuilder.getConditionBuilder
                    .setNwDstIp(IPSubnetUtil.toProto(local_endpoint + "/32"))
                    .setNwProto(50)
                redirectRuleBuilder.getTransformRuleDataBuilder
                    .setTargetPortId(portId)
                storage.create(redirectRuleBuilder.build())

                val redirectRuleBuilder2 = Rule.newBuilder()
                    .setId(UUID.randomUUID)
                    .setType(Rule.Type.L2TRANSFORM_RULE)
                    .setChainId(chain.getId)
                    .setAction(Rule.Action.REDIRECT)
                redirectRuleBuilder2.getConditionBuilder
                    .setNwDstIp(IPSubnetUtil.toProto(local_endpoint + "/32"))
                    .setNwProto(17)
                redirectRuleBuilder2.getConditionBuilder.getTpSrcBuilder
                    .setStart(500).setEnd(500)
                redirectRuleBuilder2.getTransformRuleDataBuilder
                    .setTargetPortId(portId)
                storage.create(redirectRuleBuilder2.build())

                val group = ServiceContainerGroup.newBuilder()
                    .setId(UUID.randomUUID())
                    .build()
                storage.create(group)

                val container = ServiceContainer.newBuilder()
                    .setId(UUID.randomUUID())
                    .setServiceType("IPSEC")
                    .setServiceGroupId(group.getId)
                    .setConfigurationId(vpnConfigId)
                    .setPortId(port.getId).build()
                storage.create(container)
        }
    }

    def clearContainer(storage: Storage, routerId: UUID): Unit = {
        val router = storage.get(classOf[Router], routerId).await()
        if (router.hasLocalRedirectChainId) {
            val chainId = router.getLocalRedirectChainId
            storage.update(router.toBuilder.clearLocalRedirectChainId.build)
            storage.delete(classOf[Chain], chainId)
        }

        val portAddress = IPAddressUtil.toProto("169.254.1.1")
        val currentPort = router.getPortIdsList.asScala.map {
            (id: PUUID) => storage.get(classOf[Port], id).await()
        } find(_.getPortAddress == portAddress)

        val host = storage.getAll(classOf[Host]).await().last
        currentPort match {
            case Some(p) =>
                if (p.hasServiceContainerId) {
                    val serviceContainer =
                        storage.get(classOf[ServiceContainer],
                                    p.getServiceContainerId).await()
                    storage.delete(classOf[ServiceContainer],
                                   serviceContainer.getId)
                    storage.delete(classOf[ServiceContainerGroup],
                                   serviceContainer.getServiceGroupId)
                }
                storage.delete(classOf[Port], p.getId)
            case None =>
        }
    }

    val opts = new ScallopConf(args) {
        val setup = opt[Boolean]("setup", noshort = true,
                                 descr = "Setup a VPN container")
        val clear = opt[Boolean]("clear", noshort = true,
                                 descr = "Clear VPN container")
        val router = opt[String]("router", noshort=true,
                                 descr = "Router to set it up on")
        val localEndpoint = opt[String]("local-endpoint", noshort=true,
                                        descr = "Local IPSec endpoint IP")
        val remoteEndpoint = opt[String]("remote-endpoint", noshort=true,
                                         descr = "Remote IPSec endpoint IP")
        val localSubnet = opt[String]("local-subnet", noshort=true,
                                      descr = "Local protected subnet")
        val remoteSubnet = opt[String]("remote-subnet", noshort=true,
                                       descr = "Remote protected subnet")
        val help = opt[Boolean]("help", noshort = true,
                                descr = "Show this message")
    }

    val router = UUID.fromString(opts.router.get.get)
    val storage = getStorage
    if (opts.setup.isDefined && opts.setup.get.get) {

        val localEndpoint = opts.localEndpoint.get.get
        val remoteEndpoint = opts.remoteEndpoint.get.get
        val localSubnet = opts.localSubnet.get.get
        val remoteSubnet = opts.remoteSubnet.get.get

        val ipsecConf = setupIPSec(storage,
                                   router,
                                   localEndpoint, remoteEndpoint,
                                   localSubnet, remoteSubnet)
        setupContainer(storage, router, localEndpoint, remoteEndpoint,
                       localSubnet, remoteSubnet, ipsecConf)
    } else if (opts.clear.isDefined && opts.clear.get.get) {
        clearIPSec(storage, router)
        clearContainer(storage, router)
    }
}
