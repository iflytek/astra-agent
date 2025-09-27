#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
RAGFlow Utility Class Module

Provides helper methods for RAGFlow document processing, including file handling, configuration building, format conversion, etc.
"""

import asyncio
import logging
import os
import time
import urllib.parse
from typing import Any, Dict, List, Optional

import aiohttp

from knowledge.infra.ragflow.ragflow_client import (
    create_dataset,
    list_datasets,
    list_document_chunks,
    list_documents_in_dataset,
)

logger = logging.getLogger(__name__)


class RagflowUtils:
    """RAGFlow utility class providing document processing helper methods"""

    @staticmethod
    def get_default_dataset_name() -> str:
        """
        Get default dataset name from environment variable
        """
        return os.getenv("RAGFLOW_DEFAULT_GROUP", "Stellar Knowledge Base")

    @staticmethod
    async def get_dataset_id_by_name(dataset_name: str) -> Optional[str]:
        """
        Get dataset ID by dataset name
        """
        try:
            from knowledge.infra.ragflow import ragflow_client

            datasets_response = await ragflow_client.list_datasets(name=dataset_name)
            if datasets_response.get("code") == 0:
                datasets = datasets_response.get("data", [])
                for dataset in datasets:
                    if dataset.get("name") == dataset_name:
                        return dataset.get("id")
            return None
        except Exception as e:
            logger.error(f"Failed to find dataset: {e}")
            return None

    @staticmethod
    def convert_ragflow_query_response(
        ragflow_response: Dict[str, Any], threshold: float
    ) -> List[Dict[str, Any]]:
        """
        Convert RAGFlow query response to abstract interface format
        Based on format conversion logic in format_converter.py
        """
        results = []
        try:
            # RAGFlow response format: {"code": 0, "data": {"chunks": [...]}}
            if ragflow_response.get("code") == 0 and "data" in ragflow_response:
                chunks_data = ragflow_response["data"].get("chunks", [])

                for chunk in chunks_data:
                    # Get similarity score
                    score = chunk.get("similarity", 0.0)
                    if score >= threshold:

                        title = chunk.get(
                            "document_keyword", chunk.get("document_name", "")
                        )

                        results.append(
                            {
                                "score": score,
                                "docId": chunk.get("document_id", ""),
                                "title": title,
                                "content": chunk.get("content", ""),
                                "context": chunk.get("content", ""),
                                "references": chunk.get("references", {}),
                            }
                        )
        except Exception as e:
            logger.error(f"Failed to convert RAGFlow response: {e}")
        return results

    @staticmethod
    async def ensure_dataset(group: str) -> str:
        """
        Ensure dataset exists, create if it doesn't exist

        Args:
            group: Dataset name

        Returns:
            Dataset ID
        """
        try:
            # 1. Check if dataset exists
            logger.info(f"Checking if dataset exists: {group}")
            datasets_response = await list_datasets(name=group)

            if datasets_response.get("code") == 0:
                datasets = datasets_response.get("data", [])
                for dataset in datasets:
                    if dataset.get("name") == group:
                        dataset_id = dataset.get("id")
                        logger.info(
                            f"Found existing dataset: {group}, ID: {dataset_id}"
                        )
                        return dataset_id

            # 2. Dataset doesn't exist, create new dataset
            logger.info(f"Dataset doesn't exist, creating new dataset: {group}")
            create_response = await create_dataset(
                name=group,
                description=f"Automatically created dataset: {group}",
                chunk_method="naive",
            )

            if create_response.get("code") == 0:
                dataset_id = create_response.get("data", {}).get("id")
                logger.info(f"Dataset created successfully: {group}, ID: {dataset_id}")
                return dataset_id
            else:
                raise Exception(f"Dataset creation failed: {create_response}")

        except Exception as e:
            logger.error(f"Dataset management failed: {e}")
            raise Exception(f"Unable to ensure dataset exists: {str(e)}")

    @staticmethod
    async def _download_url_file(file: str) -> tuple[bytes, str]:
        """
        Download file from URL

        Args:
            file: File URL

        Returns:
            (file content, filename)
        """
        logger.info(f"Downloading file from URL: {file}")

        async with aiohttp.ClientSession() as session:
            async with session.get(file) as response:
                if response.status != 200:
                    raise Exception(f"File download failed: HTTP {response.status}")

                file_content = await response.read()
                logger.info(f"Download completed: {len(file_content)} bytes")

                # Get filename
                filename = RagflowUtils._extract_filename_from_url(file, response)

                # Validate downloaded content
                if len(file_content) == 0:
                    raise Exception("Downloaded file is empty")

                return file_content, filename

    @staticmethod
    def _extract_filename_from_url(file: str, response: Any) -> str:
        """
        Extract filename from URL or response

        Args:
            file: Original URL
            response: HTTP response object

        Returns:
            Extracted filename
        """
        filename = None

        # First try to get filename from HTTP response headers
        if response.url:
            filename = response.url.name

        # If no filename in response headers, extract from URL
        if not filename:
            raw_filename = file.split("/")[-1]
            # Remove URL parameters (content after ?)
            if "?" in raw_filename:
                raw_filename = raw_filename.split("?")[0]
            filename = urllib.parse.unquote(raw_filename, encoding="utf-8")

        return filename

    @staticmethod
    def _read_local_file(file: str) -> tuple[bytes, str]:
        """
        Read local file

        Args:
            file: Local file path

        Returns:
            (file content, filename)
        """
        logger.info(f"Reading local file: {file}")

        if not os.path.exists(file):
            raise Exception(f"Local file does not exist: {file}")

        with open(file, "rb") as f:
            file_content = f.read()
        filename = os.path.basename(file)

        logger.info(
            f"Local file reading completed: {filename}, size: {len(file_content)} bytes"
        )
        return file_content, filename

    @staticmethod
    async def process_file(file: str) -> tuple[bytes, str]:
        """
        Process file (download or read)

        Args:
            file: File path or URL

        Returns:
            (file content, filename)
        """
        if file.startswith(("http://", "https://")):
            return await RagflowUtils._download_url_file(file)
        else:
            return RagflowUtils._read_local_file(file)

    @staticmethod
    async def get_document_chunks(dataset_id: str, doc_id: str) -> List[Dict[str, Any]]:
        """
        Get all chunk content of a document

        Args:
            dataset_id: Dataset ID
            doc_id: Document ID

        Returns:
            List of chunk data, returns empty list if retrieval fails
        """
        try:
            all_chunks = []
            page = 1
            page_size = 100  # Get 100 chunks per page

            while True:
                # Use functional API to get chunks, supports pagination
                chunks_response = await list_document_chunks(
                    dataset_id, doc_id, page=page, page_size=page_size
                )

                if chunks_response.get("code") == 0:
                    data = chunks_response.get("data", {})
                    chunks = data.get("chunks", [])
                    total = data.get("total", 0)

                    if chunks:
                        all_chunks.extend(chunks)
                        logger.info(
                            f"Got page {page} chunks: {len(chunks)} items, total: {len(all_chunks)} items"
                        )

                        # If all chunks are retrieved, exit loop
                        if len(all_chunks) >= total:
                            break

                        page += 1
                    else:
                        # No more chunks
                        break
                else:
                    logger.warning(f"Failed to get chunks: {chunks_response}")
                    break

            logger.info(
                f"Successfully retrieved all {len(all_chunks)} chunks of document {doc_id}"
            )
            return all_chunks

        except Exception:
            return []

    @staticmethod
    def convert_to_standard_format(
        doc_id: str, chunks_data: List[Dict[str, Any]]
    ) -> List[Dict[str, Any]]:
        """
        Convert RAGFlow format to standard format

        Args:
            doc_id: Document ID
            chunks_data: Chunk data

        Returns:
            Standard format chunk list
        """
        result = []

        if chunks_data:
            # If there is actual chunk data, use real data
            for i, chunk in enumerate(chunks_data):
                result.append(
                    {
                        "docId": doc_id,
                        "dataIndex": chunk.get("id", str(i)),
                        "title": "",
                        "content": chunk.get("content", ""),
                        "context": chunk.get("content", ""),
                        "references": None,
                    }
                )

        return result

    @staticmethod
    async def _check_document_status(dataset_id: str, doc_id: str) -> tuple[str, int]:
        """
        Check parsing status of a single document

        Args:
            dataset_id: Dataset ID
            doc_id: Document ID

        Returns:
            (status, token count)
        """
        response = await list_documents_in_dataset(dataset_id, doc_id)

        if response.get("code") != 0:
            return "UNKNOWN", 0

        docs = response.get("data", {}).get("docs", [])
        for doc in docs:
            if doc.get("id") == doc_id:
                run_status = doc.get("run", "UNSTART")
                token_count = doc.get("token_count", 0)
                return run_status, token_count

        logger.warning(f"Document {doc_id} not found in list")
        return "NOT_FOUND", 0

    @staticmethod
    def _handle_parsing_status(
        doc_id: str, run_status: str, token_count: int
    ) -> Optional[str]:
        """
        Handle parsing status

        Args:
            doc_id: Document ID
            run_status: Running status
            token_count: Token count

        Returns:
            Processed status, returns None if need to continue waiting
        """
        if run_status == "DONE":
            logger.info(
                f"Document {doc_id} parsing completed with {token_count} tokens"
            )
            return run_status
        elif run_status == "FAIL":
            raise Exception(f"Document {doc_id} parsing failed")
        elif run_status == "RUNNING":
            logger.info(f"Document {doc_id} is being parsed...")

        return None  # Continue waiting

    @staticmethod
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
                run_status, token_count = await RagflowUtils._check_document_status(
                    dataset_id, doc_id
                )

                if run_status != last_status:
                    logger.info(
                        f"Document {doc_id} status: {run_status}, tokens: {token_count}"
                    )
                    last_status = run_status

                # Handle status, if returns non-None value, parsing is complete or failed
                result = RagflowUtils._handle_parsing_status(
                    doc_id, run_status, token_count
                )
                if result:
                    return result

                await asyncio.sleep(1)

            except Exception as e:
                logger.warning(f"Error checking parsing status: {e}")
                await asyncio.sleep(1)

        logger.warning(
            f"Document parsing timeout after {max_wait_time} seconds, last status: {last_status}"
        )
        return last_status or "TIMEOUT"

    @staticmethod
    def build_parser_config(
        lengthRange: List[int], overlap: int, separator: List[str], titleSplit: bool
    ) -> Dict[str, Any]:
        """
        Build parser configuration

        Args:
            lengthRange: Chunk length range
            overlap: Overlap length
            separator: Separator list
            titleSplit: Whether to split by title

        Returns:
            Parser configuration dictionary
        """
        # Build RAGFlow parser configuration based on abstract interface parameters
        chunk_token_count = (
            lengthRange[1]
            if len(lengthRange) > 1
            else lengthRange[0] if lengthRange else 256
        )

        # Separator handling: convert abstract interface separator to RAGFlow delimiter format
        delimiter = "\\n"
        if separator:
            delimiter = "".join(separator) + "\\n"

        parser_config = {
            "chunk_token_count": min(
                chunk_token_count, 2048
            ),  # RAGFlow limits maximum 2048
            "delimiter": delimiter,
            "layout_recognize": titleSplit,  # Use titleSplit to control layout recognition
            "html4excel": False,
            "task_page_size": 12,  # PDF specific, default value
            "raptor": {"use_raptor": False},
        }

        logger.info(
            f"Built parser config: chunk_size={chunk_token_count}, delimiter='{delimiter}', layout_recognize={titleSplit}"
        )
        return parser_config

    @staticmethod
    def detect_file_type(file_content: bytes, filename: str) -> tuple[str, str]:
        """
        Detect file type

        Args:
            file_content: File content
            filename: Filename

        Returns:
            (content_type, file_type)
        """
        # Type detection based on file content (more reliable)
        if file_content.startswith(b"%PDF"):
            return "application/pdf", "pdf"
        elif filename.lower().endswith((".txt", ".md")):
            return "text/plain", "text"
        elif filename.lower().endswith((".doc", ".docx")) or file_content.startswith(
            b"PK"
        ):
            return (
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "docx",
            )
        else:
            return "application/octet-stream", "unknown"

    @staticmethod
    def correct_filename(filename: str, file_type: str) -> str:
        """
        Correct filename extension

        Args:
            filename: Original filename
            file_type: Detected file type

        Returns:
            Corrected filename
        """
        # Ensure filename has correct extension
        if file_type == "pdf" and not filename.lower().endswith(".pdf"):
            if "." in filename:
                filename = filename.rsplit(".", 1)[0] + ".pdf"
            else:
                filename = filename + ".pdf"
            logger.info(f"Corrected filename to: {filename}")

        return filename
