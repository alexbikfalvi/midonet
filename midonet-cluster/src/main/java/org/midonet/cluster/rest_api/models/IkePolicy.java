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
import java.util.UUID;

import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.MoreObjects;

import org.midonet.cluster.data.ZoomClass;
import org.midonet.cluster.data.ZoomEnum;
import org.midonet.cluster.data.ZoomEnumValue;
import org.midonet.cluster.data.ZoomField;
import org.midonet.cluster.models.Neutron;
import org.midonet.cluster.rest_api.ResourceUris;
import org.midonet.cluster.rest_api.neutron.models.IPSecAuthAlgorithm;
import org.midonet.cluster.rest_api.neutron.models.IPSecEncryptionAlgorithm;
import org.midonet.cluster.rest_api.neutron.models.IPSecPfs;

@ZoomClass(clazz = org.midonet.cluster.models.Neutron.IkePolicy.class)
public class IkePolicy extends UriResource {

    @ZoomEnum(clazz = Neutron.IkePolicy.Phase1NegotiationMode.class)
    public enum Phase1NegotiationMode {
        @ZoomEnumValue("MAIN") MAIN
    }

    @ZoomEnum(clazz = Neutron.IkePolicy.IkeVersion.class)
    public enum IkeVersion {
        @ZoomEnumValue("V1") V1,
        @ZoomEnumValue("V2") V2
    }

    @ZoomField(name = "id")
    public UUID id;

    @ZoomField(name = "tenant_id")
    public String tenantId;

    @ZoomField(name = "name")
    @NotNull
    public String name;

    @ZoomField(name = "description")
    public String description;

    @ZoomField(name = "auth_algorithm")
    public IPSecAuthAlgorithm authAlgorithm = IPSecAuthAlgorithm.SHA1;

    @ZoomField(name = "encryption_algorithm")
    @NotNull
    public IPSecEncryptionAlgorithm encryptionAlgorithm;

    @ZoomField(name = "phase1_negotiation_mode")
    public Phase1NegotiationMode phase1NegotiationMode = Phase1NegotiationMode.MAIN;

    @ZoomField(name = "ike_version")
    @NotNull
    public IkeVersion ikeVersion;

    @ZoomField(name = "lifetime_value")
    @NotNull
    public Integer lifetimeValue;

    @ZoomField(name = "lifetime_units")
    public String lifetimeUnits;

    @ZoomField(name = "pfs")
    public IPSecPfs pfs;

    public URI getUri() {
        return absoluteUri(ResourceUris.IKE_POLICIES, id);
    }

    @Override
    @JsonIgnore
    public void create() {
        if (null == id) {
            id = UUID.randomUUID();
        }
    }

    @JsonIgnore
    public void update(IkePolicy from) {
        id = from.id;
    }

    public String toString() {
        return MoreObjects.toStringHelper(this)
            .omitNullValues()
            .add("id", id)
            .add("tenantId", tenantId)
            .add("name", name)
            .add("description", description)
            .add("authAlgorithm", authAlgorithm)
            .add("encryptionAlgorithm", encryptionAlgorithm)
            .add("phase1NegotiationMode", phase1NegotiationMode)
            .add("ikeVersion", ikeVersion)
            .add("lifetimeValue", lifetimeValue)
            .add("lifetimeUnits", lifetimeUnits)
            .add("pfs", pfs)
            .toString();
    }
}
