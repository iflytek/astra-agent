import ctypes
from typing import Optional

from common.metrology_auth.base import BaseClass


class Authorization(BaseClass):

    def __init__(self, ctype_filename: str):
        self.lib = self.get_lib(ctype_filename)

        class CheckReturnType(ctypes.Structure):
            _fields_ = [
                ("r0", ctypes.c_char_p),
                ("r1", ctypes.c_char_p),
                ("r2", ctypes.c_char_p),
            ]

        class CheckV2ReturnType(ctypes.Structure):
            _fields_ = [
                ("r0", ctypes.c_char_p),
                ("r1", ctypes.c_char_p),
                ("r2", ctypes.c_char_p),
            ]

        self.CheckReturnType = CheckReturnType
        self.CheckV2ReturnType = CheckV2ReturnType

        self.lib.Check.restype = self.CheckReturnType
        self.lib.Check.argtypes = [
            ctypes.c_char_p,
            ctypes.c_char_p,
            ctypes.c_char_p,
            ctypes.POINTER(ctypes.c_char_p),
            ctypes.c_int,
            ctypes.c_char_p,
        ]

        self.lib.CheckV2.restype = self.CheckV2ReturnType
        self.lib.CheckV2.argtypes = [
            ctypes.c_char_p,
            ctypes.c_char_p,
            ctypes.c_char_p,
            ctypes.POINTER(ctypes.c_char_p),
            ctypes.c_int,
            ctypes.c_char_p,
        ]

        self.lib.Check_Init.restype = ctypes.c_char_p
        self.lib.Check_Init.argtypes = [
            ctypes.c_char_p,
            ctypes.c_char_p,
            ctypes.c_char_p,
            ctypes.c_char_p,
            ctypes.c_char_p,
            ctypes.c_int,
            ctypes.POINTER(ctypes.c_char_p),
            ctypes.c_int,
            ctypes.c_char_p,
        ]

        self.lib.Check_Fini.restype = None
        self.lib.Check_Fini.argtypes = []

        self.check_inited = False

    def check_init(
        self,
        url: str,
        pro: str,
        gro: str,
        service: str,
        version: str,
        mode: int,
        channel_list: list[str],
        sname: str,
    ) -> Optional[str]:
        b_url = url.encode()
        b_pro = pro.encode()
        b_gro = gro.encode()
        b_service = service.encode()
        b_version = version.encode()
        b_sname = sname.encode()
        channel_array = (ctypes.c_char_p * len(channel_list))(
            *[b_c.encode("utf-8") for b_c in channel_list]
        )
        result = self.lib.Check_Init(
            b_url,
            b_pro,
            b_gro,
            b_service,
            b_version,
            mode,
            channel_array,
            len(channel_list),
            b_sname,
        )
        if not result:
            self.check_inited = True
            return None
        else:
            return result.decode()

    def check(
        self, appid: str, uid: str, channel: str, func_list: list[str], tag: str
    ) -> tuple[Optional[str], Optional[str], Optional[str]]:
        # if self.check_inited is False:
        #     raise Exception("check not inited")
        b_appid = appid.encode()
        b_uid = uid.encode()
        b_channel = channel.encode()
        func_array = (ctypes.c_char_p * len(func_list))(
            *[b_f.encode() for b_f in func_list]
        )
        b_tag = tag.encode()
        check_result = self.lib.Check(
            b_appid, b_uid, b_channel, func_array, len(func_list), b_tag
        )
        return (
            check_result.r0.decode() if check_result.r0 else None,
            check_result.r1.decode() if check_result.r1 else None,
            check_result.r2.decode() if check_result.r2 else None,
        )

    def checkV2(
        self, appid: str, uid: str, channel: str, func_list: list[str], tag: str
    ) -> tuple[Optional[str], Optional[str], Optional[str]]:
        # if self.check_inited is False:
        #     raise Exception("check not inited")
        b_appid = appid.encode()
        b_uid = uid.encode()
        b_channel = channel.encode()
        func_array = (ctypes.c_char_p * len(func_list))(
            *[b_f.encode() for b_f in func_list]
        )
        b_tag = tag.encode()
        check_result = self.lib.CheckV2(
            b_appid, b_uid, b_channel, func_array, len(func_list), b_tag
        )
        return (
            check_result.r0.decode() if check_result.r0 else None,
            check_result.r1.decode() if check_result.r1 else None,
            check_result.r2.decode() if check_result.r2 else None,
        )

    def check_fini(self) -> None:
        self.lib.Check_Fini()
