"""Test utilities for schema validation tests."""

import threading
from typing import Any, List


def wait_for_threads_completion(threads: List[threading.Thread]) -> None:
    """Wait for all threads in the list to complete.

    Args:
        threads: List of Thread objects to wait for
    """
    for thread in threads:
        thread.join()


def validate_thread_results(results: List[Any], expected_count: int) -> None:
    """Validate that thread execution results match expectations.

    Args:
        results: List of results from thread execution
        expected_count: Expected number of results

    Raises:
        AssertionError: If validation fails
    """
    assert (
        len(results) == expected_count
    ), f"Expected {expected_count} results, got {len(results)}"
    assert all(result is not None for result in results), "Some results are None"
