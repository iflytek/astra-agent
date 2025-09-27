"""
OCR LLM client module providing optical character recognition and LLM capabilities.
"""

import base64
import json
import logging
import os
import queue
import time
from typing import List

from plugin.aitools.service.ase_sdk.__base.entities.req_data import ReqData
from plugin.aitools.service.ase_sdk.__base.entities.result import Result
from plugin.aitools.service.ase_sdk.__base.power import Power
from plugin.aitools.service.ocr_llm.entities.ocr_result import OcrResult
from plugin.aitools.service.ocr_llm.entities.req_data import (
    OcrLLMReqSourceData,
)
from plugin.aitools.service.ase_sdk.const.data_status import DataStatusEnum
from plugin.aitools.service.ase_sdk.exception.CustomException import CustomException
from plugin.aitools.service.ase_sdk.util.hmac_auth import HMACAuth


class OcrLLMClient(Power):
    """
    AI Service_通用OCR大模型
    """

    def __init__(
        self,
        url: str = os.getenv(
            "OCR_LLM_WS_URL", "https://cbm01.cn-huabei-1.xf-yun.com/v1/private/se75ocrbm"
        ),
        method: str = "GET",
    ):
        super().__init__(url, method)

    def invoke(self, req_source_data: OcrLLMReqSourceData):
        """
        能力执行

        Args:
            req_source_data:    请求原始参数

        Returns:

        """
        credentials = req_source_data.credentials
        params = HMACAuth.build_auth_params(
            self.url, api_key=credentials.api_key, api_secret=credentials.api_secret
        )

        markdown = "watermark=0,page_header=0,page_footer=0,page_number=0,graph=0"
        sed = "watermark=0,page_header=0,page_footer=0,page_number=0,graph=0"
        body = {
            "header": {"app_id": credentials.app_id, "status": 2},
            "parameter": {
                "ocr": {
                    "result_option": "normal",
                    "result_format": "json",
                    "output_type": "one_shot",
                    "exif_option": "0",
                    "json_element_option": "",
                    "markdown_element_option": markdown,
                    "sed_element_option": sed,
                    "alpha_option": "0",
                    "rotation_min_angle": 5,
                    "result": {
                        "encoding": "utf8",
                        "compress": "raw",
                        "format": "plain",
                    },
                }
            },
            "payload": {
                "image": {
                    "image": base64.b64encode(
                        req_source_data.body.payload.data
                    ).decode(),
                    "status": 2,
                    "seq": 0,
                }
            },
        }
        req_data = ReqData(params=params, body=body)
        time.sleep(int(os.getenv("OCR_LLM_SLEEP_TIME", 0)))
        logging.info("invoke ocr_llm")
        return self._invoke(req_data)

    def _subscribe(self):
        try:
            while True:
                one = self.queue.get(timeout=60)
                if isinstance(one, Result):
                    data = json.loads(one.data)
                    payload = data.get("payload")
                    if not payload:
                        header = data.get("header", {})
                        code = header.get("code", 0)
                        message = header.get("message", "")
                        if code != 0:
                            raise CustomException(code=code, message=message)
                        continue
                    text = payload.get("result", {}).get("text", "")
                    if text:
                        tt = base64.b64decode(text).decode(encoding="utf-8")
                        yield OcrResult(
                            name="markdown",
                            value=OcrRespParse.parse(json.loads(tt)),
                            source_data=tt,
                        )
                    if one.status == DataStatusEnum.END.value:
                        break
                elif issubclass(one.__class__, Exception):
                    raise one
                else:
                    raise TypeError(f"Unknown type, {type(one)}")
        except queue.Empty:
            pass
        except Exception as e:
            raise e

    def handle_generate_response(self) -> OcrResult:
        """
        处理非流式响应
        Returns:
            list[OcrResult] ocr识别列表

        """
        name = ""
        values = []
        source_datas = []
        for content in self._subscribe():
            if content:
                name = content.name
                values.append(content.value)
                source_datas.append(content.source_data)
        return OcrResult(name=name, value="\n".join(values))


