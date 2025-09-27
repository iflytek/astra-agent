import os
from pathlib import Path

import uvicorn
from common.initialize.initialize import initialize_services
from fastapi import FastAPI
from loguru import logger
from plugin.link.api.router import router
from plugin.link.consts import const
from plugin.link.domain.models.manager import init_data_base
from plugin.link.utils.json_schemas.read_json_schemas import (
    load_create_tool_schema,
    load_http_run_schema,
    load_mcp_register_schema,
    load_tool_debug_schema,
    load_update_tool_schema,
)
from plugin.link.utils.log.logger import configure
from plugin.link.utils.sid.sid_generator2 import spark_link_init_sid


class SparkLinkServer:

    def start(self):
        """
        Start the Spark Link server by setting up the environment,
        configuring the server, and launching Uvicorn.

        This method orchestrates the complete server startup process including
        environment configuration, server setup, and HTTP server initialization.
        """
        self.setup_server()
        self.start_uvicorn()

    @staticmethod
    def setup_server():
        """Initialize service suite"""
        need_init_services = [
            "settings_service",
            "log_service",
            "otlp_sid_service",
            "otlp_span_service",
            "otlp_metric_service",
            "kafka_producer_service",
        ]
        initialize_services(services=need_init_services)

    @staticmethod
    def start_uvicorn():
        """
        Start the Uvicorn ASGI server with configuration loaded from environment
        variables.

        This method creates and starts a Uvicorn server instance using configuration
        parameters such as host, port, worker count, reload settings, and WebSocket
        ping intervals retrieved from environment variables.
        """
        uvicorn_config = uvicorn.Config(
            app=spark_link_app(),
            host="0.0.0.0",
            port=int(os.getenv(const.SERVICE_PORT_KEY)),
            workers=20,
            reload=False,
            # log_config=None
        )
        uvicorn_server = uvicorn.Server(uvicorn_config)
        uvicorn_server.run()


def spark_link_app():
    """
    Create Spark Link app.

    Returns:
        FastAPI: The configured FastAPI application instance
    """
    configure(
        os.getenv(const.LOG_LEVEL_KEY),
        Path(__file__).parent.parent / os.getenv(const.LOG_PATH_KEY),
    )
    init_data_base()
    load_create_tool_schema()
    load_update_tool_schema()
    load_http_run_schema()
    load_tool_debug_schema()
    load_mcp_register_schema()
    spark_link_init_sid()
    app = FastAPI()
    app.include_router(router)
    logger.error("init success")
    return app


if __name__ == "__main__":
    SparkLinkServer().start()
