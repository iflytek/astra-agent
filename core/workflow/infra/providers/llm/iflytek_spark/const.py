"""
Constants and configuration for iFlytek Spark LLM provider.

This module contains version mappings, API endpoints, and configuration constants
used throughout the Spark LLM integration.
"""

from enum import Enum

# Spark API endpoint paths for different versions
SPARK_V1_URL = "v1.1/chat"
SPARK_V2_URL = "v2.1/chat"
SPARK_V3_URL = "v3.1/chat"
SPARK_V35_URL = "v3.2/chat"

# General model identifiers for different versions
GENERAL_V1 = "general"
GENERAL_V2 = "generalv2"
GENERAL_V3 = "generalv3"
GENERAL_V35 = "generalv3.5"

# Mapping from general model names to their corresponding API endpoints
spark_mapping = {
    GENERAL_V1: SPARK_V1_URL,
    GENERAL_V2: SPARK_V2_URL,
    GENERAL_V3: SPARK_V3_URL,
    GENERAL_V35: SPARK_V3_URL,
}


# Maximum number of retry attempts for failed requests
RETRY_CNT = 2


class RespFormatEnum(Enum):
    """Response format enumeration for different output types."""

    TEXT = 0
    MARKDOWN = 1
    JSON = 2
