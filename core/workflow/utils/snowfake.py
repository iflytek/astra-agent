"""
Snowflake ID generator utility for generating unique identifiers.

This module provides a simple interface for generating unique IDs using the
Snowflake algorithm, which ensures globally unique identifiers across
distributed systems.
"""

import time

from snowflake import SnowflakeGenerator  # type: ignore

# Initialize snowflake generator with a unique worker ID
t = time.time()
work_id = int(round(t * 1000)) % 1024  # Generate worker ID from current timestamp
gen = SnowflakeGenerator(work_id)


def get_id() -> int:
    """
    Generate a unique snowflake ID.

    :return: Unique snowflake ID as integer
    """
    return next(gen)
