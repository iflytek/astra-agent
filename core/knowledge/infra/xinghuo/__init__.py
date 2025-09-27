# -*- coding: utf-8 -*-
"""
Xinghuo (Spark) infrastructure module

Provides functionality for interacting with iFlytek Spark service
"""

from .xinghuo import (
    assemble_spark_auth_headers_async,
    async_form_request,
    async_request,
    dataset_addchunk,
    dataset_delchunk,
    dataset_updchunk,
    get_chunks,
    get_file_info,
    get_file_status,
    new_topk_search,
    split,
    upload,
)

__all__ = [
    "upload",
    "split",
    "get_chunks",
    "new_topk_search",
    "get_file_status",
    "get_file_info",
    "dataset_addchunk",
    "dataset_delchunk",
    "dataset_updchunk",
    "async_request",
    "async_form_request",
    "assemble_spark_auth_headers_async",
]
