"""
Knowledge service main entry module.

This module is responsible for:
1. Initializing logging, monitoring and tracing systems
2. Creating and configuring FastAPI application
3. Setting up global exception handling
4. Starting UVicorn server
"""

import json
import logging
import os
import sys

import uvicorn
from common.initialize.initialize import initialize_services
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from loguru import logger

from knowledge.api.v1.api import rag_router
from knowledge.consts.error_code import CodeEnum
from knowledge.domain.response import ErrorResponse


def initialize_extensions() -> None:
    need_init_services = [
        "settings_service",
        "log_service",
        "otlp_sid_service",
        "otlp_span_service",
        "otlp_metric_service",
    ]
    initialize_services(services=need_init_services)


def create_app() -> FastAPI:
    os.environ["CONFIG_ENV_PATH"] = "./knowledge/config.env"
    initialize_extensions()
    logging.info(""" KNOWLEDGE SERVER START """)

    app = FastAPI()
    app.include_router(rag_router)

    @app.exception_handler(RequestValidationError)
    async def global_validation_exception_handler(
        _request: Request, exc: RequestValidationError
    ) -> JSONResponse:
        """
        Global RequestValidationError handler, returns unified format
        :param _request: Request object (can be used to get request path, method and other context)
        :param exc: RequestValidationError exception instance (contains specific error information)
        """
        error_details = [
            f"field: {'.'.join(map(str, err['loc']))}, message: {err['msg']}"
            for err in exc.errors()
        ]
        error_response = ErrorResponse(
            code_enum=CodeEnum.ParameterInvalid,
            message=f"Request parameter error: {error_details}",
        )

        return JSONResponse(content=error_response.model_dump())

    @app.on_event("startup")
    async def print_routes() -> None:
        route_infos = []
        for route in app.routes:
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
        print("Registered routes:")
        for route_info in route_infos:
            logger.info(json.dumps(route_info, ensure_ascii=False))
            print(json.dumps(route_info, ensure_ascii=False))

    @app.on_event("shutdown")
    async def shutdown() -> None:
        try:
            from knowledge.infra.ragflow import cleanup_session

            await cleanup_session()
        except Exception as e:
            logger.warning(f"Failed to cleanup RAGFlow session: {e}")

        print("🧹 Final shutdown hook executed.")

    return app


if __name__ == "__main__":
    uvicorn.run(
        app="main:create_app",
        host="0.0.0.0",
        port=int(os.getenv("SERVICE_PORT", "20010")),
        workers=(
            None
            if sys.platform in ["win", "win32", "darwin"]
            else int(os.getenv("WORKERS", "1"))
        ),
        reload=False,
        log_level="error",
    )
