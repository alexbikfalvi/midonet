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

from resource import Resource


class RouterPort(Resource):

    def __init__(self, api, manager, router, data):
        super(RouterPort, self).__init__(api, manager, data)
        self._router = router
        self._inbound_filter = None
        self._outbound_filter = None

    def create(self):
        net_address, net_len = self._data.get('address').split('/')
        logging.info("+ ROUTER PORT %s %s", self._data['name'],
                     self._data['address'])
        self._resource = self._router.get_resource().add_port()
        self._resource.port_address(net_address)\
                      .network_address(net_address)\
                      .network_length(net_len)\
                      .create()

    def delete(self):
        logging.info("- ROUTER PORT %s", self._data['name'])
        if self._resource.get().get_peer_id():
            self._resource.unlink()
        self._resource.delete()

    def link(self, port):
        logging.info("> PORT %s <-> PORT %s", self.get_name(), port.get_name())
        self._resource.link(port.get_resource().get_id())

    def get_id(self):
        return self._resource.get_id()
