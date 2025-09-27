"""
SparkDesk RAG strategy implementation module
Provides Retrieval-Augmented Generation (RAG) functionality based on iFlytek Spark LLM
"""

from typing import Any, Dict, List, Optional

from knowledge.domain.entity.rag_do import ChunkInfo, FileInfo
from knowledge.infra.desk.sparkdesk import sparkdesk_query_async
from knowledge.service.rag_strategy import RAGStrategy


class SparkDeskRAGStrategy(RAGStrategy):
    """SparkDesk-RAG strategy implementation."""

    async def query(
        self,
        query: str,
        doc_ids: Optional[List[str]] = None,
        repo_ids: Optional[List[str]] = None,
        top_k: Optional[int] = None,
        threshold: Optional[float] = 0,
        **kwargs: Any
    ) -> Dict[str, Any]:
        """
        Execute RAG query

        Args:
            query: Query text
            doc_ids: Document ID list
            repo_ids: Knowledge base ID list
            top_k: Number of results to return
            threshold: Similarity threshold
            **kwargs: Other parameters

        Returns:
            Query result dictionary
        """
        results = await sparkdesk_query_async(query, repo_ids, **kwargs)
        return {"results": results}

    async def split(
        self,
        file: str,
        lengthRange: List[int],
        overlap: int,
        resourceType: int,
        separator: List[str],
        titleSplit: bool,
        cutOff: List[str],
        **kwargs: Any
    ) -> List[Dict[str, Any]]:
        """
        Split file into multiple chunks

        Args:
            file: File content
            length_range: Length range
            overlap: Overlap length
            resource_type: Resource type
            separator: Separator list
            title_split: Whether to split by title
            cut_off: Cutoff marker list
            **kwargs: Other parameters

        Returns:
            List of split chunks

        Raises:
            NotImplementedError: SparkDesk-RAG does not support split operation
        """
        raise NotImplementedError("SparkDesk-RAG does not support split operation.")

    async def chunks_save(
        self, docId: str, group: str, uid: str, chunks: List[Any], **kwargs: Any
    ) -> Any:
        """
        Save chunks to knowledge base

        Args:
            doc_id: Document ID
            group: Group name
            uid: User ID
            chunks: Chunk list
            **kwargs: Other parameters

        Returns:
            Save result

        Raises:
            NotImplementedError: SparkDesk-RAG does not support save operation
        """
        raise NotImplementedError(
            "SparkDesk-RAG does not support chunks_save operation."
        )

    async def chunks_update(
        self,
        docId: str,
        group: str,
        uid: str,
        chunks: List[Dict[str, Any]],
        **kwargs: Any
    ) -> Any:
        """
        Update chunks

        Args:
            doc_id: Document ID
            group: Group name
            uid: User ID
            chunks: Chunk list
            **kwargs: Other parameters

        Returns:
            Update result

        Raises:
            NotImplementedError: SparkDesk-RAG does not support update operation
        """
        raise NotImplementedError(
            "SparkDesk-RAG does not support chunks_update operation."
        )

    async def chunks_delete(
        self, docId: str, chunkIds: List[str], **kwargs: Any
    ) -> Any:
        """
        Delete chunks

        Args:
            doc_id: Document ID
            chunk_ids: Chunk ID list
            **kwargs: Other parameters

        Returns:
            Delete result

        Raises:
            NotImplementedError: SparkDesk-RAG does not support delete operation
        """
        raise NotImplementedError(
            "SparkDesk-RAG does not support chunks_delete operation."
        )

    async def query_doc(self, docId: str, **kwargs: Any) -> List[ChunkInfo]:
        """
        Query all chunks of a document

        Args:
            doc_id: Document ID
            **kwargs: Other parameters

        Returns:
            List of chunk information

        Raises:
            NotImplementedError: SparkDesk-RAG does not support document query operation
        """
        raise NotImplementedError("SparkDesk-RAG does not support query_doc operation.")

    async def query_doc_name(self, docId: str, **kwargs: Any) -> Optional[FileInfo]:
        """
        Query document name information

        Args:
            doc_id: Document ID
            **kwargs: Other parameters

        Returns:
            File information object

        Raises:
            NotImplementedError: SparkDesk-RAG does not support document name query operation
        """
        raise NotImplementedError(
            "SparkDesk-RAG does not support query_doc_name operation."
        )
