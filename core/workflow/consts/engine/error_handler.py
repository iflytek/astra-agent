from enum import Enum


class ErrorHandler(Enum):
    """
    Error handling strategy enumeration.

    Defines different approaches for handling errors in workflow execution.
    """

    Interrupted = 0
    CustomReturn = 1
    FailBranch = 2
