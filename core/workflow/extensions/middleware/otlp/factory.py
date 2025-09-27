"""
OSS Service Factory module.

This module provides a factory class for creating OSS service instances
based on configuration environment variables.
"""

from typing import Any

from workflow.extensions.middleware.factory import ServiceFactory
from workflow.extensions.middleware.otlp.base import BaseOTLPService
from workflow.extensions.middleware.otlp.manager import OtlpService
from workflow.extensions.middleware.utils import ServiceType


class OTLPServiceFactory(ServiceFactory):
    """
    Factory class for creating OTLP service instances.
    """

    name = ServiceType.OTLP_SERVICE

    def __init__(self) -> None:
        super().__init__(BaseOTLPService)
        self.client: BaseOTLPService | None = None

    def create(self, *args: Any, **kwargs: Any) -> BaseOTLPService:
        """
        Creates an OTLP service instance based on the OTLP_TYPE environment variable.

        :return: Configured OTLP service instance
        :raises AssertionError: If client creation fails
        """
        self.client = OtlpService()
        # Narrow type for mypy: client must be set
        assert self.client is not None
        return self.client
