"""Error code definition module.
This module defines all error codes used in the RPA service."""

from enum import Enum


class ErrorCode(Enum):
    """Error code definitions for RPA service."""

    # Define success and failure error codes
    SUCCESS = (0, "Success")
    FAILURE = (55000, "Failure")
    # Error code range, 55000 - 59999

    CREATE_TASK_ERROR = (55001, "Create task error")
    QUERY_TASK_ERROR = (55002, "Query task error")
    TIMEOUT_ERROR = (55003, "Timeout error")

    UNKNOWN_ERROR = (55999, "Unknown error")

    # Return error code
    @property
    def code(self) -> int:
        """Return error code."""
        return self.value[0]

    # Return error message
    @property
    def message(self) -> str:
        """Return error message."""
        return self.value[1]

    def __str__(self) -> str:
        return f"code: {self.code}, msg: {self.message}"
