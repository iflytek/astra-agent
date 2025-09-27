"""
AIUI RAG strategy implementation module
Provides Retrieval-Augmented Generation (RAG) functionality based on AIUI
"""

from typing import Any, Dict, List, Optional

from knowledge.domain.entity.rag_do import ChunkInfo
from knowledge.infra.aiui import aiui
from knowledge.service.rag_strategy import RAGStrategy
from knowledge.utils.verification import check_not_empty


class AIUIRAGStrategy(RAGStrategy):
    """AIUI-RAG2 strategy implementation."""

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
        chunk_query_response_data = await aiui.chunk_query(
            query, doc_ids, repo_ids, top_k, threshold, **kwargs
        )

        if not (
            chunk_query_response_data
            and "results" in chunk_query_response_data
            and chunk_query_response_data["results"] is not None
        ):
            return {"query": query, "count": 0, "results": []}

        results = []
        for result in chunk_query_response_data["results"]:
            if isinstance(result, dict):
                doc_info = result.get("docInfo", {})
                file_name = (
                    doc_info.get("documentName", "")
                    if check_not_empty(doc_info)
                    else ""
                )

                results.append(
                    {
                        "score": result.get("score"),
                        "docId": result.get("docId", ""),
                        "title": result.get("title"),
                        "content": result.get("content", ""),
                        "context": result.get("context", ""),
                        "chunkId": result.get("chunkId"),
                        "references": result.get("references", {}),
                        "docInfo": doc_info,
                        "fileName": file_name,
                    }
                )

        return {
            "query": chunk_query_response_data.get("query"),
            "count": chunk_query_response_data.get("count"),
            "results": results,
        }

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
            lengthRange: Length range
            overlap: Overlap length
            resourceType: Resource type
            separator: Separator list
            titleSplit: Whether to split by title
            cutOff: Cutoff marker list
            **kwargs: Other parameters

        Returns:
            List of split chunks
        """
        # Set default values
        lengthRange = lengthRange or [16, 512]
        overlap = overlap or 16
        separator = separator or ["。", "！", "；", "？"]
        titleSplit = True  # Force set to True

        # Document parsing
        doc_parse_response_data = await aiui.document_parse(
            file, resourceType, **kwargs
        )

        # Split chunks
        doc_split_response_data = await aiui.chunk_split(
            lengthRange=lengthRange,
            document=doc_parse_response_data,
            overlap=overlap,
            cutOff=cutOff,
            separator=separator,
            titleSplit=titleSplit,
            **kwargs
        )

        # Process split results
        data: List[Dict[str, Any]] = []
        if check_not_empty(doc_split_response_data):
            for chunk in doc_split_response_data:
                if isinstance(chunk, dict):
                    data.append(
                        {
                            "docId": chunk.get("docId", ""),
                            "dataIndex": chunk.get("chunkId", ""),
                            "title": chunk.get("title", ""),
                            "content": chunk.get("content", ""),
                            "context": chunk.get("context", ""),
                            "references": chunk.get("references", {}),
                            "docInfo": chunk.get("docInfo", {}),
                        }
                    )

        return data

    async def chunks_save(
        self, docId: str, group: str, uid: str, chunks: List[Any], **kwargs: Any
    ) -> Any:
        """
        Save chunks to knowledge base

        Args:
            docId: Document ID
            group: Group name
            uid: User ID
            chunks: Chunk list
            **kwargs: Other parameters

        Returns:
            Save result
        """
        return await aiui.chunk_save(doc_id=docId, group=group, chunks=chunks, **kwargs)

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
            docId: Document ID
            group: Group name
            uid: User ID
            chunks: Chunk list
            **kwargs: Other parameters

        Returns:
            Update result
        """
        chunk_ids = []
        if check_not_empty(chunks):
            chunk_ids = [
                chunk.get("chunkId")
                for chunk in chunks
                if check_not_empty(chunk) and isinstance(chunk, dict)
            ]

        # Delete first, then save
        await self.chunks_delete(doc_id=docId, chunk_ids=chunk_ids, **kwargs)
        return await self.chunks_save(
            doc_id=docId, group=group, uid=uid, chunks=chunks, **kwargs
        )

    async def chunks_delete(
        self, docId: str, chunkIds: List[str], **kwargs: Any
    ) -> Any:
        """
        Delete chunks

        Args:
            docId: Document ID
            chunkIds: Chunk ID list
            **kwargs: Other parameters

        Returns:
            Delete result
        """
        return await aiui.chunk_delete(doc_id=docId, chunk_ids=chunkIds, **kwargs)

    async def query_doc(self, docId: str, **kwargs: Any) -> List[dict]:
        """
        Query all chunks of a document
        """
        result: List[dict] = []
        datas = await aiui.get_doc_content(docId, **kwargs)
        if check_not_empty(datas):
            for data in datas:
                if isinstance(data, dict):
                    content_text = data.get("content", "")
                    references = data.get("references", {})

                    if isinstance(references, dict):
                        for key, value in references.items():
                            if isinstance(value, dict):
                                if value.get("format") == "table":
                                    content_text = content_text.replace(
                                        "<" + key + ">", value.get("content", "")
                                    )
                                elif value.get("format") == "image":
                                    content_text = content_text.replace(
                                        "<" + key + ">",
                                        "![Image name](" + value.get("link", "") + ")",
                                    )

                    result.append(
                        ChunkInfo(
                            docId=data.get("docId", ""),
                            chunkId=data.get("chunkId", ""),
                            content=content_text,
                        ).__dict__
                    )

            result = sorted(result, key=lambda x: x["chunkId"])
        return result

    async def query_doc_name(self, docId: str, **kwargs: Any) -> Optional[dict]:
        """
        Query document name information

        Args:
            docId: Document ID (unused)
            **kwargs: Other parameters

        Returns:
            File information object (current implementation returns None)
        """
        return None
