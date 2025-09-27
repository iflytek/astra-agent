"""Main module for the FastAPI application.

This module initializes the FastAPI app, sets up middleware,
configures routes, and handles application lifecycle events.
"""

import json
import os
import sys
from contextlib import asynccontextmanager
from typing import Any, AsyncGenerator

import uvicorn
from common.initialize.initialize import initialize_services
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from loguru import logger
from memory.database.api import router
from memory.database.domain.entity.views.http_resp import format_response
from memory.database.exceptions.error_code import CodeEnum
from starlette.middleware.cors import CORSMiddleware


async def initialize_extensions() -> None:
    """Initialize required extensions and services for the application."""
    os.environ["CONFIG_ENV_PATH"] = "./memory/database/config.env"

    need_init_services = [
        "settings_service",
        "log_service",
        "otlp_sid_service",
        "otlp_span_service",
        "otlp_metric_service",
    ]
    initialize_services(services=need_init_services)

    from repository.middleware.initialize import \
        initialize_services as \
        rep_initialize_services  # pylint: disable=import-outside-toplevel

    await rep_initialize_services()


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
    """Async context manager for application lifespan events.

    Args:
        app: The FastAPI application instance.

    Yields:
        None: After successful initialization.
    """
    try:
        await initialize_extensions()
        # Execute before application startup
        yield
        # Execute after application startup
        route_infos = []
        for route in app.routes:
            if hasattr(route, "path") and hasattr(route, "name"):
                route_infos.append(
                    {
                        "path": route.path,
                        "name": route.name,
                        "methods": (
                            list(route.methods) if hasattr(route, "methods") else "chat"
                        ),
                    }
                )
        logger.info("Registered routes:")
        for route_info in route_infos:
            logger.info(json.dumps(route_info, ensure_ascii=False))
    except Exception as e:  # pylint: disable=broad-except
        logger.exception(f"Failed during lifespan startup.\n{e}")


def create_app() -> FastAPI:
    """Create and configure the FastAPI application.

    Returns:
        FastAPI: The configured FastAPI application instance.
    """
    try:
        app = FastAPI(lifespan=lifespan)

        origins = ["*"]
        app.add_middleware(
            CORSMiddleware,
            allow_origins=origins,
            allow_credentials=True,
            allow_methods=["*"],
            allow_headers=["*"],
        )

        app.include_router(router.router)

        # Define global Pydantic validation exception handler (applies to all routes)
        @app.exception_handler(RequestValidationError)
        async def global_validation_exception_handler(
            _request: Request, exc: RequestValidationError
        ) -> JSONResponse:
            """Global validation exception handler.

            Args:
                _request: The incoming request (unused).
                exc: The validation error.

            Returns:
                JSONResponse: Formatted error response.
            """
            # Format error information (extract field path and error description)
            error_details = [
                f"field: {'.'.join(map(str, err['loc']))}, message: {err['msg']}"
                for err in exc.errors()
            ]
            return format_response(  # type: ignore[no-any-return]
                code=CodeEnum.ParamError.code,
                message=f"Parameter validation failed: {error_details}",
            )

        # Register global exception handler
        @app.exception_handler(Exception)
        async def global_exception_handler(
            _request: Request, exc: Exception
        ) -> JSONResponse:
            """Global exception handler.

            Args:
                _request: The incoming request (unused).
                exc: The exception.

            Returns:
                JSONResponse: Formatted error response.
            """
            return format_response(  # type: ignore[no-any-return]
                code=CodeEnum.HttpError.code, message=f"{str(exc.__cause__)}"
            )

        # Register custom exception handler
        from api import \
            CustomException  # pylint: disable=import-outside-toplevel

        @app.exception_handler(CustomException)
        async def custom_exception_handler(_request: Request, exc: Any) -> JSONResponse:
            """Custom exception handler.

            Args:
                _request: The incoming request (unused).
                exc: The custom exception.

            Returns:
                JSONResponse: Formatted error response.
            """
            return JSONResponse(
                status_code=400,
                content={
                    "code": exc.code,
                    "message": exc.message,
                    "sid": getattr(exc, "sid", None),
                },
            )

    except Exception as e:  # pylint: disable=broad-except
        logger.error(f"Failed to create app: {e}")

    return app


if __name__ == "__main__":
    logger.debug(f"current platform {sys.platform}")
    # app = asyncio.run(create_app())

    uvicorn.run(
        app="main:create_app",
        host="0.0.0.0",
        port=int(os.getenv("SERVICE_PORT", "7990")),
        workers=(
            None
            if sys.platform in ["win", "win32", "darwin"]
            else int(os.getenv("WORKERS", "20"))
        ),
        reload=False,
        log_level="error",
        ws_ping_interval=None,
        ws_ping_timeout=None,
    )
