import os
import threading

from loguru import logger
from workflow.extensions.middleware.getters import get_kafka_producer_service
from workflow.extensions.otlp.log_trace.workflow_log import WorkflowLog
from workflow.extensions.otlp.trace.span import Span


def kafka_report(
    workflow_log: WorkflowLog, span: Span, code: int = 0, message: str = "success"
) -> None:
    """
    Report workflow execution status to Kafka asynchronously.

    This function sends workflow execution logs to a Kafka topic in a separate thread
    to avoid blocking the main execution flow. The report includes the final status
    code and message of the workflow execution.

    :param workflow_log: The workflow log object containing execution details
    :param span: The tracing span for observability
    :param code: Status code indicating the execution result (default: 0 for success)
    :param message: Status message describing the execution result (default: "success")
    """

    def _report() -> None:
        """
        Internal function to perform the actual Kafka reporting.

        Sets the final status and end time for the workflow log, then attempts
        to send the log data to the configured Kafka topic.
        """
        # Set final execution status and end timestamp
        workflow_log.set_status(code=code, message=message)
        workflow_log.set_end()

        try:
            # Get Kafka topic from environment variables
            topic = os.getenv("KAFKA_TOPIC") or ""
            # Send workflow log as JSON to Kafka topic
            get_kafka_producer_service().send(topic, workflow_log.to_json())
        except Exception as err:
            logger.error("Failed to produce message: {}".format(err))

    # Create and start daemon thread for asynchronous reporting
    thread = threading.Thread(target=_report, daemon=True)
    thread.start()
