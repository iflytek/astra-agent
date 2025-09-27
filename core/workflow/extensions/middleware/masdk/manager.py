import os
from typing import Any, List, Optional

from workflow.engine.callbacks.openai_types_sse import LLMGenerate
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.middleware.base import Service
from workflow.extensions.middleware.utils import ServiceType


class MASDKService(Service):
    """
    MASDK (Metrics and Authentication SDK) service implementation.

    This service provides integration with the MASDK library for metrics collection
    and authentication. It initializes the MASDK client with Polaris service discovery
    configuration and provides error handling for various MASDK-related errors.
    """

    name = ServiceType.MASDK_SERVICE

    def __init__(
        self,
        channel_list: List[str],
        strategy_type: list[str],
        polaris_url: str = "",
        polaris_project: str = "",
        polaris_group: str = "",
        polaris_service: str = "",
        polaris_version: str = "",
        rpc_config_file: Optional[str] = None,
        metrics_service_name: Optional[str] = None,
    ) -> None:
        """
        Initialize the MASDK service with configuration parameters.

        :param channel_list: List of channels for MASDK communication
        :param strategy_type: List of strategy types (e.g., ["cnt", "conc"])
        :param polaris_url: Polaris service discovery URL
        :param polaris_project: Polaris project name
        :param polaris_group: Polaris service group
        :param polaris_service: Polaris service name
        :param polaris_version: Polaris service version
        :param rpc_config_file: Optional RPC configuration file path
        :param metrics_service_name: Optional metrics service name
        """
        # Initialize MASDK only if the switch is enabled
        if not os.getenv("MASDK_SWITCH"):
            return
        from xingchen_utils.metrology_auth import MASDK  # type: ignore

        # Create MASDK instance with provided configuration
        self.ma_sdk = MASDK(
            channel_list,
            strategy_type,
            polaris_url,
            polaris_project,
            polaris_group,
            polaris_service,
            polaris_version,
            rpc_config_file,
            metrics_service_name,
        )

    def retErr(self, sid: str, log: str = "") -> Any:
        """
        Handle MASDK errors and return appropriate error responses.

        This method analyzes error logs from MASDK and maps them to specific
        error codes and messages. It handles various types of MASDK errors
        including connection errors, license limit errors, rate limit errors,
        and concurrent limit errors.

        :param sid: Session ID for the request
        :param log: Error log message from MASDK
        :return: LLMGenerate error response with appropriate error code and message
        """
        # Handle empty or None log messages
        if log == "" or log is None:
            return LLMGenerate.workflow_end_error(
                sid, CodeEnum.MASDK_CONNECT_ERROR.code, CodeEnum.MASDK_CONNECT_ERROR.msg
            )

        # Check for specific MASDK error codes in the log message
        if '"{\\"xingchen_agent_workflow\\":\\"11200\\"}"' in log:
            # License limit exceeded error
            return LLMGenerate.workflow_end_error(
                sid,
                CodeEnum.MASDK_LICC_LIMIT_ERROR.code,
                CodeEnum.MASDK_LICC_LIMIT_ERROR.msg,
            )
        elif '"{\\"xingchen_agent_workflow\\":\\"11201\\"}"' in log:
            # Over limit error
            return LLMGenerate.workflow_end_error(
                sid,
                CodeEnum.MASDK_OVER_LIMIT_ERROR.code,
                CodeEnum.MASDK_OVER_LIMIT_ERROR.msg,
            )
        elif '"{\\"xingchen_agent_workflow\\":\\"11202\\"}"' in log:
            # QPS (Queries Per Second) limit exceeded error
            return LLMGenerate.workflow_end_error(
                sid,
                CodeEnum.MASDK_OVER_QPS_LIMIT_ERROR.code,
                CodeEnum.MASDK_OVER_QPS_LIMIT_ERROR.msg,
            )
        elif '"{\\"xingchen_agent_workflow\\":\\"11203\\"}"' in log:
            # Concurrent limit exceeded error
            return LLMGenerate.workflow_end_error(
                sid,
                CodeEnum.MASDK_OVER_CONC_LIMIT_ERROR.code,
                CodeEnum.MASDK_OVER_CONC_LIMIT_ERROR.msg,
            )
        else:
            # Unknown error - return generic error with log details
            return LLMGenerate.workflow_end_error(
                sid,
                CodeEnum.MASDK_UNKNOWN_ERROR.code,
                CodeEnum.MASDK_UNKNOWN_ERROR.msg + log,
            )
