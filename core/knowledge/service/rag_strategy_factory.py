# -*- coding: utf-8 -*-
"""
RAG strategy factory module.

This module implements the factory pattern for RAG strategies, used to create corresponding strategy instances based on strategy type.
"""

import inspect
from typing import Dict, Type

from knowledge.service.impl.aiui_strategy import AIUIRAGStrategy
from knowledge.service.impl.cbg_strategy import CBGRAGStrategy
from knowledge.service.impl.ragflow_strategy import RagflowRAGStrategy
from knowledge.service.impl.sparkdesk_strategy import SparkDeskRAGStrategy
from knowledge.service.rag_strategy import RAGStrategy


class RAGStrategyFactory:
    """RAG strategy factory, responsible for creating corresponding strategy instances based on ragType."""

    _strategies: Dict[str, Type[RAGStrategy]] = {
        "AIUI-RAG2": AIUIRAGStrategy,
        "SparkDesk-RAG": SparkDeskRAGStrategy,
        "CBG-RAG": CBGRAGStrategy,
        "Ragflow-RAG": RagflowRAGStrategy,
    }

    @classmethod
    def get_strategy(cls, ragType: str) -> RAGStrategy:  # pylint: disable=invalid-name
        """
        Get the corresponding strategy instance based on ragType.

        Args:
            ragType: The RAG type identifier

        Returns:
            An instance of the corresponding RAG strategy

        Raises:
            ValueError: If the ragType is not supported
            TypeError: If the strategy class is abstract and cannot be instantiated
        """
        strategy_class = cls._strategies.get(ragType)
        if not strategy_class:
            raise ValueError(f"Unsupported RAG type: {ragType}")

        # Check if the class is abstract
        if inspect.isabstract(strategy_class):
            abstract_methods = []
            for name, method in inspect.getmembers(
                strategy_class, predicate=inspect.ismethod
            ):
                if getattr(method, "__isabstractmethod__", False):
                    abstract_methods.append(name)
            raise TypeError(
                f"Cannot instantiate abstract class {strategy_class.__name__} "
                f"with abstract methods: {', '.join(abstract_methods)}"
            )

        return strategy_class()

    @classmethod
    def register_strategy(
        cls,
        ragType: str,
        strategy_class: Type[RAGStrategy],  # pylint: disable=invalid-name
    ) -> None:
        """
        Register a new RAG strategy.

        Args:
            ragType: The RAG type identifier
            strategy_class: The strategy class to register

        Raises:
            TypeError: If the strategy class is not a subclass of RAGStrategy or is abstract
        """
        if not issubclass(strategy_class, RAGStrategy):
            raise TypeError("Strategy class must be a subclass of RAGStrategy.")

        # Check if the class is abstract
        if inspect.isabstract(strategy_class):
            abstract_methods = []
            for name, method in inspect.getmembers(
                strategy_class, predicate=inspect.ismethod
            ):
                if getattr(method, "__isabstractmethod__", False):
                    abstract_methods.append(name)
            raise TypeError(
                f"Cannot register abstract class {strategy_class.__name__} "
                f"with abstract methods: {', '.join(abstract_methods)}"
            )

        cls._strategies[ragType] = strategy_class
