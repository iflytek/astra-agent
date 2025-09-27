from pydantic import Field
from pydantic_settings import BaseSettings

# Use unified common package import module
from common_imports import ip


class XChenUtilsConfig(BaseSettings):
    # metrics
    OTLP_ENDPOINT: str = Field(default="127.0.0.1:4317")
    OTLP_METRIC_TIMEOUT: int = Field(default=3000)
    OTLP_METRIC_EXPORT_INTERVAL_MILLIS: int = Field(default=3000)
    OTLP_METRIC_EXPORT_TIMEOUT_MILLIS: int = Field(default=3000)

    # sid
    SID_LOCAL_IP: str = Field(default=ip)

    # trace
    OTLP_TRACE_TIMEOUT: int = Field(default=3000)
    OTLP_TRACE_MAX_QUEUE_SIZE: int = Field(default=2048)
    OTLP_TRACE_SCHEDULE_DELAY_MILLIS: int = Field(default=3000)
    OTLP_TRACE_MAX_EXPORT_BATCH_SIZE: int = Field(default=500)
    OTLP_TRACE_EXPORT_TIMEOUT_MILLIS: int = Field(default=3000)

    # kafka for node tracing
    KAFKA_SERVERS: str = Field(default="")
    KAFKA_TIMEOUT: int = Field(default=10)
    KAFKA_TOPIC: str = Field(default="spark-agent-builder")
