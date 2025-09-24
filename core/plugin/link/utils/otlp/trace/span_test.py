"""Unit tests for OTLP distributed tracing spans.

Contains test functions for validating OpenTelemetry span creation,
attribute management, nested span hierarchies, and trace correlation
functionality.
"""

import time

from opentelemetry.trace import Status as OTelStatus
from opentelemetry.trace import StatusCode
from plugin.link.utils.otlp.trace.span import Span
from plugin.link.utils.otlp.trace.trace import init_trace
from plugin.link.utils.sid.sid_generator2 import init_sid


def do_work1():
    """Create root span with attributes and nested span calls.

    Demonstrates span creation, attribute setting, status management,
    and calling nested functions to create span hierarchies.
    """
    # Create a tracer from global tracer provider
    span = Span()
    # When the 'with' code block goes out of scope, the 'span' is closed
    with span.start() as current_span:
        current_span.set_attribute("operation.value", "chain1")
        current_span.set_attribute("operation.name", "Saying hello!")
        current_span.set_attribute("operation.other-stuff", [1, 2, 3])
        current_span.set_status(OTelStatus(StatusCode.ERROR))
        current_span.set_attributes(
            attributes={"info1": "chain1 info1", "info2": "chain1 info2"}
        )
        current_span.add_error_event("error event content")
        # current_span.record_exception(ex)
        time.sleep(1)
        do_work2(current_span)


def do_work2(span: Span):
    """Create nested span with custom name.

    Args:
        span: Parent span for creating nested span context
    """
    with span.start(func_name="do_work222") as current_span:
        time.sleep(1)
        do_work3(current_span)


def do_work3(span: Span):
    """Create deeply nested span with events and attributes.

    Args:
        span: Parent span for creating nested span context
    """
    with span.start(attributes={"do work3": "do something"}) as current_span:
        time.sleep(1)
        current_span.set_attribute("test log", "do_work3")
        current_span.set_attribute("operation.name", "Saying hello!")
        current_span.get_otlp_span().add_event(
            "event test", {"event content": "success"}
        )
        print("Hello world from OpenTelemetry Python!")


def test_do_work():
    """Integration test for span tracing functionality.

    Initializes tracing infrastructure and executes nested span operations
    to validate end-to-end distributed tracing functionality.
    """
    init_trace(endpoint="172.30.209.27:4317", service_name="SparkFlow")
    init_sid("spf", "hf", "127.0.0.1", "10000")
    do_work1()
