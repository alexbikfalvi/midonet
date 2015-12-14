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

import javax.ws.rs.Path
import javax.ws.rs.core.MediaType._

import com.google.inject.Inject
import com.google.inject.servlet.RequestScoped

import org.midonet.cluster.rest_api.annotation._
import org.midonet.cluster.rest_api.models.IPSecPolicy
import org.midonet.cluster.services.rest_api.MidonetMediaTypes._
import org.midonet.cluster.services.rest_api.resources.MidonetResource.ResourceContext

@ApiResource(version = 1, name = "ipsecPolicies", template = "ipsecPolicyTemplate")
@Path("ipsec_policies")
@RequestScoped
@AllowGet(Array(APPLICATION_IPSEC_POLICY_JSON,
                APPLICATION_JSON))
@AllowList(Array(APPLICATION_IPSEC_POLICY_COLLECTION_JSON,
                 APPLICATION_JSON))
@AllowCreate(Array(APPLICATION_IPSEC_POLICY_JSON,
                   APPLICATION_JSON))
@AllowUpdate(Array(APPLICATION_IPSEC_POLICY_JSON,
                   APPLICATION_JSON))
@AllowDelete
class IPSecPolicyResource @Inject()(resContext: ResourceContext)
    extends MidonetResource[IPSecPolicy](resContext) {

    protected override def updateFilter(to: IPSecPolicy, from: IPSecPolicy,
                                        tx: ResourceTransaction): Unit = {
        to.update(from)
    }
}
