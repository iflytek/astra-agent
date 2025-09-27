"""
AIUI interface client module
Provides various interaction functions with AIUI service, including document parsing, chunk querying, and slicing operations
"""

import asyncio
import base64
import hashlib
import hmac
import json
import os
from datetime import datetime
from time import mktime
from typing import Any, Dict, List, Optional
from urllib.parse import urlencode, urlparse
from wsgiref.handlers import format_date_time

import aiohttp
from loguru import logger

from knowledge.consts.error_code import CodeEnum
from knowledge.exceptions.exception import CustomException, ThirdPartyException
from knowledge.utils.file_utils import get_file_extension_from_url
from knowledge.utils.verification import check_not_empty


async def assemble_auth_url(request_path: str, method: str = "POST") -> str:
    """
    Assemble authentication URL

    Args:
        request_url: Request URL
        method: HTTP method

    Returns:
        Complete URL with authentication parameters
    """
    api_key = os.getenv("AIUI_API_KEY", "")
    api_secret = os.getenv("AIUI_API_SECRET", "")
    request_url = os.getenv("AIUI_URL_V2", "") + request_path

    url_parsed = urlparse(request_url)
    host = url_parsed.hostname
    path = url_parsed.path

    now = datetime.now()
    date = format_date_time(mktime(now.timetuple()))

    signature_origin = f"host: {host}\ndate: {date}\n{method} {path} HTTP/1.1"
    signature_sha = hmac.new(
        api_secret.encode("utf-8"),
        signature_origin.encode("utf-8"),
        digestmod=hashlib.sha256,
    ).digest()

    signature_sha = base64.b64encode(signature_sha).decode(encoding="utf-8")
    authorization_origin = (
        f'api_key="{api_key}", algorithm="hmac-sha256", '
        f'headers="host date request-line", signature="{signature_sha}"'
    )

    authorization = base64.b64encode(authorization_origin.encode("utf-8")).decode(
        encoding="utf-8"
    )

    values = {"host": host, "date": date, "authorization": authorization}
    return f"{request_url}?{urlencode(values)}"


async def chunk_query(
    query: str,
    doc_ids: Optional[List[str]] = None,
    repo_ids: Optional[List[str]] = None,
    top_k: Optional[int] = None,
    threshold: Optional[float] = 0,
    **kwargs: Any,
) -> Dict[str, Any]:
    """
    Retrieve similar document chunks based on user input content

    Args:
        query: Query text
        doc_ids: Document ID list
        repo_ids: Knowledge base ID list
        top_k: Number of results to return
        threshold: Similarity threshold
        **kwargs: Other parameters

    Returns:
        Query results
    """
    post_body = {
        "query": query,
        "topN": top_k,
        "topK": 10,
        "reRankMethod": "search",
        "match": {"docIds": doc_ids, "groups": repo_ids, "uid": None},
        "repoSources": [
            {"repoId": os.getenv("AIUI_QUERY_REPOID_V2", ""), "threshold": threshold}
        ],
    }
    url = await assemble_auth_url(request_path="/v2/aiui/cbm/chunk/query")
    return await request(post_body=post_body, url=url, **kwargs)


async def document_parse(
    file_url: str, resource_type: int, **kwargs: Any
) -> Dict[str, Any]:
    """
    Parse document

    Args:
        file_url: File URL
        resource_type: Resource type
        **kwargs: Other parameters

    Returns:
        Parse results

    Raises:
        CustomException: When file type retrieval fails or resource type does not exist
    """
    post_body = {"file": file_url, "fileType": "pdf", "useLayout": False}

    if resource_type == 0:
        image_extensions = {"jpg", "jpeg", "png", "bmp"}
        file_extension = get_file_extension_from_url(file_url)

        if check_not_empty(file_extension):
            post_body["fileType"] = file_extension
            post_body["useLayout"] = file_extension.upper() == "PDF"

            if file_extension.lower() in image_extensions:
                post_body["fileType"] = "image"
        else:
            raise CustomException(
                e=CodeEnum.ParameterInvalid,
                msg=f"File type retrieval failed: {file_url}",
            )
    elif resource_type == 1:
        post_body["fileType"] = "url"
    else:
        raise CustomException(
            e=CodeEnum.ParameterInvalid,
            msg="Resource type [resourceType] does not exist",
        )
    url = await assemble_auth_url(request_path="/v2/aiui/cbm/document/parse")
    return await request(post_body=post_body, url=url, **kwargs)


