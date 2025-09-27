"""
Session ID generator module for creating unique session identifiers.
"""

from __future__ import annotations

import os
import socket
import time

from plugin.aitools.const.const import (
    SERVICE_LOCATION_KEY,
    SERVICE_PORT_KEY,
    SERVICE_SUB_KEY,
)

sid_generator2: SidGenerator2 = None


def new_sid():
    """
    description: 生成sid
    :return:
    """
    return get_sid_generate().gen()


def get_sid_generate() -> SidGenerator2:
    if not sid_generator2:
        service_sub = os.getenv(SERVICE_SUB_KEY)
        service_location = os.getenv(SERVICE_LOCATION_KEY)
        service_port = os.getenv(SERVICE_PORT_KEY)

        service_ip = get_host_ip()
        init_sid(service_sub, service_location, service_ip, service_port)
    return sid_generator2


def get_host_ip():
    """
    description:查询本机ip
    """
    s = None
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.settimeout(3)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
    except Exception as err:
        raise Exception(f"failed to get local ip, err reason {str(err)}") from err
    finally:
        if not s:
            s.close()

    return ip


def init_sid(sub: str, location: str, local_ip: str, local_port: str):
    global sid_generator2
    sid_generator2 = SidGenerator2(sub, location, local_ip, local_port)


class SidGenerator2:
    # 2.0架构专用后缀
    sid2 = 2

    def __init__(self, service_sub, service_location, host_ip, service_port):
        self.index = 0
        ip = socket.inet_aton(host_ip)
        if ip:
            ip_sec3 = ip[2]
            ip_sec4 = ip[3]
            ip3 = ip_sec3 & 0xFF
            ip4 = ip_sec4 & 0xFF
            self.short_local_ip = f"{ip3:02x}{ip4:02x}"
        else:
            raise ValueError("Bad IP !! " + host_ip)
        if len(service_port) < 4:
            raise ValueError("Bad Port!! ")
        self.port = service_port
        self.location = service_location
        self.sub = service_sub

    def gen(self):
        if len(self.sub) == 0:
            self.sub = "src"
        pid = os.getpid() & 0xFF
        self.index = (self.index + 1) & 0xFFFF
        tm_int = int(time.time() * 1000)
        tm = format(tm_int, "011x")
        sid = f"{self.sub}{pid:04x}{self.index:04x}@{self.location}{tm[-11:]}\
            {self.short_local_ip}{self.port[:2]}{self.sid2}"
        return sid
