"""
Xinghuo knowledge base interface module.

This module provides asynchronous interfaces for interacting with Xinghuo knowledge base, including file upload, chunking, search, and other functions.
"""

import asyncio
import base64
import json
import os
import time
from typing import Any, Dict, List, Optional

import aiohttp
from loguru import logger

from knowledge.consts.error_code import CodeEnum
from knowledge.exceptions.exception import CustomException, ThirdPartyException
from knowledge.utils.file_utils import get_file_info_from_url
from knowledge.utils.spark_signature import get_signature


async def upload(
    url: str, wiki_split_extends: Dict[str, Any], resource_type: int, **kwargs: Any
) -> Dict[str, Any]:
    """
    Upload file to Xinghuo knowledge base.

    Args:
        url: File URL or path
        wiki_split_extends: Wiki split extension parameters
        resource_type: Resource type identifier

    Returns:
        Result data of upload operation

    Raises:
        ThirdPartyException: Raised when upload fails
    """
    file_name, _, file_extension = get_file_info_from_url(url)
    file_type = ["pdf", "doc", "docx"]

    if resource_type == 1:
        post_body = {
            "webPageUrl": url,
            "fileName": "file_name.txt",
            "fileType": "wiki",
            "parseType": "OCR" if file_extension.lower() in file_type else "AUTO",
            "stepByStep": "true",
        }
    else:
        post_body = {
            "url": url,
            "fileName": file_name,
            "fileType": "wiki",
            "parseType": "OCR" if file_extension.lower() in file_type else "AUTO",
            "stepByStep": "true",
        }

    post_body["extend"] = json.dumps({"wikiSplitExtends": wiki_split_extends})

    data = await async_form_request(
        post_body, os.getenv("XINGHUO_RAG_URL", "") + "openapi/v1/file/upload", **kwargs
    )
    return data


async def split(
    file_id: Optional[str] = None,
    cut_off: Optional[List[str]] = None,
    length_range: Optional[List[int]] = None,
    **kwargs: Any,
) -> Dict[str, Any]:
    """
    Perform chunking processing on documents.

    Args:
        file_id: File ID
        cut_off: Cutoff character list
        length_range: Chunk length range

    Returns:
        Result data of chunking operation

    Raises:
        ThirdPartyException: Raised when chunking fails
    """
    if not file_id:
        raise ThirdPartyException("File ID is required for split operation")

    post_body = {
        "fileIds": [file_id],
        "isSplitDefault": False,
        "splitType": "wiki",
        "wikiSplitExtends": {},
    }

    split_chars = []
    if cut_off:
        for s in cut_off:
            split_chars.append(
                base64.b64encode(s.encode("utf-8")).decode(encoding="utf-8")
            )

    post_body["wikiSplitExtends"] = {
        "chunkSeparators": split_chars,
        "minChunkSize": (
            length_range[0] if length_range and len(length_range) > 0 else 256
        ),
        "chunkSize": (
            length_range[1] if length_range and len(length_range) > 1 else 2000
        ),
    }

    max_retries = 3
    retry_count = 0
    data: Optional[Dict[str, Any]] = None

    while retry_count < max_retries:
        try:
            response = await async_request(
                post_body,
                os.getenv("XINGHUO_RAG_URL", "") + "openapi/v1/file/split",
                **kwargs,
            )
            data = response
            break
        except Exception:
            print(
                f"Retry {retry_count + 1}, document splitting not successful, continuing to retry..."
            )
            retry_count += 1
            if retry_count < max_retries:
                await asyncio.sleep(1)

    if data is None:
        raise ThirdPartyException("Document splitting failed after retries")

    return data


async def get_chunks(
    file_id: Optional[str] = None, **kwargs: Any
) -> List[Dict[str, Any]]:
    """
    Get document chunk content.

    Args:
        file_id: File ID

    Returns:
        List of document chunk content

    Raises:
        ThirdPartyException: Raised when document splitting fails
        CustomException: Raised when unable to get chunk content
    """
    if not file_id:
        raise CustomException(CodeEnum.ParameterCheckException, "File ID is required")

    max_retries = 70
    retry_count = 0
    data: Optional[List[Dict[str, Any]]] = None

    while retry_count < max_retries:
        file_status = await get_file_status(file_id=file_id, **kwargs)
        if file_status and file_status[0]["fileStatus"] == "failed":
            raise ThirdPartyException("Document splitting failed")

        if file_status and file_status[0]["fileStatus"] in ["spliting", "ocring"]:
            logger.info(
                f"File: {file_id} - Retry {retry_count + 1}, document is being chunked, continuing to retry..."
            )
            retry_count += 1
            if retry_count < max_retries:
                await asyncio.sleep(4)
                continue

        chunks_url = (
            os.getenv("XINGHUO_RAG_URL", "")
            + "openapi/v1/file/chunks?fileId="
            + file_id
            + "&multiLable=true"
        )
        response = await async_request({}, chunks_url, "GET", **kwargs)

        if response:
            # Response could be a dict or list, handle both cases
            if isinstance(response, list):
                data = response
            else:
                # If response is a dict, wrap it in a list to match expected type
                data = [response] if response else []
            break

        logger.info(
            f"File: {file_id} - Retry {retry_count + 1}, document chunk content not obtained, continuing to retry..."
        )
        retry_count += 1
        if retry_count < max_retries:
            await asyncio.sleep(4)

    if not data:
        raise CustomException(
            CodeEnum.GetFileContentFailed,
            "Xinghuo knowledge base failed to get document chunk content data",
        )

    # Ensure data is properly typed as List[Dict[str, Any]]
    return data if isinstance(data, list) else []


