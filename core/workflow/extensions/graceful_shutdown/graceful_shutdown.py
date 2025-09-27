import asyncio
from datetime import datetime, timedelta
from typing import Awaitable, Callable

from loguru import logger

from workflow.extensions.graceful_shutdown.base_shutdown_event import BaseShutdownEvent


class GracefulShutdown:
    """
    Manages graceful shutdown process with event clearance monitoring.

    This class provides a mechanism to wait for shutdown events to be cleared
    before proceeding with the actual shutdown process, ensuring that all
    ongoing operations are properly completed.
    """

    def __init__(
        self, event: BaseShutdownEvent, check_interval: int = 2, timeout: int = 30
    ):
        """
        Initialize the graceful shutdown manager.

        :param event: The shutdown event instance to monitor for clearance
        :param check_interval: Interval in seconds between event clearance checks
        :param timeout: Maximum time in seconds to wait for event clearance
        """
        self.event = event
        self.check_interval = check_interval
        self.timeout = timeout

    async def wait_for_event_clearance(self) -> bool:
        """
        Wait for all shutdown events to be cleared within the timeout period.

        This method continuously checks the event clearance status at regular
        intervals until either all events are cleared or the timeout is reached.

        :return: True if all events were cleared within timeout, False otherwise
        """
        logger.info("[GracefulShutdown] Waiting for events to be cleared...")

        # Calculate the deadline for event clearance
        deadline = datetime.now() + timedelta(seconds=self.timeout)

        # Poll for event clearance until deadline or success
        while datetime.now() < deadline:
            try:
                is_cleared = self.event.is_cleared()
                if is_cleared:
                    logger.info("[GracefulShutdown] All events cleared.")
                    return True
            except Exception as e:
                logger.warning(f"[GracefulShutdown] Error checking events: {e}")
                break

            # Wait before next check
            await asyncio.sleep(self.check_interval)

        logger.warning("[GracefulShutdown] Timeout reached. Some events may remain.")
        return False

    async def run(self, shutdown_callback: Callable[[], Awaitable[None]]) -> None:
        """
        Execute the graceful shutdown process.

        In multi-process scenarios, this method does not listen for signals
        but waits for FastAPI's shutdown callback to invoke this method.
        It first waits for all events to be cleared, then executes the
        provided shutdown callback.

        :param shutdown_callback: Async callback function to execute after event clearance
        """
        logger.info("[GracefulShutdown] Shutdown started.")

        # Wait for all events to be cleared before proceeding
        await self.wait_for_event_clearance()

        # Execute the shutdown callback
        await shutdown_callback()
