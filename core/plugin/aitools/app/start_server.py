"""
Server startup module responsible for FastAPI application initialization and startup.
"""

import os
import threading
import time

import uvicorn
from fastapi import FastAPI
from loguru import logger
from plugin.aitools.api.route import app
from plugin.aitools.const.const import SERVICE_APP_KEY, SERVICE_PORT_KEY

from common.initialize.initialize import initialize_services


class AIToolsServer:

    def start(self):
        # self.set_env()
        self.setup_server()
        self.start_uvicorn()

    def start_config_watcher(self):
        """启动一个后台线程，每 5 分钟执行一次拉取配置"""

        def config_watcher():
            while True:
                try:
                    self.load_config()
                except Exception as e:
                    logger.error("配置拉取失败: %s", e)
                time.sleep(300)

        thread = threading.Thread(target=config_watcher, daemon=True)
        thread.start()

    @staticmethod
    def setup_server():
        """初始化服务套件"""

        os.environ["CONFIG_ENV_PATH"] = "./plugin/aitools/config.env"
        need_init_services = [
            "settings_service",
            "oss_service",
            "kafka_producer_service",
            "otlp_sid_service",
            "otlp_span_service",
            "otlp_metric_service",
        ]
        initialize_services(services=need_init_services)

    @staticmethod
    def start_uvicorn():
        uvicorn_config = uvicorn.Config(
            app=os.getenv(SERVICE_APP_KEY),
            host="0.0.0.0",
            port=int(os.getenv(SERVICE_PORT_KEY)),
            workers=20,
            reload=False,
            ws_ping_interval=None,
            ws_ping_timeout=NotImplemented,
            # log_config=None
        )
        uvicorn_server = uvicorn.Server(uvicorn_config)
        uvicorn_server.run()


def aitools_app():
    """
    description: create ai tools app
    :return:
    """
    main_app = FastAPI()
    main_app.include_router(app)

    return main_app
