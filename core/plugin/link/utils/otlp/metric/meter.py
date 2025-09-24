"""OTLP metric meter for telemetry data collection.

Provides measurement and reporting functionality for OpenTelemetry metrics including
error counts, success counts, and performance histograms. Used for monitoring
service performance and reliability.
"""

import inspect
import os
import time

from loguru import logger
from plugin.link.consts import const
from plugin.link.utils.otlp.metric import metric
from plugin.link.utils.otlp.metric.atomic_int import AtomicCounter
from plugin.link.utils.sid.sid_generator2 import get_host_ip

counter = AtomicCounter()


class Meter:
    """Telemetry meter for measuring and reporting OTLP metrics.

    Provides methods to measure and report error counts, success rates, and performance
    timing data to OpenTelemetry collectors. Automatically tracks function execution
    time and generates appropriate telemetry attributes.

    Attributes:
        start_time: Timestamp when measurement started
        app_id: Application identifier for telemetry
        in_histogram_flag: Flag to prevent duplicate histogram reporting
        func: Function name being measured
    """

    start_time: int
    app_id: str
    # Latency reporting flag
    in_histogram_flag = False
    func: str

    def __init__(self, app_id: str = "", func: str = ""):
        self.app_id = app_id
        self.start_time = int(int(round(time.time() * 1000)))

        if func:
            self.func = func
            return
        # Get the stack frame of the calling method
        frame = inspect.currentframe().f_back
        # Get the method name of the calling method
        self.func = frame.f_code.co_name

    def _get_default_attr(self):
        return {
            "dc": os.getenv(const.OTLP_DC_KEY),
            "server_host": get_host_ip(),
            "server_name": os.getenv(const.OTLP_SERVICE_NAME_KEY),
            "app_id": self.app_id,
            "func": self.func,
            "pid": os.getpid(),
        }

    def in_error_count(
        self,
        code: int,
        lables: dict = None,
        count: int = 1,
        is_in_histogram: bool = True,
    ):
        """
        Report error count, with latency reporting by default.

        Args:
            code: Error code
            lables: Error code labels
            count: Error count, default is 1
            is_in_histogram: Whether to report latency, default is True
        """
        attr = self._get_default_attr()
        attr["ret"] = code

        if lables:
            attr.update(lables)

        metric.COUNTER.add(count, attr)
        counter.increment()
        if is_in_histogram:
            self.in_histogram(lables)
            self.in_histogram_flag = True
        logger.info(f"code: {code}, count: {counter.value}")
        # print(f"code: {code}, count: {counter.value}, pid: {os.getpid()}")

    def in_success_count(self, lables: dict = None, count: int = 1):
        """
        Report success count.

        Args:
            lables: Success code labels
            count: Success count, default is 1
        """
        self.in_error_count(0, lables, count)

    def in_histogram(self, lables: dict = None):
        """
        Report latency.

        Args:
            lables: Latency labels
        """
        if self.in_histogram_flag:
            return

        attr = self._get_default_attr()

        if lables:
            attr.update(lables)

        end_time = int(int(round(time.time() * 1000)))
        duration = end_time - self.start_time
        # print(f"duration: {duration}")
        metric.HISTOGRAM.record(duration, attr)
