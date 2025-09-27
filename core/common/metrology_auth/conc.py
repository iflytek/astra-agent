"""
conc.py
"""

import ctypes
from typing import Any, Optional

from common.metrology_auth.base import BaseClass


class Concurrent(BaseClass):

    def __init__(self, ctype_filename: str):
        self.lib = self.get_lib(ctype_filename)

        class ResultType(ctypes.Structure):
            _fields_ = [
                ("_pass", ctypes.c_int),
                ("addr", ctypes.c_char_p),
                ("currentUsed", ctypes.c_char_p),
                ("err", ctypes.c_char_p),
            ]

        self.ResultType = ResultType

        self.lib.Conc_Init.restype = ctypes.c_char_p
        self.lib.Conc_Init.argtypes = [
            ctypes.c_char_p,
            ctypes.c_char_p,
            ctypes.c_char_p,
            ctypes.c_char_p,
            ctypes.c_char_p,
            ctypes.c_int,
            ctypes.c_char_p,
            ctypes.c_char_p,
        ]

        self.lib.AcquireConc.restype = ResultType
        self.lib.AcquireConc.argtypes = [
            ctypes.c_char_p,
            ctypes.c_char_p,
            ctypes.c_char_p,
            ctypes.c_char_p,
        ]

        self.lib.ReleaseConc.restype = ctypes.c_char_p
        self.lib.ReleaseConc.argtypes = [
            ctypes.c_char_p,
            ctypes.c_char_p,
            ctypes.c_char_p,
            ctypes.c_char_p,
        ]

        self.conc_inited = False

    def conc_init(
        self,
        url: str,
        pro: str,
        gro: str,
        service: str,
        version: str,
        mode: int,
        addr: str,
        sname: str,
    ) -> Optional[str]:
        b_url = url.encode()
        b_pro = pro.encode()
        b_gro = gro.encode()
        b_service = service.encode()
        b_version = version.encode()
        b_addr = addr.encode()
        b_sname = sname.encode()
        result = self.lib.Conc_Init(
            b_url, b_pro, b_gro, b_service, b_version, mode, b_addr, b_sname
        )
        if not result:
            self.conc_inited = True
            return None
        else:
            return result.decode()

    def acquire_conc(
        self, sid: str, appid: str, channel: str, function: str
    ) -> tuple[str, Any, Any, Any, Any]:
        b_sid = sid.encode()
        b_appid = appid.encode()
        b_channel = channel.encode()
        b_function = function.encode()
        conc_result = self.lib.AcquireConc(b_sid, b_appid, b_channel, b_function)
        return (
            b_sid.decode() if b_sid else "",
            conc_result._pass,
            conc_result.addr.decode() if conc_result.addr else "",
            conc_result.currentUsed.decode() if conc_result.currentUsed else "",
            conc_result.err.decode() if conc_result.err else "",
        )

    def release_conc(
        self, sid: str, appid: str, channel: str, function: str
    ) -> Optional[str]:
        b_sid = sid.encode()
        b_appid = appid.encode()
        b_channel = channel.encode()
        b_function = function.encode()
        conc_result = self.lib.ReleaseConc(b_sid, b_appid, b_channel, b_function)
        return conc_result.decode() if conc_result else ""
