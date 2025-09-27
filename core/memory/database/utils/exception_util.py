"""
Unwrap cause module for error.
"""


def unwrap_cause(exc: BaseException) -> BaseException:
    """
    Layer by layer unwrap __cause__ until getting the underlying exception.
    """
    while hasattr(exc, "__cause__") and exc.__cause__:
        exc = exc.__cause__
    return exc
