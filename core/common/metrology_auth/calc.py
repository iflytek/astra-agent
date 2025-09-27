"""
calc.py
"""

import ctypes
from typing import Optional

from common.metrology_auth.base import BaseClass


class Metrology(BaseClass):

    def __init__(self, ctype_filename: str):
        self.lib = self.get_lib(ctype_filename)

        class CalcReturnType(ctypes.Structure):
            _fields_ = [("r0", ctypes.c_int), ("r1", ctypes.c_char_p)]

        self.CalcReturnType = CalcReturnType

        self.lib.Calc.restype = self.CalcReturnType

        self.lib.Calc.argtypes = [
            ctypes.c_char_p,
            ctypes.c_char_p,
            ctypes.c_char_p,
            ctypes.c_longlong,
        ]

        self.lib.Calc_Init.restype = ctypes.c_char_p
        self.lib.Calc_Init.argtypes = [
            ctypes.c_char_p,
            ctypes.c_char_p,
            ctypes.c_char_p,
            ctypes.c_char_p,
            ctypes.c_char_p,
            ctypes.c_int,
            ctypes.c_char_p,
        ]

        self.lib.Calc_Fini.restype = None
        self.lib.Calc_Fini.argtypes = []

        self.calc_inited = False

    def calc_init(
        self,
        url: str,
        pro: str,
        gro: str,
        service: str,
        version: str,
        mode: int,
        sname: str,
    ) -> Optional[str]:
        b_url = url.encode()
        b_pro = pro.encode()
        b_gro = gro.encode()
        b_service = service.encode()
        b_version = version.encode()
        b_sname = sname.encode()
        result = self.lib.Calc_Init(
            b_url, b_pro, b_gro, b_service, b_version, mode, b_sname
        )
        if not result:
            self.calc_inited = True
            return None
        else:
            return result.decode()

    def calc(
        self, appid: str, channel: str, funcs: str, c: int
    ) -> tuple[int, Optional[str]]:
        b_appid = appid.encode()
        b_channel = channel.encode()
        b_funcs = funcs.encode()
        calc_result = self.lib.Calc(b_appid, b_channel, b_funcs, c)
        return calc_result.r0, calc_result.r1.decode() if calc_result.r1 else None

    def calc_fini(self) -> None:
        self.lib.Calc_Fini()
