# -*- coding: utf-8 -*-
"""
SparkDesk infrastructure module

Provides functionality for interacting with iFlytek Spark Desktop service
"""

from .sparkdesk import assemble_auth_headers_async, async_request, sparkdesk_query_async

__all__ = [
    "sparkdesk_query_async",
    "async_request",
    "assemble_auth_headers_async",
]
