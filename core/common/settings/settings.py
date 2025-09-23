import json
import logging
import os
import threading
import time
from typing import ClassVar, Optional, Type

from dotenv import load_dotenv
from pydantic import Field
from pydantic.fields import FieldInfo
from pydantic_settings import (
    BaseSettings,
    PydanticBaseSettingsSource,
    SettingsConfigDict,
)

from common.service.settings.base_settings import BaseRemoteSettings

# from xingchen_utils.polaris.client import Polaris, ConfigFilter


class ProjectSettings(BaseSettings):
    # enp is "env polaris", pls is "polaris"

    hot_loading: bool = Field(default=False)
    reloading_interval: int = Field(default=300)
    model_config = SettingsConfigDict(
        env_file=os.getenv("CONFIG_ENV_PATH"), env_file_encoding="utf-8", extra="ignore"
    )

    __reload_env_file: str = ""
    _monitor_thread: Optional[threading.Thread] = None
    _running: bool = False

    remote_settings_source: ClassVar[Optional[Type[BaseRemoteSettings]]] = None

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.__reload_env_file = str(self.model_config.get("env_file", ""))
        if self.__reload_env_file and self.hot_loading:
            self._start_monitor()

        self.sync_fields_to_environ()

        if self.__reload_env_file:
            self.do_sync_env_file_to_environ()

    def sync_fields_to_environ(self):
        env_keys = os.environ.keys()
        for field, field_info in self.model_fields.items():
            env_key, env_value = self.dump_field_and_value(field, field_info)
            if env_key in env_keys:
                continue
            os.environ[env_key] = str(env_value)

    def do_sync_env_file_to_environ(self):
        load_dotenv(self.__reload_env_file, override=False)

    def dump_field_and_value(self, field: str, field_info: FieldInfo):
        value = getattr(self, field)
        if getattr(field_info, "json_schema_extra", None) is None:
            env_key = field
        else:
            if isinstance(field_info.json_schema_extra, dict):
                env_key = str(field_info.json_schema_extra.get("env", "")) or field
            else:
                env_key = field
        if isinstance(value, str):
            env_value = value
        if isinstance(value, set):
            env_value = json.dumps(list(value), ensure_ascii=False)
        else:
            env_value = json.dumps(value, ensure_ascii=False)
        return env_key, env_value

    @classmethod
    def settings_customise_sources(
        cls,
        settings_cls: type[BaseSettings],
        init_settings: PydanticBaseSettingsSource,
        env_settings: PydanticBaseSettingsSource,
        dotenv_settings: PydanticBaseSettingsSource,
        file_secret_settings: PydanticBaseSettingsSource,
    ) -> tuple[PydanticBaseSettingsSource, ...]:

        if cls.remote_settings_source is None:
            return (
                init_settings,
                env_settings,
                dotenv_settings,
                file_secret_settings,
            )

        return (
            init_settings,
            env_settings,
            cls.remote_settings_source(settings_cls, dotenv_settings, cls),
            dotenv_settings,
            file_secret_settings,
        )

    def _reload_settings(self) -> None:
        new_settings = self.__class__(hot_loading=False)
        for field in self.model_fields:
            setattr(self, field, getattr(new_settings, field))

    #
    def _start_monitor(self):
        """启动监控线程"""
        if self._running:
            return

        self._running = True

        # 启动守护线程
        self._monitor_thread = threading.Thread(
            target=self._monitor_loop, daemon=True, name="SettingsMonitorThread"
        )
        self._monitor_thread.start()

    def _monitor_loop(self):
        """监控循环"""
        while self._running:
            time.sleep(self.reloading_interval)
            try:
                self._reload_settings()
            except Exception as e:
                logging.error(f"Reload settings error: {e}")
