"""Base module providing SQLModel serialization functionality with orjson support."""

from typing import Any, Callable, Optional

import orjson
from sqlmodel import SQLModel


def orjson_dumps(
    v: Any,
    *,
    default: Optional[Callable[[Any], Any]] = None,
    sort_keys: bool = False,
    indent_2: bool = True
) -> str:
    """Serialize Python object to JSON string using orjson.

    Args:
        v: Object to serialize to JSON
        default: Optional function to handle non-serializable objects
        sort_keys: Whether to sort dictionary keys in output
        indent_2: Whether to indent output with 2 spaces

    Returns:
        str: JSON formatted string
    """
    option = orjson.OPT_SORT_KEYS if sort_keys else None  # pylint: disable=no-member
    if indent_2:
        if option is None:
            option = orjson.OPT_INDENT_2  # pylint: disable=no-member
        else:
            option |= orjson.OPT_INDENT_2  # pylint: disable=no-member
    if default is None:
        return orjson.dumps(v, option=option).decode()  # pylint: disable=no-member
    return orjson.dumps(  # pylint: disable=no-member
        v, default=default, option=option
    ).decode()  # pylint: disable=no-member


class SQLModelSerializable(SQLModel):
    """Extends SQLModel with enhanced JSON serialization capabilities.

    Provides custom JSON serialization using orjson for better performance.
    """

    class Config:  # pylint: disable=too-few-public-methods
        """Configuration for SQLModel serialization behavior."""

        from_attributes = True

    def json(self, **kwargs: Any) -> str:
        """Serialize the model instance to JSON string.

        Args:
            **kwargs: Additional arguments passed to dict() method

        Returns:
            str: JSON string representation of the model
        """
        return orjson_dumps(self.dict(**kwargs))

    @classmethod
    def parse_raw(cls, b: bytes, **kwargs: Any) -> "SQLModelSerializable":  # type: ignore[override]  # pylint: disable=unused-argument
        """Parse raw JSON data into model instance.

        Args:
            b: Raw JSON bytes or string to parse
            **kwargs: Additional arguments (kept for API compatibility)

        Returns:
            SQLModelSerializable: Instance of the model class
        """
        return cls.parse_obj(orjson.loads(b))  # pylint: disable=no-member
