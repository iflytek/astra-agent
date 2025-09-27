"""
Service base module defining abstract service interface.
"""

from abc import ABC


class Service(ABC):
    """Abstract base class for service implementations.

    Attributes:
        name (str): Name identifier for the service
        ready (bool): Flag indicating if service is initialized and ready
    """

    name: str
    ready: bool = False

    def teardown(self) -> None:
        """Clean up service resources.

        Subclasses should override this method to implement custom cleanup logic.
        """
        pass  # pylint: disable=unnecessary-pass

    def set_ready(self) -> None:
        """Mark service as ready for use."""
        self.ready = True
