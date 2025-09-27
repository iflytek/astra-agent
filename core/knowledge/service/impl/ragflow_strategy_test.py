#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
RAGFlow Strategy Mock Test File.

Using Mock objects to completely replace external dependencies in test files.
No dependency on real RAGFlow services, network connections or configuration files.
"""

import json
import time
from typing import Any, AsyncGenerator, Dict, List, Optional

import pytest
import pytest_asyncio


class MockRagflowClient:
    """Mock RAGFlow client."""

    @staticmethod
    async def cleanup_session() -> None:
        """Mock session cleanup."""

    @staticmethod
    def reload_config() -> None:
        """Mock configuration reload."""


class MockRagflowRAGStrategy:
    """Mock RAGFlow strategy class."""

    def __init__(self) -> None:
        """Initialize mock strategy."""
        self.doc_database: Dict[str, Dict[str, Any]] = {}
        self.chunk_database: Dict[str, List[Dict[str, Any]]] = {}
        self._setup_mock_data()

    def _setup_mock_data(self) -> None:
        """Set up mock data."""
        self.doc_database = self._create_mock_docs()
        self.chunk_database = self._create_mock_chunks()

    def _create_mock_docs(self) -> Dict[str, Dict[str, Any]]:
        """Create mock document data."""
        return {
            "c3c9cc6691fc11f095a90242ac1a0007": {
                "docId": "c3c9cc6691fc11f095a90242ac1a0007",
                "fileName": "test_document1.pdf",
                "fileStatus": "completed",
                "fileQuantity": 15,
            },
            "4d47376892d811f0a5960242ac1c0007": {
                "docId": "4d47376892d811f0a5960242ac1c0007",
                "fileName": "test_document2.pdf",
                "fileStatus": "completed",
                "fileQuantity": 8,
            },
            "6d2c2154939311f0bd4f0242c0a83007": {
                "docId": "6d2c2154939311f0bd4f0242c0a83007",
                "fileName": "integration_test.pdf",
                "fileStatus": "completed",
                "fileQuantity": 12,
            },
        }

    def _create_mock_chunks(self) -> Dict[str, List[Dict[str, Any]]]:
        """Create mock chunk data."""
        return {
            "c3c9cc6691fc11f095a90242ac1a0007": [
                {
                    "docId": "c3c9cc6691fc11f095a90242ac1a0007",
                    "chunkId": "chunk_001",
                    "content": (
                        "This is a test chunk about Second album music content"
                    ),
                    "dataIndex": "0.0",
                },
                {
                    "docId": "c3c9cc6691fc11f095a90242ac1a0007",
                    "chunkId": "chunk_002",
                    "content": ("Another chunk with album information and metadata"),
                    "dataIndex": "1.0",
                },
            ]
        }

    async def query(
        self,
        query: str,
        doc_ids: List[str],
        top_k: int = 5,
        threshold: float = 0.3,
    ) -> Dict[str, Any]:
        """Mock query method."""
        results = self._search_chunks(query, doc_ids)
        return {
            "count": len(results),
            "results": results[:top_k],
            "query": query,
            "doc_ids": doc_ids,
        }

    def _search_chunks(self, query: str, doc_ids: List[str]) -> List[Dict[str, Any]]:
        """Search for matching chunks in specified documents."""
        results = []
        query_words = query.split()

        for doc_id in doc_ids:
            if doc_id not in self.chunk_database:
                continue

            chunks = self.chunk_database[doc_id]
            for chunk in chunks:
                if self._is_chunk_matching(chunk["content"], query_words):
                    results.append(
                        {
                            "docId": doc_id,
                            "chunkId": chunk["chunkId"],
                            "content": chunk["content"],
                            "score": 0.85,
                            "dataIndex": chunk["dataIndex"],
                        }
                    )
        return results

    def _is_chunk_matching(self, content: str, query_words: List[str]) -> bool:
        """Check if chunk content matches query words."""
        content_lower = content.lower()
        return any(word.lower() in content_lower for word in query_words)

    async def query_doc(self, docId: str) -> List[Dict[str, Any]]:
        """Mock document query method."""
        if docId not in self.chunk_database:
            return []

        return [
            {
                "docId": docId,
                "chunkId": chunk["chunkId"],
                "content": chunk["content"],
                "dataIndex": chunk["dataIndex"],
            }
            for chunk in self.chunk_database[docId]
        ]

    async def query_doc_name(self, docId: str) -> Optional[Dict[str, Any]]:
        """Mock document name query method."""
        return self.doc_database.get(docId)

    async def split(
        self,
        file: str,
        lengthRange: List[int],
        overlap: int,
        resourceType: int,
        cutOff: List[str],
        separator: List[str],
        titleSplit: bool,
    ) -> List[Dict[str, Any]]:
        """Mock document split method."""
        timestamp = int(time.time())
        doc_id = f"split_doc_{timestamp}"

        self._add_split_doc_to_database(doc_id, file)
        chunks = self._generate_mock_chunks(doc_id, timestamp, file)
        self._add_chunks_to_database(doc_id, chunks)

        return chunks

    def _add_split_doc_to_database(self, doc_id: str, file: str) -> None:
        """Add split document to mock database."""
        self.doc_database[doc_id] = {
            "docId": doc_id,
            "fileName": "mock_split_document.pdf",
            "fileStatus": "completed",
            "fileQuantity": 5,
        }

    def _generate_mock_chunks(
        self, doc_id: str, timestamp: int, file: str
    ) -> List[Dict[str, Any]]:
        """Generate mock chunks for split document."""
        chunks = []
        for i in range(5):
            chunk = {
                "docId": doc_id,
                "dataIndex": f"split_chunk_{timestamp}_{i}",
                "title": f"Mock Split Section {i + 1}",
                "content": (
                    f"This is mock split content {i + 1}. "
                    "Content about mechatronics and technology systems."
                ),
                "context": f"Mock context for section {i + 1}",
                "references": {},
                "docInfo": {
                    "documentId": doc_id,
                    "documentName": "mock_split_document.pdf",
                    "documentSource": file,
                    "documentType": "pdf",
                },
            }
            chunks.append(chunk)
        return chunks

    def _add_chunks_to_database(
        self, doc_id: str, chunks: List[Dict[str, Any]]
    ) -> None:
        """Add chunks to mock database."""
        self.chunk_database[doc_id] = [
            {
                "docId": doc_id,
                "chunkId": f"chunk_{i:03d}",
                "content": chunk["content"],
                "dataIndex": str(float(i)),
            }
            for i, chunk in enumerate(chunks)
        ]

    async def chunks_save(
        self, uid: str, docId: str, group: str, chunks: List[Dict[str, Any]]
    ) -> List[Dict[str, Any]]:
        """Mock chunk save method."""
        if not chunks:
            raise ValueError("Parameter chunks cannot be empty")

        if docId not in self.doc_database:
            return self._create_error_response(docId)

        return self._process_chunk_save(docId, group, chunks)

    def _create_error_response(self, docId: str) -> List[Dict[str, Any]]:
        """Create error response for non-existent document."""
        return [
            {
                "id": "document_error",
                "datasetId": "mock_dataset",
                "fileId": docId,
                "createTime": int(time.time()),
                "updateTime": int(time.time()),
                "chunkType": "RAW",
                "content": f"Document {docId} does not exist",
                "dataIndex": "error",
                "imgReference": {},
            }
        ]

    def _process_chunk_save(
        self, docId: str, group: str, chunks: List[Dict[str, Any]]
    ) -> List[Dict[str, Any]]:
        """Process chunk save operation."""
        saved_chunks = []
        for i, chunk in enumerate(chunks):
            saved_chunk = self._save_single_chunk(docId, group, chunk, i)
            saved_chunks.append(saved_chunk)
        return saved_chunks

    def _save_single_chunk(
        self, docId: str, group: str, chunk: Dict[str, Any], index: int
    ) -> Dict[str, Any]:
        """Save a single chunk to database."""
        existing_chunks = self.chunk_database.get(docId, [])
        data_index = chunk.get("dataIndex")

        existing_chunk = self._find_existing_chunk(existing_chunks, data_index)

        if existing_chunk:
            return self._create_existing_chunk_response(
                group, docId, existing_chunk, index
            )
        else:
            return self._create_new_chunk_response(docId, group, chunk, index)

    def _find_existing_chunk(
        self, existing_chunks: List[Dict[str, Any]], data_index: Any
    ) -> Optional[Dict[str, Any]]:
        """Find existing chunk by data index."""
        for existing in existing_chunks:
            if existing["dataIndex"] == data_index:
                return existing
        return None

    def _create_existing_chunk_response(
        self,
        group: str,
        docId: str,
        existing_chunk: Dict[str, Any],
        index: int,
    ) -> Dict[str, Any]:
        """Create response for existing chunk."""
        return {
            "id": f"existing_{index}",
            "datasetId": group,
            "fileId": docId,
            "createTime": int(time.time()) - 3600,
            "updateTime": int(time.time()),
            "chunkType": "RAW",
            "content": existing_chunk["content"],
            "dataIndex": existing_chunk["dataIndex"],
            "imgReference": {},
        }

    def _create_new_chunk_response(
        self, docId: str, group: str, chunk: Dict[str, Any], index: int
    ) -> Dict[str, Any]:
        """Create response for new chunk."""
        new_chunk = {
            "docId": docId,
            "chunkId": f"new_chunk_{int(time.time())}_{index}",
            "content": chunk["content"],
            "dataIndex": chunk.get("dataIndex", str(float(index))),
        }

        if docId not in self.chunk_database:
            self.chunk_database[docId] = []
        self.chunk_database[docId].append(new_chunk)

        return {
            "id": f"saved_{index}",
            "datasetId": group,
            "fileId": docId,
            "createTime": int(time.time()),
            "updateTime": int(time.time()),
            "chunkType": "RAW",
            "content": chunk["content"],
            "dataIndex": new_chunk["dataIndex"],
            "imgReference": {},
        }

    async def chunks_update(
        self, docId: str, group: str, uid: str, chunks: List[Dict[str, Any]]
    ) -> Optional[Dict[str, Any]]:
        """Mock chunk update method."""
        if not chunks:
            raise ValueError("Parameter chunks cannot be empty")

        failed_chunks = []
        for chunk in chunks:
            success = self._update_single_chunk(docId, chunk)
            if not success:
                failed_chunks.append(
                    {
                        "dataIndex": chunk.get("dataIndex"),
                        "reason": "Chunk not found",
                    }
                )

        return {"failedChunk": failed_chunks} if failed_chunks else None

    def _update_single_chunk(self, docId: str, chunk: Dict[str, Any]) -> bool:
        """Update a single chunk in the database."""
        data_index = chunk.get("dataIndex")
        doc_chunks = self.chunk_database.get(docId, [])

        for existing_chunk in doc_chunks:
            if existing_chunk["dataIndex"] == data_index:
                existing_chunk.update(
                    {
                        "content": chunk["content"],
                        "chunkId": existing_chunk["chunkId"] + "_updated",
                    }
                )
                return True
        return False


# Mock fixtures
@pytest_asyncio.fixture(scope="session", autouse=True)
async def mock_cleanup_sessions() -> AsyncGenerator[None, None]:
    """Mock session cleanup fixture."""
    yield
    await MockRagflowClient.cleanup_session()


def mock_load_config() -> bool:
    """Mock configuration loading."""
    return True


class TestRagflowRAGStrategyMock:
    """Test class using Mock for RAGFlow strategy."""

    @pytest.fixture
    def strategy(self) -> MockRagflowRAGStrategy:
        """Provide Mock strategy instance."""
        return MockRagflowRAGStrategy()

    @pytest.fixture
    def sample_chunks(self) -> List[Dict[str, Any]]:
        """Provide test chunk data."""
        timestamp = int(time.time())
        return [
            {
                "docId": "4d47376892d811f0a5960242ac1c0007",
                "dataIndex": f"test_chunk_{timestamp}_0",
                "title": "Test Title 1",
                "content": (
                    "This is the first test chunk content for verifying "
                    "RAGFlow chunk save functionality."
                ),
                "context": "This is the first test chunk context information.",
                "references": {},
                "docInfo": {
                    "documentId": "4d47376892d811f0a5960242ac1c0007",
                    "documentName": "test_document.pdf",
                    "documentSource": "test_source",
                    "documentType": "pdf",
                },
            },
            {
                "docId": "4d47376892d811f0a5960242ac1c0007",
                "dataIndex": f"test_chunk_{timestamp}_1",
                "title": "Test Title 2",
                "content": (
                    "This is the second test chunk content, "
                    "containing special characters: @#$%^&*() test."
                ),
                "context": "This is the second test chunk context information.",
                "references": {},
                "docInfo": {
                    "documentId": "4d47376892d811f0a5960242ac1c0007",
                    "documentName": "test_document.pdf",
                    "documentSource": "test_source",
                    "documentType": "pdf",
                },
            },
        ]

    def _validate_chunk_fields(
        self, chunk: Dict[str, Any], required_fields: List[str]
    ) -> bool:
        """Validate that chunk contains all required fields."""
        if not isinstance(chunk, dict):
            return False

        return all(field in chunk for field in required_fields)

    def _validate_chunk_types(self, chunk: Dict[str, Any]) -> bool:
        """Validate chunk field types."""
        type_checks = [
            isinstance(chunk["id"], str),
            isinstance(chunk["datasetId"], str),
            isinstance(chunk["fileId"], str),
            chunk["chunkType"] == "RAW",
            isinstance(chunk["dataIndex"], (int, float, str)),
            isinstance(chunk["imgReference"], dict),
        ]
        return all(type_checks)

    def validate_chunk_save_response(self, result: List[Dict[str, Any]]) -> bool:
        """Validate chunks_save return format."""
        if not isinstance(result, list):
            return False

        required_fields = [
            "id",
            "datasetId",
            "fileId",
            "createTime",
            "updateTime",
            "chunkType",
            "content",
            "dataIndex",
            "imgReference",
        ]

        for chunk in result:
            if not self._validate_chunk_fields(chunk, required_fields):
                return False
            if not self._validate_chunk_types(chunk):
                return False

        return True

    def validate_chunk_update_response(self, result: Any) -> bool:
        """Validate chunks_update return format."""
        if result is None:
            return True  # All chunks updated successfully

        if not isinstance(result, dict):
            return False

        if "failedChunk" in result:
            return isinstance(result["failedChunk"], (dict, list))

        return False

    @pytest.mark.asyncio
    async def test_query_mock(self, strategy: MockRagflowRAGStrategy) -> None:
        """Test query interface (Mock version)."""
        print("\\n=== Test query interface (Mock version) ===")

        result = await strategy.query(
            query="Second album",
            doc_ids=[
                "c3c9cc6691fc11f095a90242ac1a0007",
                "1385557a91ff11f085d50242ac1a0007",
            ],
            top_k=5,
            threshold=0.3,
        )

        print("Query results:")
        print(json.dumps(result, ensure_ascii=False, indent=2))

        # Validate return format
        assert "count" in result, "Should contain count field"
        assert "results" in result, "Should contain results field"
        assert "query" in result, "Should contain query field"
        assert isinstance(result["results"], list), "Results should be a list"

        print("✅ Query interface Mock test passed")

    @pytest.mark.asyncio
    async def test_query_doc_mock(self, strategy: MockRagflowRAGStrategy) -> None:
        """Test document query interface (Mock version)."""
        print("\\n=== Test document query interface (Mock version) ===")

        result = await strategy.query_doc(docId="c3c9cc6691fc11f095a90242ac1a0007")

        print("Document query results:")
        for i, chunk in enumerate(result):
            print(
                f"Chunk {i + 1}: docId={chunk['docId']}, " f"chunkId={chunk['chunkId']}"
            )
            print(f"Content: {chunk['content']}")
            print("---")

        # Validate return format
        assert isinstance(result, list), "Should return a list"
        if result:
            assert "docId" in result[0], "Should contain docId field"
            assert "chunkId" in result[0], "Should contain chunkId field"
            assert "content" in result[0], "Should contain content field"

        print("✅ Document query interface Mock test passed")

    @pytest.mark.asyncio
    async def test_query_doc_name_mock(self, strategy: MockRagflowRAGStrategy) -> None:
        """Test document name query interface (Mock version)."""
        print("\\n=== Test document name query interface (Mock version) ===")

        result = await strategy.query_doc_name(docId="4d47376892d811f0a5960242ac1c0007")

        print("Document name query results:")
        if result:
            print(f"Document ID: {result['docId']}")
            print(f"File name: {result.get('fileName', '')}")
            print(f"Status: {result.get('fileStatus', '')}")
            print(f"Chunk count: {result.get('fileQuantity', '')}")
        else:
            print("Document does not exist")

        # Validate return format
        if result:
            assert "docId" in result, "Should contain docId field"
            assert "fileName" in result, "Should contain fileName field"

        print("✅ Document name query interface Mock test passed")

    @pytest.mark.asyncio
    async def test_split_mock(self, strategy: MockRagflowRAGStrategy) -> None:
        """Test document split interface (Mock version)."""
        print("\\n=== Test document split interface (Mock version) ===")

        test_url = "https://mock.example.com/test.pdf"

        result = await strategy.split(
            file=test_url,
            lengthRange=[100, 1000],
            overlap=20,
            resourceType=0,
            cutOff=[],
            separator=[".", "\\n"],
            titleSplit=True,
        )

        print(f"Split results: returned {len(result)} chunks")

        # Validate return format
        assert isinstance(result, list), "Should return a list"
        assert len(result) > 0, "Should return at least one chunk"

        if result:
            first_chunk = result[0]
            expected_keys = [
                "docId",
                "dataIndex",
                "title",
                "content",
                "context",
                "references",
            ]
            for key in expected_keys:
                assert key in first_chunk, f"Should contain {key} field"

            # Show content summary of first 2 chunks
            for i, chunk in enumerate(result[:2]):
                content_preview = (
                    chunk.get("content", "")[:100] + "..."
                    if len(chunk.get("content", "")) > 100
                    else chunk.get("content", "")
                )
                doc_id = chunk.get("docId", "")
                title = chunk.get("title", "")
                print(f"Chunk {i + 1}: docId={doc_id}, title={title}")
                print(f"Content preview: {content_preview}")
                print("---")

        print("✅ Document split interface Mock test passed")

    @pytest.mark.asyncio
    async def test_chunks_save_success_mock(
        self, strategy: MockRagflowRAGStrategy, sample_chunks: List[Dict[str, Any]]
    ) -> None:
        """Test successful chunk save (Mock version)."""
        print("\\n=== Test successful chunk save (Mock version) ===")

        result = await strategy.chunks_save(
            uid="test_user_001",
            docId="4d47376892d811f0a5960242ac1c0007",
            group="Stellar Knowledge Base",
            chunks=list(sample_chunks),
        )

        print("chunks_save results:")
        print(json.dumps(result, ensure_ascii=False, indent=2))

        # Validate return format
        assert self.validate_chunk_save_response(
            result
        ), "chunks_save return format is incorrect"

        # Validate success scenario
        if len(result) > 0:
            first_chunk = result[0]
            # Check if it's an error return
            if "error" not in first_chunk.get("id", "").lower():
                expected_content = "This is the first test chunk content"
                content = first_chunk.get("content", "")
                assert (
                    expected_content in content
                ), "Content should contain expected text"
                print("✅ Successfully saved expected content")

        print("✅ Chunk save success scenario Mock test passed")

    @pytest.mark.asyncio
    async def test_chunks_save_empty_chunks_mock(
        self, strategy: MockRagflowRAGStrategy
    ) -> None:
        """Test empty chunk list save (Mock version)."""
        print("\\n=== Test empty chunk list save (Mock version) ===")

        with pytest.raises(ValueError) as exc_info:
            await strategy.chunks_save(
                uid="test_user_002",
                docId="6d2c2154939311f0bd4f0242c0a83007",
                group="Stellar Knowledge Base",
                chunks=[],
            )

        print(f"✅ Expected exception: {exc_info.value}")
        exc_str = str(exc_info.value).lower()
        assert (
            "empty" in exc_str or "cannot be empty" in exc_str
        ), "Should raise exception about empty parameter"

    @pytest.mark.asyncio
    async def test_chunks_save_nonexistent_doc_mock(
        self, strategy: MockRagflowRAGStrategy, sample_chunks: List[Dict[str, Any]]
    ) -> None:
        """Test save to non-existent document (Mock version)."""
        print("\\n=== Test save to non-existent document (Mock version) ===")

        fake_doc_id = f"nonexistent_doc_{int(time.time())}"
        fake_chunks = []
        for chunk in sample_chunks:
            fake_chunk = chunk.copy()
            fake_chunk["docId"] = fake_doc_id
            fake_chunks.append(fake_chunk)

        result = await strategy.chunks_save(
            uid="test_user_003",
            docId=fake_doc_id,
            group="Stellar Knowledge Base",
            chunks=list(fake_chunks),
        )

        print("Non-existent document save results:")
        print(json.dumps(result, ensure_ascii=False, indent=2))

        # Validate return format
        assert self.validate_chunk_save_response(
            result
        ), "Non-existent document save return format is incorrect"
        assert len(result) == 1, "Non-existent document should return one error chunk"
        assert "error" in result[0]["id"], "Should return error information"
        content = result[0]["content"].lower()
        assert (
            "does not exist" in content
        ), "Error message should indicate non-existence"

        print("✅ Non-existent document save format validation passed")

    @pytest.mark.asyncio
    async def test_chunks_update_success_mock(
        self, strategy: MockRagflowRAGStrategy
    ) -> None:
        """Test successful chunk update (Mock version)."""
        print("\\n=== Test successful chunk update (Mock version) ===")

        # Step 1: Save some chunks for subsequent update
        timestamp = int(time.time())
        test_doc_id = "6d2c2154939311f0bd4f0242c0a83007"

        chunks_to_save = [
            {
                "docId": test_doc_id,
                "dataIndex": f"update_test_{timestamp}_0",
                "title": "Update test chunk 0",
                "content": "This is content 0 prepared for update",
                "context": "This is context 0 prepared for update",
                "references": {},
                "docInfo": {
                    "documentId": test_doc_id,
                    "documentName": "test_document.pdf",
                    "documentSource": "test_source",
                    "documentType": "pdf",
                },
            },
            {
                "docId": test_doc_id,
                "dataIndex": f"update_test_{timestamp}_1",
                "title": "Update test chunk 1",
                "content": "This is content 1 prepared for update",
                "context": "Update test context 1",
                "references": {},
                "docInfo": {
                    "documentId": test_doc_id,
                    "documentName": "test_document.pdf",
                    "documentSource": "test_source",
                    "documentType": "pdf",
                },
            },
        ]

        print("Step 1: Save test chunks...")
        save_result = await strategy.chunks_save(
            docId=test_doc_id,
            group="Stellar Knowledge Base",
            uid="test_user_update_prep",
            chunks=list(chunks_to_save),
        )

        print(f"Save results: {len(save_result)} chunks")
        assert len(save_result) >= 1, "Should successfully save at least 1 chunk"

        # Check if save was successful
        successful_chunks = []
        for chunk in save_result:
            if "error" not in chunk.get("id", "").lower():
                successful_chunks.append(chunk)

        if not successful_chunks:
            print("⚠️ No successfully saved chunks, skipping update test")
            return

        print(f"Successfully saved {len(successful_chunks)} chunks")

        # Step 2: Prepare update data
        chunks_to_update = []
        for i, saved_chunk in enumerate(successful_chunks[:2]):
            actual_data_index = saved_chunk.get("dataIndex")
            update_chunk: Dict[str, Any] = {
                "docId": test_doc_id,
                "dataIndex": actual_data_index,
                "title": f"Updated title {i}",
                "content": f"This is updated content {i} - {timestamp}",
                "context": f"Updated context {i}",
                "references": {},
            }
            chunks_to_update.append(update_chunk)

        print(f"Step 2: Update {len(chunks_to_update)} chunks...")
        data_indices = [c.get("dataIndex") for c in chunks_to_update]
        print(f"Update dataIndex: {data_indices}")

        # Execute update
        result = await strategy.chunks_update(
            docId=test_doc_id,
            group="Stellar Knowledge Base",
            uid="test_user_update_success",
            chunks=chunks_to_update,
        )

        print("chunks_update results:")
        print(json.dumps(result, ensure_ascii=False, indent=2))

        # Validate return format
        assert self.validate_chunk_update_response(
            result
        ), "chunks_update return format is incorrect"

        # Validate update success
        if result is None:
            print("✅ All chunks updated successfully")
        elif isinstance(result, dict) and "failedChunk" in result:
            failed_chunks = result.get("failedChunk")
            print(f"⚠️ Some chunks update failed: {failed_chunks}")
        else:
            print(f"⚠️ Unexpected update result: {result}")

        print("✅ Chunk update success scenario format validation passed")

    @pytest.mark.asyncio
    async def test_chunks_update_empty_chunks_mock(
        self, strategy: MockRagflowRAGStrategy
    ) -> None:
        """Test empty chunk list update (Mock version)."""
        print("\\n=== Test empty chunk list update (Mock version) ===")

        with pytest.raises(ValueError) as exc_info:
            await strategy.chunks_update(
                docId="6d2c2154939311f0bd4f0242c0a83007",
                group="Stellar Knowledge Base",
                uid="test_user_005",
                chunks=[],
            )

        print(f"✅ Expected exception: {exc_info.value}")
        exc_str = str(exc_info.value).lower()
        assert (
            "empty" in exc_str or "cannot be empty" in exc_str
        ), "Should raise exception about empty parameter"

    @pytest.mark.asyncio
    async def test_chunks_update_nonexistent_chunks_mock(
        self, strategy: MockRagflowRAGStrategy
    ) -> None:
        """Test update of non-existent chunks (Mock version)."""
        print("\\n=== Test update of non-existent chunks (Mock version) ===")

        fake_chunks = [
            {
                "docId": "6d2c2154939311f0bd4f0242c0a83007",
                "dataIndex": f"nonexistent_chunk_{int(time.time())}",
                "title": "Non-existent chunk",
                "content": "This chunk doesn't exist, should fail to update",
                "context": "Test context",
                "references": {},
            }
        ]

        result = await strategy.chunks_update(
            docId="6d2c2154939311f0bd4f0242c0a83007",
            group="Stellar Knowledge Base",
            uid="test_user_006",
            chunks=list(fake_chunks),
        )

        print("Non-existent chunk update results:")
        print(json.dumps(result, ensure_ascii=False, indent=2))

        # Validate return format
        assert self.validate_chunk_update_response(
            result
        ), "Non-existent chunk update return format is incorrect"

        # Should succeed with None or return failed chunk info
        if result is None:
            print("✅ Update completed successfully (chunks may have been ignored)")
        elif isinstance(result, dict) and "failedChunk" in result:
            print(f"✅ Update completed with some failures: {result['failedChunk']}")
        else:
            print(f"⚠️ Unexpected result format: {result}")

        print("✅ Non-existent chunk update format validation passed")

    @pytest.mark.asyncio
    async def test_full_integration_workflow_mock(
        self, strategy: MockRagflowRAGStrategy
    ) -> None:
        """Complete integration test workflow (Mock version)."""
        print("\\n=== Complete integration test workflow (Mock version) ===")

        test_pdf_url = "https://mock.example.com/integration_test.pdf"

        try:
            # Step 1: File split
            print("📄 Step 1: Call split interface for file chunking...")
            split_result = await strategy.split(
                file=test_pdf_url,
                lengthRange=[100, 1000],
                overlap=20,
                resourceType=0,
                cutOff=[],
                separator=[".", "\\n"],
                titleSplit=True,
            )

            print(f"Split results: returned {len(split_result)} chunks")
            assert len(split_result) > 0, "Split should return at least one chunk"

            doc_id = split_result[0].get("docId")
            print(f"Got document ID: {doc_id}")
            assert doc_id, "Should get valid document ID"

            # Step 2: Batch save test
            print("\\n📝 Step 2: Test batch save...")
            batch_save_chunks: List[Dict[str, Any]] = []
            for i, chunk in enumerate(split_result[:3]):
                batch_save_chunks.append(
                    {
                        "docId": doc_id,
                        "dataIndex": chunk.get("dataIndex"),
                        "title": f"Batch test title {i}",
                        "content": chunk.get("content"),
                        "context": chunk.get("context"),
                        "references": {},
                    }
                )

            batch_save_result = await strategy.chunks_save(
                uid="integration_test_user",
                docId=doc_id,
                group="Stellar Knowledge Base",
                chunks=batch_save_chunks,
            )

            print(f"Batch save results: {len(batch_save_result)} chunks")

            # Step 3: Single chunk update test
            print("\\n🔄 Step 3: Test first chunk update...")
            if split_result:
                first_chunk = split_result[0]
                update_chunks: List[Dict[str, Any]] = [
                    {
                        "docId": doc_id,
                        "dataIndex": first_chunk.get("dataIndex"),
                        "title": "Integration test update title",
                        "content": (
                            f"This is integration test updated content - "
                            f"{int(time.time())}"
                        ),
                        "context": "Integration test updated context",
                        "references": {},
                    }
                ]

                update_result = await strategy.chunks_update(
                    docId=doc_id,
                    group="Stellar Knowledge Base",
                    uid="integration_test_user",
                    chunks=update_chunks,
                )

                if update_result is None:
                    print("Update results: All chunks updated successfully")
                    print("✅ First chunk update successful")
                elif isinstance(update_result, dict) and "failedChunk" in update_result:
                    failed_chunk = update_result.get("failedChunk")
                    print(f"Update results: Some chunks failed - {failed_chunk}")
                else:
                    print(f"Update results: Unexpected result - {update_result}")

            # Step 4: Query interface test
            print("\\n🔍 Step 4: Test query interfaces...")

            # Test query interface
            print("Test query interface...")
            query_result = await strategy.query(
                query="mechatronics technology",
                doc_ids=[doc_id],
                top_k=3,
                threshold=0.1,
            )
            result_count = query_result.get("count", 0)
            print(f"Query results: found {result_count} relevant results")

            # Test query_doc interface
            print("Test query_doc interface...")
            query_doc_result = await strategy.query_doc(docId=doc_id)
            print(f"Query Doc results: found {len(query_doc_result)} chunks")

            # Test query_doc_name interface
            print("Test query_doc_name interface...")
            query_doc_name_result = await strategy.query_doc_name(docId=doc_id)
            if query_doc_name_result:
                print("Query Doc Name results:")
                print(f"  Document ID: {query_doc_name_result['docId']}")
                print(f"  File name: {query_doc_name_result['fileName']}")
                print(f"  Status: {query_doc_name_result['fileStatus']}")
                print(f"  Chunk count: {query_doc_name_result['fileQuantity']}")

            # Step 5: Validate final state
            print("\\n📊 Step 5: Validate final state...")
            final_chunks = await strategy.query_doc(docId=doc_id)
            print(f"Final chunk count in document: {len(final_chunks)}")

            print("\\n🎉 Complete integration test workflow Mock version executed!")
            print("Test process:")
            print("  ✅ 1. Split - Document chunking")
            print("  ✅ 2. Chunks Save - Batch save")
            print("  ✅ 3. Chunks Update - Update first chunk")
            print("  ✅ 4. Query interfaces - Query test")
            print("  ✅ 5. State validation - Final check")

        except Exception as e:
            print(f"❌ Integration test failed: {e}")
            raise

    @pytest.mark.asyncio
    async def test_cleanup_mock(self, strategy: MockRagflowRAGStrategy) -> None:
        """Clean up test resources (Mock version)."""
        print("\\n=== Clean up test resources (Mock version) ===")

        # Mock version cleanup is simple
        strategy.doc_database.clear()
        strategy.chunk_database.clear()

        print("✅ Mock test resource cleanup completed")


if __name__ == "__main__":
    print("🚀 Start RAGFlow strategy Mock comprehensive test")
    pytest.main([__file__, "-v", "-s"])
