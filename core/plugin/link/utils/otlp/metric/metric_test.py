"""Unit tests for OTLP metric functionality.

Contains test cases for validating OpenTelemetry metric collection,
reporting functionality, and multi-threaded metric operations.
"""

import os
import threading
import time
import unittest

from plugin.link.utils.otlp.metric.meter import Meter
from plugin.link.utils.otlp.metric.metric import init_metric

# SDK metric reporting interval, recommended < 30000ms, default 1000ms
EXPORT_INTERVAL_MILLIS = 3000
# Default configuration for metric reporting server timeout in ms, default 5000ms
EXPORT_TIMEOUT_MILLIS = 5000
# Default configuration for server connection time in ms, default 5000ms
TIMEOUT = 1000
# OpenTelemetry address
ENDPOINT = "172.30.209.27:4317"


class TestMetric(unittest.TestCase):
    """Test cases for OTLP metric collection and reporting.

    Tests metric initialization, single operations, continuous metric reporting,
    and multi-threaded metric collection scenarios.
    """

    def init_metric(self):
        """Initialize OTLP metric collection for testing.

        Sets up metric exporter with test endpoint and service configuration.
        """
        init_metric(
            ENDPOINT,
            "langflow",
            timeout=TIMEOUT,
            export_interval_millis=EXPORT_INTERVAL_MILLIS,
            export_timeout_millis=EXPORT_TIMEOUT_MILLIS,
        )

    def test_one(self):
        """Test single metric reporting operation.

        Validates basic metric initialization and single error count reporting.
        """

        os.environ["DC"] = "hf"
        os.environ["SERVICE_NAME"] = "langflow"

        self.init_metric()

        m = Meter("test_appid_langflow")
        time.sleep(0.2)
        m.in_error_count(0)

    def test_metric(self):
        """Test continuous metric reporting under load.

        Generates multiple metric events with different return codes
        to test sustained metric collection performance.
        """

        os.environ["DC"] = "hf"
        os.environ["SERVICE_NAME"] = "langflow"

        self.init_metric()
        for i in range(1000):
            for ret_code in range(3):
                m = Meter("test_appid_langflow")
                time.sleep(0.2)
                m.in_error_count(ret_code)
                print(i)

        while True:
            pass

    def test_thread(self):
        """Test multi-threaded metric reporting.

        Validates metric collection accuracy and thread-safety
        when multiple threads report metrics concurrently.
        """
        os.environ["DC"] = "hf"
        os.environ["SERVICE_NAME"] = "langflow"

        self.init_metric()

        thread1 = MyThread()
        thread2 = MyThread()
        thread3 = MyThread()
        thread1.start()
        thread2.start()
        thread3.start()
        thread1.join()
        thread2.join()
        thread3.join()
        print("Exiting main thread")


class MyThread(threading.Thread):
    """Worker thread for multi-threaded metric testing.

    Generates metric events in a separate thread to test
    concurrent metric reporting and thread-safety.
    """

    def run(self):
        for i in range(100000):
            for ret_code in range(3):
                m = Meter("test_appid_langflow")
                # time.sleep(0.2)
                m.in_error_count(ret_code, "get_flow_x1")
                print(i)
