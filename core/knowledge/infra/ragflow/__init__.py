"""RAGFlow Infrastructure Module

Provides convenient imports and resource management for RAGFlow client
"""

from .ragflow_client import (  # Query related APIs; Dataset management APIs; Document management APIs; Chunk management APIs; Helper functions; Resource management
    add_chunk,
    cleanup_session,
    create_dataset,
    delete_chunks,
    delete_documents,
    get_document_info,
    list_datasets,
    list_document_chunks,
    list_documents_in_dataset,
    parse_documents,
    reload_config,
    retrieval,
    update_chunk,
    update_document,
    upload_document_to_dataset,
    wait_for_parsing,
)

__all__ = [
    # Query related APIs
    "retrieval",
    # Dataset management APIs
    "list_datasets",
    "create_dataset",
    # Document management APIs
    "upload_document_to_dataset",
    "update_document",
    "parse_documents",
    "list_documents_in_dataset",
    "list_document_chunks",
    "delete_documents",
    # Chunk management APIs
    "delete_chunks",
    "update_chunk",
    "add_chunk",
    # Helper functions
    "wait_for_parsing",
    "get_document_info",
    # Resource management
    "cleanup_session",
    "reload_config",
]
