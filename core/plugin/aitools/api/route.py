"""
AI Tools API routing module defining HTTP interfaces for various AI services.

Contains API endpoints for OCR text recognition, image generation, image understanding,
speech synthesis, and speech evaluation functionalities.
"""

import asyncio
import base64
import json
import logging
import os
import time
import uuid

import requests
from fastapi import APIRouter, Request
from plugin.aitools.api.schema.types import (
    OCRLLM,
    ErrorCResponse,
    ErrorResponse,
    ImageGenerate,
    ImageUnderstandingInput,
    ISEInput,
    SmartTTSInput,
    SuccessDataResponse,
)
from plugin.aitools.common.logger import log
from plugin.aitools.const.const import IMAGE_GENERATE_MAX_PROMPT_LEN
from plugin.aitools.const.err_code.code import CodeEnum
from plugin.aitools.const.err_code.code_convert import CodeConvert
from plugin.aitools.service.ase_sdk.common.entities.req_data import Credentials
from plugin.aitools.service.ocr_llm.client_multithreading import (
    OcrLLMClientMultithreading,
)
from plugin.aitools.service.ocr_llm.entities.req_data_multithreading import (
    BodyM,
    OcrLLMReqSourceDataMultithreading,
    PayloadM,
)
from plugin.aitools.service.ase_sdk.exception.CustomException import CustomException

from common.otlp.log_trace.node_trace_log import NodeTraceLog, Status
from common.otlp.metrics.meter import Meter
from common.otlp.trace.span import Span
from common.service import get_kafka_producer_service, get_oss_service

app = APIRouter(prefix="/aitools/v1")


# 拨测接口
@app.get("/dial_test")
def dial_test(request: Request):
    from plugin.aitools.service.dial_test.dial_test import dial_test_main

    # Simple default values to pass pylint - function may be deprecated
    return dial_test_main(
        method="GET",
        url="http://localhost/health",
        headers={},
        payload={},
        _success_code=200,
        _call_frequency=1,
    )


# 图片理解 - 开放平台
@app.post("/image_understanding")
def image_understanding(params: ImageUnderstandingInput, request: Request):
    from plugin.aitools.service.route_service import image_understanding_main

    return image_understanding_main(
        question=params.question, image_url=params.image_url, request=request
    )


@app.post("/ocr")
def req_ase_ability_ocr(ase_ocr_llm_vo: OCRLLM):
    app_id = os.getenv("AI_APP_ID")
    uid = str(uuid.uuid1())
    caller = ""
    tool_id = ""
    span = Span(
        app_id=app_id,
        uid=uid,
    )
    usr_input = ase_ocr_llm_vo
    with span.start(func_name="req_ase_ability_ocr") as span_context:
        m = Meter(app_id=span_context.app_id, func="req_ase_ability_ocr")
        span_context.add_info_events(
            {"usr_input": json.dumps(str(usr_input), ensure_ascii=False)}
        )
        span_context.set_attributes(
            attributes={"file_url": str(ase_ocr_llm_vo.file_url)}
        )

        node_trace = NodeTraceLog(
            service_id=tool_id,
            sid=span_context.sid,
            app_id=span_context.app_id,
            uid=span_context.uid,
            chat_id=span_context.sid,
            sub="aitools",
            caller=caller,
            log_caller="ocr",
            question=json.dumps(str(usr_input), ensure_ascii=False),
        )
        node_trace.start_time = int(round(time.time() * 1000))
        kafka_service = get_kafka_producer_service()

        image_byte_arrays = []
        log.info("req_ase_ability_ocr request: %s", ase_ocr_llm_vo.json())
        try:
            image_byte_arrays.append(
                requests.get(ase_ocr_llm_vo.file_url, timeout=30).content
            )
            client = OcrLLMClientMultithreading(url=os.getenv("OCR_LLM_WS_URL"))
            asyncio.run(
                client.invoke(
                    req_source_data=OcrLLMReqSourceDataMultithreading(
                        body=BodyM(
                            payload=PayloadM(
                                data=image_byte_arrays,
                                ocr_document_page_start=ase_ocr_llm_vo.page_start,
                                ocr_document_page_end=ase_ocr_llm_vo.page_end,
                            )
                        ),
                        credentials=Credentials(
                            app_id=os.getenv("AI_APP_ID"),
                            api_key=os.getenv("AI_API_KEY"),
                            api_secret=os.getenv("AI_API_SECRET"),
                        ),
                    )
                )
            )
            content = client.handle_generate_response()
            log.info("content: %s", content)
        except CustomException as e:
            response = ErrorCResponse(code=e.code, message=e.message)
            m.in_error_count(response.code)
            node_trace.answer = response.message
            node_trace.status = Status(code=response.code, message=response.message)
            kafka_service.send("spark-agent-builder", node_trace.to_json())
            log.error("request: %s, error: %s", ase_ocr_llm_vo.json(), str(e))

            return response
        except Exception as e:
            log.error("request: %s, error: %s", ase_ocr_llm_vo.json(), str(e))
            response = ErrorResponse(CodeEnum.OCR_FILE_HANDLING_ERROR)
            m.in_error_count(response.code)

            node_trace.answer = response.message
            node_trace.status = Status(code=response.code, message=response.message)
            kafka_service.send("spark-agent-builder", node_trace.to_json())

            return response

        response = SuccessDataResponse(data=content)
        m.in_success_count()

        node_trace.answer = str(response.data)
        node_trace.status = Status(code=response.code, message=response.message)
        kafka_service.send("spark-agent-builder", node_trace.to_json())

        return response


