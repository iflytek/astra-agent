# -*- coding: utf-8 -*-
"""
AIUI infrastructure module

Provides functionality for interacting with AIUI service
"""

from .aiui import (
    assemble_auth_url,
    chunk_delete,
    chunk_query,
    chunk_save,
    chunk_split,
    document_parse,
    get_doc_content,
    request,
)

__all__ = [
    "assemble_auth_url",
    "chunk_query",
    "document_parse",
    "chunk_split",
    "chunk_save",
    "chunk_delete",
    "get_doc_content",
    "request",
]
