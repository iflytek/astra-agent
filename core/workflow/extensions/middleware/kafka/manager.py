import os
from typing import Any, Optional

from confluent_kafka import Producer  # type: ignore
from loguru import logger
from workflow.extensions.middleware.base import Service
from workflow.extensions.middleware.utils import ServiceType


class KafkaProducerService(Service):
    """
    Kafka producer service wrapper that provides a high-level interface for sending messages to Kafka topics.
    Encapsulates the confluent-kafka Producer with error handling and logging capabilities.
    """

    name = ServiceType.KAFKA_PRODUCER_SERVICE

    def __init__(self, config: dict):
        """
        Initialize the Kafka producer service with the provided configuration.

        :param config: Dictionary containing Kafka producer configuration parameters
        """
        self.config = config
        self.producer = Producer(**config)

    def send(
        self,
        topic: str,
        value: str,
        callback: Optional[Any] = None,
        timeout: int = int(os.getenv("KAFKA_TIMEOUT", "10")),
    ) -> None:
        """
        Send a message to the specified Kafka topic.

        :param topic: Target Kafka topic name
        :param value: Message content (serialized JSON string)
        :param callback: Optional callback function for delivery confirmation
        :param timeout: Poll timeout in seconds for message delivery
        :raises Exception: If message sending fails
        """
        # Use default delivery report callback if none provided
        if not callback:
            callback = self._delivery_report
        try:
            # Produce message to Kafka topic
            self.producer.produce(topic=topic, value=value, callback=callback)
            # Poll for delivery confirmation
            self.producer.poll(timeout)
        except Exception as e:
            logger.error(f"Kafka message send failed: {e}")
            raise e

    def _delivery_report(self, err: Optional[Any], msg: Any) -> None:
        """
        Default callback function for Kafka message delivery confirmation.
        Logs the delivery status of sent messages.

        :param err: Error object if message delivery failed, None if successful
        :param msg: Message object containing delivery information
        """
        if err is not None:
            logger.error("Message delivery failed: {}".format(err))
        else:
            logger.info(
                "Message delivered to {} [{}]".format(msg.topic(), msg.partition())
            )
