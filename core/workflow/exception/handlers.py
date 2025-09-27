"""
Exception handlers for the workflow system.

This module provides FastAPI-compatible exception handlers for various types of errors
that can occur during request processing. It ensures consistent error response formatting
across different endpoints and handles both standard JSON responses and Server-Sent Events (SSE)
for real-time communication scenarios.

Key Features:
- Request validation error handling with detailed error formatting
- Support for both JSON and SSE response formats
- Integration with the workflow's tracing and logging systems
- Consistent error code and message formatting across all endpoints
- Automatic error categorization and user-friendly error messages

The handlers are designed to provide clear, actionable error information to clients
while maintaining security by not exposing internal system details.
"""

from fastapi import Request, Response
from fastapi.exceptions import RequestValidationError
from workflow.domain.entities.response import response_error, response_error_sse
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.otlp.trace.span import Span


async def validation_exception_handler(
    request: Request, exc: RequestValidationError
) -> Response:
    """
    Handle request validation errors with appropriate response formatting.

    This handler processes FastAPI RequestValidationError exceptions and returns
    appropriate error responses based on the request path. It automatically
    determines the response format based on the endpoint type:
    - Chat endpoints receive SSE-formatted responses for real-time communication
    - Other endpoints receive standard JSON responses

    The handler formats validation errors into human-readable messages that include:
    - Parameter location in the request structure
    - The actual input value that caused the error
    - The specific validation error message and type

    All errors are logged with the tracing system for debugging and monitoring purposes.

    :param request: The FastAPI request object containing path and other request details
    :param exc: The RequestValidationError exception containing validation error details
    :return: Formatted error response (JSON for standard endpoints, SSE for chat endpoints)
    """
    span = Span()
    with span.start() as span_ctx:
        # Format validation errors into human-readable messages with detailed information
        errors_list = [
            (
                f"Parameter: {'->'.join(map(str, error['loc']))}, "
                f"Input: {error.get('input')}, "
                f"Error: {error['msg']} ({error['type']})"
            )
            for error in exc.errors()
        ]
        # Log validation errors to the tracing system for monitoring and debugging
        span_ctx.add_error_events(attributes={"errors": "\n".join(errors_list)})

        # Handle chat endpoints with SSE response format for real-time communication
        if request.url.path in [
            "/workflow/v1/debug/chat/completions",
            "/workflow/v1/chat/completions",
            "/workflow/v1/debug/resume",
            "/workflow/v1/resume",
        ]:
            return response_error_sse(
                CodeEnum.PARAM_ERROR.code, "\n".join(errors_list), span_ctx.sid
            )

        # Handle other endpoints with standard JSON response format
        else:
            return response_error(
                CodeEnum.PARAM_ERROR.code, "\n".join(errors_list), span_ctx.sid
            )
