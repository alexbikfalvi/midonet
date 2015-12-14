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

package org.midonet.cluster.services.rest_api.resources

import java.util.UUID

import javax.ws.rs.Path
import javax.ws.rs.core.MediaType._

import scala.collection.JavaConverters._

import com.google.inject.Inject
import com.google.inject.servlet.RequestScoped

import org.midonet.cluster.models.Topology._
import org.midonet.cluster.rest_api.annotation._
import org.midonet.cluster.rest_api.models.{IPSecSiteConnection, VpnService}
import org.midonet.cluster.services.rest_api.MidonetMediaTypes._
import org.midonet.cluster.services.rest_api.resources.IPSecSiteConnectionResource._
import org.midonet.cluster.services.rest_api.resources.MidonetResource.ResourceContext
import org.midonet.cluster.util.UUIDUtil._
import org.midonet.cluster.util.{IPAddressUtil, IPSubnetUtil}
import org.midonet.packets.MAC

object IPSecSiteConnectionResource {

    private[resources] def xorUuid(id: UUID, msb: Long, lsb: Long): UUID = {
        val msbMask = msb & 0xffffffffffff0fffL
        val lsbMask = lsb & 0x3fffffffffffffffL
        new UUID(id.getMostSignificantBits ^ msbMask,
                 id.getLeastSignificantBits ^ lsbMask)
    }

    private[resources] def vpnPortId(ipsecConnectionId: UUID): UUID =
        xorUuid(ipsecConnectionId, 0x68fd6d8bbd3343d8L, 0x96b1f45a04e6d128L)

    private[resources] def vpnChainId(ipsecConnectionId: UUID): UUID =
        xorUuid(ipsecConnectionId, 0xa00989d341c8636fL, 0x928eb605e3e04119L)

    private[resources] def vpnContainerGroupId(ipsecConnectionId: UUID): UUID =
        xorUuid(ipsecConnectionId, 0x9909aa4ad4b691d8L, 0x8c40e4ca90769cf4L)

    private[resources] def vpnContainerId(ipsecConnectionId: UUID): UUID =
        xorUuid(ipsecConnectionId, 0xb807509d2fa04b9eL, 0xbac97789e63e4663L)

}

@ApiResource(version = 1, name = "ipsecConnections",
             template = "ipsecConnectionsTemplate")
@Path("ipsec_connections")
@RequestScoped
@AllowGet(Array(APPLICATION_IPSEC_SITE_CONNECTION_JSON,
                APPLICATION_JSON))
@AllowDelete
class IPSecSiteConnectionResource @Inject()(resContext: ResourceContext)
    extends MidonetResource[IPSecSiteConnection](resContext) {

    protected override def deleteFilter(id: String,
                                        tx: ResourceTransaction): Unit = {
        val connectionId = UUID.fromString(id)

        tx.delete(classOf[IPSecSiteConnection], id)
        tx.tx.delete(classOf[ServiceContainer], vpnContainerId(connectionId))
        tx.tx.delete(classOf[ServiceContainerGroup], vpnContainerGroupId(connectionId))
        tx.tx.delete(classOf[Chain], vpnChainId(connectionId))
        tx.tx.delete(classOf[Port], vpnPortId(connectionId))

    }

}

@RequestScoped
@AllowList(Array(APPLICATION_IPSEC_SITE_CONNECTION_COLLECTION_JSON,
                 APPLICATION_JSON))
@AllowCreate(Array(APPLICATION_IPSEC_SITE_CONNECTION_JSON,
                   APPLICATION_JSON))
