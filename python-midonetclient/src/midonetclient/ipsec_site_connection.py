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
from midonetclient import resource_base
from midonetclient import vendor_media_type

class IPSecSiteConnection(resource_base.ResourceBase,
                          admin_state_up_mixin.AdminStateUpMixin):

    media_type = vendor_media_type.APPLICATION_IPSEC_SITE_CONNECTION_JSON

    def __init__(self, uri, dto, auth):
        super(IPSecSiteConnection, self).__init__(uri, dto, auth)

    def get_id(self):
        return self.dto['id']

    def get_tenant_id(self):
        return self.dto['tenantId']

    def get_name(self):
        return self.dto['name']

    def get_description(self):
        return self.dto['description']

    def get_peer_address(self):
        return self.dto['peerAddress']

    def get_local_cidrs(self):
        return self.dto['localCidrs']

    def get_peer_cidrs(self):
        return self.dto['peerCidrs']

    def get_route_mode(self):
        return self.dto['routeMode']

    def get_mtu(self):
        return self.dto['mtu']

    def get_initiator(self):
        return self.dto['initiator']

    def get_auth_mode(self):
        return self.dto['authMode']

    def get_psk(self):
        return self.dto['psk']

    def get_status(self):
        return self.dto['status']

    def get_dpd_action(self):
        return self.dto['dpdAction']

    def get_dpd_interval(self):
        return self.dto['dpdInterval']

    def get_dpd_timeout(self):
        return self.dto['dpdTimeout']

    def get_ike_policy_id(self):
        return self.dto['ikePolicyId']

    def get_ipsec_policy_id(self):
        return self.dto['ipsecPolicyId']

    def name(self, name):
        self.dto['name'] = name
        return self

    def description(self, description):
        self.dto['description'] = description
        return self

    def peer_address(self, address):
        self.dto['peerAddress'] = address
        return self

    def local_cidrs(self, local_cidrs):
        self.dto['localCidrs'] = local_cidrs
        return self

    def peer_cidrs(self, peer_cirds):
        self.dto['peerCidrs'] = peer_cirds
        return self

    def route_mode(self, route_mode):
        self.dto['routeMode'] = route_mode
        return self

    def mtu(self, mtu):
        self.dto['mtu'] = mtu
        return self

    def initiator(self, initiator):
        self.dto['initiator'] = initiator
        return self

    def auth_mode(self, auth_mode):
        self.dto['authMode'] = auth_mode
        return self

    def psk(self, psk):
        self.dto['psk'] = psk
        return self

    def dpd_action(self, dpd_action):
        self.dto['dpdAction'] = dpd_action
        return self

    def dpd_interval(self, dpd_interval):
        self.dto['dpdInterval'] = dpd_interval
        return self

    def dpd_timeout(self, dpd_timeout):
        self.dto['dpdTimeout'] = dpd_timeout
        return self

    def ike_policy_id(self, ike_policy_id):
        self.dto['ikePolicyId'] = ike_policy_id
        return self

    def ipsec_policy_id(self, ipsec_policy_id):
        self.dto['ipsecPolicyId'] = ipsec_policy_id
        return self
