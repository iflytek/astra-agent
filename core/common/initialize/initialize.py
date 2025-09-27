from loguru import logger

from common.service import service_manager
from common.service.utils import get_factories_and_deps


def initialize_services(services: list | None = None) -> None:
    """
    Initialize all the services needed.
    """
    for factory, dependencies in get_factories_and_deps(services):
        try:
            service_manager.register_factory(factory, dependencies=dependencies)
        except Exception as exc:
            logger.exception(exc)
            raise RuntimeError(
                "Could not initialize services. Please check your settings."
            ) from exc
