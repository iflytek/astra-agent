import os

from loguru import logger

from common.service.base import ServiceFactory
from common.service.log.logger_service import LogService


class LogServiceFactory(ServiceFactory):
    def __init__(self) -> None:
        super().__init__(LogService)  # type: ignore[arg-type]

    def create(self) -> LogService:  # type: ignore[override, no-untyped-def]
        """
        Initialize log instance.
        :return:
        """
        print("------ init loguru in factory")
        current_dir = os.getenv("LOG_PATH", "./")
        log_dir = os.path.join(current_dir, "logs")
        os.makedirs(log_dir, exist_ok=True)

        log_path = os.path.join(log_dir, "app.log")
        log_level = os.getenv("LOG_LEVEL", "ERROR")

        logger.remove()

        log_format = "{level} | {time:YYYY-MM-DD HH:mm:ss} | {file} - {function}: {line} {message}"

        logger.add(
            log_path,
            rotation="100 MB",
            retention="10 days",
            compression="zip",
            format=log_format,
            serialize=True,
            level=log_level,
        )
        logger.debug(
            f"Loguru initialized successfully. Log file: {log_path}, Log level: {log_level}"
        )
        return LogService()


# print("------ logger factory")
