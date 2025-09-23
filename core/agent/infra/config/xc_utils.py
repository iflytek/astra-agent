from pydantic import Field
from pydantic_settings import BaseSettings

# Use unified common package import module
from common_imports import ip


class XChenUtilsConfig(BaseSettings):
    # metrics
    metric_endpoint: str = Field(default="127.0.0.1:4317")
    metric_timeout: int = Field(default=3000)
    metric_export_interval_millis: int = Field(default=3000)
    metric_export_timeout_millis: int = Field(default=3000)

    # sid
    sid_sub: str = Field(default="sag")
    sid_location: str = Field(default="dx")
    sid_local_ip: str = Field(default=ip)
    sid_local_port: str = Field(default="17870")

    # trace
    trace_endpoint: str = Field(default="127.0.0.1:4317")
    trace_timeout: int = Field(default=3000)
    trace_max_queue_size: int = Field(default=2048)
    trace_schedule_delay_millis: int = Field(default=3000)
    trace_max_export_batch_size: int = Field(default=500)
    trace_export_timeout_millis: int = Field(default=3000)
