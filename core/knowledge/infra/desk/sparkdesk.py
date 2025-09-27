"""
SparkDesk knowledge base query module.

This module provides functionality to asynchronously call SparkDesk knowledge base API, including query and request sending.
Mainly contains two async functions: sparkdesk_query_async for executing queries, async_request for handling HTTP requests.
"""

import asyncio
import json
import os
import time
from typing import Any, Dict, List, Optional

import aiohttp
from loguru import logger

from knowledge.consts.error_code import CodeEnum
from knowledge.exceptions.exception import ThirdPartyException
from knowledge.utils.spark_signature import get_signature


async def sparkdesk_query_async(
    query: str, repo_ids: Optional[List[str]] = None, **kwargs: Any
) -> Dict[str, Any]:
    """
    Asynchronously execute SparkDesk knowledge base query.

    Args:
        query: Query string
        repo_ids: Knowledge base ID list, optional
        **kwargs: Other keyword arguments

    Returns:
        API response data

    Raises:
        ThirdPartyException: Raised when request fails or API returns error
    """
    post_body = {
        "question": query,
        "datasetId": repo_ids[0] if repo_ids and len(repo_ids) > 0 else None,
        "flowId": kwargs.get("flow_id"),
    }

    data = await async_request(body=post_body, **kwargs)
    return data


async def async_request(
    body: Dict[str, Any], method: str = "POST", **kwargs: Any
) -> Dict[str, Any]:
    """
    Asynchronously send request to SparkDesk API (using aiohttp)

    Args:
        body: Request body data
        method: HTTP method, default is POST
        **kwargs: Other keyword arguments

    Returns:
        API response data

    Raises:
        ThirdPartyException: Raised when request fails or API returns error
    """
    span = kwargs.get("span")
    desk_url = os.getenv("DESK_RAG_URL", "")
    if span:
        with span.start(
            func_name="ASYNC_REQUEST_SPARKDESK", add_source_function_name=True
        ) as span_context:
            logger.info(f"Async requesting SPARKDESK-RAG: {desk_url}")
            span_context.add_info_events({"SPARKDESK_URL": desk_url})
            span_context.add_info_events(
                {"SPARKDESK_INPUT": json.dumps(body, ensure_ascii=False)}
            )

            try:
                # Use aiohttp for async requests
                async with aiohttp.ClientSession() as session:
                    async with session.request(
                        method=method,
                        url=desk_url,
                        json=body,
                        headers=await assemble_auth_headers_async(),
                        timeout=aiohttp.ClientTimeout(
                            total=float(os.getenv("DESK_CLIENT_TIMEOUT", "30"))
                        ),  # Set timeout
                    ) as resp:

                        response_text = await resp.text()
                        logger.info(
                            f"Async response from SPARKDESK-RAG: {response_text}"
                        )
                        span_context.add_info_events(
                            {"SPARKDESK_OUTPUT": response_text}
                        )

                        if resp.status != 200:
                            error_msg = f"SPARKDESK-RAG request failed with status: {resp.status}"
                            logger.error(error_msg)
                            raise ThirdPartyException(
                                e=CodeEnum.DESK_RAGError, msg=error_msg
                            )

                        try:
                            msg_js = json.loads(response_text)
                        except json.JSONDecodeError as e:
                            error_msg = f"Failed to parse JSON response: {e}"
                            logger.error(error_msg)
                            raise ThirdPartyException(
                                e=CodeEnum.DESK_RAGError, msg=error_msg
                            ) from e

                        if msg_js.get("code") == 0 and msg_js.get("flag"):
                            return msg_js.get("data", {})
                        error_desc = msg_js.get(
                            "desc", "Unknown error from SPARKDESK-RAG"
                        )
                        logger.error(f"SPARKDESK-RAG API error: {error_desc}")
                        raise ThirdPartyException(
                            e=CodeEnum.DESK_RAGError, msg=error_desc
                        )

            except asyncio.TimeoutError as e:
                error_msg = f"Async request to {desk_url} timed out after 30 seconds"
                logger.error(error_msg)
                span_context.record_exception(asyncio.TimeoutError(error_msg))
                raise ThirdPartyException(
                    e=CodeEnum.DESK_RAGError, msg=error_msg
                ) from e
            except aiohttp.ClientError as e:
                error_msg = f"Network error during async request: {e}"
                logger.error(error_msg)
                span_context.record_exception(e)
                raise ThirdPartyException(
                    e=CodeEnum.DESK_RAGError, msg=error_msg
                ) from e
            except Exception as e:
                error_msg = f"Unexpected error during async request: {e}"
                logger.error(error_msg)
                span_context.record_exception(e)
                raise ThirdPartyException(
                    e=CodeEnum.DESK_RAGError, msg=error_msg
                ) from e

    return {}


async def assemble_auth_headers_async() -> Dict[str, str]:
    """
    Asynchronously build authentication request headers

    Returns:
        Dictionary containing authentication information request headers
    """
    timestamp = int(time.time())
    signature = get_signature(
        os.getenv("DESK_APP_ID", ""), timestamp, os.getenv("DESK_API_SECRET", "")
    )
    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json",
        "Method": "POST",
        "appId": os.getenv("DESK_APP_ID", ""),
        "timestamp": str(timestamp),
        "signature": signature,
    }
    return headers
