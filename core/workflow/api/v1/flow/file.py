"""
File upload API endpoints for workflow system.

This module provides API endpoints for uploading single and multiple files
to the workflow system with proper validation and storage handling.
"""

import uuid
from typing import Annotated, List

from fastapi import APIRouter, File, Header, UploadFile
from fastapi.responses import JSONResponse

from workflow.domain.entities.response import Resp
from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.middleware.getters import get_oss_service
from workflow.extensions.otlp.metric.meter import Meter
from workflow.extensions.otlp.trace.span import Span
from workflow.service import file_service

router = APIRouter(tags=["SSE_OPENAPI"])


@router.post("/upload_file")
async def upload_file(
    x_consumer_username: Annotated[str, Header()], file: UploadFile = File(...)
) -> JSONResponse:
    """
    Upload a single file to the workflow system.

    :param x_consumer_username: Consumer username from header
    :param file: File to upload
    :return: Response with uploaded file URL
    """
    app_id = x_consumer_username
    m = Meter(app_id)
    span = Span(app_id=app_id)
    with span.start() as span_context:
        try:
            contents = await file.read()
            file_service.check(file, contents, span_context)
            if not file.filename:
                raise CustomException(
                    err_code=CodeEnum.FILE_INVALID_ERROR,
                    err_msg="File name cannot be empty",
                )
            extension = file.filename.split(".")[-1].lower()
            file_url = get_oss_service().upload_file(
                f"{str(uuid.uuid4())}.{extension}", contents
            )
            m.in_success_count()
            return Resp.success(data={"url": file_url}, sid=span_context.sid)
        except CustomException as e:
            span_context.record_exception(e)
            m.in_error_count(e.code, span=span_context)
            return Resp.error(e.code, e.message, span_context.sid)
        except Exception as e:
            span_context.record_exception(e)
            m.in_error_count(CodeEnum.FILE_STORAGE_ERROR.code, span=span_context)
            return Resp.error(
                CodeEnum.FILE_STORAGE_ERROR.code,
                CodeEnum.FILE_STORAGE_ERROR.msg,
                span_context.sid,
            )


@router.post("/upload_files")
async def upload_files(
    x_consumer_username: Annotated[str, Header()], files: List[UploadFile] = File(...)
) -> JSONResponse:
    """
    Upload multiple files to the workflow system.

    :param x_consumer_username: Consumer username from header
    :param files: List of files to upload
    :return: Response with uploaded file URLs
    """
    app_id = x_consumer_username
    m = Meter(app_id)
    span = Span(app_id=app_id)
    with span.start() as span_context:
        try:
            file_urls = []
            for file in files:
                contents = await file.read()
                file_service.check(file, contents, span_context)
                if not file.filename:
                    raise CustomException(
                        err_code=CodeEnum.FILE_INVALID_ERROR,
                        err_msg="File name cannot be empty",
                    )
                extension = file.filename.split(".")[-1].lower()
                file_url = get_oss_service().upload_file(
                    f"{str(uuid.uuid4())}.{extension}", contents
                )
                file_urls.append(file_url)
            m.in_success_count()
            return Resp.success(data={"urls": file_urls}, sid=span_context.sid)
        except CustomException as e:
            span_context.record_exception(e)
            m.in_error_count(e.code, span=span_context)
            return Resp.error(e.code, e.message, span_context.sid)
        except Exception as e:
            span_context.record_exception(e)
            m.in_error_count(CodeEnum.FILE_STORAGE_ERROR.code, span=span_context)
            return Resp.error(
                CodeEnum.FILE_STORAGE_ERROR.code,
                CodeEnum.FILE_STORAGE_ERROR.msg,
                span_context.sid,
            )
