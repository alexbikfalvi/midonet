/*
 * Copyright 2016 Midokura SARL
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

package org.midonet.containers

import java.util.UUID

import javax.inject.Named

import scala.concurrent.{Promise, Future}

import akka.actor.ActorSystem

import com.google.inject.Inject

import rx.Observable

import org.midonet.midolman.containers.{ContainerHandler, ContainerPort, ContainerStatus}
import org.midonet.midolman.routingprotocols.RoutingManagerActor
import org.midonet.midolman.routingprotocols.RoutingManagerActor.{DeleteInterior, CreateInterior}
import org.midonet.midolman.topology.VirtualTopology

/**
  * Implements a [[ContainerHandler]] for a BGP end-point. The implementation
  * of this container is using the existing
  * [[org.midonet.midolman.routingprotocols.RoutingHandler]] actor.
  */
@Container(name = Containers.BGP_CONTAINER, version = 1)
class BgpContainer @Inject()(@Named("id") id: UUID,
                             vt: VirtualTopology,
                             implicit val actorSystem: ActorSystem)
    extends ContainerHandler with ContainerCommons {

    override def logSource = "org.midonet.containers.bgp"

    @volatile private var portId: UUID = null

    /**
      * @see [[ContainerHandler.create]]
      */
    override def create(port: ContainerPort): Future[Option[String]] = {
        val promise = Promise[Option[String]]
        portId = port.portId
        RoutingManagerActor.getRef() ! CreateInterior(port.portId, promise)
        promise.future
    }

    /**
      * @see [[ContainerHandler.updated]]
      */
    override def updated(port: ContainerPort): Future[Option[String]] = {
        delete() flatMap { _ => create(port) }
    }

    /**
      * @see [[ContainerHandler.delete]]
      */
    override def delete(): Future[Unit] = {
        val promise = Promise[Unit]
        RoutingManagerActor.getRef() ! DeleteInterior(portId, promise)
        promise.future
    }

    /**
      * @see [[ContainerHandler.cleanup]]
      */
    override def cleanup(name: String): Future[Unit] = {
        ???
    }

    /**
      * @see [[ContainerHandler.status]]
      */
    override def status: Observable[ContainerStatus] = {
        ???
    }
}
