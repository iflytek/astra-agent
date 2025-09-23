"""
Spark Flow Main Application Module

This module serves as the entry point for the Spark Flow workflow engine application.
It initializes the FastAPI application with all necessary middleware, routers, and
extensions including metrics, tracing, and graceful shutdown handling.
"""

import json
import os
import sys
from pathlib import Path

import uvicorn
from dotenv import load_dotenv
from fastapi import FastAPI
from fastapi.exceptions import RequestValidationError
from fastapi.routing import APIRoute
from loguru import logger
from starlette.middleware.cors import CORSMiddleware

from workflow.api.v1.flow.publish_auth import publish_auth_router
from workflow.api.v1.router import sparkflow_router, workflow_router
from workflow.cache.event_registry import EventRegistry
from workflow.consts.runtime_env import RuntimeEnv
from workflow.exception import handlers
from workflow.extensions.graceful_shutdown.graceful_shutdown import GracefulShutdown
from workflow.extensions.middleware.initialize import initialize_services
from workflow.extensions.otlp.metric.metric import init_metric
from workflow.extensions.otlp.sid.sid_generator2 import init_sid
from workflow.extensions.otlp.trace.trace import init_trace
from workflow.extensions.otlp.util.ip import ip


def initialize_extensions() -> None:
    """
    Initialize all application extensions including metrics, SID generator, and tracing.

    This function sets up the OpenTelemetry (OTLP) infrastructure for observability,
    including metrics collection, distributed tracing, and service identification.
    It also initializes various middleware services required by the application.
    """
    # Initialize metrics collection with OTLP configuration
    init_metric(
        endpoint=os.getenv("OTLP_ENDPOINT") or "",
        service_name=os.getenv("OTLP_SERVICE_NAME") or "",
        timeout=int(os.getenv("OTLP_METRIC_TIMEOUT", "5000")),
        export_interval_millis=int(
            os.getenv("OTLP_METRIC_EXPORT_INTERVAL_MILLIS", "3000")
        ),
        export_timeout_millis=int(
            os.getenv("OTLP_METRIC_EXPORT_TIMEOUT_MILLIS", "5000")
        ),
    )

    # Initialize service identification generator
    init_sid(
        sub=os.getenv("SERVICE_SUB", "spf"),
        location=os.getenv("SERVICE_LOCATION", "SparkFlow"),
        localIp=ip,
        localPort=os.getenv("SERVICE_PORT", "7860"),
    )

    # Initialize distributed tracing with OTLP configuration
    init_trace(
        endpoint=os.getenv("OTLP_ENDPOINT") or "",
        service_name=os.getenv("OTLP_SERVICE_NAME") or "",
        timeout=int(os.getenv("OTLP_TRACE_TIMEOUT", "5000")),
        max_queue_size=int(os.getenv("OTLP_TRACE_MAX_QUEUE_SIZE", "2048")),
        schedule_delay_millis=int(
            os.getenv("OTLP_TRACE_SCHEDULE_DELAY_MILLIS", "5000")
        ),
        max_export_batch_size=int(os.getenv("OTLP_TRACE_MAX_EXPORT_BATCH_SIZE", "512")),
        export_timeout_millis=int(
            os.getenv("OTLP_TRACE_EXPORT_TIMEOUT_MILLIS", "30000")
        ),
    )

    # Initialize application services and middleware
    initialize_services()