class VpnServiceIPSecSiteConnectionResource(vpnServiceId: UUID,
                                            resContext: ResourceContext)
    extends MidonetResource[IPSecSiteConnection](resContext) {

    protected override def listIds: Seq[Any] = {
        getResource(classOf[VpnService], vpnServiceId).ipsecSiteConnectionIds.asScala
    }

    protected override def createFilter(ipsecConnection: IPSecSiteConnection,
                                        tx: ResourceTransaction): Unit = {
        ipsecConnection.create(vpnServiceId)
        tx.create(ipsecConnection)

        // TODO: These are crude translations, where we map a container to an
        // TODO: IPSec site connection for simplicity. Later work may map a
        // TODO: VPN container to a VPN service but this requires additional
        // TODO: work on the VPN service handler side.

        val vpnService = tx.get(classOf[VpnService], vpnServiceId)

        val portId = vpnPortId(ipsecConnection.id)
        val port = Port.newBuilder()
            .setId(portId.asProto)
            .setRouterId(vpnService.routerId.asProto)
            .setInterfaceName(s"vpn-${portId.toString.substring(0,8)}")
            .setPortSubnet(IPSubnetUtil.toProto("169.254.1.0/30"))
            .setPortAddress(IPAddressUtil.toProto("169.254.1.1"))
            .setPortMac(MAC.random().toString)
            .build()
        val subnetRoute = Route.newBuilder()
            .setId(UUID.randomUUID.asProto)
            .setSrcSubnet(IPSubnetUtil.toProto("0.0.0.0/0"))
            .setDstSubnet(IPSubnetUtil.toProto("169.254.1.0/30"))
            .setNextHop(Route.NextHop.PORT)
            .setNextHopPortId(portId.asProto)
            .build()
        val localRoute = Route.newBuilder()
            .setId(UUID.randomUUID.asProto)
            .setSrcSubnet(IPSubnetUtil.toProto("0.0.0.0/0"))
            .setDstSubnet(IPSubnetUtil.toProto("169.254.1.1/32"))
            .setNextHopGateway(IPAddressUtil.toProto("255.255.255.255"))
            .setNextHop(Route.NextHop.LOCAL)
            .setNextHopPortId(portId.asProto)
            .build()

        tx.tx.create(port)
        tx.tx.create(subnetRoute)
        tx.tx.create(localRoute)

        for (localCidr <- ipsecConnection.localCidrs.asScala;
             peerCidr <- ipsecConnection.peerCidrs.asScala) {
            val siteRoute = Route.newBuilder()
                .setId(UUID.randomUUID.asProto)
                .setSrcSubnet(IPSubnetUtil.toProto(localCidr))
                .setDstSubnet(IPSubnetUtil.toProto(peerCidr))
                .setNextHopGateway(IPAddressUtil.toProto("169.254.1.2"))
                .setNextHop(Route.NextHop.PORT)
                .setNextHopPortId(portId.asProto)
                .build()
            tx.tx.create(siteRoute)
        }

        val chain = Chain.newBuilder()
            .setId(vpnChainId(ipsecConnection.id).asProto)
            .setName("OS_VPN_REDIRECT_" + portId.toString)
            .build()
        tx.tx.create(chain)

        val router = tx.tx.get(classOf[Router], vpnService.routerId)
        tx.tx.update(router.toBuilder.setLocalRedirectChainId(chain.getId).build())

        val redirectRuleBuilder1 = Rule.newBuilder()
            .setId(UUID.randomUUID.asProto)
            .setType(Rule.Type.L2TRANSFORM_RULE)
            .setChainId(chain.getId)
            .setAction(Rule.Action.REDIRECT)
        redirectRuleBuilder1.getConditionBuilder
            .setNwDstIp(IPSubnetUtil.toProto(vpnService.externalV4Ip + "/32"))
            .setNwProto(50)
        redirectRuleBuilder1.getTransformRuleDataBuilder
            .setTargetPortId(portId.asProto)
        tx.tx.create(redirectRuleBuilder1.build())

        val redirectRuleBuilder2 = Rule.newBuilder()
            .setId(UUID.randomUUID.asProto)
            .setType(Rule.Type.L2TRANSFORM_RULE)
            .setChainId(chain.getId)
            .setAction(Rule.Action.REDIRECT)
        redirectRuleBuilder2.getConditionBuilder
            .setNwDstIp(IPSubnetUtil.toProto(vpnService.externalV4Ip + "/32"))
            .setNwProto(17)
        redirectRuleBuilder2.getConditionBuilder.getTpSrcBuilder
            .setStart(500).setEnd(500)
        redirectRuleBuilder2.getTransformRuleDataBuilder
            .setTargetPortId(portId.asProto)
        tx.tx.create(redirectRuleBuilder2.build())

        val group = ServiceContainerGroup.newBuilder()
            .setId(vpnContainerGroupId(ipsecConnection.id).asProto)
            .build()
        tx.tx.create(group)

        val container = ServiceContainer.newBuilder()
            .setId(vpnContainerId(ipsecConnection.id).asProto)
            .setServiceType("IPSEC")
            .setServiceGroupId(group.getId)
            .setConfigurationId(ipsecConnection.id.asProto)
            .setPortId(port.getId).build()
        tx.tx.create(container)
    }

}
