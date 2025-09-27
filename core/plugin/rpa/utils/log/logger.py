"""Logging module for configuring and managing application logging."""

import os
from pathlib import Path
from typing import Any, Optional

import appdirs
import orjson
from loguru import logger
from plugin.rpa.consts import const

VALID_LOG_LEVELS = ["DEBUG", "INFO", "WARNING", "ERROR", "CRITICAL"]


def serialize(record: Any) -> bytes:
    """Serialize only the timestamp from the log record."""
    subset = {"timestamp": record["time"].timestamp()}
    return orjson.dumps(subset)  # pylint: disable=no-member


def patching(record: Any) -> None:
    """Add a serialized version of the log record."""
    record["extra"]["serialized"] = serialize(record)


def set_log(log_level: Optional[str] = None, log_path: Optional[str] = None) -> None:
    """Set up logging configuration."""
    if os.getenv(const.LOG_LEVEL_KEY) in VALID_LOG_LEVELS and log_level is None:
        log_level = os.getenv(const.LOG_LEVEL_KEY)
    if log_level is None:
        log_level = "INFO"
    log_format = (
        "{level} | {time:YYYY-MM-DD HH:mm:ss} | {process} - {thread} "
        "| {file} - {function}: {line} {message}"
    )

    logger.remove()
    logger.patch(patcher=patching)

    if not log_path:
        log_path = appdirs.user_cache_dir("rpa-server")

    log_path = f"{log_path}/rpa-server.log"
    log_path_ = Path(log_path)
    log_path_.parent.mkdir(parents=True, exist_ok=True)

    logger.add(
        sink=str(log_path_),
        level=log_level.upper(),
        format=log_format,
        rotation="10 MB",  # Log rotation based on file size
    )

    logger.debug(f"Logger set up with log level: {log_level}")
    if log_path_:
        logger.info(f"Log file: {log_path}")
