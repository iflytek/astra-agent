from typing import Type

from common.otlp.trace.span import Span
from common.service.base import Service, ServiceType


class OtlpSpanService(Service):

    name: str = ServiceType.OTLP_SPAN_SERVICE  # type: ignore[assignment]

    def get_span(self) -> Type[Span]:
        return Span
