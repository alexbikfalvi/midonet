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

package org.midonet.cluster.rest_api.models;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.midonet.cluster.data.ZoomClass;
import org.midonet.cluster.data.ZoomEnum;
import org.midonet.cluster.data.ZoomEnumValue;
import org.midonet.cluster.data.ZoomField;
import org.midonet.cluster.models.Neutron;
import org.midonet.cluster.rest_api.ResourceUris;
import org.midonet.cluster.util.IPSubnetUtil;
import org.midonet.packets.IPSubnet;

@ZoomClass(clazz = Neutron.IPSecSiteConnection.class)
public class IPSecSiteConnection extends UriResource {

    @ZoomField(name = "id")
    public UUID id;

    @ZoomField(name = "tenant_id")
    public String tenantId;

    @ZoomField(name = "name")
    public String name;

    @ZoomField(name = "description")
    public String description;

    @ZoomField(name = "admin_state_up")
    public boolean adminStateUp = true;

    @ZoomField(name = "peer_address")
    public String peerAddress;

    @ZoomField(name = "local_cidrs", converter = IPSubnetUtil.Converter.class)
    public List<String> localCidrs;

    @ZoomField(name = "peer_cidrs", converter = IPSubnetUtil.Converter.class)
    public List<String> peerCidrs;

    @ZoomField(name = "route_mode")
    public RouteMode routeMode = RouteMode.STATIC;

    @ZoomField(name = "mtu")
    public Integer mtu = 1400;

    @ZoomField(name = "initiator")
    @NotNull
    public Initiator initiator;

    @ZoomField(name = "auth_mode")
    public AuthMode authMode = AuthMode.PSK;

    @ZoomField(name = "psk")
    @NotNull
    public String psk;

    @ZoomField(name = "status")
    public Status status;

    @ZoomField(name = "dpd_action")
    public DpdAction dpdAction;

    @ZoomField(name = "dpd_interval")
    public Integer dpdInterval;

    @ZoomField(name = "dpd_timeout")
    public Integer dpdTimeout;

    @JsonIgnore
    @ZoomField(name = "vpn_service_id")
    public UUID vpnServiceId;

    @ZoomField(name = "ike_policy_id")
    public UUID ikePolicyId;

    @ZoomField(name = "ipsec_policy_id")
    public UUID ipsecPolicyId;

    @ZoomEnum(clazz = Neutron.IPSecSiteConnection.Status.class)
    public enum Status {
        @ZoomEnumValue("ACTIVE") ACTIVE,
        @ZoomEnumValue("DOWN") DOWN,
        @ZoomEnumValue("BUILD") BUILD,
        @ZoomEnumValue("ERROR") ERROR,
        @ZoomEnumValue("PENDING_CREATE") PENDING_CREATE,
        @ZoomEnumValue("PENDING_UPDATE") PENDING_UPDATE,
        @ZoomEnumValue("PENDING_DELETE") PENDING_DELETE;
    }

    @ZoomEnum(clazz = Neutron.IPSecSiteConnection.DpdAction.class)
    public enum DpdAction {
        @ZoomEnumValue("CLEAR") CLEAR,
        @ZoomEnumValue("HOLD") HOLD,
        @ZoomEnumValue("RESTART") RESTART,
        @ZoomEnumValue("DISABLED") DISABLED,
        @ZoomEnumValue("RESTART_BY_PEER") RESTART_BY_PEER;
    }

    @ZoomEnum(clazz = Neutron.IPSecSiteConnection.AuthMode.class)
    public enum AuthMode {
        @ZoomEnumValue("PSK") PSK
    }

    @ZoomEnum(clazz = Neutron.IPSecSiteConnection.Initiator.class)
    public enum Initiator {
        @ZoomEnumValue("BI_DIRECTIONAL") BI_DIRECTIONAL,
        @ZoomEnumValue("RESPONSE_ONLY") RESPONSE_ONLY;
    }

    @ZoomEnum(clazz = Neutron.IPSecSiteConnection.RouteMode.class)
    public enum RouteMode {
        @ZoomEnumValue("STATIC") STATIC
    }

    public URI getUri() {
        return absoluteUri(ResourceUris.IPSEC_CONNECTIONS, id);
    }

    public URI getVpnService() {
        return absoluteUri(ResourceUris.VPN_SERVICES, vpnServiceId);
    }

    public URI getIkePolicy() {
        return absoluteUri(ResourceUris.IKE_POLICIES, ikePolicyId);
    }

    public URI getIpsecPolicy() {
        return absoluteUri(ResourceUris.IPSEC_POLICIES, ipsecPolicyId);
    }

    @Override
    @JsonIgnore
    public void create() {
        if (null == id) {
            id = UUID.randomUUID();
        }
    }

    @JsonIgnore
    public void create(UUID vpnServiceId) {
        create();
        this.vpnServiceId = vpnServiceId;
    }

    @JsonIgnore
    public void update(IPSecSiteConnection from) {
        id = from.id;
        vpnServiceId = from.vpnServiceId;
    }

}
