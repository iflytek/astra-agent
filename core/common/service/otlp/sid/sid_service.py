from common.otlp import sid as sid_module
from common.service.base import Service, ServiceType


class OtlpSidService(Service):

    name: str = ServiceType.OTLP_SID_SERVICE  # type: ignore[assignment]

    def sid(self) -> str:
        if isinstance(sid_module.sid_generator2, sid_module.SidGenerator2):
            return sid_module.sid_generator2.gen()
        raise Exception("sid_generator2 is not initialized")
