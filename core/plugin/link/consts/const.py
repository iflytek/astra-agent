"""Constants module for Spark Link service.

Contains all configuration constants, environment variables, and key mappings used
throughout the Spark Link service. Constants are imported from various key modules
and used by other modules via attribute access (e.g., const.service_name_key).
"""

import os

# common
# pylint: disable=unused-import  # These imports are used via module attribute access
from plugin.link.consts.keys.common_keys import (
    DATACENTER_ID_KEY,
    DEFAULT_APPID_KEY,
    DOMAIN_BLACK_LIST_KEY,
    HTTP_AUTH_AWAU_API_KEY_KEY,
    HTTP_AUTH_AWAU_API_SECRET_KEY,
    HTTP_AUTH_AWAU_APP_ID_KEY,
    HTTP_AUTH_QU_APP_ID_KEY,
    HTTP_AUTH_QU_APP_KEY_KEY,
    IP_BLACK_LIST_KEY,
    OFFICIAL_TOOL_KEY,
    SEGMENT_BLACK_LIST_KEY,
    THIRD_TOOL_KEY,
    WORKER_ID_KEY,
)

# mysql
# pylint: disable=unused-import  # These imports are used via module attribute access
from plugin.link.consts.keys.mysql_keys import (
    MYSQL_DB_KEY,
    MYSQL_HOST_KEY,
    MYSQL_PASSWORD_KEY,
    MYSQL_PORT_KEY,
    MYSQL_USER_KEY,
)

# redis
# pylint: disable=unused-import  # These imports are used via module attribute access
from plugin.link.consts.keys.redis_keys import (
    REDIS_ADDR_KEY,
    REDIS_CLUSTER_ADDR_KEY,
    REDIS_PASSWORD_KEY,
)

# spark
# pylint: disable=unused-import  # These imports are used via module attribute access
from plugin.link.consts.keys.spark_keys import (
    CONFIG_FILE_KEY,
    ENVIRONMENT_KEY,
    LOG_LEVEL_KEY,
    LOG_PATH_KEY,
    POLARIS_CLUSTER_KEY,
    POLARIS_PASSWORD_KEY,
    POLARIS_URL_KEY,
    POLARIS_USERNAME_KEY,
    PROJECT_NAME_KEY,
    VERSION_KEY,
)

# uvicorn
from plugin.link.consts.keys.uvicorn_keys import SERVICE_PORT_KEY

# common
# These imports are used via module attribute access
from plugin.link.consts.keys.xc_utils_keys import (  # SERVICE_PORT_KEY,
    KAFKA_TOPIC_KEY,
    OTLP_DC_KEY,
    OTLP_ENABLE_KEY,
    OTLP_SERVICE_NAME_KEY,
    SERVICE_LOCATION_KEY,
    SERVICE_NAME_KEY,
    SERVICE_SUB_KEY,
)

# Environment variables
Env = os.getenv(ENVIRONMENT_KEY)
ENV_PRODUCTION = "production"
ENV_PRERELEASE = "prerelease"
ENV_DEVELOPMENT = "development"

# Runtime environment constants
DevelopmentEnv = "development"
ProductionEnv = "production"

# Keeping XingchenEnviron to match existing usage pattern
XingchenEnviron = (
    ProductionEnv if Env in (ENV_PRODUCTION, ENV_PRERELEASE) else DevelopmentEnv
)

# Default tool version and deletion flag value
DEF_VER = "V1.0"
DEF_DEL = 0

__all__ = [
    # xc_utils_keys
    "SERVICE_NAME_KEY",
    "SERVICE_SUB_KEY",
    "SERVICE_LOCATION_KEY",
    "OTLP_ENABLE_KEY",
    "OTLP_DC_KEY",
    "OTLP_SERVICE_NAME_KEY",
    "KAFKA_TOPIC_KEY",
    # mysql_keys
    "MYSQL_DB_KEY",
    "MYSQL_HOST_KEY",
    "MYSQL_PASSWORD_KEY",
    "MYSQL_PORT_KEY",
    "MYSQL_USER_KEY",
    # redis_keys
    "REDIS_ADDR_KEY",
    "REDIS_CLUSTER_ADDR_KEY",
    "REDIS_PASSWORD_KEY",
    # spark_keys
    "CONFIG_FILE_KEY",
    "ENVIRONMENT_KEY",
    "LOG_LEVEL_KEY",
    "LOG_PATH_KEY",
    "POLARIS_CLUSTER_KEY",
    "POLARIS_PASSWORD_KEY",
    "POLARIS_URL_KEY",
    "POLARIS_USERNAME_KEY",
    "PROJECT_NAME_KEY",
    "VERSION_KEY",
    # uvicorn_keys
    "SERVICE_PORT_KEY",
]