async def new_topk_search(
    query: str,
    doc_ids: Optional[List[str]] = None,
    top_n: Optional[int] = 5,
    **kwargs: Any,
) -> Dict[str, Any]:
    """
    Use new retrieval interface for hybrid search.

    Args:
        query: Query string
        doc_ids: Document ID list
        top_n: Number of results to return

    Returns:
        Search result data
    """
    post_body = {
        "datasetId": os.getenv("XINGHUO_DATASET_ID", ""),
        "fileIds": doc_ids or [],
        "topK": top_n,
        "overlap": os.getenv("XINGHUO_SEARCH_OVERLAP", ""),
        "query": query,
        "chunkType": "RAW",
    }

    data = await async_request(
        post_body,
        os.getenv("XINGHUO_RAG_URL", "") + "openapi/v1/dataset/search/mix",
        **kwargs,
    )
    return data


async def get_file_status(
    file_id: Optional[str] = None, **kwargs: Any
) -> List[Dict[str, Any]]:
    """
    Get file status information.

    Args:
        file_id: File ID

    Returns:
        File status data
    """
    if not file_id:
        return []

    get_body = {"fileIds": file_id}
    data = await async_form_request(
        get_body,
        os.getenv("XINGHUO_RAG_URL", "") + "openapi/v1/file/status",
        "POST",
        **kwargs,
    )
    # Handle response type - could be dict or list
    if isinstance(data, list):
        return data
    elif isinstance(data, dict):
        return [data]  # Wrap single dict in list
    else:
        return []


async def get_file_info(file_id: Optional[str] = None, **kwargs: Any) -> Dict[str, Any]:
    """
    Get detailed file information.

    Args:
        file_id: File ID

    Returns:
        File information data
    """
    if not file_id:
        return {}

    get_body = {"fileId": file_id}
    data = await async_form_request(
        get_body,
        os.getenv("XINGHUO_RAG_URL", "") + "openapi/v1/file/info",
        "POST",
        **kwargs,
    )
    return data or {}


async def dataset_addchunk(
    chunks: Optional[List[Any]] = None, **kwargs: Any
) -> Dict[str, Any]:
    """
    Add chunks to dataset.

    Args:
        chunks: List of chunk objects

    Returns:
        Result data of add operation
    """
    data = await async_request(
        chunks or [],
        os.getenv("XINGHUO_RAG_URL", "")
        + "openapi/v1/dataset/add-chunk?datasetId="
        + os.getenv("XINGHUO_DATASET_ID", ""),
        **kwargs,
    )
    return data


async def dataset_delchunk(
    chunk_ids: Optional[List[str]] = None, **kwargs: Any
) -> Dict[str, Any]:
    """
    Delete chunks from dataset.

    Args:
        chunk_ids: List of chunk IDs

    Returns:
        Result data of delete operation
    """
    if not chunk_ids:
        return {}

    chunk_ids_str = ",".join(chunk_ids)
    delete_body = {
        "datasetId": os.getenv("XINGHUO_DATASET_ID", ""),
        "chunkIds": chunk_ids_str,
    }

    data = await async_form_request(
        delete_body,
        os.getenv("XINGHUO_RAG_URL", "") + "openapi/v1/dataset/delete-chunks",
        "DELETE",
        **kwargs,
    )
    return data


