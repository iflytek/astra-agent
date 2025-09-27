from common.service.base import ServiceFactory
from common.service.settings.settings_service import SettingsService


class SettingsServiceFactory(ServiceFactory):

    def __init__(self) -> None:
        super().__init__(SettingsService)  # type: ignore[arg-type]

    def create(self) -> SettingsService:  # type: ignore[override, no-untyped-def]
        setting_service = SettingsService()  # type: ignore[assignment]
        setting_service.sync_env_file_to_environ()
        return setting_service
