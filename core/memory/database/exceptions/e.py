from typing import Optional

from memory.database.exceptions.error_code import CodeEnum


class CustomException(Exception):
    code: int
    message: str
    cause_error: str

    def __init__(
        self, err_code: "CodeEnum", err_msg: str = "", cause_error: Optional[str] = None
    ):
        """
        Custom exception
        :param err_code:    Error code
        :param err_msg:     Error message, if not provided, use the error code's own msg
        :param cause_error: Root cause of the exception
        """
        self.code = err_code.code
        self.message = err_code.msg if not err_msg else f"{err_code.msg}({err_msg})"
        self.cause_error = cause_error or ""

    def __str__(self) -> str:
        if self.cause_error is not None:
            return f"{self.code}: {self.message}({self.cause_error})"
        return f"{self.code}: {self.message}"
