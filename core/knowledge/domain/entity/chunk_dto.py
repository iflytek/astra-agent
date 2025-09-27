# -*- coding: utf-8 -*-
"""
Data model definition module
Contains request model definitions for file splitting, chunking operations, and queries
Uses Pydantic for data validation and serialization
"""

from enum import Enum
from typing import Any, List, Optional

from pydantic import BaseModel, Field, conlist


class RAGType(str, Enum):
    """Define RAG type enumeration"""

    AIUI_RAG2 = "AIUI-RAG2"
    CBG_RAG = "CBG-RAG"
    SparkDesk_RAG = "SparkDesk-RAG"
    RagFlow_RAG = "Ragflow-RAG"


class FileSplitReq(BaseModel):
    """
    File splitting request model

    Attributes:
        file: File content or path, required
        resourceType: Resource type, 0-regular file, 1-URL webpage, default is 0
        ragType: RAG type
        lengthRange: Split length range, optional
        overlap: Overlap length, optional
        separator: Separator list, optional
        cutOff: Cutoff marker list, optional
        titleSplit: Whether to split by title, default is False
    """

    file: str = Field(..., min_length=1, description="Required, minimum length 1")
    resourceType: Optional[int] = Field(
        default=0, description="0-regular file; 1-URL webpage"
    )
    ragType: RAGType = Field(..., description="RAG type")
    lengthRange: Optional[List[int]] = Field(
        default=None, description="Split length range"
    )
    overlap: Optional[int] = Field(default=None, description="Overlap length")
    separator: Optional[List[str]] = Field(default=None, description="Separator list")
    cutOff: Optional[List[str]] = Field(default=None, description="Cutoff marker list")
    titleSplit: Optional[bool] = Field(
        default=False, description="Whether to split by title"
    )


class ChunkSaveReq(BaseModel):
    """
    Chunk save request model

    Attributes:
        docId: Document ID, required
        group: Group identifier, required
        uid: User ID, optional
        chunks: Chunk list, must contain at least one element
        ragType: RAG type
    """

    docId: str = Field(..., min_length=1, description="Required, minimum length 1")
    group: str = Field(..., min_length=1, description="Required, minimum length 1")
    uid: Optional[str] = Field(default=None, description="User ID")
    chunks: conlist(Any, min_length=1) = Field(
        ..., description="Chunk list, must contain at least one element"
    )
    ragType: RAGType = Field(..., description="RAG type")


class ChunkUpdateReq(BaseModel):
    """
    Chunk update request model

    Attributes:
        docId: Document ID, required
        group: Group identifier, required
        uid: User ID, optional
        chunks: Chunk dictionary list, must contain at least one element
        ragType: RAG type
    """

    docId: str = Field(..., min_length=1, description="Required, minimum length 1")
    group: str = Field(..., min_length=1, description="Required, minimum length 1")
    uid: Optional[str] = Field(default=None, description="User ID")
    chunks: conlist(dict, min_length=1) = Field(
        ..., description="Chunk dictionary list, must contain at least one element"
    )
    ragType: RAGType = Field(..., description="RAG type")


class ChunkDeleteReq(BaseModel):
    """
    Chunk delete request model

    Attributes:
        docId: Document ID, required
        chunkIds: Chunk ID list, optional
        ragType: RAG type
    """

    docId: str = Field(..., min_length=1, description="Required, minimum length 1")
    chunkIds: Optional[List[str]] = Field(default=None, description="Chunk ID list")
    ragType: RAGType = Field(..., description="RAG type")


class QueryMatch(BaseModel):
    """
    Query matching condition model

    Attributes:
        docIds: Document ID list, optional
        repoId: Knowledge base ID list, must contain at least one element
        threshold: Similarity threshold, range 0~1, default is 0
        flowId: Flow ID, optional
    """

    docIds: Optional[List[str]] = Field(default=None, description="Document ID list")
    repoId: conlist(str, min_length=1) = Field(
        ..., description="Knowledge base ID list, must contain at least one element"
    )
    threshold: float = Field(
        default=0, ge=0, le=1, description="Optional, default value 0, range 0~1"
    )
    flowId: Optional[str] = Field(default=None, description="Flow ID")


class ChunkQueryReq(BaseModel):
    """
    Chunk query request model

    Attributes:
        query: Query text, required
        topN: Number of results to return, range 1~5
        match: Matching conditions
        ragType: RAG type
    """

    query: str = Field(..., min_length=1, description="Required, minimum length 1")
    topN: int = Field(..., ge=1, le=5, description="Required, range 1~5")
    match: QueryMatch = Field(..., description="Matching conditions")
    ragType: RAGType = Field(..., description="RAG type")


class QueryDocReq(BaseModel):
    """
    Document query request model

    Attributes:
        docId: Document ID, required
        ragType: RAG type
    """

    docId: str = Field(..., min_length=1, description="Required, minimum length 1")
    ragType: RAGType = Field(..., description="RAG type")
