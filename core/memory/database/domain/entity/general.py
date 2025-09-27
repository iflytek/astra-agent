"""
Module providing SQL parsing and execution utilities
for async database operations.
"""

import re
from typing import Any, Dict, Optional

from memory.database.exceptions.e import CustomException
from memory.database.exceptions.error_code import CodeEnum
from memory.database.utils.retry import retry_on_invalid_cached_statement
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession


def extract_sql_params(sql: str) -> set:
    """
    Extract all binding parameters in SQL that appear in :param format

    Args:
        sql: SQL query string

    Returns:
        set: Set of all found parameter names
    """
    return set(re.findall(r":([a-zA-Z_][a-zA-Z0-9_]*)", sql))


@retry_on_invalid_cached_statement(max_retries=3)
async def parse_and_exec_sql(
    session: AsyncSession, sql: str, params: Optional[Dict[str, Any]] = None
) -> Any:
    """
    Safely parse and execute SQL with automatic parameter binding.

    Args:
        session: SQLAlchemy AsyncSession
        sql: SQL statement with binding parameters (e.g., :username)
        params: Parameter dictionary, e.g., {"username": "alice"}

    Returns:
        SQL execution result
    Raises:
        MissingSQLParamsError: If there are missing binding parameters
    """
    param_names = extract_sql_params(sql)
    provided_keys = set(params or {})
    missing = param_names - provided_keys
    if missing:
        raise CustomException(
            err_code=CodeEnum.SQLParseError.code,
            err_msg=f"Missing binding parameters: {missing}",
        )

    return await session.execute(text(sql), params or {})


@retry_on_invalid_cached_statement(max_retries=3)
async def exec_sql_statement(session: AsyncSession, statement: str) -> Any:
    """Execute raw SQL statement

    Args:
        session: SQLAlchemy AsyncSession
        statement: SQL statement to execute

    Returns:
        SQL execution result
    """
    return await session.execute(text(statement))