async def dataset_updchunk(chunk: Dict[str, Any], **kwargs: Any) -> Dict[str, Any]:
    """
    Update chunks in dataset.

    Args:
        chunk: Chunk data dictionary

    Returns:
        Result data of update operation
    """
    upd_body = {
        "id": chunk.get("chunkId"),
        "chunkType": chunk.get("chunkType", "RAW"),
        "content": chunk.get("content", ""),
        "question": chunk.get("question", ""),
        "answer": chunk.get("answer", ""),
        "imgReference": chunk.get("imgReference", {}),
    }

    data = await async_request(
        upd_body,
        os.getenv("XINGHUO_RAG_URL", "")
        + "openapi/v1/dataset/update-chunk?datasetId="
        + os.getenv("XINGHUO_DATASET_ID", ""),
        "POST",
        **kwargs,
    )
    return data


async def async_request(
    body: Any, url: str, method: str = "POST", **kwargs: Any
) -> Dict[str, Any]:
    """
    Send asynchronous request to Xinghuo knowledge base API.

    Args:
        body: Request body data
        url: Request URL
        method: HTTP method

    Returns:
        API response data

    Raises:
        ThirdPartyException: Raised when network error, timeout, or API returns error
    """
    span = kwargs.get("span")
    if span:
        with span.start(
            func_name="REQUEST_ASYNC_XINGHUO", add_source_function_name=True
        ) as span_context:
            span_context.add_info_events(
                {"RAG_URL": json.dumps(url, ensure_ascii=False)}
            )
            span_context.add_info_events(
                {"RAG_INPUT": json.dumps(body, ensure_ascii=False)}
            )

            headers = await assemble_spark_auth_headers_async()
            headers["Content-Type"] = "application/json"

            try:
                async with aiohttp.ClientSession() as session:
                    async with session.request(
                        method=method,
                        url=url,
                        data=json.dumps(body),
                        headers=headers,
                        timeout=aiohttp.ClientTimeout(
                            total=float(os.getenv("XINGHUO_CLIENT_TIMEOUT", "60.0"))
                        ),
                    ) as response:
                        background_json = await response.text()
                        span_context.add_info_events({"RAG_OUTPUT": background_json})
                        msg_js = json.loads(background_json)

                        if msg_js["code"] == 0 and msg_js["flag"]:
                            return msg_js["data"]
                        logger.error(
                            url + "Failed to 【XINGHUO-RAG】,err reason %s",
                            msg_js["desc"],
                        )
                        raise ThirdPartyException(msg_js["desc"])
            except aiohttp.ClientError as e:
                logger.error(f"Network error: {e}")
                span_context.record_exception(e)
                raise ThirdPartyException(
                    e=CodeEnum.CBG_RAGError, msg=f"CBG Network error: {e}"
                ) from e
            except asyncio.TimeoutError as e:
                logger.error(f"Request timeout: {url}")
                span_context.record_exception(e)
                raise ThirdPartyException(
                    e=CodeEnum.CBG_RAGError, msg=f"CBG Request timeout: {url}"
                ) from e
    else:
        # Fallback without span
        headers = await assemble_spark_auth_headers_async()
        headers["Content-Type"] = "application/json"

        try:
            async with aiohttp.ClientSession() as session:
                async with session.request(
                    method=method,
                    url=url,
                    data=json.dumps(body),
                    headers=headers,
                    timeout=aiohttp.ClientTimeout(
                        total=float(os.getenv("XINGHUO_CLIENT_TIMEOUT", "60.0"))
                    ),
                ) as response:
                    background_json = await response.text()
                    msg_js = json.loads(background_json)

                    if msg_js["code"] == 0 and msg_js["flag"]:
                        return msg_js["data"]
                    logger.error(
                        url + "Failed to 【XINGHUO-RAG】,err reason %s",
                        msg_js["desc"],
                    )
                    raise ThirdPartyException(msg_js["desc"])
        except aiohttp.ClientError as e:
            logger.error(f"Network error: {e}")
            raise ThirdPartyException(
                e=CodeEnum.CBG_RAGError, msg=f"CBG Network error: {e}"
            ) from e
        except asyncio.TimeoutError as e:
            logger.error(f"Request timeout: {url}")
            raise ThirdPartyException(
                e=CodeEnum.CBG_RAGError, msg=f"CBG Request timeout: {url}"
            ) from e


def _prepare_form_data(body: Any) -> aiohttp.FormData:
    """
    Prepare form data

    Args:
        body: Request body data

    Returns:
        FormData: Prepared form data
    """
    form_data = aiohttp.FormData()
    # Iterate through body dictionary, add fields to FormData
    for key, value in body.items():
        # Regular fields (strings, numbers, etc.)
        form_data.add_field(key, str(value))
    return form_data


