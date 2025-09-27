# -*- coding: utf-8 -*-
"""
Chunk DTO test module.

This module contains comprehensive unit tests for all Pydantic data models
defined in chunk_dto.py, including validation tests for field constraints
and custom validators.
"""

import pytest
from pydantic import ValidationError

from knowledge.domain.entity.chunk_dto import (
    ChunkDeleteReq,
    ChunkQueryReq,
    ChunkSaveReq,
    ChunkUpdateReq,
    FileSplitReq,
    QueryDocReq,
    QueryMatch,
    RAGType,
)


class TestFileSplitReq:
    """Test FileSplitReq model."""

    def test_required_fields_valid(self) -> None:
        """Test valid creation with required fields only."""
        req = FileSplitReq(file="test content", ragType=RAGType.AIUI_RAG2)
        assert req.file == "test content"
        assert req.ragType == RAGType.AIUI_RAG2
        assert req.resourceType == 0  # default value
        assert req.titleSplit is False  # default value

    def test_all_fields_valid(self) -> None:
        """Test valid creation with all fields."""
        req = FileSplitReq(
            file="test file content",
            resourceType=1,
            ragType=RAGType.SparkDesk_RAG,
            lengthRange=[100, 500],
            overlap=50,
            separator=["\n", "\r\n"],
            cutOff=["EOF"],
            titleSplit=True,
        )
        assert req.file == "test file content"
        assert req.resourceType == 1
        assert req.ragType == RAGType.SparkDesk_RAG
        assert req.lengthRange == [100, 500]
        assert req.overlap == 50
        assert req.separator == ["\n", "\r\n"]
        assert req.cutOff == ["EOF"]
        assert req.titleSplit is True

    def test_file_empty_validation(self) -> None:
        """Test file field empty validation."""
        with pytest.raises(ValidationError) as exc_info:
            FileSplitReq(file="", ragType=RAGType.AIUI_RAG2)

        errors = exc_info.value.errors()
        assert len(errors) == 1
        assert errors[0]["type"] == "string_too_short"
        assert "file" in str(errors[0]["loc"])

    def test_missing_required_fields(self) -> None:
        """Test missing required fields validation."""
        with pytest.raises(ValidationError) as exc_info:
            FileSplitReq()  # type: ignore

        errors = exc_info.value.errors()
        assert len(errors) == 2
        field_names = [error["loc"][0] for error in errors]
        assert "file" in field_names
        assert "ragType" in field_names


class TestChunkSaveReq:
    """Test ChunkSaveReq model."""

    def test_required_fields_valid(self) -> None:
        """Test valid creation with required fields."""
        req = ChunkSaveReq(
            docId="doc123",
            group="test-group",
            chunks=[{"content": "test chunk"}],
            ragType=RAGType.AIUI_RAG2,
        )
        assert req.docId == "doc123"
        assert req.group == "test-group"
        assert req.chunks == [{"content": "test chunk"}]
        assert req.ragType == RAGType.AIUI_RAG2
        assert req.uid is None  # default value

    def test_with_uid(self) -> None:
        """Test creation with optional uid field."""
        req = ChunkSaveReq(
            docId="doc123",
            group="test-group",
            uid="user456",
            chunks=[{"content": "test chunk"}],
            ragType=RAGType.AIUI_RAG2,
        )
        assert req.uid == "user456"

    def test_empty_chunks_validation(self) -> None:
        """Test chunks list empty validation."""
        with pytest.raises(ValidationError) as exc_info:
            ChunkSaveReq(
                docId="doc123", group="test-group", chunks=[], ragType=RAGType.AIUI_RAG2
            )

        errors = exc_info.value.errors()
        assert len(errors) == 1
        assert errors[0]["type"] == "too_short"
        assert "chunks" in str(errors[0]["loc"])

    def test_multiple_chunks(self) -> None:
        """Test with multiple chunks."""
        chunks = [
            {"content": "chunk 1"},
            {"content": "chunk 2"},
            {"content": "chunk 3"},
        ]
        req = ChunkSaveReq(
            docId="doc123", group="test-group", chunks=chunks, ragType=RAGType.AIUI_RAG2
        )
        assert req.chunks == chunks

    def test_empty_string_validation(self) -> None:
        """Test empty string validation for required fields."""
        with pytest.raises(ValidationError) as exc_info:
            ChunkSaveReq(
                docId="",
                group="test-group",
                chunks=[{"content": "test"}],
                ragType=RAGType.AIUI_RAG2,
            )

        errors = exc_info.value.errors()
        assert len(errors) == 1
        assert errors[0]["type"] == "string_too_short"
        assert "docId" in str(errors[0]["loc"])


