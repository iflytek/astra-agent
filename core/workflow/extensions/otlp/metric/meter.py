import inspect
import os
import time
from typing import Any, Dict, Optional

from opentelemetry.trace import Status, StatusCode

from workflow.extensions.otlp.metric import metric
from workflow.extensions.otlp.trace.span import Span
from workflow.extensions.otlp.util.ip import ip


class Meter:
    """
    A meter class for collecting and reporting metrics including error counts and performance timing.

    This class provides functionality to track application metrics such as error counts,
    success counts, and execution time histograms for monitoring and observability purposes.
    """

    start_time: int
    app_id: str
    # Flag indicating whether histogram has been reported
    in_histogram_flag = False
    func: str
    labels: Dict[str, str] = {}

    def __init__(self, app_id: str = "", func: str = ""):
        """
        Initialize the Meter instance.

        :param app_id: Application identifier for the meter
        :param func: Function name, if not provided will be auto-detected from call stack
        """
        self.app_id = app_id
        self.start_time = int(int(round(time.time() * 1000)))

        if func:
            self.func = func
            return
        # Get the calling method's stack frame
        frame = inspect.currentframe()
        if frame is not None and frame.f_back is not None:
            # Get the calling method's name
            self.func = frame.f_back.f_code.co_name
        else:
            self.func = "unknown"
        self.labels = {}

    def set_label(self, key: str, value: str) -> None:
        """
        Set a custom label for metric reporting.

        :param key: Label key
        :param value: Label value
        """
        self.labels[key] = value

    def _get_default_labels(self) -> Dict[str, Any]:
        """
        Get default labels for metric reporting.

        :return: Dictionary containing default labels including DC, server info, app_id, function name, and process ID
        """
        default_labels = {
            "dc": os.getenv("SERVICE_LOCATION", "hf"),
            "server_host": ip,
            "server_name": os.getenv("SERVICE_NAME", "default"),
            "app_id": self.app_id,
            "func": self.func,
            "pid": os.getpid(),
        }
        if self.labels:
            default_labels.update(self.labels)
        return default_labels

    def in_error_count(
        self,
        code: int,
        lables: Optional[dict] = None,
        count: int = 1,
        is_in_histogram: bool = True,
        span: Optional[Span] = None,
    ) -> None:
        """
        Report error count metrics, with optional timing histogram.

        :param code: Error code to report
        :param lables: Additional labels for the error metric
        :param count: Number of errors to report, defaults to 1
        :param is_in_histogram: Whether to report timing histogram, defaults to True
        :param span: Optional span for tracing correlation
        """
        attr = self._get_default_labels()
        attr["ret"] = str(code)

        if lables:
            attr.update(lables)

        if metric.counter is not None:
            metric.counter.add(count, attr)
        if is_in_histogram:
            self.in_histogram(lables)
            self.in_histogram_flag = True
        if span:
            span.set_code(code)
            if code != 0:
                span.set_status(Status(StatusCode.ERROR))

    def in_success_count(self, lables: Optional[dict] = None, count: int = 1) -> None:
        """
        Report success count metrics.

        :param lables: Additional labels for the success metric
        :param count: Number of successes to report, defaults to 1
        """
        self.in_error_count(0, lables, count)

    def in_histogram(self, lables: Optional[dict] = None) -> None:
        """
        Report execution time histogram.

        :param lables: Additional labels for the timing metric
        """
        if self.in_histogram_flag:
            return

        attr = self._get_default_labels()

        if lables:
            attr.update(lables)

        end_time = int(int(round(time.time() * 1000)))
        duration = end_time - self.start_time
        # print(f"duration: {duration}")
        if metric.histogram is not None:
            metric.histogram.record(duration, attr)
