"""
File service module for handling file upload validation and processing.

This module provides functionality to validate uploaded files including
file type checking and size limit enforcement.
"""

import os

from fastapi import UploadFile

from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.otlp.trace.span import Span


def check(file: UploadFile, contents: bytes, span_context: Span) -> None:
    """
    Validate uploaded file against supported file types and size limits.

    :param file: The uploaded file object containing metadata
    :param contents: The file contents as bytes (currently unused but kept
                     for future use)
    :param span_context: Tracing span for logging validation events
    :raises CustomException: If file type is not supported or file size exceeds limit
    """
    file_name = file.filename
    file_size = file.size
    # Extract file extension from filename
    extension = file_name.split(".")[-1].lower() if file_name else ""

    # Get supported file types from environment variable
    support_file_type = os.getenv(
        "SUPPORT_FILE_TYPE", "pdf,png,jpg,jpeg,bmp,doc,docx,ppt,pptx,xls,xlsx,csv,txt"
    ).split(",")

    # Get file size limit from environment variable (default: 20MB)
    file_size_limit = int(os.getenv("FILE_SIZE_LIMIT", "20971520"))

    # Validate file type
    if extension not in support_file_type:
        span_context.add_info_event(f"Unsupported file type: {extension}")
        raise CustomException(
            err_code=CodeEnum.FILE_INVALID_ERROR,
            err_msg=f"Unsupported file type: {extension}",
        )

    # Validate file size
    if file_size is not None and file_size > file_size_limit:
        span_context.add_info_event(
            f"File size {file_size} exceeds limit {file_size_limit} bytes"
        )
        raise CustomException(
            err_code=CodeEnum.FILE_INVALID_ERROR,
            err_msg="File size exceeds limit",
        )