class TestChunkUpdateReq:
    """Test ChunkUpdateReq model."""

    def test_valid_creation(self) -> None:
        """Test valid creation."""
        req = ChunkUpdateReq(
            docId="doc123",
            group="test-group",
            chunks=[{"id": "chunk1", "content": "updated content"}],
            ragType=RAGType.AIUI_RAG2,
        )
        assert req.docId == "doc123"
        assert req.group == "test-group"
        assert req.chunks == [{"id": "chunk1", "content": "updated content"}]
        assert req.ragType == RAGType.AIUI_RAG2

    def test_chunks_must_be_dict(self) -> None:
        """Test chunks must be dictionary objects."""
        req = ChunkUpdateReq(
            docId="doc123",
            group="test-group",
            chunks=[{"id": "chunk1"}, {"id": "chunk2"}],
            ragType=RAGType.AIUI_RAG2,
        )
        assert len(req.chunks) == 2
        assert all(isinstance(chunk, dict) for chunk in req.chunks)


class TestChunkDeleteReq:
    """Test ChunkDeleteReq model."""

    def test_required_fields_only(self) -> None:
        """Test creation with required fields only."""
        req = ChunkDeleteReq(docId="doc123", ragType=RAGType.AIUI_RAG2)
        assert req.docId == "doc123"
        assert req.ragType == RAGType.AIUI_RAG2
        assert req.chunkIds is None

    def test_with_chunk_ids(self) -> None:
        """Test creation with chunk IDs."""
        req = ChunkDeleteReq(
            docId="doc123",
            chunkIds=["chunk1", "chunk2", "chunk3"],
            ragType=RAGType.AIUI_RAG2,
        )
        assert req.chunkIds == ["chunk1", "chunk2", "chunk3"]

    def test_empty_chunk_ids_list(self) -> None:
        """Test with empty chunk IDs list."""
        req = ChunkDeleteReq(docId="doc123", chunkIds=[], ragType=RAGType.AIUI_RAG2)
        assert req.chunkIds == []


class TestQueryMatch:
    """Test QueryMatch model."""

    def test_required_fields_only(self) -> None:
        """Test creation with required fields only."""
        match = QueryMatch(repoId=["repo1"])
        assert match.repoId == ["repo1"]
        assert match.threshold == 0  # default value
        assert match.docIds is None
        assert match.flowId is None

    def test_all_fields(self) -> None:
        """Test creation with all fields."""
        match = QueryMatch(
            docIds=["doc1", "doc2"],
            repoId=["repo1", "repo2"],
            threshold=0.8,
            flowId="flow123",
        )
        assert match.docIds == ["doc1", "doc2"]
        assert match.repoId == ["repo1", "repo2"]
        assert match.threshold == 0.8
        assert match.flowId == "flow123"

    def test_empty_repo_id_validation(self) -> None:
        """Test empty repoId list validation."""
        with pytest.raises(ValidationError) as exc_info:
            QueryMatch(repoId=[])

        errors = exc_info.value.errors()
        assert len(errors) == 1
        assert errors[0]["type"] == "too_short"
        assert "repoId" in str(errors[0]["loc"])

    def test_threshold_range_validation(self) -> None:
        """Test threshold range validation."""
        # Test valid threshold values
        match = QueryMatch(repoId=["repo1"], threshold=0.0)
        assert match.threshold == 0.0

        match = QueryMatch(repoId=["repo1"], threshold=1.0)
        assert match.threshold == 1.0

        match = QueryMatch(repoId=["repo1"], threshold=0.5)
        assert match.threshold == 0.5

        # Test invalid threshold values
        with pytest.raises(ValidationError) as exc_info:
            QueryMatch(repoId=["repo1"], threshold=-0.1)

        errors = exc_info.value.errors()
        assert len(errors) == 1
        assert errors[0]["type"] == "greater_than_equal"

        with pytest.raises(ValidationError) as exc_info:
            QueryMatch(repoId=["repo1"], threshold=1.1)

        errors = exc_info.value.errors()
        assert len(errors) == 1
        assert errors[0]["type"] == "less_than_equal"

    def test_none_doc_ids_validation(self) -> None:
        """Test None docIds does not trigger unique validation."""
        match = QueryMatch(docIds=None, repoId=["repo1"])
        assert match.docIds is None
        assert match.repoId == ["repo1"]