async def chunk_split(
    document: Any,
    length_range: Optional[List[int]] = None,
    overlap: Optional[int] = None,
    cut_off: Optional[List[str]] = None,
    separator: Optional[List[str]] = None,
    title_split: Optional[bool] = None,
    **kwargs: Any,
) -> Dict[str, Any]:
    """
    Slice document

    Args:
        document: Document object
        length_range: Length range
        overlap: Overlap length
        cut_off: Cutoff markers
        separator: Separator list
        title_split: Whether to split by title
        **kwargs: Other parameters

    Returns:
        Split results
    """
    post_body = {
        "lengthRange": length_range,
        "overlap": overlap,
        "cutOff": cut_off,
        "separator": separator,
        "document": document,
        "titleSplit": title_split,
    }

    url = await assemble_auth_url(request_path="/v2/aiui/cbm/chunk/split")

    return await request(post_body=post_body, url=url, **kwargs)


async def chunk_save(
    doc_id: str, group: str, chunks: List[Any], **kwargs: Any
) -> Dict[str, Any]:
    """
    Save chunks

    Args:
        doc_id: Document ID
        group: Group name
        chunks: Chunk list
        **kwargs: Other parameters

    Returns:
        Save results
    """
    post_body = {"docId": doc_id, "group": group, "chunks": chunks}
    url = await assemble_auth_url(
        request_path="/v2/aiui/cbm/chunk/" + os.getenv("AIUI_QUERY_REPOID_V2", "")
    )
    return await request(post_body=post_body, url=url, **kwargs)


async def chunk_delete(
    doc_id: str, chunk_ids: Optional[List[str]] = None, **kwargs: Any
) -> Dict[str, Any]:
    """
    Delete chunks

    Args:
        doc_id: Document ID
        chunk_ids: Chunk ID list
        **kwargs: Other parameters

    Returns:
        Delete results
    """
    post_body = {"docId": doc_id, "chunkIds": chunk_ids}
    url = await assemble_auth_url(
        request_path="/v2/aiui/cbm/chunk/" + os.getenv("AIUI_QUERY_REPOID_V2", ""),
        method="DELETE",
    )

    return await request(post_body=post_body, url=url, method="DELETE", **kwargs)


async def get_doc_content(doc_id: str, **kwargs: Any) -> Dict[str, Any]:
    """
    Get document content

    Args:
        doc_id: Document ID
        **kwargs: Other parameters

    Returns:
        Document content
    """
    url = await assemble_auth_url(
        request_path="/v2/aiui/cbm/chunk/" + os.getenv("AIUI_QUERY_REPOID_V2", ""),
        method="GET",
    )
    params = {"docId": doc_id}
    url += "&" + urlencode(params)
    return await request(post_body=params, url=url, method="GET", **kwargs)


async def request(
    post_body: Dict[str, Any], url: str, method: str = "POST", **kwargs: Any
) -> Dict[str, Any]:
    """
    Send request to AIUI service

    Args:
        post_body: Request body
        url: URL
        method: HTTP method
        **kwargs: Other parameters

    Returns:
        Response data

    Raises:
        ThirdPartyException: When AIUI service returns error or network exception occurs
    """
    span = kwargs.get("span")
    if span:
        with span.start(
            func_name="REQUEST_AIUI",
            add_source_function_name=True,
            attributes={"url": url},
        ) as span_context:
            logger.info(f"【url】:{url};Request AIUI body:{post_body}")
            span_context.add_info_events(
                {"AIUI_INPUT": json.dumps(post_body, ensure_ascii=False)}
            )

            try:
                async with aiohttp.ClientSession() as session:
                    async with session.request(
                        method=method,
                        url=url,
                        data=json.dumps(post_body),
                        timeout=aiohttp.ClientTimeout(
                            total=float(os.getenv("AIUI_CLIENT_TIMEOUT", "30.0"))
                        ),
                    ) as response:
                        response_text = await response.text()
                        logger.info(
                            f"【url】:{url};Response 【XINGHUO-RAG】 body:{response_text}"
                        )
                        span_context.add_info_events({"AIUI_OUTPUT": response_text})
                        msg_json = json.loads(response_text)
                        try:
                            if msg_json["message"]["code"] == 0:
                                return msg_json["data"]

                            if msg_json["message"]["code"] == 1020:
                                return msg_json["data"]

                            error_msg = f"【url】{url}, reason {msg_json} "
                            logger.error(
                                f"{url} Failed to AIUI knowledge, err reason {error_msg}"
                            )

                            raise ThirdPartyException(
                                e=CodeEnum.AIUI_RAGError, msg=msg_json
                            )
                        except Exception:
                            raise ThirdPartyException(
                                e=CodeEnum.AIUI_RAGError, msg=msg_json
                            )

            except aiohttp.ClientError as e:
                logger.error(f"AIUI Network error: {e}")
                span_context.record_exception(e)
                raise ThirdPartyException(
                    e=CodeEnum.AIUI_RAGError, msg=f"AIUI Network error: {e}"
                ) from e

            except asyncio.TimeoutError as e:
                logger.error(f"AIUI Request timeout: {url}")
                span_context.record_exception(e)
                raise ThirdPartyException(
                    e=CodeEnum.AIUI_RAGError, msg=f"AIUI Request timeout: {url}"
                ) from e
