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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.MoreObjects;

import org.midonet.cluster.data.ZoomClass;
import org.midonet.cluster.data.ZoomField;
import org.midonet.cluster.models.Neutron;
import org.midonet.cluster.rest_api.ResourceUris;
import org.midonet.cluster.util.IPAddressUtil;

@ZoomClass(clazz = Neutron.VpnService.class)
public class VpnService extends UriResource {

    @ZoomField(name = "id")
    public UUID id;

    @ZoomField(name = "name")
    public String name;

    @ZoomField(name = "description")
    public String description;

    @ZoomField(name = "admin_state_up")
    public boolean adminStateUp = true;

    @ZoomField(name = "tenant_id")
    public String tenantId;

    @ZoomField(name = "router_id")
    public UUID routerId;

    @ZoomField(name = "subnet_id")
    public UUID subnetId;

    @ZoomField(name = "status")
    public String status;

    @ZoomField(name = "external_v4_ip", converter = IPAddressUtil.Converter.class)
    public String externalV4Ip;

    @ZoomField(name = "external_v6_ip", converter = IPAddressUtil.Converter.class)
    public String externalV6Ip;

    @JsonIgnore
    @ZoomField(name = "ipsec_site_connection_ids")
    public List<UUID> ipsecSiteConnectionIds;

    public URI getUri() {
        return absoluteUri(ResourceUris.VPN_SERVICES, id);
    }

    public URI getRouter() {
        return absoluteUri(ResourceUris.ROUTERS, routerId);
    }

    @Override
    @JsonIgnore
    public void create() {
        if (null == id) {
            id = UUID.randomUUID();
        }
    }

    @JsonIgnore
    public void update(VpnService from) {
        id = from.id;
        ipsecSiteConnectionIds = from.ipsecSiteConnectionIds;
    }

    public String toString() {
        return MoreObjects.toStringHelper(this)
            .omitNullValues()
            .add("id", id)
            .add("name", name)
            .add("description", description)
            .add("adminStateUp", adminStateUp)
            .add("tenantId", tenantId)
            .add("routerId", routerId)
            .add("subnetId", subnetId)
            .add("status", status)
            .add("externalV4Ip", externalV4Ip)
            .add("externalV6Ip", externalV6Ip)
            .toString();
    }
}
