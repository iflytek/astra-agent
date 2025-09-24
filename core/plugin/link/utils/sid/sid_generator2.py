"""Service ID generator module for distributed systems.

Generates unique service IDs incorporating service metadata, location,
IP address, port, and timestamp information for distributed service identification.
"""

from __future__ import annotations

import os
import socket
import time

from plugin.link.consts import const

sid_generator2: SidGenerator2


def new_sid():
    """
    description: Generate SID
    :return:
    """
    return get_sid_generate().gen()


def get_sid_generate() -> SidGenerator2:
    """Get the global SID generator instance.

    Returns:
        SidGenerator2: The global SID generator instance
    """
    return sid_generator2


def spark_link_init_sid():
    """
    description: Initialize SID
    :return:
    """
    sub = os.getenv(const.SERVICE_SUB_KEY)
    location = os.getenv(const.SERVICE_LOCATION_KEY)
    local_port = os.getenv(const.SERVICE_PORT_KEY)
    local_ip = get_host_ip()
    init_sid(sub, location, local_ip, local_port)


def get_host_ip():
    """
    description: Query local IP
    """
    s = None
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.settimeout(3)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
    except Exception as err:
        raise Exception("failed to get local ip, err reason %s" % str(err))
    finally:
        if not s:
            s.close()

    return ip


def init_sid(sub: str, location: str, local_ip: str, local_port: str):
    """Initialize the global SID generator with service configuration.

    Args:
        sub: Service subsystem identifier
        location: Service location identifier
        local_ip: Local IP address of the service
        local_port: Local port of the service
    """
    global sid_generator2
    sid_generator2 = SidGenerator2(sub, location, local_ip, local_port)


class SidGenerator2:
    """Service ID generator for version 2.0 architecture.

    Generates unique service identifiers incorporating service metadata,
    timestamp, IP address, and process information.
    """

    # 2.0 architecture specific suffix
    sid2 = 2

    def __init__(self, sub, location, local_ip, local_port):
        self.index = 0
        ip = socket.inet_aton(local_ip)
        if ip:
            ip_sec3 = ip[2]
            ip_sec4 = ip[3]
            ip3 = ip_sec3 & 0xFF
            ip4 = ip_sec4 & 0xFF
            self.short_local_ip = f"{ip3:02x}{ip4:02x}"
        else:
            raise ValueError("Bad IP !! " + local_ip)
        if len(local_port) < 4:
            raise ValueError("Bad Port!! ")
        self.port = local_port
        self.location = location
        self.sub = sub
        print("sid init success")

    def gen(self):
        """Generate a unique service ID.

        Returns:
            str: Unique service ID in format:
            {sub}{pid}{index}@{location}{timestamp}{ip}{port}{version}
        """
        if len(self.sub) == 0:
            self.sub = "src"
        pid = os.getpid() & 0xFF
        self.index = (self.index + 1) & 0xFFFF
        tm_int = int(time.time() * 1000)
        tm = format(tm_int, "011x")
        sid = (
            f"{self.sub}{pid:04x}{self.index:04x}@"
            f"{self.location}{tm[-11:]}{self.short_local_ip}{self.port[:2]}{self.sid2}"
        )
        return sid


if __name__ == "__main__":
    os.environ["SERVICE_SUB"] = "spl"
    os.environ["SERVICE_LOCATION"] = "hf"
    os.environ["SERVICE_PORT"] = "18080"
    spark_link_init_sid()
    print(new_sid())
