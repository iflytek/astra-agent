#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
RAGFlow Strategy Implementation Module

Provides document processing and knowledge management strategy based on RAGFlow
"""

import logging
import os
import time
from typing import Any, Dict, List, Optional

from knowledge.consts.error_code import CodeEnum
from knowledge.exceptions.exception import CustomException
from knowledge.infra.ragflow import ragflow_client
from knowledge.infra.ragflow.ragflow_utils import RagflowUtils
from knowledge.service.rag_strategy import RAGStrategy
from knowledge.utils.verification import check_not_empty

logger = logging.getLogger(__name__)


class RagflowRAGStrategy(RAGStrategy):
    """RAGFlow RAG strategy implementation."""

    def __init__(self) -> None:
        """
        Initialize RAGFlow strategy
        """

    async def query(
        self,
        query: str,
        doc_ids: Optional[List[str]] = None,
        repo_ids: Optional[List[str]] = None,
        top_k: Optional[int] = None,
        threshold: Optional[float] = 0,
        **kwargs: Any,
    ) -> Dict[str, Any]:
        """
        Execute query using RAGFlow and return results.

        Args:
            query: Query string
            doc_ids: List of specified document IDs
            repo_ids: Ignore this parameter, use default dataset name from config
            top_k: Number of results to return
            threshold: Similarity threshold
            **kwargs: Additional parameters

        Returns:
            Query result dictionary (abstract interface format)
        """
        try:
            logger.info("Starting RAGFlow query: query=%s, doc_ids=%s", query, doc_ids)

            # Get dataset name from configuration
            dataset_name = RagflowUtils.get_default_dataset_name()
            dataset_id = await RagflowUtils.get_dataset_id_by_name(dataset_name)

            if not dataset_id:
                logger.warning("Dataset not found: %s", dataset_name)
                return {"query": query, "count": 0, "results": []}

            logger.info("Using dataset: %s (ID: %s)", dataset_name, dataset_id)

            # Build RAGFlow retrieval request with correct parameter format
            ragflow_request = {
                "question": query,
                "dataset_ids": [dataset_id],
                "top_k": top_k or 6,
                "similarity_threshold": threshold,
                "vector_similarity_weight": 0.2,
            }

            # Only add document_ids parameter when document IDs are provided
            if doc_ids:
                ragflow_request["document_ids"] = doc_ids

            # Call RAGFlow retrieval API
            ragflow_response = await ragflow_client.retrieval_with_dataset(
                dataset_id=dataset_id, request_data=ragflow_request
            )

            if ragflow_response.get("code") != 0:
                logger.error("RAGFlow query failed: %s", ragflow_response)
                return {"query": query, "count": 0, "results": []}

            # Parse response and convert format
            results = RagflowUtils.convert_ragflow_query_response(
                ragflow_response, threshold or 0
            )

            if top_k and top_k > 0:
                results = results[:top_k]

            logger.info("Query completed, returning %d results", len(results))
            return {"query": query, "count": len(results), "results": results}

        except Exception as e:
            logger.error("RAGFlow query exception: %s", e)
            return {"query": query, "count": 0, "results": []}

    async def split(
        self,
        file: str,
        lengthRange: Optional[List[int]] = None,
        overlap: int = 16,
        resourceType: int = 0,
        separator: Optional[List[str]] = None,
        titleSplit: bool = False,
        cutOff: Optional[List[str]] = None,
        **kwargs: Any,
    ) -> List[Dict[str, Any]]:
        """
        Split file into chunks using RAGFlow.

        Complete process:
        1. Check or create dataset (using group as dataset name)
        2. File download/reading and type detection
        3. Upload document to RAGFlow
        4. Trigger parsing
        5. Wait for parsing completion
        6. Get chunk results and convert format

        Args:
            file: File path or URL
            lengthRange: Chunk length range [min_length, max_length]
            overlap: Overlap length
            resourceType: Resource type
            separator: List of separators
            titleSplit: Whether to split by title
            cutOff: Truncation rules
            **kwargs: Other parameters, including group (dataset name)

        Returns:
            List of chunk results in format:
            [
                {
                    "docId": str,
                    "dataIndex": str,
                    "title": str,
                    "content": str,
                    "context": str,
                    "references": dict
                }
            ]
        """
        # Get group parameter, use default if not provided
        group = os.getenv("RAGFLOW_DEFAULT_GROUP", "Stellar Knowledge Base")
        doc_id = None
        dataset_id = None

        logger.info("Starting split request: %s, group: %s", file, group)

        try:
            # Step 1: Dataset management - use group as dataset name
            dataset_id = await RagflowUtils.ensure_dataset(group)
            logger.info("Using dataset: %s, name: %s", dataset_id, group)

            # Step 2: File processing
            file_content, filename = await RagflowUtils.process_file(file)
            logger.info(
                "File processing completed: %s, size: %d bytes",
                filename,
                len(file_content),
            )

            # Step 3: Upload document to specified dataset
            upload_response = await ragflow_client.upload_document_to_dataset(
                dataset_id=dataset_id, file_content=file_content, filename=filename
            )

            if any(upload_response):
                doc_info = upload_response[0]
                doc_id = doc_info.id
                logger.info("Document uploaded successfully, ID: %s", doc_id)

            if not doc_id:
                raise ValueError("File upload failed")

            # Step 4: Trigger parsing
            logger.info("Triggering document parsing...")
            parse_response = await ragflow_client.parse_documents(dataset_id, [doc_id])

            if parse_response.get("code") == 0:
                logger.info("Document parsing triggered successfully")
                # Step 5: Wait for parsing completion
                try:
                    final_status = await RagflowUtils.wait_for_parsing(
                        dataset_id, doc_id, max_wait_time=300
                    )
                    logger.info(
                        "Document parsing completed, final status: %s", final_status
                    )
                except Exception as parse_error:
                    logger.warning("Parsing wait timeout or error: %s", parse_error)
                    final_status = "TIMEOUT"

                if final_status != "DONE":
                    raise ValueError(
                        "File parsing timeout or error, please check in RAGFlow"
                    )

            else:
                logger.warning("File parsing failed: %s", parse_response)
                raise ValueError("File parsing failed, please check in RAGFlow")

            # Step 6: Attempt to get actual chunk content (if API supports)
            chunks_data = await RagflowUtils.get_document_chunks(dataset_id, doc_id)

            # Step 7: Convert to standard format
            result = RagflowUtils.convert_to_standard_format(doc_id, chunks_data)

            logger.info("Split processing completed, returning %d chunks", len(result))
            return result

        except Exception as e:
            logger.error("Split operation failed: %s", e)
            raise ValueError(f"File chunking processing failed: {str(e)}") from e

    def _create_error_chunk(
        self, error_id: str, dataset_id: str, doc_id: str, content: str
    ) -> Dict[str, Any]:
        """Create error format chunk"""
        return {
            "id": error_id,
            "datasetId": dataset_id,
            "fileId": doc_id,
            "createTime": time.strftime("%Y-%m-%d %H:%M:%S"),
            "updateTime": time.strftime("%Y-%m-%d %H:%M:%S"),
            "chunkType": "RAW",
            "content": content,
            "question": None,
            "answer": None,
            "dataIndex": error_id,
            "imgReference": None,
            "copiedFrom": None,
        }

    async def _validate_chunks_save_config(self, doc_id: str) -> str:
        """Validate chunks_save configuration and dataset"""
        default_group = os.getenv("RAGFLOW_DEFAULT_GROUP")
        if not default_group:
            logger.error("RAGFLOW_DEFAULT_GROUP not found in configuration")
            raise CustomException(
                CodeEnum.ChunkSaveFailed, "RAGFLOW_DEFAULT_GROUP configuration missing"
            )

        dataset_id = await RagflowUtils.ensure_dataset(default_group)
        if not dataset_id:
            logger.error(f"Unable to find or create dataset: {default_group}")
            raise CustomException(
                CodeEnum.ChunkSaveFailed,
                f"Unable to find or create dataset: {default_group}",
            )

        return dataset_id

    async def _validate_document_exists(self, dataset_id: str, doc_id: str) -> None:
        """Validate if document exists, raise exception if error occurs"""
        try:
            docs_response = await ragflow_client.list_documents_in_dataset(
                dataset_id, doc_id, page=1, page_size=1000
            )

            if docs_response.get("code") == 0:
                docs_data = docs_response.get("data", {})
                docs = docs_data.get("docs", [])

                for doc in docs:
                    if doc.get("id") == doc_id:
                        logger.info(f"Document {doc_id} exists in RAGFlow")
                        return  # Document exists, validation passed

                logger.error(f"Document {doc_id} does not exist in RAGFlow")
                raise CustomException(
                    CodeEnum.ChunkSaveFailed, f"Document {doc_id} does not exist"
                )
            else:
                logger.error(f"Unable to get document list: {docs_response}")
                raise CustomException(
                    CodeEnum.ChunkSaveFailed, "Unable to get document list"
                )

        except CustomException:
            raise  # Re-raise custom exceptions
        except Exception as e:
            logger.error(f"Error checking document existence: {e}")
            raise CustomException(
                CodeEnum.ChunkSaveFailed,
                f"Error occurred while checking document existence: {str(e)}",
            )

    async def _get_existing_chunks(
        self, dataset_id: str, doc_id: str
    ) -> Dict[str, Dict]:
        """Get mapping of existing chunks"""
        existing_chunks = {}
        try:
            chunks_response = await ragflow_client.list_document_chunks(
                dataset_id, doc_id, page=1, page_size=1000
            )
            if chunks_response.get("code") == 0:
                chunks_data = chunks_response.get("data", {})
                existing_chunk_list = chunks_data.get("chunks", [])

                for chunk in existing_chunk_list:
                    # Get various identifiers of chunk
                    data_index = str(chunk.get("dataIndex", ""))
                    chunk_id = chunk.get("id") or chunk.get("chunk_id")

                    if chunk_id:
                        # Use chunk_id as primary key (corresponding to dataIndex in split results)
                        existing_chunks[str(chunk_id)] = chunk

                        # If dataIndex exists, also use as backup key
                        if data_index:
                            existing_chunks[data_index] = chunk

                logger.info(
                    f"Document {doc_id} already has {len(existing_chunk_list)} chunks, established {len(existing_chunks)} mappings"
                )
            else:
                logger.info(
                    f"Unable to get existing chunks or document does not exist: {chunks_response}"
                )
        except Exception as e:
            logger.warning(f"Error checking existing chunks: {e}")

        return existing_chunks

    async def _process_single_chunk(
        self,
        i: int,
        chunk: Dict,
        dataset_id: str,
        doc_id: str,
        existing_chunks: Dict,
        current_time: str,
    ) -> Dict[str, Any]:
        """Process saving of single chunk"""
        try:
            content = chunk.get("content", "")
            if not content:
                logger.warning(f"Chunk {i} content is empty, skipping")
                raise CustomException(
                    CodeEnum.ParameterInvalid, f"Chunk {i} content cannot be empty"
                )

            data_index = str(chunk.get("dataIndex", i))

            # Check if chunk already exists
            if data_index in existing_chunks:
                existing_chunk = existing_chunks[data_index]
                logger.info(
                    f"Chunk dataIndex={data_index} already exists, returning directly: {existing_chunk.get('id')}"
                )

                return {
                    "id": existing_chunk.get("id"),
                    "datasetId": dataset_id,
                    "fileId": doc_id,
                    "createTime": existing_chunk.get("create_time", current_time),
                    "updateTime": existing_chunk.get("update_time", current_time),
                    "chunkType": "RAW",
                    "content": existing_chunk.get(
                        "content_with_weight",
                        existing_chunk.get("content_ltks", content),
                    ),
                    "question": None,
                    "answer": None,
                    "dataIndex": existing_chunk.get("id"),
                    "imgReference": None,
                    "copiedFrom": None,
                }

            # Save new chunk
            important_keywords = []
            if chunk.get("title"):
                important_keywords.append(chunk["title"])

            logger.info(
                f"Saving new chunk {i}: dataIndex={data_index}, content length={len(content)}"
            )

            add_response = await ragflow_client.add_chunk(
                dataset_id=dataset_id,
                document_id=doc_id,
                content=content,
                important_keywords=important_keywords if important_keywords else None,
            )

            logger.info(f"Chunk {i} save response: {add_response}")
            return self._handle_chunk_save_response(
                add_response, chunk, i, dataset_id, doc_id, current_time, content
            )

        except CustomException:
            raise  # Re-raise custom exceptions
        except Exception as e:
            logger.error(f"Chunk {i} save exception: {e}")
            raise CustomException(
                CodeEnum.ChunkSaveFailed,
                f"Exception occurred while saving chunk {i}: {str(e)}",
            )

    def _handle_chunk_save_response(
        self,
        add_response: Dict,
        chunk: Dict,
        i: int,
        dataset_id: str,
        doc_id: str,
        current_time: str,
        content: str,
    ) -> Dict[str, Any]:
        """Handle chunk save response"""
        if add_response.get("code") == 0:
            chunk_data = add_response.get("data", {}).get("chunk", {})
            chunk_id = chunk_data.get("id", f"generated_{int(time.time())}_{i}")

            # Use actual content returned by RAGFlow, fallback to original content if not available
            actual_content = chunk_data.get("content", content)

            saved_chunk = {
                "id": chunk_id,
                "datasetId": dataset_id,
                "fileId": doc_id,
                "createTime": chunk_data.get("create_time", current_time),
                "updateTime": chunk_data.get("create_time", current_time),
                "chunkType": "RAW",
                "content": actual_content,  # Use RAGFlow's actual saved content
                "question": None,
                "answer": None,
                "dataIndex": chunk_id,
                "imgReference": None,
                "copiedFrom": None,
            }

            logger.info(f"Successfully saved new chunk {i}: {saved_chunk['id']}")
            return saved_chunk
        else:
            error_msg = add_response.get("message", "Save failed")
            logger.error(f"Chunk {i} save failed: {error_msg}")
            raise CustomException(
                CodeEnum.ChunkSaveFailed, f"Failed to save chunk {i}: {error_msg}"
            )

    async def _process_chunks_batch(
        self,
        chunks: List[Dict[str, Any]],
        dataset_id: str,
        docId: str,
        existing_chunks: Dict[str, Any],
        current_time: str,
    ) -> tuple[List[Dict[str, Any]], List[Dict[str, Any]]]:
        """Process chunks in batch and return results"""
        saved_chunks = []
        failed_chunks = []

        for i, chunk in enumerate(chunks):
            try:
                result = await self._process_single_chunk(
                    i, chunk, dataset_id, docId, existing_chunks, current_time
                )
                if result:  # Successfully processed
                    saved_chunks.append(result)
            except CustomException as e:
                failed_chunks.append(
                    {
                        "index": i,
                        "error": str(e),
                        "chunk": chunk.get("dataIndex", f"chunk_{i}"),
                    }
                )
                logger.error(f"Failed to process chunk {i}: {e}")

        return saved_chunks, failed_chunks

    async def _handle_chunk_results(
        self,
        saved_chunks: List[Dict[str, Any]],
        failed_chunks: List[Dict[str, Any]],
        chunks: List[Dict[str, Any]],
    ) -> List[Dict[str, Any]]:
        """Handle chunk processing results and errors"""
        if not saved_chunks and failed_chunks:
            # All chunks failed
            error_details = "; ".join(
                [f"Chunk {fc['index']}: {fc['error']}" for fc in failed_chunks]
            )
            raise CustomException(
                CodeEnum.ChunkSaveFailed, f"All chunks save failed: {error_details}"
            )
        elif failed_chunks:
            # Some chunks failed
            error_details = "; ".join(
                [f"Chunk {fc['index']}: {fc['error']}" for fc in failed_chunks]
            )
            logger.warning(f"Some chunks save failed: {error_details}")
            # Continue and return successful chunks

        logger.info(
            f"Chunk save completed: total={len(chunks)}, saved={len(saved_chunks)}, failed={len(failed_chunks)}"
        )
        return saved_chunks

    async def chunks_save(
        self, docId: str, group: str, uid: str, chunks: List[object], **kwargs: Any
    ) -> List[Dict[str, Any]]:
        """
        Save knowledge chunks using RAGFlow.

        Args:
            docId: Document ID
            group: Group (dataset name)
            uid: User ID
            chunks: List of knowledge chunks, each chunk contains:
                   - docId: Document ID
                   - dataIndex: Chunk index
                   - title: Title
                   - content: Text content
                   - context: Context
                   - references: Reference information
            **kwargs: Other parameters

        Returns:
            List of save results in format:
            [
                {
                    "id": "chunk_id",
                    "datasetId": "dataset_id",
                    "fileId": "doc_id",
                    "createTime": "2025-09-15 14:41:19",
                    "updateTime": "2025-09-15 14:41:19",
                    "chunkType": "RAW",
                    "content": "chunk content",
                    "dataIndex": 0.0,
                    "imgReference": {}
                }
            ]
        """
        if not check_not_empty(chunks):
            logger.error("Chunks list is empty or invalid")
            raise CustomException(
                CodeEnum.MissingParameter, "chunks parameter cannot be empty"
            )

        logger.info(
            f"Starting chunk save request: docId={docId}, group={group}, chunks_count={len(chunks)}"
        )

        try:
            # 1. Validate configuration and dataset
            dataset_id = await self._validate_chunks_save_config(docId)
            logger.info(f"Using dataset: {dataset_id}")

            # 2. Validate if document exists
            await self._validate_document_exists(dataset_id, docId)

            # 3. Get existing chunks
            existing_chunks = await self._get_existing_chunks(dataset_id, docId)

            # 4. Process each chunk
            current_time = time.strftime("%Y-%m-%d %H:%M:%S")
            chunks_typed = [
                chunk if isinstance(chunk, dict) else chunk.__dict__ for chunk in chunks
            ]
            saved_chunks, failed_chunks = await self._process_chunks_batch(
                chunks_typed, dataset_id, docId, existing_chunks, current_time
            )

            # 5. Handle results and return
            return await self._handle_chunk_results(
                saved_chunks, failed_chunks, chunks_typed
            )

        except CustomException:
            raise  # Re-raise custom exceptions to be handled by API layer
        except Exception as e:
            logger.error(f"Chunk save operation failed: {e}")
            raise CustomException(CodeEnum.ChunkSaveFailed, str(e))

    async def _validate_chunks_update_config(self) -> str:
        """Validate chunks_update configuration and dataset"""
        default_group = os.getenv("RAGFLOW_DEFAULT_GROUP")
        if not default_group:
            logger.error("RAGFLOW_DEFAULT_GROUP not found in configuration")
            raise CustomException(
                CodeEnum.ChunkUpdateFailed,
                "RAGFLOW_DEFAULT_GROUP configuration missing",
            )

        dataset_id = await RagflowUtils.ensure_dataset(default_group)
        if not dataset_id:
            logger.error(f"Unable to find or create dataset: {default_group}")
            raise CustomException(
                CodeEnum.ChunkUpdateFailed,
                f"Unable to find or create dataset: {default_group}",
            )

        return dataset_id

    async def _process_chunk_update(
        self,
        chunk: Dict,
        dataset_id: str,
        doc_id: str,
        failed_chunks: Dict,
        successful_count: int,
    ) -> int:
        """Process update of single chunk"""
        chunk_id = (
            chunk.get("chunkId")
            or chunk.get("dataIndex")
            or chunk.get("chunk_id")
            or chunk.get("id")
        )

        if not chunk_id:
            # Collect error information, use "chunkId" uniformly as key
            if "chunkId" not in failed_chunks:
                failed_chunks["chunkId"] = "missing chunk identifier"
            else:
                failed_chunks["chunkId"] += "; missing chunk identifier"
            return successful_count

        try:
            update_params = self._build_update_params(chunk)

            if not update_params:
                error_msg = f"no fields to update for chunk {chunk_id}"
                if "chunkId" not in failed_chunks:
                    failed_chunks["chunkId"] = error_msg
                else:
                    failed_chunks["chunkId"] += f"; {error_msg}"
                return successful_count

            logger.info(f"Updating chunk ID={chunk_id}: {list(update_params.keys())}")

            update_response = await ragflow_client.update_chunk(
                dataset_id=dataset_id,
                document_id=doc_id,
                chunk_id=str(chunk_id),
                **update_params,
            )

            logger.info(f"Chunk ID={chunk_id} update response: {update_response}")

            if update_response.get("code") == 0:
                successful_count += 1
                logger.info(f"Successfully updated chunk: ID={chunk_id}")
            else:
                error_msg = update_response.get("message", "Update failed")
                full_error = f"Chunk {chunk_id} update failed: {error_msg}"
                if "chunkId" not in failed_chunks:
                    failed_chunks["chunkId"] = full_error
                else:
                    failed_chunks["chunkId"] += f"; {full_error}"
                logger.warning(f"Chunk ID={chunk_id} update failed: {error_msg}")

        except Exception as e:
            error_msg = f"Chunk {chunk_id} update exception: {str(e)}"
            if "chunkId" not in failed_chunks:
                failed_chunks["chunkId"] = error_msg
            else:
                failed_chunks["chunkId"] += f"; {error_msg}"
            logger.error(f"Chunk ID={chunk_id} update exception: {e}")

        return successful_count

    def _build_update_params(self, chunk: Dict) -> Dict[str, Any]:
        """Build update parameters"""
        update_params = {}

        # Only update content if it's provided and not empty (RAGFlow has issues with empty content)
        if "content" in chunk and chunk["content"]:
            update_params["content"] = chunk["content"]

        # Only set important_keywords if title exists
        if "title" in chunk and chunk["title"]:
            update_params["important_keywords"] = [chunk["title"]]

        update_params["available"] = chunk.get("available", True)

        return update_params

    async def chunks_update(
        self,
        docId: str,
        group: str,
        uid: str,
        chunks: List[Dict[str, Any]],
        **kwargs: Any,
    ) -> Optional[Dict[str, Any]]:
        """
        Update knowledge chunks using RAGFlow.

        Args:
            docId: Document ID
            group: Group (dataset name)
            uid: User ID
            chunks: List of knowledge chunks, each chunk contains:
                   - docId: Document ID
                   - dataIndex: chunk ID (used as chunkId)
                   - title: Title
                   - content: Text content
                   - context: Context
                   - references: Reference information
                   - docInfo: Document information
            **kwargs: Other parameters

        Returns:
            Update result data:
            - None if all successful
            - {"failedChunk": {"chunkId": "error info"}} if some failed
        """
        if not check_not_empty(chunks):
            logger.warning("Chunks list is empty, no update needed")
            raise CustomException(
                CodeEnum.MissingParameter, "chunks parameter cannot be empty"
            )

        logger.info(
            f"Processing chunk update request: docId={docId}, group={group}, chunks_count={len(chunks)}"
        )

        try:
            # 1. Validate configuration and dataset
            dataset_id = await self._validate_chunks_update_config()
            logger.info(f"Using dataset: {dataset_id}")

            # 2. Process each chunk update
            failed_chunks: Dict[str, str] = {}
            successful_count = 0

            for chunk in chunks:
                successful_count = await self._process_chunk_update(
                    chunk, dataset_id, docId, failed_chunks, successful_count
                )

            # 3. Return data part only (API layer will wrap the final response)
            if not failed_chunks:
                # All successful - return None
                logger.info(f"All {successful_count} chunks updated successfully")
                return None
            else:
                # Some failed - return failed chunk info
                logger.warning(
                    f"Update completed: {successful_count} successful, {len(failed_chunks)} failed"
                )
                return {"failedChunk": failed_chunks}

        except CustomException:
            raise  # Re-raise custom exceptions
        except Exception as e:
            logger.error(f"Chunk update operation failed: {e}")
            raise CustomException(CodeEnum.ChunkUpdateFailed, str(e))

    async def chunks_delete(
        self, docId: str, chunkIds: List[str], **kwargs: Any
    ) -> None:
        """
        Delete knowledge chunks using RAGFlow.

        Args:
            docId: Document ID
            chunkIds: List of chunk IDs to delete
            **kwargs: Additional parameters

        Returns:
            None if successful

        Raises:
            CustomException: If deletion fails
        """
        # Parameter validation
        if not check_not_empty(chunkIds):
            logger.error("chunkIds parameter cannot be empty")
            raise CustomException(
                CodeEnum.MissingParameter, "chunkIds parameter cannot be empty"
            )

        logger.info(
            f"Processing chunk deletion request: docId={docId}, chunks_count={len(chunkIds)}"
        )

        try:
            # 1. Get dataset name from config, then find dataset ID
            default_group = os.getenv("RAGFLOW_DEFAULT_GROUP")
            if not default_group:
                logger.error(
                    "RAGFLOW_DEFAULT_GROUP not found in config, chunks_delete operation failed"
                )
                raise CustomException(
                    CodeEnum.ChunkDeleteFailed,
                    "RAGFLOW_DEFAULT_GROUP configuration missing",
                )

            dataset_id = await RagflowUtils.ensure_dataset(default_group)
            if not dataset_id:
                logger.error(f"Unable to find or create dataset: {default_group}")
                raise CustomException(
                    CodeEnum.ChunkDeleteFailed,
                    f"Unable to find or create dataset: {default_group}",
                )

            logger.info(f"Using dataset: {default_group} (ID: {dataset_id})")

            # 2. Call RAGFlow deletion API directly
            delete_response = await ragflow_client.delete_chunks(
                dataset_id=dataset_id, document_id=docId, chunk_ids=chunkIds
            )

            logger.info(f"RAGFlow chunk deletion response: {delete_response}")

            # 3. Process response
            if delete_response.get("code") == 0:
                logger.info(f"Successfully deleted {len(chunkIds)} chunks")
                return None  # Success, let API layer handle the response
            else:
                # RAGFlow deletion failed
                error_msg = delete_response.get("message", "Deletion failed")
                logger.error(f"RAGFlow deletion failed: {error_msg}")
                raise CustomException(
                    CodeEnum.ChunkDeleteFailed, f"Deletion failed: {error_msg}"
                )

        except CustomException:
            raise  # Re-raise custom exceptions
        except Exception as e:
            logger.error(f"Chunk deletion operation failed: {e}")
            raise CustomException(
                CodeEnum.ChunkDeleteFailed, f"Deletion operation failed: {str(e)}"
            )

    async def query_doc(self, docId: str, **kwargs: Any) -> List[Dict[str, Any]]:
        """
        Query all chunk information for a document using RAGFlow.
        """
        try:
            logger.info(f"Starting document chunk query: docId={docId}")

            # Get dataset ID
            dataset_name = RagflowUtils.get_default_dataset_name()
            dataset_id = await RagflowUtils.get_dataset_id_by_name(dataset_name)
            if not dataset_id:
                logger.warning(f"Dataset not found: {dataset_name}")
                return []

            # Step 1: Get total count
            first_response = await ragflow_client.list_document_chunks(
                dataset_id, docId, page=1, page_size=1
            )

            if first_response.get("code") != 0:
                logger.warning(
                    f"Failed to get chunks: {first_response.get('message', 'Unknown error')}"
                )
                return []

            total_count = first_response.get("data", {}).get("total", 0)
            if total_count == 0:
                logger.warning("Document has no chunks")
                return []

            logger.info(f"Document has {total_count} total chunks")

            # Step 2: Get all data
            chunks_response = await ragflow_client.list_document_chunks(
                dataset_id, docId, page=1, page_size=total_count
            )

            if chunks_response.get("code") != 0:
                logger.warning(
                    f"Failed to get all chunks: {chunks_response.get('message', 'Unknown error')}"
                )
                return []

            logger.info(
                f"Successfully retrieved {len(chunks_response.get('data', {}).get('chunks', []))} chunks"
            )

            # Convert to ChunkInfo object list
            chunk_infos = []
            data = chunks_response.get("data", {})
            page_chunks = data.get("chunks", [])

            for i, chunk_data in enumerate(page_chunks):
                content = chunk_data.get("content", "")
                chunk_doc_id = chunk_data.get("document_id", docId)
                chunk_id = chunk_data.get("id", str(i))

                chunk_info = {
                    "docId": chunk_doc_id,
                    "chunkId": chunk_id,
                    "content": content,
                }
                chunk_infos.append(chunk_info)

            logger.info(f"Successfully converted {len(chunk_infos)} ChunkInfo objects")
            return chunk_infos

        except Exception as e:
            logger.error(f"Failed to query document chunk information: {e}")
            return []

    async def query_doc_name(
        self, docId: str, **kwargs: Any
    ) -> Optional[Dict[str, Any]]:
        """
        Query document name and information using RAGFlow.
        """
        try:
            logger.info(f"Starting document info query: docId={docId}")

            # Get dataset ID
            dataset_name = RagflowUtils.get_default_dataset_name()
            dataset_id = await RagflowUtils.get_dataset_id_by_name(dataset_name)
            if not dataset_id:
                logger.warning(f"Dataset not found: {dataset_name}")
                return None

            # Get document information
            doc_info = await ragflow_client.get_document_info(dataset_id, docId)
            if not doc_info:
                logger.warning(f"Document {docId} does not exist")
                return None

            # Convert to FileInfo object
            ragflow_status = doc_info.get("run", "")
            file_status = str(ragflow_status) if ragflow_status is not None else ""

            # Prefer chunk_count (chunk count), fallback to token_count (token count) if not available
            file_quantity = doc_info.get("chunk_count", doc_info.get("token_count", 0))

            file_info = {
                "docId": docId,
                "fileName": doc_info.get("name", ""),
                "fileStatus": file_status,
                "fileQuantity": file_quantity,
            }

            logger.info(
                f"Document info query successful: fileName={file_info['fileName']}"
            )
            return file_info

        except Exception as e:
            logger.error(f"Failed to query document information: {e}")
            return None
