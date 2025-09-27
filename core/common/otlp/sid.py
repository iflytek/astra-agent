import os
import socket
import time
from typing import Optional

from pydantic import BaseModel


class SidInfo(BaseModel):
    sub: str
    location: str
    index: int
    local_ip: str
    local_port: str


sid_generator2: Optional["SidGenerator2"] = None


def init_sid(sid_info: SidInfo) -> None:
    global sid_generator2
    sid_generator2 = SidGenerator2(sid_info=sid_info)


class SidGenerator2:
    sid2 = 2

    def __init__(self, sid_info: SidInfo):

        self.sid_info = sid_info

        ip = socket.inet_aton(self.sid_info.local_ip)
        if ip:
            ip_sec3 = ip[2]
            ip_sec4 = ip[3]
            ip3 = ip_sec3 & 0xFF
            ip4 = ip_sec4 & 0xFF
            self.short_local_ip = f"{ip3:02x}{ip4:02x}"
        else:
            raise ValueError("Bad IP !! " + self.sid_info.local_ip)
        if len(self.sid_info.local_port) < 4:
            raise ValueError("Bad Port!! " + self.sid_info.local_port)

    def gen(self) -> str:
        pid = os.getpid() & 0xFF
        self.index = (self.sid_info.index + 1) & 0xFFFF
        tm_int = int(time.time() * 1000)
        tm = format(tm_int, "011x")
        sid = f"{self.sid_info.sub}{pid:04x}{self.sid_info.index:04x}@{self.sid_info.location}{tm[-11:]}{self.short_local_ip}{self.sid_info.local_port[:2]}{self.sid2}"
        return sid
