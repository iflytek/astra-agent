"""
Response handling utilities for streaming and standard HTTP responses.

This module provides utilities for handling streaming responses, error responses,
and standard JSON responses in the workflow system.
"""

import json
import time
import typing
from typing import Any, Dict

from starlette.concurrency import iterate_in_threadpool
from starlette.responses import ContentStream, JSONResponse, StreamingResponse


class Streaming:
    """
    Utility class for handling streaming responses.

    Provides methods for sending streaming data, processing content streams,
    and generating appropriate response formats for different content types.
    """

    @staticmethod
    async def send(
        response: ContentStream,
        response_type: "type[StreamingResponse] | type[JSONResponse]" = StreamingResponse,
        media_type: "str | None" = "text/event-stream",
        headers: "typing.Mapping[str, str] | None" = None,
    ) -> "StreamingResponse | JSONResponse":
        """
        Send a streaming or JSON response based on the specified type.

        :param response: Content stream to send
        :param response_type: Type of response to generate
                              (StreamingResponse or JSONResponse)
        :param media_type: Media type for the response (defaults to text/event-stream)
        :param headers: Optional headers for the response
        :return: StreamingResponse or JSONResponse based on the type
        """
        if response_type == StreamingResponse:
            if headers is None:
                headers = {"Cache-Control": "no-cache", "X-Accel-Buffering": "no"}
            return StreamingResponse(
                response,
                media_type=media_type,
                headers=headers,
            )
        else:
            return JSONResponse(
                await Streaming._get_content_with_content_stream(response)
            )

    @staticmethod
    async def _get_content_with_content_stream(response: ContentStream) -> dict:
        """
        Extract content from a content stream and return as dictionary.

        :param response: Content stream to process
        :return: Dictionary containing the parsed content
        """
        if isinstance(response, typing.AsyncIterable):
            body_iterator = response
        else:
            body_iterator = iterate_in_threadpool(response)

        async for chunk in body_iterator:
            if isinstance(chunk, bytes):
                return json.loads(chunk.decode("utf-8").removeprefix("data: "))
            elif isinstance(chunk, str):
                return json.loads(chunk.removeprefix("data: "))
        return {}

    @staticmethod
    async def send_error(
        response: dict,
        response_type: "type[StreamingResponse] | type[JSONResponse]" = StreamingResponse,
        media_type: "str | None" = "text/event-stream",
        headers: "typing.Mapping[str, str] | None" = None,
    ) -> "StreamingResponse | JSONResponse":
        """
        Send an error response in streaming or JSON format.

        :param response: Error response dictionary
        :param response_type: Type of response to generate
        :param media_type: Media type for the response
        :param headers: Optional headers for the response
        :return: StreamingResponse or JSONResponse containing the error
        """

        def _iterator(response: Dict[str, Any]) -> typing.Iterator[str]:
            """
            Wrap the return value into an iterator.
            """
            yield Streaming.generate_data(response)

        if response_type == StreamingResponse:
            return await Streaming.send(
                _iterator(response), response_type, media_type, headers
            )
        else:
            return JSONResponse(response)

    @staticmethod
    def generate_data(response: dict) -> str:
        """
        Generate SSE (Server-Sent Events) formatted data string.

        :param response: Response dictionary to format
        :return: SSE formatted string
        """
        return (
            f"data: {json.dumps(response, ensure_ascii=False, separators=(',', ':'))}"
            f"\n\n"
        )

    @staticmethod
    def generate_interrupt_data(response: dict) -> str:
        """
        Generate SSE formatted interrupt event data string.

        :param response: Response dictionary to format
        :return: SSE formatted interrupt event string
        """
        return (
            f"event:Interrupt\n,"
            f"data: {json.dumps(response, ensure_ascii=False, separators=(',', ':'))}"
            f"\n\n"
        )


class Resp:
    """
    Response class.
    """

    @staticmethod
    def success(data: Any = None, sid: "str | None" = None) -> JSONResponse:
        """
        Create a successful JSON response.

        :param data: Optional data to include in the response
        :param sid: Optional session ID to include in the response
        :return: JSONResponse with success status
        """
        ret = {"code": 0, "message": "success"}
        if data:
            ret["data"] = data
        if sid:
            ret["sid"] = sid
        return JSONResponse(content=ret)

    @staticmethod
    def error(code: int, message: str, sid: str = "") -> JSONResponse:
        """
        Create an error JSON response.

        :param code: Error code
        :param message: Error message
        :param sid: Optional session ID to include in the response
        :return: JSONResponse with error status
        """
        ret = {"code": code, "message": message}
        if sid:
            ret["sid"] = sid
        return JSONResponse(content=ret)

    @staticmethod
    def error_sse(code: int, message: str, sid: str) -> JSONResponse:
        """
        Create an error JSON response with SSE format.

        :param code: Error code
        :param message: Error message
        :param sid: Session ID for the response
        :return: JSONResponse with error status and SSE format
        """
        ret = {
            "code": code,
            "message": message,
            "id": sid,
            "created": time.time() * 1000,
        }
        return JSONResponse(content=ret)