class OcrRespParse:

    @staticmethod
    def parse(ocr_resp: dict) -> str:
        """
        OCR 响应解析

        Args:
            ocr_resp:

        Returns:

        """
        images = ocr_resp.get("image", [])
        result = []
        for image in images:
            contents = image.get("content", [[]])
            for content in contents:
                for one in content:
                    child_ocr_texts = OcrRespParse._deal_one(one)
                    result.extend(child_ocr_texts)
        return "\n".join(result)

    @staticmethod
    def _deal_table_data(cells: []):
        max_row = max(item["row"] for item in cells)
        table = "<table border='1'>\n"

        for r in range(1, max_row + 1):
            table += "  <tr>\n"
            for item in cells:
                if item["row"] == r:

                    # 单独加一层list，可复用 _deal_one 函数
                    root_content = [item.get("content", [{}])]
                    c = {"content": root_content}
                    # 处理结果
                    text_arr = OcrRespParse._deal_one(c)

                    # 检查它是否是标题行
                    if r == 1:
                        table += f"    <th colspan=\
                            '{item['colspan']}'>{'<br>'.join(text_arr)}</th>\n"
                    else:
                        table += f"    <td colspan='{item['colspan']}'\
                              rowspan=\
                                '{item['rowspan']}'>{'<br>'.join(text_arr)}</td>\n"

            # 设置标题行样式
            if r == 1:
                table += "  </tr>\n"
                table = table.replace(
                    "<th", "<th style='font-weight: bold; background-color: #f2f3f4;'"
                )
            else:
                table += "  </tr>\n"

        table += "</table>"
        return table

    @staticmethod
    def _deal_one(root_content: dict, is_get_text_attribute: bool = False) -> List[str]:
        """
        递归处理单个内容

        Args:
            root_content:

        Returns:

        """

        child_contents = root_content.get("content", [[{}]])
        child_ocr_texts = []
        for child_content in child_contents:
            for child_content2 in child_content:
                # 获取文本类型
                content_type = child_content2.get("type", "")

                # 获取文本属性
                if is_get_text_attribute:
                    if content_type == "text_unit":
                        attributes = child_content2.get("attribute", [{}])
                        return [OcrRespParse._deal_text_attributes(attributes)]
                    else:
                        return OcrRespParse._deal_one(
                            child_content2, is_get_text_attribute
                        )

                # 正常处理文本
                elif content_type == "paragraph":
                    text_arr = child_content2.get("text", [])
                    text_str = "\n".join(text_arr).replace("\n", "<br>")
                    # 获取文本的格式信息
                    text_format = OcrRespParse._deal_one(child_content2, True)
                    if text_format:
                        text_str = text_format[0].format(text=text_str)
                    child_ocr_texts.append(text_str)

                # 处理表格信息
                elif content_type == "table":
                    # 处理表头
                    note = child_content2.get("note", [])
                    if note:
                        header_content = OcrRespParse._deal_one({"content": [note]})
                        child_ocr_texts.append("<br>".join(header_content))
                    cells = child_content2.get("cell", [])
                    table_content = OcrRespParse._deal_table_data(cells)
                    child_ocr_texts.append(table_content)

                # 递归获取子内容
                else:
                    if child_content2:
                        content2_result = OcrRespParse._deal_one(child_content2)

                        # 代码块
                        if content_type == "code":
                            child_ocr_texts.append("```")
                            child_ocr_texts.extend(content2_result)
                            child_ocr_texts.append("```")

                        # 标题
                        elif content_type == "title":
                            level = child_content2.get("level", 0)
                            if level:
                                text = f'{"#" * level} {"<br>".join(content2_result)}'
                                child_ocr_texts.append(text)
                            else:
                                child_ocr_texts.extend(content2_result)

                        # 列表
                        elif content_type == "item":
                            child_ocr_texts.append(f'- {"<br>".join(content2_result)}')

                        # 其他
                        else:
                            child_ocr_texts.extend(content2_result)

        return child_ocr_texts

    @staticmethod
    def _deal_text_attributes(attributes: [{}]) -> str:
        ff = "{text}"
        for attribute in attributes:
            name = attribute.get("name", "")
            # 处理粗体
            if name == "bold":
                ff = f"<b>{ff}</b>"
            # 处理斜体
            elif name == "italic":
                ff = f"<i>{ff}</i>"
            # 待定
            else:
                pass
        return ff
