"""Snowflake ID generator module for unique distributed identifiers.

Implements Twitter's Snowflake algorithm to generate unique 64-bit IDs
across distributed systems using timestamp, datacenter, worker, and sequence.
"""

import os
import threading
import time

from plugin.link.consts import const


class Snowflake:
    """Snowflake ID generator implementing Twitter's algorithm.

    Generates unique 64-bit IDs combining timestamp, datacenter ID,
    worker ID, and sequence number for distributed systems.
    """

    def __init__(self, datacenter_id, worker_id):
        self.epoch = 1609459200000  # Custom start time, e.g., 2021-01-01 00:00:00
        self.datacenter_id = datacenter_id
        self.worker_id = worker_id
        self.sequence = 0
        self.last_timestamp = -1
        self.lock = threading.Lock()

    @staticmethod
    def _get_timestamp():
        return int(time.time() * 1000)

    def _wait_for_next_millisecond(self, last_timestamp):
        timestamp = self._get_timestamp()
        while timestamp <= last_timestamp:
            timestamp = self._get_timestamp()
        return timestamp

    def get_id(self):
        """Generate a unique Snowflake ID.

        Returns:
            int: Unique 64-bit Snowflake ID

        Raises:
            Exception: If clock moves backwards
        """
        with self.lock:
            timestamp = self._get_timestamp()

            if timestamp < self.last_timestamp:
                raise Exception(
                    "Clock moved backwards. Refusing to generate id for %d milliseconds"
                    % (self.last_timestamp - timestamp)
                )

            if timestamp == self.last_timestamp:
                self.sequence = (self.sequence + 1) & 0xFFF
                if self.sequence == 0:
                    timestamp = self._wait_for_next_millisecond(self.last_timestamp)
            else:
                self.sequence = 0

            self.last_timestamp = timestamp

            return (
                ((timestamp - self.epoch) << 22)
                | (self.datacenter_id << 17)
                | (self.worker_id << 12)
                | self.sequence
            )


def gen_id():
    """Generate a Snowflake ID using environment configuration.

    Returns:
        int: Unique Snowflake ID generated from environment settings
    """
    snowflake_client = Snowflake(
        int(os.getenv(const.DATACENTER_ID_KEY)), int(os.getenv(const.WORKER_ID_KEY))
    )
    return snowflake_client.get_id()


if __name__ == "__main__":
    import re

    print(re.compile("^tool@[0-9a-zA-Z]+$").match("tool@1232A8232"))
    id = f"{hex(gen_id())}"
    print(f"tool@{id}")
    print(f"tool@{id[2:]}")
    print(f"tool@{gen_id()}")