def create_app() -> FastAPI:
    """
    Create and configure the FastAPI application instance.

    This function initializes the FastAPI app with all necessary middleware,
    routers, exception handlers, and lifecycle event handlers. It sets up
    CORS, graceful shutdown, and route logging functionality.

    :return: Configured FastAPI application instance
    """
    # Initialize all application extensions first
    initialize_extensions()

    # Create the FastAPI application instance
    app = FastAPI()

    # Configure CORS middleware to allow cross-origin requests
    origins = ["*"]
    app.add_middleware(
        CORSMiddleware,
        allow_origins=origins,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    # Include API routers for different endpoints
    # app.include_router(openapi_router)  # Commented out - not currently used
    app.include_router(publish_auth_router)
    app.include_router(sparkflow_router)
    app.include_router(workflow_router)

    # Add global exception handler for request validation errors
    app.add_exception_handler(
        RequestValidationError,
        handlers.validation_exception_handler,  # type: ignore[arg-type]
    )

    # Configure graceful shutdown handler
    shutdown_handler = GracefulShutdown(
        event=EventRegistry(),
        check_interval=int(os.getenv("SHUTDOWN_INTERVAL", "2")),
        timeout=int(os.getenv("SHUTDOWN_TIMEOUT", "180")),
    )

    # Register startup event handler to log all available routes
    @app.on_event("startup")
    async def print_routes() -> None:
        """
        Log all registered routes during application startup.

        This function collects information about all registered routes
        and logs them in JSON format for debugging and monitoring purposes.
        """
        route_infos = []
        for route in app.routes:
            if isinstance(route, APIRoute):
                route_infos.append(
                    {
                        "path": route.path,
                        "name": route.name,
                        "methods": list(route.methods),
                    }
                )
            else:
                route_infos.append(
                    {
                        "path": getattr(route, "path", "unknown"),
                        "name": getattr(route, "name", "unknown"),
                        "methods": "N/A",
                    }
                )
        logger.info("Registered routes:")
        for route_info in route_infos:
            logger.info(json.dumps(route_info, ensure_ascii=False))

    # Define final shutdown logic callback
    async def do_final_shutdown_logic() -> None:
        """
        Execute final cleanup logic during application shutdown.

        This function is called as part of the graceful shutdown process
        to perform any necessary cleanup operations.
        """
        print("🧹 Final shutdown hook executed.")

    # Register shutdown event handler for graceful shutdown
    @app.on_event("shutdown")
    async def shutdown() -> None:
        """
        Handle application shutdown gracefully.

        This function ensures that the application shuts down cleanly
        by running the graceful shutdown handler with the final cleanup callback.
        """
        await shutdown_handler.run(shutdown_callback=do_final_shutdown_logic)

    return app


def set_env() -> None:
    """
    Set environment variables by loading configuration from environment files.

    This function determines the appropriate configuration file based on the
    runtime environment (local vs production) and loads the environment
    variables from the corresponding .env file.

    :raises ValueError: If no configuration file is found
    :raises Exception: Re-raises any other exceptions that occur during loading
    """
    try:
        # Determine the runtime environment (defaults to Local)
        running_env = os.getenv("RUNTIME_ENV", RuntimeEnv.Local.value)

        # Select the appropriate configuration file based on environment
        if running_env == RuntimeEnv.Local.value:
            env_file = Path(__file__).parent.parent / "workflow/config.local.env"
        else:
            env_file = Path(__file__).parent.parent / "workflow/config.env"

        logger.debug(f"config.env: {env_file}")

        # Load environment variables from the configuration file
        if os.path.exists(env_file):
            load_dotenv(env_file, override=True)
            logger.debug("Using config.env file.")
        else:
            raise ValueError("No config.env file found.")
    except Exception:
        # Re-raise any exceptions that occur during environment setup
        raise


if __name__ == "__main__":
    """
    Main entry point for the Spark Flow application.

    This block initializes the application environment and starts the Uvicorn
    ASGI server with appropriate configuration for different platforms.
    """
    # Log the current platform for debugging purposes
    logger.debug(f"current platform {sys.platform}")

    # Load environment configuration
    set_env()

    # Start the Uvicorn ASGI server with platform-specific configuration
    uvicorn.run(
        app="main:create_app",  # Reference to the FastAPI app factory function
        host="0.0.0.0",  # Bind to all available network interfaces
        port=int(os.getenv("SERVICE_PORT", "7880")),  # Default port 7880
        workers=(
            None  # Single worker for Windows and macOS (development platforms)
            if sys.platform in ["win", "win32", "darwin"]
            else int(
                os.getenv("WORKERS", "20")
            )  # Multiple workers for Linux (production)
        ),
        reload=True,  # Enable auto-reload for development
        log_level="error",  # Set log level to error to reduce noise
        ws_ping_interval=None,  # Disable WebSocket ping interval
        ws_ping_timeout=None,  # Disable WebSocket ping timeout
    )
