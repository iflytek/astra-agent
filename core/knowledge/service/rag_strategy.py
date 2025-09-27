"""
RAG strategy abstract base class module.

This module defines the abstract interface that all RAG strategy classes must implement,
including query, document splitting, knowledge chunk operations and other functions.
"""

from abc import ABC, abstractmethod
from typing import Any, Dict, List, Optional


class RAGStrategy(ABC):
    """Abstract base class for all RAG strategy classes."""

    @abstractmethod
    async def query(  # pylint: disable=too-many-positional-arguments
        self,
        query: str,
        doc_ids: Optional[List[str]] = None,
        repo_ids: Optional[List[str]] = None,
        top_k: Optional[int] = None,
        threshold: Optional[float] = 0,
        **kwargs: Any
    ) -> Dict[str, Any]:
        """Execute query and return results."""
        raise NotImplementedError

    @abstractmethod
    async def split(  # pylint: disable=too-many-arguments,too-many-positional-arguments
        self,
        file: str,
        lengthRange: List[int],  # pylint: disable=invalid-name
        overlap: int,
        resourceType: int,  # pylint: disable=invalid-name
        separator: List[str],
        titleSplit: bool,  # pylint: disable=invalid-name
        cutOff: List[str],  # pylint: disable=invalid-name
        **kwargs: Any
    ) -> List[Dict[str, Any]]:
        """Split file into chunks."""
        raise NotImplementedError

    @abstractmethod
    async def chunks_save(
        self,
        docId: str,
        group: str,
        uid: str,
        chunks: List[object],
        **kwargs: Any  # pylint: disable=invalid-name
    ) -> Any:
        """Save knowledge chunks."""
        raise NotImplementedError

    @abstractmethod
    async def chunks_update(
        self,
        docId: str,
        group: str,
        uid: str,
        chunks: List[dict],
        **kwargs: Any  # pylint: disable=invalid-name
    ) -> Any:
        """Update knowledge chunks."""
        raise NotImplementedError

    @abstractmethod
    async def chunks_delete(
        self, docId: str, chunkIds: List[str], **kwargs: Any
    ) -> Any:  # pylint: disable=invalid-name
        """Delete knowledge chunks."""
        raise NotImplementedError

    @abstractmethod
    async def query_doc(
        self, docId: str, **kwargs: Any
    ) -> List[dict]:  # pylint: disable=invalid-name
        """Query all chunk information for a document."""
        raise NotImplementedError

    @abstractmethod
    async def query_doc_name(
        self, docId: str, **kwargs: Any
    ) -> Optional[dict]:  # pylint: disable=invalid-name
        """Query document name and information."""
        raise NotImplementedError
