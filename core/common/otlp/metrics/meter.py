import inspect
import os
import threading
import time
from typing import TYPE_CHECKING, Dict, Optional

# import atomics
from loguru import logger

from common.otlp.args import global_otlp_metric_args
from common.otlp.ip import local_ip
from common.otlp.metrics import metric

if TYPE_CHECKING:
    from common.otlp.trace.span import Span


class AtomicCounter:
    def __init__(self) -> None:
        self.value = 0
        self.lock = threading.Lock()

    def increment(self) -> None:
        with self.lock:
            self.value += 1


counter = AtomicCounter()


class Meter:
    start_time: int
    app_id: str
    in_histogram_flag = False
    func: str
    labels: Dict[str, str] = {}

    def __init__(self, app_id: str = "", func: str = "") -> None:
        self.app_id = app_id
        self.start_time = int(int(round(time.time() * 1000)))

        if func:
            self.func = func
            return
        cf = inspect.currentframe()
        if cf is not None:
            frame = cf.f_back
            if frame is not None:
                self.func = frame.f_code.co_name
        self.labels = {}

    def set_label(self, key: str, value: str) -> None:
        self.labels[key] = value

    def _get_default_labels(self) -> dict:
        default_labels = {
            "dc": global_otlp_metric_args.otlp_dc,
            "server_host": local_ip,
            "server_name": global_otlp_metric_args.otlp_service_name,
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
        span: Optional["Span"] = None,
    ) -> None:
        """
        上报错误次数，默认上报耗时
        :param code:    错误码
        :param lables:  错误码标签
        :param count:   错误次数，默认为1
        :param is_in_histogram: 是否上报耗时，默认上报
        :return:
        """
        attr = self._get_default_labels()
        attr["ret"] = code

        if lables:
            attr.update(lables)

        if metric.counter is None:
            raise Exception("metric.counter is None")
        metric.counter.add(count, attr)
        counter.increment()
        if is_in_histogram:
            self.in_histogram(lables)
            self.in_histogram_flag = True
        logger.info(f"code: {code}, count: {counter.value}")
        if span:
            span.set_code(code)
        # print(f"code: {code}, count: {counter.value}, pid: {os.getpid()}")

    def in_success_count(self, lables: Optional[dict] = None, count: int = 1) -> None:
        """
        上报成功次数
        :param code:    错误码
        :param lables:  成功码标签
        :param count:   成功次数，默认为1
        :return:
        """
        self.in_error_count(0, lables, count)

    def in_histogram(self, lables: Optional[dict] = None) -> None:
        """
        上报耗时
        :param lables:  耗时标签
        :return:
        """
        if self.in_histogram_flag:
            return

        attr = self._get_default_labels()

        if lables:
            attr.update(lables)

        end_time = int(int(round(time.time() * 1000)))
        duration = end_time - self.start_time
        # print(f"duration: {duration}")
        if metric.histogram is None:
            raise Exception("histogram is None")
        metric.histogram.record(duration, attr)
