"""
Speech synthesis module providing text-to-speech and voice cloning functionality.
"""

import json
import logging
import os
import time
import uuid

from plugin.aitools.api.schema.types import ErrorResponse, SuccessDataResponse
from plugin.aitools.const.err_code.code import CodeEnum
from plugin.aitools.service.speech_synthesis.smart_tts.smart_tts_client import (
    SmartTTSClient,
)

from common.otlp.log_trace.node_trace_log import NodeTraceLog, Status
from common.otlp.metrics.meter import Meter
from common.otlp.trace.span import Span
from common.service import get_kafka_producer_service, get_oss_service


# 超拟人
def smarttts_main(text: str, vcn: str, speed: int, request):
    app_id = os.getenv("AI_APP_ID")
    uid = str(uuid.uuid1())
    caller = ""
    tool_id = ""
    span = Span(
        app_id=app_id,
        uid=uid,
    )
    usr_input = {"text": text, "vcn": vcn, "speed": str(speed)}
    try:
        with span.start(
            func_name="smarttts", add_source_function_name=False
        ) as span_context:
            m = Meter(app_id=span_context.app_id, func="smarttts")
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
                log_caller="smarttts",
                question=json.dumps(usr_input, ensure_ascii=False),
            )
            kafka_service = get_kafka_producer_service()

            node_trace.start_time = int(round(time.time() * 1000))

            if not text:
                response = ErrorResponse(
                    CodeEnum.TEXT_RESULT_NULL_ERROR, sid=uuid.uuid4()
                )
                m.in_error_count(response.code)
                node_trace.answer = response.message
                node_trace.status = Status(code=response.code, message=response.message)
                kafka_service.send("spark-agent-builder", node_trace.to_json())
                return response
            data = {"text": text, "vcn": vcn, "speed": speed}
            span_context.add_info_events(
                {"参数data": json.dumps(data, ensure_ascii=False)}
            )

            messages, audio_data = SmartTTSClient(
                app_id=os.getenv("AI_APP_ID"),
                api_key=os.getenv("AI_API_KEY"),
                api_secret=os.getenv("AI_API_SECRET"),
                text=text,
                vcn=vcn,
                speed=speed,
            ).start()
            audios = audio_data
            # print('audios:',audios)
            # print('messages:', messages)
            sid = messages[0].get("header", "").get("sid", "")
            if not audios:
                response = {
                    "code": messages[0].get("header", "").get("code", ""),
                    "message": messages[0].get("header", "").get("message", ""),
                    "sid": sid,
                    "data": "",
                }
                m.in_error_count(response["code"])
                node_trace.answer = response["message"]
                node_trace.status = Status(
                    code=response["code"], message=response["message"]
                )
                kafka_service.send("spark-agent-builder", node_trace.to_json())

                return response

            oss_service = get_oss_service()
            voice_url = oss_service.upload_file(str(uuid.uuid4()) + ".MP3", audios)

            span_context.add_info_events(
                {"voice_url": json.dumps(voice_url, ensure_ascii=False)}
            )

            response = SuccessDataResponse(data={"voice_url": voice_url}, sid=sid)
            m.in_success_count()
            node_trace.answer = json.dumps(response.data, ensure_ascii=False)
            node_trace.status = Status(code=response.code, message=response.message)
            kafka_service.send("spark-agent-builder", node_trace.to_json())

            return response
    except Exception as e:
        logging.error("smarttts请求error: %s", str(e))
        response = ErrorResponse(CodeEnum.VOICE_GENERATE_ERROR)
        m.in_error_count(response.code)
        node_trace.answer = response.message
        node_trace.status = Status(code=response.code, message=response.message)
        kafka_service.send("spark-agent-builder", node_trace.to_json())

        return response
