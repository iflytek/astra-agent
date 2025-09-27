# -*- coding: utf-8 -*-
"""
Infrastructure layer module

Provides infrastructure components for interacting with external services, including AIUI, SparkDesk, and Xinghuo services
"""

from . import aiui, desk, xinghuo

__all__ = [
    "aiui",
    "desk",
    "xinghuo",
]
