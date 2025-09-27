"""Node trace patch module for compatibility with existing code."""

import time
from typing import Any, Generic, TypeVar

from pydantic import ConfigDict, Field

# Use unified common package import module
from common_imports import NodeLog, NodeTraceLog

T = TypeVar("T", bound=NodeLog)

# Alias for backward compatibility
NodeTrace = NodeTraceLog


class NodeTracePatch(NodeTraceLog, Generic[T]):
    """Node trace patch class extending NodeTraceLog with generic typing."""

    # Use type: ignore to handle the invariant List type incompatibility
    trace: list[T] = Field(default_factory=list)

    model_config = ConfigDict(arbitrary_types_allowed=True)

    def __init__(self, **data: Any) -> None:
        """Initialize NodeTracePatch."""
        super().__init__(**data)
        self.start_time: int = 0

    def record_start(self) -> None:
        """Record start time."""
        self.start_time = int(time.time() * 1000)

    def record_end(self) -> None:
        """Record end time and calculate duration."""
        self.set_end()  # Use parent class set_end method

    def upload(
        self, status: Any, log_caller: str, span: Any
    ) -> dict[str, Any]:  # pylint: disable=unused-argument
        """
        Upload node trace logs.

        Provided for compatibility with existing code.
        """
        # Set status
        self.set_status(status.code, status.message)

        # Return serialized data
        return self.model_dump()  # type: ignore[no-any-return]