@app.post("/image_generate")
def req_ase_ability_image_generate(image_generate_vo: ImageGenerate):
    app_id = os.getenv("AI_APP_ID")
    uid = str(uuid.uuid1())
    caller = ""
    tool_id = ""
    span = Span(
        app_id=app_id,
        uid=uid,
    )
    usr_input = image_generate_vo
    with span.start(func_name="req_ase_ability_image_generate") as span_context:
        m = Meter(app_id=span_context.app_id, func="req_ase_ability_image_generate")
        span_context.add_info_events(
            {"usr_input": json.dumps(str(usr_input), ensure_ascii=False)}
        )
        span_context.set_attributes(attributes={"prompt": usr_input.prompt})

        node_trace = NodeTraceLog(
            service_id=tool_id,
            sid=span_context.sid,
            app_id=span_context.app_id,
            uid=span_context.uid,
            chat_id=span_context.sid,
            sub="aitools",
            caller=caller,
            log_caller="image_generate",
            question=json.dumps(str(usr_input), ensure_ascii=False),
        )
        node_trace.start_time = int(round(time.time() * 1000))
        kafka_service = get_kafka_producer_service()

        try:
            from plugin.aitools.service.ase_sdk.__base.entities.req_data import ReqData
            from plugin.aitools.service.ase_sdk.common.client import (
                CommonClient,
            )
            from plugin.aitools.service.ase_sdk.common.entities.req_data import (
                CommonReqSourceData,
            )

            client = CommonClient(
                url=os.getenv("IMAGE_GENERATE_URL"),
                method="POST",
                stream=False,
            )
            content = image_generate_vo.prompt

            client.invoke(
                req_source_data=CommonReqSourceData(
                    credentials=Credentials(
                        app_id=os.getenv("AI_APP_ID"),
                        api_key=os.getenv("AI_API_KEY"),
                        api_secret=os.getenv("AI_API_SECRET"),
                        auth_in_params=True,
                    ),
                    req_data=ReqData(
                        body={
                            "header": {
                                "app_id": os.getenv("AI_APP_ID"),
                            },
                            "parameter": {
                                "chat": {
                                    "domain": "general",
                                    "width": image_generate_vo.height,
                                    "height": image_generate_vo.width,
                                }
                            },
                            "payload": {
                                "message": {
                                    "text": [
                                        {
                                            "role": "user",
                                            # 文生图prompt最大长度为510
                                            "content": content[
                                                :IMAGE_GENERATE_MAX_PROMPT_LEN
                                            ],
                                        }
                                    ]
                                }
                            },
                        }
                    ),
                )
            )
            content = client.handle_generate_response()

            content_dict = json.loads(content)
            header = content_dict[0].get("header", {})
            code = header.get("code", 0)
            sid = header.get("sid", "")
            if code != 0:
                codeEnum = CodeConvert.imageGeneratorCode(code)
                return ErrorResponse(codeEnum, sid=sid)

            payload = content_dict[0].get("payload", {})
            text = payload.get("choices", {}).get("text", [{}])[0].get("content", "")

            oss_service = get_oss_service()
            image_url = oss_service.upload_file(
                str(uuid.uuid4()) + ".jpg", base64.b64decode(text)
            )
            response = SuccessDataResponse(
                data={"image_url": image_url, "image_url_md": f"![]({image_url})"},
                sid=sid,
            )
            m.in_success_count()

            node_trace.answer = json.dumps(response.data, ensure_ascii=False)
            node_trace.status = Status(code=response.code, message=response.message)
            kafka_service.send("spark-agent-builder", node_trace.to_json())

            return response
        except Exception as e:
            logging.error("request: %s, error: %s", image_generate_vo.json(), str(e))
            response = ErrorResponse(CodeEnum.IMAGE_GENERATE_ERROR)
            m.in_error_count(response.code)

            node_trace.answer = response.message
            node_trace.status = Status(code=response.code, message=response.message)
            kafka_service.send("spark-agent-builder", node_trace.to_json())

            return response


# 超拟人合成
@app.post("/smarttts")
def smarttts(params: SmartTTSInput, request: Request):
    from plugin.aitools.service.speech_synthesis.voice_main import smarttts_main

    return smarttts_main(
        text=params.text, vcn=params.vcn, speed=params.speed, request=request
    )


# 智能语音评测 - ISE
@app.post("/ise")
async def ise_evaluate(params: ISEInput, request: Request):
    from plugin.aitools.service.route_service import ise_evaluate_main

    return await ise_evaluate_main(
        audio_data=params.audio_data,
        text=params.text,
        language=params.language,
        category=params.category,
        group=params.group,
        request=request,
    )
