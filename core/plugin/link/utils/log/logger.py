import os
from pathlib import Path
from typing import Optional

import appdirs
import orjson
from loguru import logger
from plugin.link.consts import const

VALID_LOG_LEVELS = ["DEBUG", "INFO", "WARNING", "ERROR", "CRITICAL"]


def serialize(record):
    """Serialize log record for structured output.

    Args:
        record: Log record containing timestamp and other data

    Returns:
        JSON serialized subset of log record
    """
    subset = {"timestamp": record["time"].timestamp()}
    return orjson.dumps(subset)


def patching(record):
    """Add serialized data to log record.

    Args:
        record: Log record to be patched with serialized data
    """
    record["extra"]["serialized"] = serialize(record)


def configure(log_level: Optional[str] = None, log_file: Optional[Path] = None):
    """Configure the logger with specified level and output file.

    Args:
        log_level: Logging level (DEBUG, INFO, WARNING, ERROR, CRITICAL)
        log_file: Path to log file, defaults to cache directory if not provided
    """
    if os.getenv(const.LOG_LEVEL_KEY) in VALID_LOG_LEVELS and log_level is None:
        log_level = os.getenv(const.LOG_LEVEL_KEY)
    if log_level is None:
        log_level = "INFO"
    log_format = (
        "{level} | {time:YYYY-MM-DD HH:mm:ss} | {process} - {thread} "
        "| {file} - {function}: {line} {message}"
    )

    logger.remove()
    logger.patch(patching)

    if not log_file:
        cache_dir = os.getenv(const.LOG_PATH_KEY)
        if cache_dir:
            cache_dir = Path(cache_dir)
        else:
            cache_dir = Path(appdirs.user_cache_dir("sparklink"))

    log_file = log_file / "link.log"
    print(f"Log file: {log_file}, Log level: {log_level}")

    log_file = Path(log_file)
    log_file.parent.mkdir(parents=True, exist_ok=True)

    logger.add(
        sink=str(log_file),
        level=log_level.upper(),
        format=log_format,
        rotation="10 MB",  # Log rotation based on file size
    )

    logger.debug(f"Logger set up with log level: {log_level}")
    if log_file:
        logger.info(f"Log file: {log_file}")
