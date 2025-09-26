"""
Service initialization module for middleware services.

This module provides functionality to initialize all middleware services
by registering their factories with the service manager.
"""

from loguru import logger

from workflow.extensions.middleware.manager import service_manager
from workflow.extensions.middleware.utils import get_factories_and_deps


def initialize_services() -> None:
    """
    Initialize all middleware services by registering their factories.

    This function iterates through all available service factories and their
    dependencies, registering them with the service manager. If any service
    fails to initialize, the entire initialization process is aborted with
    a descriptive error message.

    :raises RuntimeError: If any service fails to initialize
    """
    for factory, dependencies in get_factories_and_deps():
        try:
            service_manager.register_factory(factory, dependencies=dependencies)
        except Exception as exc:
            logger.exception(exc)
            raise RuntimeError(
                "Could not initialize services. Please check your settings."
            ) from exc
