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

import java.util
import java.util.UUID
import javax.ws.rs._
import javax.ws.rs.core.MediaType.APPLICATION_JSON
import javax.ws.rs.core.Response

import scala.collection.JavaConversions._
import com.google.inject.Inject
import com.google.inject.servlet.RequestScoped

import org.midonet.cluster.models.Topology
import org.midonet.cluster.rest_api.annotation._
import org.midonet.cluster.rest_api.models.{RouterPort, ServiceContainer, ServiceContainerGroup}
import org.midonet.cluster.services.rest_api.MidonetMediaTypes._
import org.midonet.cluster.services.rest_api.resources.MidonetResource._
import org.midonet.cluster.util.UUIDUtil._

@ApiResource(version = 1,
             name = "serviceContainers",
             template = "serviceContainerTemplate")
@Path("service_containers")
@RequestScoped
@AllowCreate(Array(APPLICATION_SERVICE_CONTAINER_JSON,
                   APPLICATION_JSON))
@AllowGet(Array(APPLICATION_SERVICE_CONTAINER_JSON,
                APPLICATION_JSON))
@AllowList(Array(APPLICATION_SERVICE_CONTAINER_COLLECTION_JSON,
                 APPLICATION_JSON))
@AllowDelete
class ServiceContainerResource @Inject()(resContext: ResourceContext)
    extends MidonetResource[ServiceContainer](resContext) {

    private var parentResId: UUID = null

    def this(resContext: ResourceContext, parentResId: UUID) = {
        this(resContext)
        this.parentResId = parentResId
    }

    protected override def getFilter(t: ServiceContainer): ServiceContainer = {
        init(t)
    }

    protected override def listFilter(list: Seq[ServiceContainer])
    : Seq[ServiceContainer] = {
        list.foreach(init)
        list
    }

    protected override def createFilter(sc: ServiceContainer,
                                        tx: ResourceTransaction): Unit = {
        if (parentResId != null) {
            sc.serviceGroupId = parentResId
        }
        super.createFilter(sc, tx)
    }

    private def init(container: ServiceContainer): ServiceContainer = {
        if (container.portId ne null) {
            val port = getResource(classOf[RouterPort], container.portId)
            container.hostId = port.hostId
        }
        container
    }

    @PUT
    @Path("{id}")
    override def update(@PathParam("id") id: String, t: ServiceContainer,
                        @HeaderParam("Content-Type") contentType: String)
    : Response = {
        OkNoContentResponse
    }

    @POST
    @Path("{id}/schedule")
    @Consumes(Array(APPLICATION_SERVICE_CONTAINER_SCHEDULE_JSON,
                    APPLICATION_JSON))
    def link(@PathParam("id") id: UUID, container: ServiceContainer): Response = tryTx { tx =>
        container.setBaseUri(resContext.uriInfo.getBaseUri)
        if (container.portId ne null) {
            val port = tx.tx.get(classOf[Topology.Port], container.portId)
            tx.tx.update(port.toBuilder.setHostId(container.hostId.asProto).build())
        }
        Response.created(container.getUri).build()
    }

    @DELETE
    @Path("{id}/schedule")
    def unlink(@PathParam("id") id: UUID): Response = tryTx { tx =>
        val container = tx.get(classOf[ServiceContainer], id)
        if (container.portId ne null) {
            val port = tx.tx.get(classOf[Topology.Port], container.portId)
            tx.tx.update(port.toBuilder.clearHostId().build())
        }
        OkNoContentResponse
    }
}

@ApiResource(version = 1,
             name = "serviceContainerGroups",
             template = "serviceContainerGroupTemplate")
@Path("service_container_groups")
@RequestScoped
@AllowCreate(Array(APPLICATION_SERVICE_CONTAINER_GROUP_JSON,
                   APPLICATION_JSON))
@AllowGet(Array(APPLICATION_SERVICE_CONTAINER_GROUP_JSON,
                APPLICATION_JSON))
@AllowList(Array(APPLICATION_SERVICE_CONTAINER_GROUP_COLLECTION_JSON,
                 APPLICATION_JSON))
@AllowDelete
class ServiceContainerGroupResource @Inject()(resContext: ResourceContext)
    extends MidonetResource[ServiceContainerGroup](resContext) {

    @GET
    @Path("{id}/service_containers")
    @Produces(Array(APPLICATION_SERVICE_CONTAINER_COLLECTION_JSON))
    def listServiceContainers(@PathParam("id") id: UUID)
    : util.List[ServiceContainer] = {
        // Let's 404 if the SCG doesn't exist
        val scg = getResource(classOf[ServiceContainerGroup], id)
        if (scg.serviceContainerIds != null) {
            scg.serviceContainerIds map { containerId =>
                init(getResource(classOf[ServiceContainer], containerId))
            }
        } else {
            List.empty[ServiceContainer]
        }
    }

    @POST
    @Consumes(Array(APPLICATION_SERVICE_CONTAINER_JSON))
    @Path("{id}/service_containers")
    def createServiceContainer(@PathParam("id") id: UUID,
                               @HeaderParam("Content-Type") ct: String,
                               sc: ServiceContainer): Response = {
        // Let's 404 if the SCG doesn't exist
        val scg = getResource(classOf[ServiceContainerGroup], id)
        new ServiceContainerResource(resContext, scg.id) // should be == id
            .create(sc, APPLICATION_SERVICE_CONTAINER_JSON)
    }

    private def init(container: ServiceContainer): ServiceContainer = {
        if (container.portId ne null) {
            val port = getResource(classOf[RouterPort], container.portId)
            container.hostId = port.hostId
        }
        container
    }
}
