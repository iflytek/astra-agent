import time
from typing import Generic, TypeVar

from pydantic import ConfigDict, Field

# Use unified common package import module
from common_imports import NodeLog, NodeTraceLog

T = TypeVar("T", bound=NodeLog)


class NodeTracePatch(NodeTraceLog, Generic[T]):
    # Use type: ignore to handle the invariant List type incompatibility
    trace: list[T] = Field(default_factory=list)  # type: ignore[assignment]

    model_config = ConfigDict(arbitrary_types_allowed=True)

    def record_start(self):
        """Record start time"""
        self.start_time = int(time.time() * 1000)

    def record_end(self):
        """Record end time and calculate duration"""
        self.set_end()  # Use parent class set_end method

    def upload(self, status, log_caller: str, span):
        """
        Upload node trace logs
        Provided for compatibility with existing code
        """
        # Set status
        self.set_status(status.code, status.message)

        # Return serialized data
        return self.model_dump()
