"""
Event registry and management module for workflow system.

This module provides event registration, tracking, and management functionality
for workflow execution events including status tracking, interruption handling,
and resume data management.
"""

import asyncio
import time
from typing import Any, Dict

from pydantic import BaseModel

from workflow.consts.engine.chat_status import ChatStatus
from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.graceful_shutdown.base_shutdown_event import BaseShutdownEvent
from workflow.extensions.middleware.getters import get_cache_service
from workflow.infra.audit_system.strategy.base_strategy import AuditStrategy

# Redis key prefix for event-related data
_EVENT_PREFIX = "sparkflowV2:event"

# Global audit strategy registry for events
EVENT_AUDIT_STRATEGY: Dict[str, AuditStrategy] = {}

# Global question number index for events
EVENT_AUDIT_QUESTION_NO_IDX: Dict[str, int] = {}


class Event(BaseModel):
    """
    Event model for storing workflow execution information.
    """

    event_id: str
    app_id: str = ""
    uid: str = ""
    flow_id: str = ""
    chat_id: str = ""
    is_stream: bool = True
    status: str = ChatStatus.RUNNING.value
    timeout: int = 180
    interrupt_node: str = ""

    def get_workflow_q_name(self) -> str:
        """
        Get the workflow queue name for this event.

        :return: Formatted workflow queue name string
        """
        return f"{_EVENT_PREFIX}:{self.event_id}:flow".replace("::", ":")

    def get_node_q_name(self) -> str:
        """
        Get the node queue name for this event.

        :return: Formatted node queue name string
        """
        return f"{_EVENT_PREFIX}:{self.event_id}:{self.interrupt_node}".replace(
            "::", ":"
        )


