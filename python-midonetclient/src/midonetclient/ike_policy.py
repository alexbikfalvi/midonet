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

from midonetclient import resource_base
from midonetclient import vendor_media_type

class IkePolicy(resource_base.ResourceBase):

    media_type = vendor_media_type.APPLICATION_IKE_POLICY_JSON

    def __init__(self, uri, dto, auth):
        super(IkePolicy, self).__init__(uri, dto, auth)

    def get_id(self):
        return self.dto['id']

    def get_tenant_id(self):
        return self.dto['tenantId']

    def get_name(self):
        return self.dto['name']

    def get_description(self):
        return self.dto['description']

    def get_auth_algorithm(self):
        return self.dto['authAlgorithm']

    def get_encryption_algorithm(self):
        return self.dto['encryptionAlgorithm']

    def get_phase1_negotiation_mode(self):
        return self.dto['phase1NegotiationMode']

    def get_ike_version(self):
        return self.dto['ikeVersion']

    def get_lifetime_value(self):
        return self.dto['lifetimeValue']

    def get_lifetime_units(self):
        return self.dto['lifetimeUnits']

    def get_pfs(self):
        return self.dto['pfs']

    def name(self, name):
        self.dto['name'] = name
        return self

    def description(self, description):
        self.dto['description'] = description
        return self

    def auth_algorithm(self, auth_algorithm):
        self.dto['authAlgorithm'] = auth_algorithm
        return self

    def encryption_algorithm(self, encryption_algorithm):
        self.dto['encryptionAlgorithm'] = encryption_algorithm
        return self

    def phase1_negotiation_mode(self, phase1_negotiation_mode):
        self.dto['phase1NegotiationMode'] = phase1_negotiation_mode
        return self

    def ike_version(self, ike_version):
        self.dto['ikeVersion'] = ike_version
        return self

    def lifetime_value(self, lifetime_value):
        self.dto['lifetimeValue'] = lifetime_value
        return self

    def lifetime_units(self, lifetime_units):
        self.dto['lifetimeUnits'] = lifetime_units
        return self

    def pfs(self, pfs):
        self.dto['pfs'] = pfs
        return self
