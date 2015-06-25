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
import subprocess


class NamespaceManager(object):

    def __init__(self, router_min, router_max, bridge_count, vm_count):
        self._router_min = int(router_min)
        self._router_max = int(router_max)
        self._bridge_count = int(bridge_count)
        self._vm_count = int(vm_count)

        self._namespaces = []

    def create(self):
        logging.info('Creating the namespaces...')
        for router_id in range(self._router_min, self._router_max):
            for bridge_id in range(0, self._bridge_count):
                for port_id in range(0, self._vm_count):
                    self.create_namespace(router_id, bridge_id, port_id)

    def clear(self):
        logging.info('Clearing the namespaces...')
        for router_id in range(self._router_min, self._router_max):
            for bridge_id in range(0, self._bridge_count):
                for port_id in range(0, self._vm_count):
                    self.delete_namespace(router_id, bridge_id, port_id)

    def create_namespace(self, router_id, bridge_id, port_id):
        ns_name = 'ns-{0:d}-{1:d}-{2:d}'.format(router_id, bridge_id,
                                                port_id)
        in_name = 'if-{0:d}-{1:d}-{2:d}'.format(router_id, bridge_id,
                                                port_id)
        out_name = 'veth-{0:d}-{1:d}-{2:d}'.format(router_id, bridge_id,
                                                   port_id)
        address = '{0:d}.{1:d}.{2:d}.{3:d}/16'.format(
            router_id + 1, (bridge_id + 1) | 0x80, (port_id + 2) >> 8,
            (port_id + 2) & 0xff)
        gw = '{0:d}.{1:d}.0.1'.format(router_id + 1,
                                      (bridge_id + 1) | 0x80)
        logging.info('+ NAMESPACE %s %s <-> %s (%s)', ns_name, out_name,
                     in_name, address)
        try:
            subprocess.call(['sudo ip netns add {0:s}'.format(ns_name)],
                            shell=True)
            subprocess.call(['sudo ip link add {0:s} type veth peer name '
                             '{1:s}'.format(out_name, in_name)], shell=True)
            subprocess.call(['sudo ip link set {0:s} netns {1:s}'.format(
                in_name, ns_name)], shell=True)
            subprocess.call(['sudo ifconfig {0:s} up'.format(out_name)],
                            shell=True)
            subprocess.call(['sudo ip netns exec {0:s} ifconfig lo up'.format(
                ns_name)], shell=True)
            subprocess.call(['sudo ip netns exec {0:s} ifconfig {1:s} {2:s} '
                             'up'.format(ns_name, in_name, address)],
                            shell=True)
            subprocess.call(['sudo ip netns exec {0:s} ip route add default '
                             'via {1:s}'.format(ns_name, gw)],
                            shell=True)
            self._namespaces.append(ns_name)
        except Exception as e:
            logging.error("! NAMESPACE %s %s", ns_name, str(e))

    def delete_namespace(self, router_id, bridge_id, port_id):
        ns_name = 'ns-{0:d}-{1:d}-{2:d}'.format(router_id, bridge_id,
                                                port_id)
        logging.info('- NAMESPACE %s', ns_name)
        try:
            subprocess.call(['sudo ip netns delete {0:s}'.format(ns_name)],
                            shell=True)
        except Exception as e:
            logging.error("! NAMESPACE %s %s", ns_name, str(e))
