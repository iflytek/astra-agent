import os

from workflow.extensions.middleware.factory import ServiceFactory
from workflow.extensions.middleware.masdk.manager import MASDKService


class MASDKServiceFactory(ServiceFactory):
    """
    Factory class for creating MASDK service instances.

    This factory handles the creation of MASDKService objects by reading
    configuration from environment variables and providing default values.
    """

    def __init__(self) -> None:
        """
        Initialize the MASDK service factory.

        Sets up the factory to create MASDKService instances.
        """
        super().__init__(MASDKService)

    def create(self) -> MASDKService:
        """
        Create a new MASDKService instance with configuration from environment variables.

        Reads the following environment variables:
        - MASDK_POLARIS_URL: Polaris service discovery URL
        - MASDK_POLARIS_PROJECT: Polaris project name
        - MASDK_POLARIS_GROUP: Polaris service group
        - MASDK_POLARIS_SERVICE: Polaris service name
        - MASDK_POLARIS_VERSION: Polaris service version
        - MASDK_CHANNEL: MASDK channel configuration

        :return: Configured MASDKService instance
        :raises ValueError: If required environment variables are not set
        """
        # Read Polaris service discovery configuration from environment variables
        polaris_url = os.getenv("MASDK_POLARIS_URL")
        if not polaris_url:
            raise ValueError("MASDK_POLARIS_URL environment variable is not configured")

        polaris_project = os.getenv("MASDK_POLARIS_PROJECT")
        if not polaris_url:
            raise ValueError("MASDK_POLARIS_URL environment variable is not configured")

        polaris_group = os.getenv("MASDK_POLARIS_GROUP")
        if not polaris_url:
            raise ValueError("MASDK_POLARIS_URL environment variable is not configured")

        polaris_service = os.getenv("MASDK_POLARIS_SERVICE")
        if not polaris_url:
            raise ValueError("MASDK_POLARIS_URL environment variable is not configured")

        polaris_version = os.getenv("MASDK_POLARIS_VERSION")
        if not polaris_url:
            raise ValueError("MASDK_POLARIS_URL environment variable is not configured")

        # Configure channel list with fallback to empty string
        channel_list = [os.getenv("MASDK_CHANNEL") or ""]
        if not polaris_url:
            raise ValueError("MASDK_POLARIS_URL environment variable is not configured")

        # Set default metrics service name and strategy types
        metrics_service_name = "masdk"
        strategy_type: list[str] = ["cnt", "conc"]

        # Create and return MASDKService instance with all configuration parameters
        return MASDKService(
            channel_list,
            strategy_type,
            polaris_url or "",
            polaris_project or "",
            polaris_group or "",
            polaris_service or "",
            polaris_version or "",
            None,
            metrics_service_name,
        )
