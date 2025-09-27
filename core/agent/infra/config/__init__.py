import os
from typing import Any

from pydantic import Field
from pydantic.fields import FieldInfo
from pydantic_settings import (
    BaseSettings,
    PydanticBaseSettingsSource,
    SettingsConfigDict,
)

# Use unified common package import module
from common_imports import ConfigFilter, DevelopmentEnv, Polaris, ProductionEnv
from infra.config.fast_uvi import UvicornConfig
from infra.config.middleware import MiddlewareConfig
from infra.config.xc_utils import XChenUtilsConfig


class PolarisSettingsSourceFactory(PydanticBaseSettingsSource):
    def __init__(
        self,
        settings_cls: type[BaseSettings],
        dotenv_settings: PydanticBaseSettingsSource,
    ):
        super().__init__(settings_cls)
        self.dotenv_settings = dotenv_settings()

    def get_field_value(
        self, field: FieldInfo, field_name: str
    ) -> tuple[Any, str, bool]:
        """Get field value - not implemented for this source."""
        raise NotImplementedError

    def __call__(self) -> dict[str, Any]:
        self.load_config()

        d: dict[str, Any] = {}

        for field_name, _ in self.settings_cls.model_fields.items():
            field_value = os.getenv(field_name)
            if field_value is not None:
                d[field_name] = field_value

        return d

    def load_config(self) -> None:
        """
        Load remote configuration and override environment variables
        """

        use_polaris = os.getenv("USE_POLARIS") or self.dotenv_settings.get(
            "USE_POLARIS"
        )

        if use_polaris == "false":
            return
        base_url = os.getenv("polaris_url") or self.dotenv_settings.get("polaris_url")
        cluster_group = os.getenv("polaris_cluster") or self.dotenv_settings.get(
            "polaris_cluster"
        )
        username = os.getenv("polaris_username") or self.dotenv_settings.get(
            "polaris_username"
        )
        password = os.getenv("polaris_password") or self.dotenv_settings.get(
            "polaris_password"
        )
        project_name = (
            os.getenv("project_name")
            or self.dotenv_settings.get("project_name")
            or "hy-spark-agent-builder"
        )
        service_name = (
            os.getenv("SERVICE_NAME")
            or self.dotenv_settings.get("SERVICE_NAME")
            or "spark-agent"
        )
        version = os.getenv("version") or self.dotenv_settings.get("version") or "1.0.0"
        config_file = (
            os.getenv("config_file")
            or self.dotenv_settings.get("config_file")
            or "spark-agent.env"
        )

        # Ensure required parameters are not None
        if not base_url or not username or not password or not cluster_group:
            return  # Skip polaris config if required params are missing

        polaris = Polaris(base_url=base_url, username=username, password=password)

        config_filter = ConfigFilter(
            project_name=project_name,
            cluster_group=cluster_group,
            service_name=service_name,
            version=version,
            config_file=config_file,
        )

        try:
            _ = polaris.pull(
                config_filter=config_filter,
                retry_count=3,
                retry_interval=5,
                set_env=True,
            )
        except (ConnectionError, TimeoutError, ValueError) as e:
            print(
                f"⚠️ Polaris configuration loading failed, "
                f"continuing with local configuration: {e}"
            )
            # Continue startup with default configuration


class EnvConfig(BaseSettings):
    RUN_ENVIRON: str = Field(default=DevelopmentEnv)

    def is_dev(self) -> bool:
        return bool(self.RUN_ENVIRON != ProductionEnv)


class AgentConfig(  # pylint: disable=too-many-ancestors
    EnvConfig,
    XChenUtilsConfig,
    UvicornConfig,
    MiddlewareConfig,
):
    """Agent configuration combining all necessary settings."""

    SERVICE_NAME: str = Field(description="Service name", default="Agent")

    model_config = SettingsConfigDict(
        env_file="env/.env", env_file_encoding="utf-8", extra="ignore"
    )

    @classmethod
    def settings_customise_sources(  # pylint: disable=too-many-arguments,too-many-positional-arguments
        cls,
        settings_cls: type[BaseSettings],
        init_settings: PydanticBaseSettingsSource,
        env_settings: PydanticBaseSettingsSource,
        dotenv_settings: PydanticBaseSettingsSource,
        file_secret_settings: PydanticBaseSettingsSource,
    ) -> tuple[PydanticBaseSettingsSource, ...]:
        """Customize settings sources to include Polaris configuration."""
        print(dotenv_settings())
        polaris_source = PolarisSettingsSourceFactory(settings_cls, dotenv_settings)
        return (
            init_settings,
            env_settings,
            polaris_source,
            dotenv_settings,
            file_secret_settings,
        )


agent_config = AgentConfig()

if __name__ == "__main__":
    agent_config = AgentConfig()
    print(agent_config.SERVICE_NAME)
    print(agent_config.RUN_ENVIRON)
    print(agent_config.OTLP_ENDPOINT)
    print(type(agent_config.OTLP_METRIC_TIMEOUT))
