"""
OSS storage client module providing object storage service interface.
"""

import logging
from urllib.parse import urlencode

import requests
from plugin.aitools.service.ase_sdk.exception.CustomException import CustomException
from plugin.aitools.service.ase_sdk.util.hmac_auth import HMACAuth

from const.err_code.code import CodeEnum


class OSSClient:
    def __init__(
        self,
        endpoint: str,
        access_key_id: str,
        access_key_secret: str,
        bucket_name: str,
        ttl: int,
    ):
        self.endpoint = endpoint
        self.access_key_id = access_key_id
        self.access_key_secret = access_key_secret
        self.bucket_name = bucket_name
        self.ttl = ttl

    def invoke(self, filename, data) -> str:
        """
        能力执行

        Args:
            filename:文件名
            data:    文件内容

        Returns:

        """
        url = f"{self.endpoint}/api/v1/{self.bucket_name}"
        params = {
            "get_link": "true",
            "link_ttl": self.ttl,
            "filename": filename,
            "expose": "true",
        }
        url = url + "?" + urlencode(params)
        headers = HMACAuth.build_auth_header(
            url,
            method="POST",
            api_key=self.access_key_id,
            api_secret=self.access_key_secret,
        )
        headers["X-TTL"] = str(self.ttl)
        headers["Content-Length"] = str(len(data))

        resp = requests.post(url, headers=headers, data=data, timeout=10)
        if resp.status_code != 200:
            logging.error(
                f"invoke oss error, status_code: {resp.status_code}, \
                    message: {resp.text}"
            )
            raise CustomException(
                CodeEnum.OSS_STORAGE_ERROR.code, CodeEnum.OSS_STORAGE_ERROR.msg
            )

        ret = resp.json()
        if ret["code"] != 0:
            logging.error(
                f"invoke oss error, code: {ret['code']}, message: {ret['message']}"
            )
            raise CustomException(
                CodeEnum.OSS_STORAGE_ERROR.code, CodeEnum.OSS_STORAGE_ERROR.msg
            )

        return ret["data"]["link"]
