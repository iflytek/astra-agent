"""
Tenant publish matrix constants.

This module defines the tenant publishing matrix for different platforms
and release statuses. The matrix maps platform types to their corresponding
publish limits and status codes.

Matrix Layout:
           |     | Xingchen | Open Platform |     | AIUI |
------------------------------------------------
Unpublished|  -1 |    0     |      0        | -1  |  0   |
------------------------------------------------
Published  | -1  |    1     |      4        | -1  |  32  |
------------------------------------------------
Publish API| -1  |    8     |      8        | -1  |  -1  |
------------------------------------------------
Taken Down | -1  |    2     |     16        | -1  |  64  |
------------------------------------------------
"""

from enum import Enum

# Mapping of platform source codes to platform names
SOURCE_MAPPING = {1: "Xingchen Platform", 2: "Open Platform", 4: "AIUI"}

# Mapping of release status codes to status names
RELEASE_MAPPING = {1: "Published", 2: "Publish to API", 3: "Taken Down"}

# Tenant publish matrix defining limits for each platform and release status
TENANT_PUBLISH_MAX = [
    [-1, 0, 0, -1, 0],  # Unpublished
    [-1, 1, 4, -1, 32],  # Published
    [-1, 8, 8, -1, -1],  # Publish to API
    [-1, 2, 16, -1, 64],  # Taken Down
]


class ReleaseStatus(Enum):
    """
    Release status enumeration.

    Defines the different states of application releases.
    """

    PUBLISH = 1
    PUBLISH_API = 2
    TAKE_OFF = 3


class Platform(Enum):
    """
    Platform enumeration.

    Defines the different platforms where applications can be published.
    """

    XINGCHEN = 1
    KAI_FANG = 2
    AI_UI = 4


class TenantPublishMatrix:
    """
    Tenant publish matrix class.

    Provides methods to retrieve publish limits and status codes
    for different platforms and release statuses.
    """

    def __init__(self, plat: Platform | int):
        """
        Initialize the tenant publish matrix.

        :param plat: Platform enum or integer value representing the platform
        """
        if isinstance(plat, Platform):
            self.plat = plat.value
        else:
            self.plat = plat

    @property
    def get_publish(self) -> int:
        """
        Get the publish limit for the current platform.

        :return: Integer value representing the publish limit
        """
        return TENANT_PUBLISH_MAX[ReleaseStatus.PUBLISH.value][self.plat]

    @property
    def get_publish_api(self) -> int:
        """
        Get the API publish limit for the current platform.

        :return: Integer value representing the API publish limit
        """
        return TENANT_PUBLISH_MAX[ReleaseStatus.PUBLISH_API.value][self.plat]

    @property
    def get_take_off(self) -> int:
        """
        Get the take-off limit for the current platform.

        :return: Integer value representing the take-off limit
        """
        return TENANT_PUBLISH_MAX[ReleaseStatus.TAKE_OFF.value][self.plat]

    def get_release_status(self, release_status: ReleaseStatus | int) -> int:
        """
        Get the release status value for the current platform and release status.

        :param release_status: Release status enum or integer value
        :return: Integer value from the tenant publish matrix
        """
        if isinstance(release_status, ReleaseStatus):
            return TENANT_PUBLISH_MAX[release_status.value][self.plat]
        return TENANT_PUBLISH_MAX[release_status][self.plat]
