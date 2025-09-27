from typing import List

from plugin.aitools.service.ase_sdk.__base.entities.req_data import BaseReqSourceData
from plugin.aitools.service.ase_sdk.common.entities.req_data import Credentials
from plugin.aitools.service.ase_sdk.util.pdf_convert import DOCUMENT_PAGE_UNLIMITED
from pydantic import BaseModel


class PayloadM(BaseModel):
    # 数据体，二进制数据，支持图片和pdf
    data: List[bytes] = None
    # 针对data中文档数据，指定识别的页码范围，从0开始
    ocr_document_page_start: int = DOCUMENT_PAGE_UNLIMITED
    ocr_document_page_end: int = DOCUMENT_PAGE_UNLIMITED


class BodyM(BaseModel):
    payload: PayloadM


class OcrLLMReqSourceDataMultithreading(BaseReqSourceData):
    credentials: Credentials
    body: BodyM
