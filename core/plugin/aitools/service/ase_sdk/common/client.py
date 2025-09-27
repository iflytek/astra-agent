from plugin.aitools.service.ase_sdk.__base.entities.req_data import ReqData
from plugin.aitools.service.ase_sdk.__base.entities.result import Result
from plugin.aitools.service.ase_sdk.__base.power import Power
from plugin.aitools.service.ase_sdk.common.entities.req_data import (
    CommonReqSourceData,
)
from plugin.aitools.service.ase_sdk.const.data_status import DataStatusEnum
from plugin.aitools.service.ase_sdk.util.hmac_auth import HMACAuth


class CommonClient(Power):

    def __init__(self, url: str, method: str = "GET", stream=False):
        super().__init__(url, method, stream)

    def invoke(self, req_source_data: CommonReqSourceData):
        """
        能力执行

        Args:
            req_source_data:    请求原始参数

        Returns:

        """
        if not req_source_data.req_data:
            req_source_data.req_data = ReqData()
        credentials = req_source_data.credentials
        if credentials:
            params = HMACAuth.build_auth_params(
                self.url,
                method=self.method,
                api_key=credentials.api_key,
                api_secret=credentials.api_secret,
            )
            if self.method.upper() == "GET" or credentials.auth_in_params:
                req_source_data.req_data.params.update(params)
            else:
                req_source_data.req_data.headers.update(params)
        return self._invoke(req_source_data.req_data)

    def _subscribe(self):
        try:
            while True:
                one = self.queue.get()
                if isinstance(one, Result):
                    yield one.data
                elif issubclass(one.__class__, Exception):
                    raise one
                else:
                    raise TypeError(f"Unknown type, {type(one)}")
                if one.status == DataStatusEnum.END.value:
                    break
        except Exception as e:
            raise e
