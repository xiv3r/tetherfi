#!/usr/bin/python3

# Copyright (C) 2025 pyamsoft
# 
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License.  You may obtain a copy
# of the License at
# 
#     http://www.apache.org/licenses/LICENSE-2.0
# 
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
# License for the specific language governing permissions and limitations
# under the License.

import socket
from traceback import print_exc

FAILED_RESP: bytes = bytes([])

class NormalUdpRequest:
    def __init__(self):
        try:
            self.s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        except socket.error:
            print_exc()

    def request(
        self,
        request: bytes,
        remote_host: str,
        remote_port: int,
    ) -> bytes:
        try:
            self.s.sendto(request, (remote_host,  remote_port))
            (resp, _)= self.s.recvfrom(4096)
            return resp
        except socket.error:
            print_exc()
        return FAILED_RESP
