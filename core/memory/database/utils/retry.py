"""
Retry invalid cached statement.
"""

import asyncio
from functools import wraps
from typing import Any, Callable, TypeVar

from asyncpg.exceptions import InvalidCachedStatementError
from loguru import logger
from sqlalchemy.exc import InterfaceError

F = TypeVar("F", bound=Callable[..., Any])


def retry_on_invalid_cached_statement(
    max_retries: int = 2, delay: float = 0.1
) -> Callable[[F], F]:
    """
    Automatically retry on asyncpg InvalidCachedStatementError.
    """

    def decorator(func: F) -> F:
        @wraps(func)
        async def wrapper(*args: Any, **kwargs: Any) -> Any:
            for attempt in range(max_retries):
                try:
                    return await func(*args, **kwargs)
                except (InvalidCachedStatementError, InterfaceError) as e:
                    if attempt < max_retries - 1:
                        logger.info(
                            f"[{func.__name__}] InvalidCachedStatementError, retrying "
                            f"({attempt + 1}/{max_retries})..."
                        )
                        await asyncio.sleep(delay)
                    else:
                        logger.error(f"[{func.__name__}] Max retries exceeded: {e}")
                        raise

        return wrapper  # type: ignore[return-value]

    return decorator
