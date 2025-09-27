import ctypes
from typing import Optional

from common.metrology_auth.base import BaseClass


class Report(BaseClass):

    def __init__(self, ctype_filename: str):
        self.lib = self.get_lib(ctype_filename)

        self.lib.Report_Init.restype = ctypes.c_char_p
        self.lib.Report_Init.argtypes = [
            ctypes.c_char_p,
            ctypes.c_char_p,
            ctypes.c_char_p,
            ctypes.c_char_p,
            ctypes.c_char_p,
            ctypes.c_int,
            ctypes.c_char_p,
            ctypes.c_char_p,
        ]

        self.lib.Report.restype = ctypes.c_char_p
        self.lib.Report.argtypes = [
            ctypes.c_char_p,
            ctypes.POINTER(ctypes.c_char_p),
            ctypes.c_int,
            ctypes.POINTER(ctypes.c_uint),
        ]

        self.lib.Report_Fini.restype = None
        self.lib.Report_Fini.argtypes = []

        self.report_inited = False

    def report_init(
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
        """
        初始化上报
        """
        b_url = url.encode()
        b_pro = pro.encode()
        b_gro = gro.encode()
        b_service = service.encode()
        b_version = version.encode()
        b_addr = addr.encode()
        b_sname = sname.encode()
        result = self.lib.Report_Init(
            b_url, b_pro, b_gro, b_service, b_version, mode, b_addr, b_sname
        )
        if not result:
            self.report_inited = True
            return None
        else:
            return result.decode()

    def report(
        self, channel: str, conc_info_keys: list[str], conc_info_values: list[int]
    ) -> Optional[str]:
        b_channel = channel.encode()
        conc_info_keys_array = (ctypes.c_char_p * len(conc_info_keys))(
            *[b_k.encode() for b_k in conc_info_keys]
        )
        conc_info_values_array = (ctypes.c_uint * len(conc_info_values))(
            *conc_info_values
        )
        result = self.lib.Report(
            b_channel, conc_info_keys_array, len(conc_info_keys), conc_info_values_array
        )
        return result.decode() if result else None

    def report_fini(self) -> None:
        self.lib.Report_Fini()
