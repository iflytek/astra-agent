"""
Snowflake ID generator utility module for generating unique distributed IDs.
"""

import time

from snowflake import SnowflakeGenerator

t = time.time()

work_id = int(round(t * 1000)) % 1024

gen = SnowflakeGenerator(work_id)


def get_id() -> int:
    """Generate and return a new Snowflake ID.

    Returns:
        int: A unique Snowflake ID
    """
    return next(gen)  # type: ignore[no-any-return]
