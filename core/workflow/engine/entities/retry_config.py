from pydantic import BaseModel
from workflow.consts.engine.error_handler import ErrorHandler


class RetryConfig(BaseModel):
    """
    Configuration for node retry mechanism.

    :param timeout: Maximum timeout in seconds for node execution
    :param should_retry: Whether to enable retry mechanism
    :param max_retries: Maximum number of retry attempts
    :param error_strategy: Error handling strategy when retry fails
    :param custom_output: Custom output to return when retry fails
    """

    timeout: float = 60
    should_retry: bool = False
    max_retries: int = 0
    error_strategy: int = ErrorHandler.Interrupted.value
    custom_output: dict = {}