async def _process_form_response(
    resp: aiohttp.ClientResponse, url: str, span_context: Any
) -> Dict[str, Any]:
    """
    Process form HTTP response

    Args:
        resp: HTTP response object
        url: Request URL
        span_context: Tracking context

    Returns:
        Dict[str, Any]: Processed response data

    Raises:
        ThirdPartyException: Raised when response error occurs
    """
    response_text = await resp.text()
    if span_context:
        span_context.add_info_events({"RAG_OUTPUT": response_text})

    if resp.status != 200:
        logger.error(f"{url} Failed to 【XINGHUO-RAG】; err code {resp.status}")
        raise ThirdPartyException(f"Failed to 【XINGHUO-RAG】; code: {resp.status}")

    try:
        msg_js = await resp.json()
    except json.JSONDecodeError:
        msg_js = json.loads(response_text)

    if msg_js.get("code") == 0 and msg_js.get("flag"):
        return msg_js.get("data", {})

    error_desc = msg_js.get("desc", "Unknown error from XINGHUO-RAG")
    logger.error(f"{url} Failed to 【XINGHUO-RAG】, err reason {error_desc}")
    raise ThirdPartyException(e=CodeEnum.CBG_RAGError, msg=error_desc)


def _handle_form_request_error(e: Exception, url: str, span_context: Any) -> None:
    """
    Handle form request errors

    Args:
        e: Exception object
        url: Request URL
        span_context: Tracking context

    Raises:
        ThirdPartyException: Unified third-party exception
    """
    if isinstance(e, asyncio.TimeoutError):
        error_msg = f"Request to {url} timed out after 60 seconds"
        logger.error(error_msg)
        if span_context:
            span_context.record_exception(e)
        raise ThirdPartyException(
            e=CodeEnum.CBG_RAGError, msg=f"CBG Request error: {e}"
        ) from e
    if isinstance(e, aiohttp.ClientError):
        error_msg = f"Network error during request to {url}: {e}"
        logger.error(error_msg)
        if span_context:
            span_context.record_exception(e)
        raise ThirdPartyException(
            e=CodeEnum.CBG_RAGError, msg=f"CBG Network error: {e}"
        ) from e
    error_msg = f"Unexpected error during request to {url}: {e}"
    logger.error(error_msg)
    if span_context:
        span_context.record_exception(e)
    raise ThirdPartyException(e=CodeEnum.CBG_RAGError, msg=str(e)) from e


async def async_form_request(
    body: Any, url: str, method: str = "POST", **kwargs: Any
) -> Dict[str, Any]:
    """
    Send form request to Xinghuo knowledge base API (using native aiohttp).

    Args:
        body: Request body data
        url: Request URL
        method: HTTP method

    Returns:
        API response data

    Raises:
        ThirdPartyException: Raised when network error or API returns error
    """
    span = kwargs.get("span")

    if span:
        with span.start(
            func_name="REQUEST_XINGHUO_FROM", add_source_function_name=True
        ) as span_context:
            span_context.add_info_events(
                {"RAG_URL": json.dumps(url, ensure_ascii=False)}
            )
            span_context.add_info_events(
                {"RAG_INPUT": json.dumps(body, ensure_ascii=False)}
            )

            # Prepare form data
            form_data = _prepare_form_data(body)

            # Get authentication headers
            headers = await assemble_spark_auth_headers_async()

            try:
                # Use native aiohttp to send asynchronous request
                async with aiohttp.ClientSession() as session:
                    async with session.request(
                        method=method,
                        url=url,
                        data=form_data,
                        headers=headers,
                        timeout=aiohttp.ClientTimeout(
                            total=float(os.getenv("XINGHUO_CLIENT_TIMEOUT", "60.0"))
                        ),
                    ) as resp:
                        return await _process_form_response(resp, url, span_context)

            except Exception as e:
                _handle_form_request_error(e, url, span_context)
    else:
        # Fallback without span
        form_data = _prepare_form_data(body)
        headers = await assemble_spark_auth_headers_async()

        try:
            async with aiohttp.ClientSession() as session:
                async with session.request(
                    method=method,
                    url=url,
                    data=form_data,
                    headers=headers,
                    timeout=aiohttp.ClientTimeout(
                        total=float(os.getenv("XINGHUO_CLIENT_TIMEOUT", "60.0"))
                    ),
                ) as resp:
                    return await _process_form_response(resp, url, None)
        except Exception as e:
            _handle_form_request_error(e, url, None)

    return {}


async def assemble_spark_auth_headers_async() -> Dict[str, str]:
    """
    Asynchronously build authentication request headers
    """
    timestamp = int(time.time())
    signature = get_signature(
        os.getenv("XINGHUO_APP_ID", ""), timestamp, os.getenv("XINGHUO_APP_SECRET", "")
    )

    headers = {
        "Accept": "application/json",
        "appId": os.getenv("XINGHUO_APP_ID", ""),
        "timestamp": str(timestamp),
        "signature": signature,
    }
    return headers
