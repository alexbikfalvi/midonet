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

from resource import Resource

import logging


class Route(Resource):

    def __init__(self, api, manager, router, data):
        super(Route, self).__init__(api, manager, data)
        self._router = router

    def create(self):
        route_type = self._data['type']
        src_address, src_len = self._data['src-address'].split('/')
        dst_address, dst_len = self._data['dst-address'].split('/')
        weight = self._data['weight']
        next_hop_port = self._data['next-hop-port']
        next_hop_gw = self._data['next-hop-gw']
        logging.info("+ ROUTE %s %s/%s %s/%s %s %s %s",
                     route_type, src_address, src_len, dst_address, dst_len,
                     weight, next_hop_port, next_hop_gw)

        port = self._router.get_port(next_hop_port).get_resource()

        self._resource = self._router.get_resource().add_route()\
            .type(route_type)\
            .src_network_addr(src_address)\
            .src_network_length(src_len)\
            .dst_network_addr(dst_address)\
            .dst_network_length(dst_len)\
            .weight(weight)\
            .next_hop_port(port.get_id())\
            .next_hop_gateway(next_hop_gw)\
            .create()

    def delete(self):
        route_type = self._data['type']
        src_address, src_len = self._data['src-address'].split('/')
        dst_address, dst_len = self._data['dst-address'].split('/')
        weight = self._data['weight']
        next_hop_port = self._data['next-hop-port']
        next_hop_gw = self._data['next-hop-gw']
        logging.info("- ROUTE %s %s/%s %s/%s %s %s %s",
                     route_type, src_address, src_len, dst_address, dst_len,
                     weight, next_hop_port, next_hop_gw)

        self._resource.delete()