class TestChunkQueryReq:
    """Test ChunkQueryReq model."""

    def test_valid_creation(self) -> None:
        """Test valid creation."""
        match = QueryMatch(repoId=["repo1"])
        req = ChunkQueryReq(
            query="test query", topN=3, match=match, ragType=RAGType.AIUI_RAG2
        )
        assert req.query == "test query"
        assert req.topN == 3
        assert req.match == match
        assert req.ragType == RAGType.AIUI_RAG2

    def test_query_empty_validation(self) -> None:
        """Test query field empty validation."""
        match = QueryMatch(repoId=["repo1"])
        with pytest.raises(ValidationError) as exc_info:
            ChunkQueryReq(query="", topN=3, match=match, ragType=RAGType.AIUI_RAG2)

        errors = exc_info.value.errors()
        assert len(errors) == 1
        assert errors[0]["type"] == "string_too_short"
        assert "query" in str(errors[0]["loc"])

    def test_top_n_range_validation(self) -> None:
        """Test topN range validation."""
        match = QueryMatch(repoId=["repo1"])

        # Valid range values
        for valid_n in [1, 2, 3, 4, 5]:
            req = ChunkQueryReq(
                query="test", topN=valid_n, match=match, ragType=RAGType.AIUI_RAG2
            )
            assert req.topN == valid_n

        # Invalid range values
        for invalid_n in [0, 6, -1, 10]:
            with pytest.raises(ValidationError) as exc_info:
                ChunkQueryReq(
                    query="test", topN=invalid_n, match=match, ragType=RAGType.AIUI_RAG2
                )

            errors = exc_info.value.errors()
            assert len(errors) == 1
            assert "topN" in str(errors[0]["loc"])

    def test_nested_match_validation(self) -> None:
        """Test nested QueryMatch validation."""
        # Test that invalid match object raises validation error
        with pytest.raises(ValidationError) as exc_info:
            ChunkQueryReq(
                query="test query",
                topN=3,
                match=QueryMatch(repoId=[]),  # Invalid empty repoId
                ragType=RAGType.AIUI_RAG2,
            )

        errors = exc_info.value.errors()
        assert len(errors) == 1
        # Check that the error is about repoId being too short
        assert "repoId" in str(errors[0]["loc"])


class TestQueryDocReq:
    """Test QueryDocReq model."""

    def test_valid_creation(self) -> None:
        """Test valid creation."""
        req = QueryDocReq(docId="doc123", ragType=RAGType.AIUI_RAG2)
        assert req.docId == "doc123"
        assert req.ragType == RAGType.AIUI_RAG2

    def test_doc_id_empty_validation(self) -> None:
        """Test docId field empty validation."""
        with pytest.raises(ValidationError) as exc_info:
            QueryDocReq(docId="", ragType=RAGType.AIUI_RAG2)

        errors = exc_info.value.errors()
        assert len(errors) == 1
        assert errors[0]["type"] == "string_too_short"
        assert "docId" in str(errors[0]["loc"])

    def test_missing_required_fields(self) -> None:
        """Test missing required fields validation."""
        with pytest.raises(ValidationError) as exc_info:
            QueryDocReq()  # type: ignore

        errors = exc_info.value.errors()
        assert len(errors) == 2
        field_names = [error["loc"][0] for error in errors]
        assert "docId" in field_names
        assert "ragType" in field_names


class TestIntegrationCases:
    """Test integration scenarios with multiple models."""

    def test_chunk_query_with_complex_match(self) -> None:
        """Test ChunkQueryReq with complex QueryMatch."""
        match = QueryMatch(
            docIds=["doc1", "doc2", "doc3"],
            repoId=["repo1", "repo2"],
            threshold=0.75,
            flowId="complex-flow",
        )
        req = ChunkQueryReq(
            query="complex integration test query",
            topN=5,
            match=match,
            ragType=RAGType.SparkDesk_RAG,
        )

        assert req.query == "complex integration test query"
        assert req.topN == 5
        assert req.match.docIds == ["doc1", "doc2", "doc3"]
        assert req.match.repoId == ["repo1", "repo2"]
        assert req.match.threshold == 0.75
        assert req.match.flowId == "complex-flow"
        assert req.ragType == RAGType.SparkDesk_RAG

    def test_model_serialization(self) -> None:
        """Test model serialization to dict."""
        req = FileSplitReq(
            file="test content", ragType=RAGType.AIUI_RAG2, lengthRange=[100, 500]
        )

        data = req.model_dump()
        assert isinstance(data, dict)
        assert data["file"] == "test content"
        assert data["ragType"] == "AIUI-RAG2"
        assert data["lengthRange"] == [100, 500]
        assert data["resourceType"] == 0
        assert data["titleSplit"] is False

    def test_model_validation_error_details(self) -> None:
        """Test detailed validation error information."""
        # First test: single model validation
        try:
            QueryMatch(repoId=[])  # Invalid empty repoId
        except ValidationError as e:
            errors = e.errors()
            assert len(errors) == 1
            assert "repoId" in str(errors[0]["loc"])

        # Second test: multiple validation errors
        try:
            # Create valid match first, then test ChunkQueryReq
            valid_match = QueryMatch(repoId=["repo1"])
            ChunkQueryReq(
                query="",  # Invalid empty query
                topN=0,  # Invalid topN range
                match=valid_match,
                ragType=RAGType.AIUI_RAG2,
            )
        except ValidationError as e:
            errors = e.errors()
            # Should have 2 validation errors (query and topN)
            assert len(errors) == 2

            # Check error types and locations
            error_locations = [str(error["loc"]) for error in errors]
            has_query_error = any("query" in loc for loc in error_locations)
            has_topn_error = any("topN" in loc for loc in error_locations)

            # Should have both query and topN errors
            assert has_query_error
            assert has_topn_error
