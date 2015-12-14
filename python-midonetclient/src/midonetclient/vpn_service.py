# Copyright (c) 2015 Midokura Europe SARL, All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may
# not use this file except in compliance with the License. You may obtain
# a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations
# under the License.

from midonetclient import admin_state_up_mixin
from midonetclient import ipsec_site_connection
from midonetclient import resource_base
from midonetclient import vendor_media_type

class VpnService(resource_base.ResourceBase,
                 admin_state_up_mixin.AdminStateUpMixin):

    media_type = vendor_media_type.APPLICATION_VPN_SERVICE_JSON

    def __init__(self, uri, dto, auth):
        super(VpnService, self).__init__(uri, dto, auth)

    def get_id(self):
        return self.dto['id']

    def get_tenant_id(self):
        return self.dto['tenantId']

    def get_name(self):
        return self.dto['name']

    def get_description(self):
        return self.dto['description']

    def get_router_id(self):
        return self.dto['routerId']

    def get_subnet_id(self):
        return self.dto['subnetId']

    def get_status(self):
        return self.dto['status']

    def get_external_v4_ip(self):
        return self.dto['externalV4Ip']

    def get_external_v6_ip(self):
        return self.dto['externalV6Ip']

    def get_ipsec_connections(self, query=None):
        headers = {'Accept': vendor_media_type.APPLICATION_IPSEC_SITE_CONNECTION_COLLECTION_JSON }
        return self.get_children(self.dto['ipsecConnections'], query, headers,
                                 ipsec_site_connection.IPSecSiteConnection)

    def name(self, name):
        self.dto['name'] = name
        return self

    def description(self, description):
        self.dto['description'] = description
        return self

    def router_id(self, router_id):
        self.dto['routerId'] = router_id
        return self

    def subnet_id(self, subnet_id):
        self.dto['subnetId'] = subnet_id
        return self

    def external_v4_ip(self, external_v4_ip):
        self.dto['externalV4Ip'] = external_v4_ip
        return self

    def external_v6_ip(self, external_v6_ip):
        self.dto['externalV6Ip'] = external_v6_ip
        return self

    def add_ipsec_connection(self):
        return ipsec_site_connection.IPSecSiteConnection(self.dto['ipsecConnections'],
                                                         {}, self.auth)
