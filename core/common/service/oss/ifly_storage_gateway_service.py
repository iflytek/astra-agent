from typing import Optional
from urllib.parse import urlencode

# import boto3
import requests  # type: ignore[import-untyped]
from loguru import logger

from common.exceptions.codes import c9010
from common.exceptions.errs import OssServiceException
from common.service.base import Service, ServiceType
from common.service.oss.base_oss import BaseOSSService
from common.utils.hmac_auth import HMACAuth


class IFlyGatewayStorageClient(BaseOSSService, Service):

    name = ServiceType.OSS_SERVICE

    def __init__(
        self,
        endpoint: str,
        access_key_id: str,
        access_key_secret: str,
        bucket_name: str,
        ttl: int,
    ):
        """


        Args:
            endpoint:
            access_key_id:
            access_key_secret:
            bucket_name:
            ttl:
        """
        self.endpoint = endpoint
        self.access_key_id = access_key_id
        self.access_key_secret = access_key_secret
        self.bucket_name = bucket_name
        self.ttl = ttl

    def upload_file(
        self, filename: str, file_bytes: bytes, bucket_name: Optional[str] = None
    ) -> str:

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
        headers["Content-Length"] = str(len(file_bytes))
        try:
            resp = requests.post(url, headers=headers, data=file_bytes)
        except Exception as e:
            logger.error(e)
            return ""
        if resp.status_code != 200:
            raise OssServiceException(*c9010)(
                f"invoke oss error, status_code: {resp.status_code}, message: {resp.text}"
            )

        ret = resp.json()
        if ret["code"] != 0:
            raise OssServiceException(*c9010)(
                f"invoke oss error, status_code: {resp.status_code}, message: {resp.text}"
            )
        try:
            link = ret["data"]["link"]
        except Exception:
            raise OssServiceException(*c9010)(
                f"invoke oss error, status_code: {resp.status_code}, message: {resp.text}"
            )
        return link
