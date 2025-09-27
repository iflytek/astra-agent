"""
Service manager module for managing service factories and their dependencies.
"""

from typing import Any, Dict, List, Optional
from venv import logger

from memory.database.repository.middleware.base import Service
from memory.database.repository.middleware.factory import ServiceFactory
from memory.database.repository.middleware.mid_utils import ServiceType


class ServiceManager:
    """Manager class for handling service registration and instantiation.

    Attributes:
        services: Dictionary of instantiated services
        factories: Dictionary of registered service factories
        dependencies: Dictionary of service dependencies
    """

    def __init__(self) -> None:
        """Initialize the service manager with empty containers."""
        self.services: Dict[str, "Service"] = {}
        self.factories: Dict[ServiceType, "ServiceFactory"] = {}
        self.dependencies: Dict[ServiceType, Optional[List[ServiceType]]] = {}

    async def register_factory(
        self,
        service_factory: "ServiceFactory",
        dependencies: Optional[List[ServiceType]] = None,
    ) -> None:
        """
        Registers a new factory with dependencies.

        Args:
            service_factory: Factory to register
            dependencies: List of service dependencies (optional)
        """
        if dependencies is None:
            dependencies = []
        service_name = service_factory.service_class.name
        self.factories[service_name] = service_factory
        self.dependencies[service_name] = dependencies
        await self._create_service(service_name)

    async def get(self, service_name: ServiceType) -> Any:
        """
        Get (or create) a service by its name.

        Args:
            service_name: Name of the service to get

        Returns:
            The requested service instance
        """
        if service_name not in self.services:
            await self._create_service(service_name)

        return self.services[service_name]

    async def _create_service(self, service_name: ServiceType) -> Any:
        """
        Create a new service given its name, handling dependencies.

        Args:
            service_name: Name of the service to create
        """
        logger.debug("Create service %s", service_name)
        self._validate_service_creation(service_name)

        # Create the actual service
        self.services[service_name] = await self.factories[service_name].create()
        self.services[service_name].set_ready()

    def _validate_service_creation(self, service_name: ServiceType) -> None:
        """
        Validate whether the service can be created.

        Args:
            service_name: Name of the service to validate

        Raises:
            ValueError: If no factory is registered for the service
        """
        if service_name not in self.factories:
            raise ValueError(
                f"No factory registered for the service class '{service_name.name}'"
            )


service_manager = ServiceManager()
