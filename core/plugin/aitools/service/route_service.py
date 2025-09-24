"""
Route service module providing business logic implementation for various AI services.

Contains core processing logic for image understanding, dial testing,
speech evaluation and other services.
"""

import base64
import json
import logging
import os
import time
import uuid

from plugin.aitools.api.schema.types import ErrorResponse, SuccessDataResponse
from plugin.aitools.common.sid_generator2 import new_sid
from plugin.aitools.const.err_code.code import CodeEnum
from plugin.aitools.service.image_understanding.image_understanding_client import (
    ImageUnderstandingClient,
)

from common.otlp.log_trace.node_trace_log import NodeTraceLog, Status
from common.otlp.metrics.meter import Meter
from common.otlp.trace.span import Span
from common.service import get_kafka_producer_service


# 图片理解 - 开放平台
def image_understanding_main(question: str, image_url: str, request):
    app_id = os.getenv("AI_APP_ID")
    uid = str(uuid.uuid1())
    caller = ""
    tool_id = ""
    span = Span(
        app_id=app_id,
        uid=uid,
    )
    usr_input = {
        "question": question,
        "image_url": json.dumps(str(image_url), ensure_ascii=False),
    }
    try:
        with span.start(
            func_name="image_understanding", add_source_function_name=False
        ) as span_context:
            m = Meter(app_id=span_context.app_id, func="image_understanding")
            span_context.add_info_events(
                {"usr_input": json.dumps(usr_input, ensure_ascii=False)}
            )
            span_context.set_attributes(attributes=usr_input)

            node_trace = NodeTraceLog(
                service_id=tool_id,
                sid=span_context.sid,
                app_id=span_context.app_id,
                uid=span_context.uid,
                chat_id=span_context.sid,
                sub="aitools",
                caller=caller,
                log_caller="image_understanding",
                question=json.dumps(usr_input, ensure_ascii=False),
            )
            node_trace.start_time = int(round(time.time() * 1000))
            kafka_service = get_kafka_producer_service()

            imageunderstanding_url = os.getenv("IMAGE_UNDERSTANDING_URL")
            answer, sid, error_message = ImageUnderstandingClient(
                app_id=os.getenv("AI_APP_ID"),
                api_key=os.getenv("AI_API_KEY"),
                api_secret=os.getenv("AI_API_SECRET"),
                imageunderstanding_url=imageunderstanding_url,
            ).get_answer(question=question, image_url=image_url)
            span_context.add_info_events(
                {"answer": json.dumps(answer, ensure_ascii=False)}
            )
            if not answer:
                span_context.add_error_event(str(error_message))
                if isinstance(error_message[0], dict):
                    response = error_message[0]
                    m.in_error_count(response.get("code"))

                    node_trace.answer = json.dumps(response, ensure_ascii=False)
                    node_trace.status = Status(
                        code=response.get("code"), message=response.get("message")
                    )
                    kafka_service.send("spark-agent-builder", node_trace.to_json())

                    return response
                else:
                    response = ErrorResponse(CodeEnum.UNAUTHORIZED_ERROR)
                    m.in_error_count(response.code)

                    node_trace.answer = response.message
                    node_trace.status = Status(
                        code=response.code, message=response.message
                    )
                    kafka_service.send("spark-agent-builder", node_trace.to_json())

                    return response

            response = SuccessDataResponse(data={"content": answer}, sid=sid)
            m.in_success_count()

            node_trace.answer = json.dumps(response.data, ensure_ascii=False)
            node_trace.status = Status(code=response.code, message=response.message)
            kafka_service.send("spark-agent-builder", node_trace.to_json())

            return response
    except Exception as e:
        logging.error("图片理解请求error: %s", str(e))
        response = ErrorResponse(CodeEnum.INTERNAL_ERROR)
        m.in_error_count(response.code)

        node_trace.answer = response.message
        node_trace.status = Status(code=response.code, message=response.message)
        kafka_service.send("spark-agent-builder", node_trace.to_json())

        return response


# 智能语音评测 - ISE
async def ise_evaluate_main(
    audio_data: str, text: str, language: str, category: str, group: str, _request
):
    sid = new_sid()
    logging.info(f"ise_evaluate_main sid: {sid}")
    app_id = os.getenv("AI_APP_ID")
    uid = str(uuid.uuid1())
    caller = ""
    tool_id = ""
    span = Span(
        app_id=app_id,
        uid=uid,
    )

    usr_input = {
        "audio_data_length": len(audio_data),
        "text": text,
        "language": language,
        "category": category,
        "group": group,
    }

    try:
        with span.start("ise_evaluate", add_source_function_name=False) as plugin_span:
            m = Meter(app_id=plugin_span.app_id, func="ise_evaluate")
            plugin_span.add_info_events(
                {"usr_input": json.dumps(usr_input, ensure_ascii=False)}
            )
            plugin_span.set_attributes(attributes=usr_input)

            node_trace = NodeTraceLog(
                service_id=tool_id,
                sid=plugin_span.sid,
                app_id=plugin_span.app_id,
                uid=plugin_span.uid,
                chat_id=plugin_span.sid,
                sub="aitools",
                caller=caller,
                log_caller="ise",
                question=json.dumps(usr_input, ensure_ascii=False),
            )
            node_trace.start_time = int(round(time.time() * 1000))
            kafka_service = get_kafka_producer_service()

            from plugin.aitools.service.ise.ise_client import ISEClient

            # 解码音频数据
            audio_bytes = base64.b64decode(audio_data)

            # 创建ISE客户端
            ise_client = ISEClient(
                os.getenv("AI_APP_ID"),
                os.getenv("AI_API_KEY"),
                os.getenv("AI_API_SECRET"),
            )

            # 执行语音评测
            success, message, result = await ise_client.evaluate_audio(
                audio_data=audio_bytes,
                text=text,
                language=language,
                category=category,
                group=group,
            )

            if success:
                m.in_success_count()
                # 记录完整的评测结果到日志中（包含raw_xml）
                node_trace.answer = json.dumps(result, ensure_ascii=False)
                node_trace.status = Status(code=0, message="success")
                kafka_service.send("spark-agent-builder", node_trace.to_json())

                # 从API响应中移除raw_xml字段，保持响应精简
                api_result = {k: v for k, v in result.items() if k != "raw_xml"}
                return SuccessDataResponse(data=api_result, sid=sid)
            else:
                m.in_error_count(CodeEnum.INTERNAL_ERROR.code)

                node_trace.answer = message
                node_trace.status = Status(
                    code=CodeEnum.INTERNAL_ERROR.code, message=message
                )
                kafka_service.send("spark-agent-builder", node_trace.to_json())

                return ErrorResponse(
                    code_enum=CodeEnum.INTERNAL_ERROR,
                    message=f"ISE评测失败: {message}",
                    sid=sid,
                )

    except Exception as e:
        logging.error("ISE语音评测失败， error: %s", str(e))
        m.in_error_count(CodeEnum.INTERNAL_ERROR.code)

        node_trace.answer = str(e)
        node_trace.status = Status(code=CodeEnum.INTERNAL_ERROR.code, message=str(e))
        kafka_service.send("spark-agent-builder", node_trace.to_json())

        return ErrorResponse(
            code_enum=CodeEnum.INTERNAL_ERROR, message=f"ISE评测异常: {str(e)}", sid=sid
        )
