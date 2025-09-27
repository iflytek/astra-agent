import os
from typing import Any, Type

from pydantic.fields import FieldInfo
from pydantic_settings import (
    BaseSettings,
    PydanticBaseSettingsSource,
    SettingsConfigDict,
)

from common.service.settings.base_settings import (
    BaseRemoteSettings,
    BaseSettingsService,
)
from common.settings.polaris import ConfigFilter, Polaris
from common.settings.settings import ProjectSettings


class EmptyReomoteSettings(BaseRemoteSettings):

    def __init__(
        self,
        settings_cls: type[BaseSettings],
        dotenv_settings: PydanticBaseSettingsSource,
        cls: Type["ProjectSettings"],
    ):
        BaseRemoteSettings.__init__(self, settings_cls, dotenv_settings, cls)

    def get_field_value(
        self, field: FieldInfo, field_name: str
    ) -> tuple[Any, str, bool]:
        raise NotImplementedError

    def __call__(self) -> dict[str, Any]:
        return {}


class PolarisRemoteSettings(BaseRemoteSettings):

    def __init__(
        self,
        settings_cls: type[BaseSettings],
        dotenv_settings: PydanticBaseSettingsSource,
        cls: Type["ProjectSettings"],
    ):
        BaseRemoteSettings.__init__(self, settings_cls, dotenv_settings, cls)

        self.polaris_keys = (
            "POLARIS_BASE_URL",
            "POLARIS_USERNAME",
            "POLARIS_PASSWORD",
            "POLARIS_PROJECT",
            "POLARIS_CLUSTER",
            "POLARIS_SERVICE",
            "POLARIS_VERSION",
            "POLARIS_CONFIG_FILE",
            "POLARIS_RETRY_COUNT",
            "POLARIS_RETRY_INTERVAL",
        )

    def get_field_value(
        self, field: FieldInfo, field_name: str
    ) -> tuple[Any, str, bool]:
        raise NotImplementedError

    def __call__(self) -> dict[str, Any]:

        polaris_configs = self.load_config()

        d: dict[str, Any] = {}

        for field_name, field in self.settings_cls.model_fields.items():
            field_value = polaris_configs.get(field_name, "not_set_in_polaris")

            if field_value != "not_set_in_polaris":
                d[field_name] = field_value

        return d

    def load_config(self) -> dict[str, Any]:
        return self.load_polaris()

    def load_polaris(self) -> dict[str, Any]:

        connect_args = []

        for key in self.polaris_keys:
            v = os.getenv(key) or self.dotenv_settings.get(key)
            if not v:
                raise ValueError(f"Polaris config {key} is missing")

            connect_args.append(v)

        polaris = Polaris(
            base_url=connect_args[0], username=connect_args[1], password=connect_args[2]
        )

        config_filter = ConfigFilter(
            project_name=connect_args[3],
            cluster_group=connect_args[4],
            service_name=connect_args[5],
            version=connect_args[6],
            config_file=connect_args[7],
        )

        content_dict = polaris.pull(
            config_filter=config_filter,
            retry_count=int(connect_args[8] or "3"),
            retry_interval=int(connect_args[9] or "5"),
            set_env=True,
        )
        return content_dict


class SettingsService(BaseSettingsService):

    @property
    def setting_base(self) -> Type[ProjectSettings]:  # type: ignore[override]
        if os.getenv("POLARIS_ENABLED", "false").lower() == "true":
            ProjectSettings.remote_settings_source = PolarisRemoteSettings
        else:
            ProjectSettings.remote_settings_source = EmptyReomoteSettings

        return ProjectSettings

    def sync_env_file_to_environ(self) -> None:
        # print("config path is:", os.getenv("CONFIG_ENV_PATH"))

        class MySettings(self.setting_base):  # type: ignore[name-defined]
            model_config = SettingsConfigDict(
                env_file=os.getenv("CONFIG_ENV_PATH"),
                env_file_encoding="utf-8",
                extra="ignore",
            )

        MySettings()
