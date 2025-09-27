from typing import List

from pydantic import BaseModel


class OcrResult(BaseModel):
    """
    OCR识别结果
    """

    # 类型
    name: str
    # 识别结果值
    value: str
    # 识别源数据
    source_data: str = ""


class OcrResultMStream(BaseModel):
    """
    多线程下OCR识别结果，流式结果
    """

    file_index: int
    content: OcrResult


class OcrResultM(BaseModel):
    """
    多线程下OCR识别结果，非流式结果
    """

    file_index: int
    content: List[OcrResult]
