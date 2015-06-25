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
from router_port import RouterPort
from route import Route

class Router(Resource):

    def __init__(self, api, manager, data):
        super(Router, self).__init__(api, manager, data)
        self._ports = {}
        self._routes = {}

    def create(self):
        logging.info("+ ROUTER %s", self._data['name'])
        self._resource = self._api.add_router()
        self._resource.tenant_id(self._manager.get_tenant_id())
        self._resource.name(self._data['name'])
        self._resource.create()

    def delete(self):
        for route in self._routes.values():
            route.delete()
        for port in self._ports.values():
            port.delete()
        logging.info("- ROUTER %s", self._data['name'])
        self._resource.delete()

    def get_port(self, name):
        return self._ports[name]

    def add_port(self, data):
        port = RouterPort(self._api, self._manager, self, data)
        port.create()
        self._ports[data['name']] = port
        return port

    def add_route(self, data):
        route = Route(self._api, self._manager, self, data)
        route.create()
        self._routes[route.get_resource().get_id()] = route
        return route