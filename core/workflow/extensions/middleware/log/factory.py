import os

from loguru import logger
from workflow.consts.runtime_env import RuntimeEnv
from workflow.extensions.middleware.factory import ServiceFactory
from workflow.extensions.middleware.log.manager import LogService


class LogServiceFactory(ServiceFactory):
    """
    Factory class for creating and configuring LogService instances.

    This factory handles the initialization of loguru logger with custom configuration
    including log file path, rotation settings, and log level.
    """

    def __init__(self) -> None:
        """
        Initialize the LogServiceFactory.

        Sets up the factory to create LogService instances.
        """
        super().__init__(LogService)

    def create(self) -> LogService:
        """
        Create and configure a LogService instance with loguru logger.

        This method initializes the loguru logger with the following configuration:
        - Creates log directory if it doesn't exist
        - Sets up log file path and level from environment variables
        - Configures log rotation, retention, and compression
        - Applies custom log format with timestamp and location info

        :return: Configured LogService instance
        """
        current_dir = os.getenv("LOG_PATH", "../..")
        log_dir = os.path.join(current_dir, "logs")
        os.makedirs(log_dir, exist_ok=True)  # Ensure log directory exists

        # Configure log storage path and log level
        log_path = os.path.join(log_dir, "app.log")
        log_level = os.getenv("LOG_LEVEL", "ERROR")

        # Initialize loguru
        logger.remove()  # Remove default logger configuration

        log_format = "{level} | {time:YYYY-MM-DD HH:mm:ss} | {file} - {function}: {line} {message}"

        # Add file handler with log level and relative path
        logger.add(
            log_path,
            rotation="100 MB",  # Automatically split log files when size exceeds 100MB
            retention="10 days",  # Retain logs for 10 days
            compression="zip",  # Optional: compress old log files
            format=log_format,  # Custom format
            serialize=True,  # Enable JSON format
            level=log_level,  # Log level
        )

        # Add console handler for local environment
        if os.getenv("RUNTIME_ENV", "") == RuntimeEnv.Local.value:
            logger.add(
                lambda msg: print(msg, end=""),
                level=log_level,
                colorize=True,
            )

        logger.debug(
            f"Loguru initialized successfully. Log file: {log_path}, Log level: {log_level}"
        )
        return LogService()
