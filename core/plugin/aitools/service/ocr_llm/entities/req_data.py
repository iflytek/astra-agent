from plugin.aitools.service.ase_sdk.__base.entities.req_data import BaseReqSourceData
from plugin.aitools.service.ase_sdk.common.entities.req_data import Credentials
from pydantic import BaseModel


class Payload(BaseModel):
    # 数据体，图片二进制数据
    data: bytes


class Body(BaseModel):
    payload: Payload


class OcrLLMReqSourceData(BaseReqSourceData):
    credentials: Credentials
    body: Body
