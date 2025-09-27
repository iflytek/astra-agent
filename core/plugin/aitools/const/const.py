"""
Constants definition module containing various constants and configuration
parameters used in the project.
"""

import os

# env
Env = os.getenv("ENVIRONMENT")
ENV_PRODUCTION = "production"
ENV_PRERELEASE = "prerelease"
ENV_DEVELOPMENT = "development"

# service info
SERVICE_SUB_KEY = "SERVICE_SUB"
SERVICE_NAME_KEY = "SERVICE_NAME"
SERVICE_LOCATION_KEY = "SERVICE_LOCATION"
SERVICE_PORT_KEY = "SERVICE_PORT"
SERVICE_APP_KEY = "SERVICE_APP"

# const
IMAGE_GENERATE_MAX_PROMPT_LEN = 510
