#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
RAGFlow Client Utility Functions Module

Provides functional call interfaces for RAGFlow API with module-level session management and configuration caching
"""

import asyncio
import io
import logging
import os
import time
from typing import Any, Dict, List, Optional
from urllib.parse import urljoin

import aiohttp

try:
    from ragflow_sdk import RAGFlow  # type: ignore[import-untyped]
except ImportError:
    # Handle missing ragflow_sdk dependency
    RAGFlow = None  # type: ignore[assignment]

# Import constants module to ensure environment variables are loaded properly
# from knowledge.consts import constants

logger = logging.getLogger(__name__)

# Module-level configuration cache and session management
_config_cache = None
_session_cache = None
_session_lock = asyncio.Lock()
_rag_object = None


def get_rag_object() -> Any:
    """
    Get or create RAGFlow client instance with proper configuration loading
    """
    global _rag_object
    if _rag_object is None:
        if RAGFlow is None:
            raise ImportError("ragflow_sdk is not available")

        # Use os.getenv directly to get environment variables, consistent with aiui.py
        base_url = os.getenv("RAGFLOW_BASE_URL", "")
        api_key = os.getenv("RAGFLOW_API_TOKEN", "")

        if not base_url:
            raise ValueError("RAGFLOW_BASE_URL not configured in environment variables")
        if not api_key:
            raise ValueError(
                "RAGFLOW_API_TOKEN not configured in environment variables"
            )

        _rag_object = RAGFlow(api_key=api_key, base_url=base_url)
        print(f"RAGFlow client initialized with base_url: {base_url}")

    return _rag_object


def _load_ragflow_config() -> Dict[str, Any]:
    """
    Load RAGFlow configuration from constants module (with caching)

    Returns:
        Configuration dictionary
    """
    global _config_cache

    if _config_cache is None:

        # Safe conversion of timeout to integer
        timeout_value = os.getenv("RAGFLOW_TIMEOUT", "30")
        try:
            timeout_int = int(timeout_value) if timeout_value else 30
        except (ValueError, TypeError):
            timeout_int = 30
            logger.warning(
                f"Invalid RAGFLOW_TIMEOUT value: {timeout_value}, using default: 30"
            )

        _config_cache = {
            "base_url": os.getenv("RAGFLOW_BASE_URL", ""),
            "api_token": os.getenv("RAGFLOW_API_TOKEN", ""),
            "timeout": timeout_int,
            "default_group": os.getenv("RAGFLOW_DEFAULT_GROUP", ""),
        }

        # Validate required configuration
        if not _config_cache["base_url"] or not _config_cache["api_token"]:
            logger.warning(
                "RAGFlow configuration incomplete, please check config.env file"
            )
            logger.warning(
                "Required configuration: RAGFLOW_BASE_URL and RAGFLOW_API_TOKEN"
            )
        else:
            logger.info(f"RAGFlow configuration loaded: {_config_cache['base_url']}")

    return _config_cache


async def _get_session() -> aiohttp.ClientSession:
    """
    Get reusable HTTP session (singleton pattern)

    Returns:
        aiohttp client session
    """
    global _session_cache

    async with _session_lock:
        # Create new session if it doesn't exist, is closed, or connector is closed
        if (
            _session_cache is None
            or _session_cache.closed
            or (_session_cache.connector and _session_cache.connector.closed)
        ):

            config = _load_ragflow_config()

            timeout_config = aiohttp.ClientTimeout(total=config["timeout"])
            connector = aiohttp.TCPConnector(
                limit=100,  # Total connection pool size
                limit_per_host=30,  # Connections per host
                keepalive_timeout=600,  # Keep connection time
                enable_cleanup_closed=True,
            )

            _session_cache = aiohttp.ClientSession(
                connector=connector,
                timeout=timeout_config,
                headers={
                    "Authorization": f'Bearer {config["api_token"]}',
                    "User-Agent": "OpenStellar-RAGFlow/1.0",
                },
            )

            logger.debug("RAGFlow HTTP session created and cached")

    return _session_cache


async def _create_file_form_data(files: Dict) -> aiohttp.FormData:
    """
    Create form data for file upload requests

    Args:
        files: File data dictionary

    Returns:
        aiohttp FormData object
    """
    form_data = aiohttp.FormData()

    for key, file_info in files.items():
        if isinstance(file_info, dict):
            file_content = file_info["content"]
            filename = file_info["filename"]
            content_type = file_info.get("content_type", "application/octet-stream")

            file_stream = io.BytesIO(file_content)
            form_data.add_field(
                key, file_stream, filename=filename, content_type=content_type
            )
        else:
            file_stream = _create_file_stream(file_info)
            form_data.add_field(
                key, file_stream, filename="upload.txt", content_type="text/plain"
            )

    return form_data


def _create_file_stream(file_info: Any) -> io.BytesIO:
    """
    Create file stream from file info

    Args:
        file_info: File information (bytes or string)

    Returns:
        BytesIO stream
    """
    if isinstance(file_info, bytes):
        return io.BytesIO(file_info)
    else:
        return io.BytesIO(file_info.encode("utf-8"))


async def _send_file_request(
    session: aiohttp.ClientSession,
    method: str,
    url: str,
    form_data: aiohttp.FormData,
    config: Dict[str, Any],
) -> tuple[Dict[str, Any], int]:
    """
    Send file upload request

    Args:
        session: HTTP session
        method: HTTP method
        url: Request URL
        form_data: Form data for file upload
        config: Configuration dictionary

    Returns:
        Response data
    """
    upload_headers = {
        "Authorization": f'Bearer {config["api_token"]}',
        "User-Agent": "OpenStellar-RAGFlow/1.0",
    }

    async with session.request(
        method, url, data=form_data, headers=upload_headers
    ) as response:
        return await response.json(), response.status


async def _send_json_request(
    session: aiohttp.ClientSession,
    method: str,
    url: str,
    data: Optional[Dict[str, Any]],
    config: Dict[str, Any],
) -> tuple[Dict[str, Any], int]:
    """
    Send JSON request

    Args:
        session: HTTP session
        method: HTTP method
        url: Request URL
        data: JSON data
        config: Configuration dictionary

    Returns:
        Response data
    """
    json_headers = {
        "Authorization": f'Bearer {config["api_token"]}',
        "Content-Type": "application/json",
        "User-Agent": "OpenStellar-RAGFlow/1.0",
    }

    async with session.request(
        method, url, json=data, headers=json_headers
    ) as response:
        return await response.json(), response.status


def _is_session_closed_error(error: Exception) -> bool:
    """
    Check if error is due to session being closed

    Args:
        error: Exception to check

    Returns:
        True if session closed error
    """
    return "Event loop is closed" in str(error) or "Session is closed" in str(error)


async def _handle_session_error(
    attempt: int, max_retries: int, error: Exception
) -> None:
    """
    Handle session closed errors with retry logic

    Args:
        attempt: Current attempt number
        max_retries: Maximum retry attempts
        error: The error that occurred

    Raises:
        Exception: If max retries exceeded
    """
    global _session_cache
    _session_cache = None
    logger.warning(f"Session closed, retrying... (attempt {attempt + 1}/{max_retries})")

    if attempt == max_retries - 1:
        raise Exception(f"Session closed and retry failed: {error}")
    return None  # This should never be reached but satisfies mypy


async def _make_request(
    method: str,
    endpoint: str,
    data: Optional[Dict[str, Any]] = None,
    files: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    """
    Common function for sending HTTP requests

    Args:
        method: HTTP method
        endpoint: API endpoint
        data: Request data
        files: File data

    Returns:
        API response data
    """
    config = _load_ragflow_config()
    max_retries = 2

    for attempt in range(max_retries):
        try:
            session = await _get_session()
            url = urljoin(config["base_url"], endpoint)

            if files:
                form_data = await _create_file_form_data(files)
                result, status = await _send_file_request(
                    session, method, url, form_data, config
                )
            else:
                result, status = await _send_json_request(
                    session, method, url, data, config
                )

            logger.debug(f"{method} {endpoint} - Status: {status}")

            if status != 200:
                raise Exception(f"API request failed: {status} - {result}")

            return result

        except (aiohttp.ClientConnectionError, RuntimeError) as e:
            if _is_session_closed_error(e):
                await _handle_session_error(attempt, max_retries, e)
                continue
            else:
                raise e
        except Exception as e:
            logger.error(f"Request failed: {method} {endpoint} - {e}")
            logger.error(f"Request URL: {url}")
            if data:
                logger.error(f"Request data: {data}")
            raise

    # This should never be reached due to exceptions being raised
    raise Exception("All retry attempts failed")


async def cleanup_session() -> None:
    """
    Clean up session resources (called when application shuts down)
    """
    global _session_cache

    if _session_cache and not _session_cache.closed:
        await _session_cache.close()
        _session_cache = None
        logger.info("RAGFlow HTTP session cleaned up")


def reload_config() -> None:
    """
    Reload configuration (called after configuration changes)
    """
    global _config_cache, _rag_object
    _config_cache = None
    _rag_object = None  # Reset RAGFlow client instance
    logger.info("RAGFlow configuration cache cleared, will reload on next request")


# ==================== Query Related APIs ====================


async def retrieval(request_data: Dict[str, Any]) -> Dict[str, Any]:
    """
    Retrieval query API

    Args:
        request_data: Query request in RAGFlow format

    Returns:
        Query response in RAGFlow format
    """
    return await _make_request("POST", "/api/v1/retrieval", data=request_data)


async def retrieval_with_dataset(
    dataset_id: str, request_data: Dict[str, Any]
) -> Dict[str, Any]:
    """
    Retrieval query API using specified dataset

    Args:
        dataset_id: Dataset ID (currently unused, parameter already included in request_data)
        request_data: Query request in RAGFlow format, contains all required parameters

    Returns:
        Query response in RAGFlow format
    """
    # Use the provided request_data directly as it's already formatted according to RAGFlow API specifications
    return await _make_request("POST", "/api/v1/retrieval", data=request_data)


# ==================== Dataset Management APIs ====================


async def list_datasets(
    name: Optional[str] = None, page: int = 1, page_size: int = 30
) -> Dict[str, Any]:
    """
    List datasets API

    Args:
        name: Dataset name (optional filter)
        page: Page number
        page_size: Page size

    Returns:
        Dataset list response
    """
    params: Dict[str, Any] = {"page": page, "page_size": page_size}
    if name:
        params["name"] = name

    # Build query string
    query_string = "&".join([f"{k}={v}" for k, v in params.items()])
    endpoint = f"/api/v1/datasets?{query_string}"

    return await _make_request("GET", endpoint)


async def create_dataset(name: str, **kwargs: Any) -> Dict[str, Any]:
    """
    Create dataset API

    Args:
        name: Dataset name
        **kwargs: Additional parameters (avatar, description, embedding_model, etc.)

    Returns:
        Creation response containing dataset information
    """
    data = {"name": name}
    data.update(kwargs)

    return await _make_request("POST", "/api/v1/datasets", data=data)


# ==================== Document Management APIs ====================


async def upload_document_to_dataset(
    dataset_id: str, file_content: bytes, filename: str
) -> List[Any]:
    """
    Upload document to specified dataset API

    Args:
        dataset_id: Dataset ID
        file_content: File content
        filename: File name

    Returns:
        Upload response containing document ID
    """

    dataset = get_rag_object().list_datasets(
        name=os.getenv("RAGFLOW_DEFAULT_GROUP", "")
    )[0]
    return dataset.upload_documents(
        [{"displayed_name": filename, "blob": file_content}]
    )


async def update_document(
    dataset_id: str, document_id: str, **kwargs: Any
) -> Dict[str, Any]:
    """
    Update document configuration API

    Args:
        dataset_id: Dataset ID
        document_id: Document ID
        **kwargs: Update parameters (name, chunk_method, parser_config, etc.)

    Returns:
        Update response
    """
    endpoint = f"/api/v1/datasets/{dataset_id}/documents/{document_id}"
    return await _make_request("PUT", endpoint, data=kwargs)


async def parse_documents(dataset_id: str, document_ids: List[str]) -> Dict[str, Any]:
    """
    Parse documents API

    Args:
        dataset_id: Dataset ID
        document_ids: Document ID list

    Returns:
        Parse response
    """
    data = {"document_ids": document_ids}
    endpoint = f"/api/v1/datasets/{dataset_id}/chunks"
    return await _make_request("POST", endpoint, data=data)


async def list_documents_in_dataset(
    dataset_id: str, doc_id: str, page: int = 1, page_size: int = 30, **kwargs: Any
) -> Dict[str, Any]:
    """
    List documents in dataset API

    Args:
        dataset_id: Dataset ID
        doc_id: Document ID
        page: Page number
        page_size: Page size
        **kwargs: Additional filter parameters

    Returns:
        Document list response
    """
    params = {"page": page, "page_size": page_size, "id": doc_id}
    params.update(kwargs)

    # Build query string
    query_string = "&".join([f"{k}={v}" for k, v in params.items()])
    endpoint = f"/api/v1/datasets/{dataset_id}/documents?{query_string}"

    return await _make_request("GET", endpoint)


async def list_document_chunks(
    dataset_id: str,
    document_id: str,
    page: int = 1,
    page_size: int = 1024,
    **kwargs: Any,
) -> Dict[str, Any]:
    """
    List document chunks API

    Args:
        dataset_id: Dataset ID
        document_id: Document ID
        page: Page number
        page_size: Page size
        **kwargs: Additional filter parameters

    Returns:
        Chunk list response
    """
    params = {"page": page, "page_size": page_size}
    params.update(kwargs)

    # Build query string
    query_string = "&".join([f"{k}={v}" for k, v in params.items()])
    endpoint = (
        f"/api/v1/datasets/{dataset_id}/documents/{document_id}/chunks?{query_string}"
    )

    return await _make_request("GET", endpoint)


async def get_document_info(dataset_id: str, doc_id: str) -> Optional[Dict[str, Any]]:
    """
    Get detailed information for a single document

    Args:
        dataset_id: Dataset ID
        doc_id: Document ID

    Returns:
        Document information, returns None if not found
    """
    try:
        # Do not pass doc_id parameter, get all documents then iterate to find
        response = await list_documents_in_dataset(
            dataset_id, doc_id="", page=1, page_size=1000
        )

        if response.get("code") == 0:
            docs = response.get("data", {}).get("docs", [])
            for doc in docs:
                if doc.get("id") == doc_id:
                    return doc
        return None

    except Exception as e:
        logger.error(f"Failed to get document info: {e}")
        return None


async def delete_documents(dataset_id: str, document_ids: List[str]) -> Dict[str, Any]:
    """
    Delete documents API

    Args:
        dataset_id: Dataset ID
        document_ids: List of document IDs to delete

    Returns:
        Deletion response
    """
    data = {"ids": document_ids}
    endpoint = f"/api/v1/datasets/{dataset_id}/documents"
    return await _make_request("DELETE", endpoint, data=data)


async def delete_chunks(
    dataset_id: str, document_id: str, chunk_ids: List[str]
) -> Dict[str, Any]:
    """
    Delete specific chunks of a document API

    Based on RAGFlow official API: DELETE /api/v1/datasets/{dataset_id}/documents/{document_id}/chunks

    Args:
        dataset_id: Dataset ID
        document_id: Document ID
        chunk_ids: List of chunk IDs to delete

    Returns:
        Deletion response
        Success: {"code": 0}
        Failure: {"code": 102, "message": "`chunk_ids` is required"}
    """
    data = {"chunk_ids": chunk_ids}
    endpoint = f"/api/v1/datasets/{dataset_id}/documents/{document_id}/chunks"
    return await _make_request("DELETE", endpoint, data=data)


async def update_chunk(
    dataset_id: str,
    document_id: str,
    chunk_id: str,
    content: Optional[str] = None,
    important_keywords: Optional[List[str]] = None,
    available: Optional[bool] = None,
) -> Dict[str, Any]:
    """
    Update content or configuration of specified chunk

    Based on RAGFlow official API: PUT /api/v1/datasets/{dataset_id}/documents/{document_id}/chunks/{chunk_id}

    Args:
        dataset_id: Dataset ID
        document_id: Document ID
        chunk_id: Chunk ID
        content: Chunk text content (optional)
        important_keywords: Important keywords list (optional)
        available: Chunk availability status (optional)

    Returns:
        Update response
        Success: {"code": 0}
        Failure: {"code": 102, "message": "Can't find this chunk xxx"}
    """
    data: Dict[str, Any] = {}
    if content is not None:
        data["content"] = content
    if important_keywords is not None:
        data["important_keywords"] = important_keywords
    if available is not None:
        data["available"] = available

    endpoint = (
        f"/api/v1/datasets/{dataset_id}/documents/{document_id}/chunks/{chunk_id}"
    )
    return await _make_request("PUT", endpoint, data=data)


async def add_chunk(
    dataset_id: str,
    document_id: str,
    content: str,
    important_keywords: Optional[List[str]] = None,
    questions: Optional[List[str]] = None,
) -> Dict[str, Any]:
    """
    Add chunk to specified document

    Based on RAGFlow official API: POST /api/v1/datasets/{dataset_id}/documents/{document_id}/chunks

    Args:
        dataset_id: Dataset ID
        document_id: Document ID
        content: Chunk text content (required)
        important_keywords: Important keywords list (optional)
        questions: Questions list (optional)

    Returns:
        Addition response
        Success: {
            "code": 0,
            "data": {
                "chunk": {
                    "content": "...",
                    "id": "12ccdc56e59837e5",
                    "important_keywords": [],
                    "questions": []
                }
            }
        }
        Failure: {"code": 102, "message": "`content` is required"}
    """
    data: Dict[str, Any] = {"content": content}
    if important_keywords:
        data["important_keywords"] = important_keywords
    if questions:
        data["questions"] = questions

    endpoint = f"/api/v1/datasets/{dataset_id}/documents/{document_id}/chunks"
    return await _make_request("POST", endpoint, data=data)


# ==================== Helper Functions ====================


async def _check_document_parsing_status(
    dataset_id: str, doc_id: str
) -> tuple[str, int, bool]:
    """
    Check document parsing status

    Args:
        dataset_id: Dataset ID
        doc_id: Document ID

    Returns:
        (status, token_count, found)
    """
    response = await list_documents_in_dataset(dataset_id, doc_id, page=1, page_size=30)

    if response.get("code") != 0:
        return "UNKNOWN", 0, False

    docs = response.get("data", {}).get("docs", [])
    for doc in docs:
        if doc.get("id") == doc_id:
            run_status = doc.get("run", "UNSTART")
            token_count = doc.get("token_count", 0)
            return run_status, token_count, True

    logger.warning(f"Document {doc_id} not found in list")
    return "NOT_FOUND", 0, False


def _handle_parsing_status_result(
    doc_id: str, run_status: str, token_count: int
) -> Optional[str]:
    """
    Handle parsing status and determine if parsing is complete

    Args:
        doc_id: Document ID
        run_status: Current parsing status
        token_count: Token count

    Returns:
        Final status if complete, None if should continue waiting

    Raises:
        Exception: If parsing failed
    """
    if run_status == "DONE":
        logger.info(f"Document {doc_id} parsing completed with {token_count} tokens")
        return run_status
    elif run_status == "FAIL":
        raise Exception(f"Document {doc_id} parsing failed")
    elif run_status == "RUNNING":
        logger.info(f"Document {doc_id} is being parsed...")

    return None


async def wait_for_parsing(
    dataset_id: str, doc_id: str, max_wait_time: int = 300
) -> str:
    """
    Wait for document parsing completion

    Args:
        dataset_id: Dataset ID
        doc_id: Document ID
        max_wait_time: Maximum wait time (seconds)

    Returns:
        Final parsing status

    Raises:
        Exception: Raised when parsing fails
    """
    start_time = time.time()
    last_status = None

    while time.time() - start_time < max_wait_time:
        try:
            run_status, token_count, found = await _check_document_parsing_status(
                dataset_id, doc_id
            )

            if run_status != last_status:
                logger.info(
                    f"Document {doc_id} status: {run_status}, tokens: {token_count}"
                )
                last_status = run_status

            if found:
                result = _handle_parsing_status_result(doc_id, run_status, token_count)
                if result:
                    return result

            await asyncio.sleep(3)

        except Exception as e:
            logger.warning(f"Error checking parsing status: {e}")
            await asyncio.sleep(3)

    logger.warning(
        f"Document parsing timeout after {max_wait_time} seconds, last status: {last_status}"
    )
    return last_status or "TIMEOUT"
