# -*- coding: utf-8 -*-
"""
Service layer module

Provides core business logic for Knowledge Service, including RAG strategy interfaces and factory
"""

from .impl import AIUIRAGStrategy, CBGRAGStrategy, SparkDeskRAGStrategy
from .rag_strategy import RAGStrategy
from .rag_strategy_factory import RAGStrategyFactory

__all__ = [
    "RAGStrategy",
    "RAGStrategyFactory",
    "AIUIRAGStrategy",
    "CBGRAGStrategy",
    "SparkDeskRAGStrategy",
]
