from pydantic import Field
from pydantic_settings import BaseSettings


class UvicornConfig(BaseSettings):
    SERVICE_APP: str = Field(default="api.app:app")
    SERVICE_HOST: str = Field(default="0.0.0.0")
    SERVICE_PORT: int = Field(default=17870)
    SERVICE_WORKERS: int = Field(default=1)
    SERVICE_RELOAD: bool = Field(default=False)
    SERVICE_WS_PING_INTERVAL: bool = Field(default=False)
    SERVICE_WS_PING_TIMEOUT: bool = Field(default=False)
