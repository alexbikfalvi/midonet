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

import sys
import logging

from namespace_manager import NamespaceManager

if __name__ == '__main__':
    try:
        logging.basicConfig(format='%(asctime)s %(message)s', level=logging.INFO)
        logging.info("Running Topology Namespace Builder...")
        if sys.argv.__len__() is 5:
            namespaces = NamespaceManager(sys.argv[1], sys.argv[2], sys.argv[3],
                                          sys.argv[4])
            namespaces.create()
            raw_input("Press any key to clear the namespaces...")
            namespaces.clear()
        else:
            logging.error("USAGE: perf_namespaces <router-min> <router-max> "
                          "<bridges> <vms>")
            sys.exit(1)
    except Exception as e:
        logging.error("! ERROR %s", str(e))
        sys.exit(2)