class EventRegistry(BaseShutdownEvent):
    """
    Event registry for managing workflow execution events.
    """

    def is_cleared(self) -> bool:
        event_ids = self.get_all_event_ids()
        if event_ids:
            return False
        return True

    @classmethod
    def _event_key(cls) -> str:
        return f"{_EVENT_PREFIX}:"

    @classmethod
    def _encode(cls, event: Event) -> str:
        return event.json()

    @classmethod
    def _decode(cls, data: str) -> Event:
        return Event.parse_raw(data)

    @classmethod
    def save_event(cls, event: Event) -> None:
        """
        Save event to cache service.

        :param cls: Class itself
        :param event: Event object to save
        """
        get_cache_service().hash_set_ex(
            name=cls._event_key(),
            key=event.event_id,
            value=cls._encode(event),
            expire_time=event.timeout,
        )

    @classmethod
    def init_event(cls, event: Event) -> None:
        """
        Initialize event and save it to cache.

        :param cls: Class itself
        :param event: Event object to initialize
        :raise Exception: Raises exception if saving event fails
        """
        try:
            cls.save_event(event)
        except Exception as e:
            raise e

    @classmethod
    def get_event(cls, event_id: str) -> Event:
        """
        Get event information by event ID.

        :param cls: Class itself
        :param event_id: Event ID string
        :return: Decoded event object if found, raises exception otherwise
        """
        data = get_cache_service().hash_get(name=cls._event_key(), key=event_id)
        if not data:
            raise CustomException(err_code=CodeEnum.EVENT_REGISTRY_NOT_FOUND_ERROR)
        return cls._decode(data)

    @classmethod
    def del_event(cls, event_id: str) -> None:
        """
        Delete event by event ID.

        :param cls: Class itself for accessing class variables and methods
        :param event_id: ID of the event to delete
        """
        get_cache_service().hash_del(cls._event_key(), event_id)

    @classmethod
    def get_all_event_ids(cls) -> dict:
        """
        Get all event IDs from cache.

        :return: Dictionary containing all event IDs
        """
        return get_cache_service().hash_get_all(cls._event_key())

    @classmethod
    def update_event(cls, event_id: str, key: str, value: Any) -> None:
        """
        Update specified event attribute value and save.

        :param cls: Class itself
        :param event_id: Event ID string
        :param key: Attribute name string to update
        :param value: New attribute value
        """
        event = cls.get_event(event_id)
        if not event:
            return
        if not hasattr(event, key):
            raise ValueError(f"Event has no field named '{key}'")
        setattr(event, key, value)
        cls.save_event(event)

    @classmethod
    def on_interrupt(cls, event_id: str) -> None:
        """
        Handle event interruption.

        Get event by given event ID, if event exists,
        set its status to "interrupted" and save the event.

        :param event_id: Unique identifier of the event
        """
        event = cls.get_event(event_id)
        if event:
            event.status = ChatStatus.INTERRUPT.value
            cls.save_event(event)

    @classmethod
    def on_finished(cls, event_id: str) -> None:
        """
        Called when event is finished.

        :param cls: Class itself
        :param event_id: Unique identifier of the event
        """
        cls.del_event(event_id)

    @classmethod
    def on_interrupt_node_start(cls, event_id: str, node_id: str, timeout: int) -> None:
        """
        Called when interrupt node starts.

        :param cls: Class itself
        :param event_id: Event ID
        :param node_id: Node ID
        :param timeout: Timeout in seconds
        """
        event = cls.get_event(event_id)
        if event:
            event.interrupt_node = node_id
            event.timeout = timeout
            cls.save_event(event)

    @classmethod
    def on_interrupt_node_end(cls, event_id: str) -> None:
        """
        Handle interrupt node end event.

        :param cls: Class itself for calling class methods
        :param event_id: Event ID as string
        """
        event = cls.get_event(event_id)
        if event:
            event.interrupt_node = ""
            cls.save_event(event)

    @classmethod
    async def write_resume_data(
        cls, queue_name: str, data: str, expire_time: int = 180
    ) -> None:
        """
        Asynchronously write resume data to specified queue.

        :param queue_name: Queue name
        :param data: Data to write
        :param expire_time: Expiration time in seconds, default 180
        :return: True if successful, False otherwise
        """
        try:
            message_key = f"{queue_name}"
            metadata_key = f"{queue_name}:metadata"
            current_time = int(time.time())

            with get_cache_service().pipeline() as pipe:
                # Check if retries field exists
                pipe.hexists(metadata_key, "retries")
                result = pipe.execute()

                # Reopen pipeline to wrap all operations
                pipe = get_cache_service().pipeline()
                if not result[0]:
                    pipe.hset(metadata_key, "retries", 0)
                else:
                    pipe.hincrby(metadata_key, "retries", 1)

                pipe.hset(metadata_key, "timestamp", current_time)
                pipe.rpush(message_key, data)

                # Set expiration time
                pipe.expire(message_key, expire_time)
                pipe.expire(metadata_key, expire_time)

                pipe.execute()
        except Exception as e:
            raise e

    @classmethod
    async def fetch_resume_data(cls, queue_name: str, timeout: int = 180) -> dict:
        """
        Get message and metadata from specified queue.

        :param queue_name: Name of the queue
        :param timeout: Timeout in seconds, default 180
        :return: Dictionary containing message and metadata
        """
        try:
            cache = get_cache_service()
            message_key = f"{queue_name}"
            metadata_key = f"{queue_name}:metadata"

            # Execute synchronous blpop in thread
            result = await asyncio.to_thread(cache.blpop, message_key, timeout)

            if result and len(result) == 2:
                _, message = result
                message_str = message.decode()

                meta_result = await asyncio.to_thread(cache.hgetall_str, metadata_key)

                return {"message": message_str, "metadata": meta_result}

            err_msg = "Timeout while waiting for user response"
            raise CustomException(
                err_code=CodeEnum.EVENT_REGISTRY_NOT_FOUND_ERROR,
                err_msg=err_msg,
            )
        except Exception as e:
            raise e
