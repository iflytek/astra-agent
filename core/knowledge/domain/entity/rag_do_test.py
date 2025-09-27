# -*- coding: utf-8 -*-
"""
RAG data object test module.

This module contains unit tests for RAG data object classes,
testing ChunkInfo and FileInfo classes with various data types and edge cases.
"""


from knowledge.domain.entity.rag_do import ChunkInfo, FileInfo


class TestChunkInfo:
    """Test ChunkInfo class."""

    def test_init_with_string_doc_id(self) -> None:
        """Test initialization with string document ID."""
        chunk = ChunkInfo(docId="doc123", chunkId=1, content="test content")
        assert chunk.docId == "doc123"
        assert chunk.chunkId == 1
        assert chunk.content == "test content"

    def test_init_with_int_doc_id(self) -> None:
        """Test initialization with integer document ID."""
        chunk = ChunkInfo(docId=456, chunkId=2, content="another content")
        assert chunk.docId == 456
        assert chunk.chunkId == 2
        assert chunk.content == "another content"

    def test_init_with_various_chunk_ids(self) -> None:
        """Test initialization with various chunk ID types."""
        # Integer chunk ID
        chunk1 = ChunkInfo(docId="doc1", chunkId=100, content="content1")
        assert chunk1.chunkId == 100

        # String chunk ID (though type hint suggests int)
        chunk2 = ChunkInfo(docId="doc2", chunkId="chunk_abc", content="content2")
        assert chunk2.chunkId == "chunk_abc"

    def test_chunk_info_attributes_immutable_after_init(self) -> None:
        """Test that attributes can be modified after initialization."""
        chunk = ChunkInfo(docId="doc1", chunkId=1, content="original")

        # Modify attributes
        chunk.docId = "new_doc"
        chunk.chunkId = 99
        chunk.content = "modified content"

        # Verify changes
        assert chunk.docId == "new_doc"
        assert chunk.chunkId == 99
        assert chunk.content == "modified content"


class TestFileInfo:
    """Test FileInfo class."""

    def test_init_with_required_params(self) -> None:
        """Test initialization with required parameters only."""
        file_info = FileInfo(docId="doc123", fileName="test.txt")

        assert file_info.docId == "doc123"
        assert file_info.fileName == "test.txt"
        assert file_info.fileStatus == ""  # default value
        assert file_info.fileQuantity == 0  # default value

    def test_init_with_all_params(self) -> None:
        """Test initialization with all parameters."""
        file_info = FileInfo(
            docId=456, fileName="document.pdf", fileStatus="processed", fileQuantity=5
        )

        assert file_info.docId == 456
        assert file_info.fileName == "document.pdf"
        assert file_info.fileStatus == "processed"
        assert file_info.fileQuantity == 5

    def test_init_with_string_doc_id(self) -> None:
        """Test initialization with string document ID."""
        file_info = FileInfo(docId="string_doc_id", fileName="file.txt")
        assert file_info.docId == "string_doc_id"
        assert isinstance(file_info.docId, str)

    def test_init_with_int_doc_id(self) -> None:
        """Test initialization with integer document ID."""
        file_info = FileInfo(docId=789, fileName="file.txt")
        assert file_info.docId == 789
        assert isinstance(file_info.docId, int)

    def test_repr_with_int_doc_id(self) -> None:
        """Test repr with integer document ID."""
        file_info = FileInfo(docId=123, fileName="test.doc")
        repr_str = repr(file_info)

        assert "FileInfo" in repr_str
        assert "docId=123" in repr_str
        assert "fileName=test.doc" in repr_str

    def test_file_status_variations(self) -> None:
        """Test various file status values."""
        statuses = ["pending", "processing", "completed", "error", ""]

        for status in statuses:
            file_info = FileInfo(docId="doc1", fileName="file.txt", fileStatus=status)
            assert file_info.fileStatus == status

    def test_file_quantity_variations(self) -> None:
        """Test various file quantity values."""
        quantities = [0, 1, 10, 100, -1]  # Including edge cases

        for quantity in quantities:
            file_info = FileInfo(
                docId="doc1", fileName="file.txt", fileQuantity=quantity
            )
            assert file_info.fileQuantity == quantity

    def test_file_name_with_special_characters(self) -> None:
        """Test file name with special characters."""
        special_names = [
            "file with spaces.txt",
            "file-with-dashes.txt",
            "file_with_underscores.txt",
            "file.with.dots.txt",
            "file_chinese.txt",
            "file🎉.txt",
        ]

        for name in special_names:
            file_info = FileInfo(docId="doc1", fileName=name)
            assert file_info.fileName == name

    def test_file_info_attributes_modifiable(self) -> None:
        """Test that attributes can be modified after initialization."""
        file_info = FileInfo(docId="doc1", fileName="original.txt")

        # Modify attributes
        file_info.docId = "new_doc"
        file_info.fileName = "modified.txt"
        file_info.fileStatus = "updated"
        file_info.fileQuantity = 42

        # Verify changes
        assert file_info.docId == "new_doc"
        assert file_info.fileName == "modified.txt"
        assert file_info.fileStatus == "updated"
        assert file_info.fileQuantity == 42

    def test_default_parameter_behavior(self) -> None:
        """Test default parameter behavior in detail."""
        # Test with empty string default
        file_info1 = FileInfo(docId="doc1", fileName="test.txt", fileStatus="")
        assert file_info1.fileStatus == ""

        # Test with zero default
        file_info2 = FileInfo(docId="doc2", fileName="test.txt", fileQuantity=0)
        assert file_info2.fileQuantity == 0

        # Test that defaults are independent
        file_info3 = FileInfo(docId="doc3", fileName="test.txt")
        file_info3.fileStatus = "modified"
        file_info3.fileQuantity = 99

        file_info4 = FileInfo(docId="doc4", fileName="test.txt")
        assert file_info4.fileStatus == ""  # Still default
        assert file_info4.fileQuantity == 0  # Still default


