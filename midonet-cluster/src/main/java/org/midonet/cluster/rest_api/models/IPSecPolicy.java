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

@ZoomClass(clazz = Neutron.IPSecPolicy.class)
public class IPSecPolicy extends UriResource {

    @ZoomField(name = "id")
    public UUID id;

    @ZoomField(name = "tenant_id")
    public String tenantId;

    @ZoomField(name = "name")
    public String name;

    @ZoomField(name = "description")
    public String description;

    @ZoomField(name = "transform_protocol")
    public TransformProtocol transformProtocol;

    @ZoomField(name = "auth_algorithm")
    public IPSecAuthAlgorithm authAlgorithm = IPSecAuthAlgorithm.SHA1;

    @ZoomField(name = "encryption_algorithm")
    public IPSecEncryptionAlgorithm encryptionAlgorithm;

    @ZoomField(name = "encapsulation_mode")
    public EncapsulationMode encapsulationMode;

    @ZoomField(name = "lifetime_value")
    public Integer lifetimeValue;

    @ZoomField(name = "lifetime_units")
    public String lifetimeUnits;

    @ZoomField(name = "pfs")
    public IPSecPfs pfs;

    @ZoomEnum(clazz = Neutron.IPSecPolicy.TransformProtocol.class)
    public enum TransformProtocol {
        @ZoomEnumValue("ESP") ESP,
        @ZoomEnumValue("AH") AH,
        @ZoomEnumValue("AH_ESP") AH_ESP
    }

    @ZoomEnum(clazz = Neutron.IPSecPolicy.EncapsulationMode.class)
    public enum EncapsulationMode {
        @ZoomEnumValue("TUNNEL") TUNNEL,
        @ZoomEnumValue("TRANSPORT") TRANSPORT
    }

    public URI getUri() {
        return absoluteUri(ResourceUris.IPSEC_POLICIES, id);
    }

    @Override
    @JsonIgnore
    public void create() {
        if (null == id) {
            id = UUID.randomUUID();
        }
    }

    @JsonIgnore
    public void update(IPSecPolicy from) {
        id = from.id;
    }

    public String toString() {
        return MoreObjects.toStringHelper(this)
            .omitNullValues()
            .add("id", id)
            .add("tenantId", tenantId)
            .add("name", name)
            .add("description", description)
            .add("transformProtocol", transformProtocol)
            .add("encryptionAlgorithm", encryptionAlgorithm)
            .add("encapsulationMode", encapsulationMode)
            .add("lifetimeValue", lifetimeValue)
            .add("lifetimeUnits", lifetimeUnits)
            .add("pfs", pfs)
            .toString();
    }
}
