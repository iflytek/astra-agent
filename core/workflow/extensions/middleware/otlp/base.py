import abc

from workflow.extensions.middleware.utils import ServiceType


class BaseOTLPService(abc.ABC):
    """
    Abstract base class for OTLP service implementations.
    """

    name = ServiceType.OTLP_SERVICE