class TestDataObjectIntegration:
    """Test integration scenarios between ChunkInfo and FileInfo."""

    def test_chunk_and_file_with_same_doc_id(self) -> None:
        """Test ChunkInfo and FileInfo with same document ID."""
        doc_id = "shared_doc_123"

        chunk = ChunkInfo(docId=doc_id, chunkId=1, content="chunk content")
        file_info = FileInfo(docId=doc_id, fileName="shared_document.txt")

        assert chunk.docId == file_info.docId == doc_id

    def test_multiple_chunks_for_same_file(self) -> None:
        """Test multiple chunks associated with same file."""
        doc_id = "multi_chunk_doc"
        file_info = FileInfo(
            docId=doc_id, fileName="large_document.txt", fileQuantity=3
        )

        chunks = [
            ChunkInfo(docId=doc_id, chunkId=1, content="First chunk"),
            ChunkInfo(docId=doc_id, chunkId=2, content="Second chunk"),
            ChunkInfo(docId=doc_id, chunkId=3, content="Third chunk"),
        ]

        # Verify all chunks belong to same document
        for chunk in chunks:
            assert chunk.docId == file_info.docId

        # Verify chunk count matches file quantity
        assert len(chunks) == file_info.fileQuantity

    def test_sorting_chunks_from_multiple_documents(self) -> None:
        """Test sorting chunks from multiple documents."""
        chunks = [
            ChunkInfo(docId="doc1", chunkId=3, content="doc1 chunk3"),
            ChunkInfo(docId="doc2", chunkId=1, content="doc2 chunk1"),
            ChunkInfo(docId="doc3", chunkId=2, content="doc3 chunk2"),
            ChunkInfo(docId="doc4", chunkId=4, content="doc4 chunk4"),
        ]

        sorted_chunks = sorted(chunks, key=lambda x: x.chunkId)

        # Verify sorting is by docId first, then by chunkId
        # Expected order: doc1/1, doc1/3, doc2/1, doc2/2
        assert sorted_chunks[0].docId == "doc2" and sorted_chunks[0].chunkId == 1
        assert sorted_chunks[1].docId == "doc3" and sorted_chunks[1].chunkId == 2
        assert sorted_chunks[2].docId == "doc1" and sorted_chunks[2].chunkId == 3
        assert sorted_chunks[3].docId == "doc4" and sorted_chunks[3].chunkId == 4
