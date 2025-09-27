import time
from contextlib import contextmanager
from datetime import datetime, timedelta
from io import StringIO
from typing import Any, Generator, Optional

import requests  # type: ignore[import-untyped]
from dotenv import dotenv_values, load_dotenv
from pydantic import BaseModel, Field


class LoginPayload(BaseModel):
    addr: Optional[str] = Field(None)
    account: Optional[str] = Field(None)
    password: Optional[str] = Field(None)


class ConfigFilter(BaseModel):
    project_name: str
    cluster_group: str
    service_name: str
    version: str
    config_file: str


class Polaris(BaseModel):
    base_url: str
    username: str
    password: str
    refresh: int = 60
    cookie_create_at: datetime = Field(
        default_factory=lambda: datetime.now() - timedelta(seconds=60 + 1)
    )
    cookie: Optional[str] = Field(default="")

    def _login_payload(self) -> LoginPayload:
        payload = LoginPayload(
            addr=self.base_url, account=self.username, password=self.password
        )
        return payload.model_dump(by_alias=False)  # type: ignore[return-value]

    def _set_cookie(self, session: requests.Session) -> None:
        url = f"{self.base_url}/api/v1/user/login"
        response = session.post(url, json=self._login_payload(), timeout=5)

        if response.status_code == 200:
            self.cookie = response.cookies.get_dict().get("JSESSIONID")
            self.cookie_create_at = datetime.now()

    @contextmanager
    def _session(self) -> Generator[requests.Session, None, None]:
        with requests.Session() as session:
            self._set_cookie(session)
            yield session

    @staticmethod
    def set_env(configs_content: str) -> None:
        load_dotenv(stream=StringIO(configs_content), override=False)

    def pull(  # type: ignore[return]
        self,
        config_filter: ConfigFilter,
        retry_count: int = 3,
        retry_interval: int = 10,
        set_env: bool = True,
        verbose: bool = False,
    ) -> dict[str, Any]:
        for i in range(retry_count):
            try:
                with self._session() as session:
                    url = f"{self.base_url}/config/download?project={config_filter.project_name}&cluster={config_filter.cluster_group}&service={config_filter.service_name}&version={config_filter.version}&configName={config_filter.config_file}"
                    response = session.get(url, cookies={"JSESSIONID": self.cookie})
                    response.raise_for_status()
                    content = response.json()["data"]["content"]
                    if set_env:
                        self.set_env(content)
                    if verbose:
                        print(content)
                    content_dict = dotenv_values(stream=StringIO(content))
                    return content_dict
            except Exception as e:
                if i == retry_count - 1:
                    raise e
            i += 1
            time.sleep(retry_interval)
