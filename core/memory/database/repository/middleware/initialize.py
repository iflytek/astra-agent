"""
Service initialization module for setting up and managing service dependencies.
"""

from loguru import logger
from memory.database.repository.middleware.manager import service_manager
from memory.database.repository.middleware.mid_utils import \
    get_factories_and_deps


async def initialize_services() -> None:
    """
    Initialize all the services needed.

    Raises:
        RuntimeError: If service initialization fails with detailed error message.
    """
    for factory, dependencies in get_factories_and_deps():
        try:
            await service_manager.register_factory(factory, dependencies=dependencies)
        except Exception as exc:
            logger.exception(exc)
            raise RuntimeError(
                "Could not initialize services. Please check your settings."
            ) from exc
