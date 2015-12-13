package org.midonet.cluster.rest_api.neutron.models;

import org.midonet.cluster.data.ZoomEnum;
import org.midonet.cluster.data.ZoomEnumValue;
import org.midonet.cluster.models.Neutron;

@ZoomEnum(clazz = Neutron.IPSecAuthAlgorithm.class)
public enum IPSecAuthAlgorithm {
    @ZoomEnumValue("SHA1") SHA1
}
