# Copyright 2015 Midokura SARL
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import logging

from midonetclient.api import MidonetApi
from bridge import Bridge
from router import Router


class TopologyManager(object):

    def __init__(self, url, tenant_id, router_min, router_max, bridge_count,
                 vm_count):
        logging.info("Starting topology with the MidoNet API at: %s", url)
        self._api = MidonetApi(url, 'midonet', 'midokura', 'services')
        self._tenant_id = tenant_id

        self._router_min = int(router_min) & 0xff
        self._router_max = int(router_max) & 0xff
        self._bridge_count = int(bridge_count) & 0x7f
        self._vm_count = int(vm_count) & 0xffff

        logging.info("> CONFIG %d-%d tenant router(s)", self._router_min,
                     self._router_max)
        logging.info("> CONFIG %d bridge(s) per tenant router",
                     self._bridge_count)
        logging.info("> CONFIG %d VM(s) per bridge", self._vm_count)

        self._hosts = {}
        self._tunnel_zone = None
        self._bridges = {}
        self._routers = {}

    def create(self):
        """
        Creates the current topology.
        """
        logging.info("Reading the physical topology...")
        self.get_hosts()
        logging.info("Creating the physical topology...")
        self.create_tunnel_zone()
        logging.info("Creating the virtual topology...")
        self.create_provider_router()
        self.create_routers()
        logging.info("Binding physical and virtual topologies...")
        self.create_bindings()

    def clear(self):
        """
        Clears the current topology.
        """
        logging.info("Clearing the topology...")
        for bridge in self._bridges.values():
            try:
                bridge.delete()
            except Exception as e:
                logging.error("! - BRIDGE %s %s", bridge, str(e))
        for router in self._routers.values():
            try:
                router.delete()
            except Exception as e:
                logging.error("! - ROUTER %s %s", router, str(e))
        try:
            self._tunnel_zone.delete()
        except Exception as e:
            logging.error("! - TUNNEL-ZONE %s", str(e))
        self._bridges.clear()
        self._routers.clear()

    def get_tenant_id(self):
        return self._tenant_id

    def get_hosts(self):
        self._hosts = self._api.get_hosts()
        for host in self._hosts:
            logging.info("+ HOST %s %s %s", host.get_name(), host.is_alive(),
                         host.get_addresses())
            interfaces = host.get_interfaces()
            for interface in interfaces:
                logging.info("+ INTERFACE %s %s %s %s", interface.get_name(),
                             interface.get_endpoint(), interface.get_mac(),
                             interface.get_addresses())

    def create_tunnel_zone(self):
        self._tunnel_zone = self._api.add_vxlan_tunnel_zone()
        self._tunnel_zone.name("test-tunnel-zone")
        self._tunnel_zone.create()
        logging.info("+ TUNNEL-ZONE %s", self._tunnel_zone.get_name())
        for host in self._hosts:
            if host.get_addresses():
                address = host.get_addresses()[0].replace("/", "")
                logging.info("+ TUNNEL-ZONE-HOST %s %s", host.get_name(),
                             address)
                tunnel_zone_host = self._tunnel_zone.add_tunnel_zone_host()
                tunnel_zone_host.ip_address(address)
                tunnel_zone_host.host_id(host.get_id())
                tunnel_zone_host.create()

    def create_bindings(self):
        for host in self._hosts:
            interfaces = filter(lambda i: "veth-" in i.get_name(),
                                host.get_interfaces())
            for interface in interfaces:
                try:
                    router_id, bridge_id, port_id = interface.get_name()\
                        .replace("veth-", "").split("-")

                    bridge_name = 'bridge-{0:05d}-{1:05d}'.format(
                        int(router_id), int(bridge_id))
                    port_name = 'vm-link-{0:05d}-{1:05d}-{2:05d}'.format(
                        int(router_id), int(bridge_id), int(port_id))

                    bridge = self._bridges[bridge_name]
                    port = bridge.get_port(port_name)

                    host.add_host_interface_port()\
                        .port_id(port.get_resource().get_id())\
                        .interface_name(interface.get_name())\
                        .create()

                    logging.info("+ BINDING %s %s <-> %s", host.get_name(),
                                 interface.get_name(), port_name)
                except Exception as e:
                    logging.error("! BINDING %s %s %s", host.get_name(),
                                  interface.get_name(), str(e))

    def create_provider_router(self):
        router = Router(self._api, self, {'name': 'provider-router'})
        router.create()
        self._routers[router.get_name()] = router
        for port_id in range(self._router_min, self._router_max):
            router.add_port({
                'name': 'provider-link-{0:05d}-a'.format(port_id),
                'address': '{0:d}.0.0.1/30'.format(port_id + 1)
            })
            router.add_route({
                'type': 'Normal',
                'src-address': '0.0.0.0/0',
                'dst-address': '{0:d}.0.0.0/30'.format(port_id + 1),
                'weight': '10',
                'next-hop-port': 'provider-link-{0:05d}-a'.format(port_id),
                'next-hop-gw': '255.255.255.255'
            })
            for subnet_id in range(0, self._bridge_count):
                router.add_route({
                    'type': 'Normal',
                    'src-address': '0.0.0.0/0',
                    'dst-address': '{0:d}.{1:d}.0.0/16'.format(
                        port_id + 1, (subnet_id + 1) | 0x80),
                    'weight': '10',
                    'next-hop-port': 'provider-link-{0:05d}-a'.format(port_id),
                    'next-hop-gw': '255.255.255.255'
                })

    def create_routers(self):
        for router_id in range(self._router_min, self._router_max):
            self.create_router(router_id)

    def create_router(self, router_id):
        router = Router(self._api, self, {
            'name': 'router-{0:05d}'.format(router_id)
        })
        router.create()
        router.add_port({
            'name': 'provider-link-{0:05d}-b'.format(router_id),
            'address': '{0:d}.0.0.2/30'.format(router_id + 1)
        }).link(self._routers['provider-router']
                .get_port('provider-link-{0:05d}-a'.format(router_id)))
        router.add_route({
            'type': 'Normal',
            'src-address': '0.0.0.0/0',
            'dst-address': '0.0.0.0/0',
            'weight': '30',
            'next-hop-port': 'provider-link-{0:05d}-b'.format(router_id),
            'next-hop-gw': '255.255.255.255'
        })

        for port_id in range(0, self._bridge_count):
            router.add_port({
                'name': 'router-link-{0:05d}-{1:05d}-a'.format(
                    router_id, port_id),
                'address': '{0:d}.{1:d}.0.1/16'.format(router_id + 1,
                                                       (port_id + 1) | 0x80)
            })
            router.add_route({
                'type': 'Normal',
                'src-address': '0.0.0.0/0',
                'dst-address': '{0:d}.{1:d}.0.0/16'.format(router_id + 1,
                                                           (port_id + 1) | 0x80),
                'weight': '10',
                'next-hop-port': 'router-link-{0:05d}-{1:05d}-a'.format(
                    router_id, port_id),
                'next-hop-gw': '255.255.255.255'
            })

        self._routers[router.get_name()] = router
        self.create_bridges(router_id)

    def create_bridges(self, router_id):
        for bridge_id in range(0, self._bridge_count):
            self.create_bridge(router_id, bridge_id)

    def create_bridge(self, router_id, bridge_id):
        bridge = Bridge(self._api, self, {
            'name': 'bridge-{0:05d}-{1:05d}'.format(router_id, bridge_id)
        })
        bridge.create()
        bridge.add_port({
            'name': 'router-link-{0:05d}-{1:05d}-b'.format(router_id, bridge_id)
        }).link(self._routers['router-{0:05d}'.format(router_id)]
                .get_port('router-link-{0:05d}-{1:05d}-a'.format(router_id,
                                                                 bridge_id)))
        for port_id in range(0, self._vm_count):
            bridge.add_port({
                'name': 'vm-link-{0:05d}-{1:05d}-{2:05d}'.format(
                    router_id, bridge_id, port_id)
            })
        self._bridges[bridge.get_name()] = bridge
