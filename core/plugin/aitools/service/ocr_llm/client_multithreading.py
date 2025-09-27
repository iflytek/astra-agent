import logging
import os
from concurrent.futures import ThreadPoolExecutor

from plugin.aitools.service.ase_sdk.__base.power import Power
from plugin.aitools.service.ocr_llm.client import OcrLLMClient
from plugin.aitools.service.ocr_llm.entities.ocr_result import (
    OcrResultM,
    OcrResultMStream,
)
from plugin.aitools.service.ocr_llm.entities.req_data import (
    Body,
    OcrLLMReqSourceData,
    Payload,
)
from plugin.aitools.service.ocr_llm.entities.req_data_multithreading import (
    OcrLLMReqSourceDataMultithreading,
)
from plugin.aitools.service.ase_sdk.util.pdf_convert import pdf_convert_png


class OcrLLMClientMultithreading(Power):
    """
    AI Service_通用OCR大模型，多线程版本，支持多张照片同时识别
    """

    def __init__(
        self,
        url: str = os.getenv(
            "OCR_LLM_WS_URL", "https://cbm01.cn-huabei-1.xf-yun.com/v1/private/se75ocrbm"
        ),
        method: str = "GET",
    ):
        super().__init__(url, method)
        self.ocr_llm_clients = []
        self.executor = ThreadPoolExecutor(
            max_workers=int(os.getenv("OCR_LLM_THREAD_WORKS", 3))
        )

    async def invoke(self, req_source_data: OcrLLMReqSourceDataMultithreading):
        """
        能力执行

        Args:
            req_source_data:    请求原始参数

        Returns:

        """
        try:
            all_image = []
            all_clients = []

            # 初始化 ocr client
            for data_byte in req_source_data.body.payload.data:
                if data_byte.startswith(b"%PDF-"):
                    images = pdf_convert_png(
                        data_byte,
                        req_source_data.body.payload.ocr_document_page_start,
                        req_source_data.body.payload.ocr_document_page_end,
                    )
                    all_image.extend(images)
                    temp_clients = [
                        OcrLLMClient(url=self.url, method=self.method) for _ in images
                    ]
                    self.ocr_llm_clients.append(temp_clients)
                    all_clients.extend(temp_clients)
                else:
                    all_image.append(data_byte)
                    client = OcrLLMClient(url=self.url, method=self.method)
                    self.ocr_llm_clients.append(client)
                    all_clients.append(client)

            # 异步提交任务
            for i, client in enumerate(all_clients):
                # logging.info('submit ocr_llm start, index: %s' % i)
                self.executor.submit(
                    client.invoke,
                    OcrLLMReqSourceData(
                        body=Body(payload=Payload(data=all_image[i])),
                        credentials=req_source_data.credentials,
                    ),
                )
                # loop.run_in_executor(self.executor,
                #                      client.invoke,
                #                      OcrLLMReqSourceData(
                #                          body=Body(
                #                              payload=Payload(
                #                                  data=all_image[i]
                #                              )
                #                          ),
                #                          credentials=req_source_data.credentials
                #                      ))
            logging.info("submit ocr_llm end")
        except Exception as e:
            raise e

    def _subscribe(self):
        """
        订阅处理
        Returns:

        """
        for i, c in enumerate(self.ocr_llm_clients):
            # client为数组，说明是pdf转图片后的批处理
            if isinstance(c, list):
                for cc in c:
                    yield OcrResultMStream(
                        file_index=i, content=cc.handle_generate_response()
                    )
            else:
                yield OcrResultMStream(
                    file_index=i, content=c.handle_generate_response()
                )

    def handle_generate_response(self) -> list[OcrResultM]:
        """
        处理非流式响应
        Returns:
            list[OcrResult] ocr识别列表

        """
        result = []
        file_index = 0
        one = OcrResultM(file_index=file_index, content=[])
        result.append(one)
        for content in self._subscribe():
            if content:
                if content.file_index != file_index:
                    file_index = content.file_index
                    one = OcrResultM(file_index=file_index, content=[content.content])
                    result.append(one)
                else:
                    one.content.append(content.content)
        return result
