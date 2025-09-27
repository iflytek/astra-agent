import asyncio
import json
import os
from typing import Any

import httpx
from aiohttp import ClientSession
from workflow.engine.nodes.code.executor.base_executor import BaseExecutor
from workflow.exception.e import CustomException, CustomExceptionCD
from workflow.exception.errors.err_code import CodeEnum
from workflow.exception.errors.third_api_code import ThirdApiCodeEnum
from workflow.extensions.otlp.trace.span import Span

# Maximum number of retry attempts for failed requests
MAX_RETRY_TIMES = 5


class IFlyExecutor(BaseExecutor):
    """
    Code executor using IFly remote execution service.

    Executes Python code on remote IFly infrastructure with automatic retry
    logic and error handling for network-related issues.
    """

    async def execute(
        self, language: str, code: str, timeout: int, span: Span, **kwargs: Any
    ) -> str:
        """
        Execute code using IFly remote execution service with retry logic.

        :param language: Programming language (currently only python supported)
        :param code: Code string to execute
        :param timeout: Maximum execution time in seconds
        :param span: Tracing span for logging
        :param kwargs: Additional execution parameters (app_id, uid)
        :return: Execution result as string
        :raises CustomException: If execution fails or service is unavailable
        """
        url = os.getenv("CODE_EXEC_URL", "")
        if not url:
            raise CustomException(
                err_code=CodeEnum.CODE_EXECUTION_ERROR,
                err_msg="code_exec_url not found",
                cause_error="code_exec_url not found",
            )

        runner_result = ""
        # Prepare request parameters
        params = {
            "appid": kwargs.get("app_id", ""),
            "uid": kwargs.get("uid", ""),
        }
        body = {
            "code": code,
            "timeout_sec": timeout,
        }
        span.add_info_events({"request_body": json.dumps(body, ensure_ascii=False)})

        try:
            retry_times = 0
            while True:
                retry_times += 1
                if retry_times > MAX_RETRY_TIMES:
                    raise CustomException(
                        err_code=CodeEnum.CODE_EXECUTION_ERROR,
                        err_msg="Retry attempts exceeded 5 times",
                        cause_error="Retry attempts exceeded 5 times",
                    )
                status, runner_result, resp_body, resp_body_str = (
                    await self._do_request(url, body, params, span)
                )
                if status == httpx.codes.OK:
                    break
                elif status in [httpx.codes.INTERNAL_SERVER_ERROR]:
                    span.add_info_events({"code execute result": resp_body_str})
                    resp_code = resp_body.get("code", 0)
                    # Pod is not ready yet, retry after delay
                    if (
                        resp_code
                        == ThirdApiCodeEnum.CODE_EXECUTE_POD_NOT_READY_ERROR.code
                    ):
                        await asyncio.sleep(1)
                        continue
                    stderr = resp_body.get("data", {}).get("stderr", "")
                    resp_message = resp_body.get("message", "")
                    span.add_error_event(f"stderr: {stderr}")
                    span.add_error_event(f"response message: {resp_message}")
                    err_code = (
                        CodeEnum.CODE_EXECUTION_TIMEOUT_ERROR.code
                        if resp_message.startswith(
                            "exec code error::context deadline exceeded::signal: killed"
                        )
                        else CodeEnum.CODE_EXECUTION_ERROR.code
                    )

                    raise CustomExceptionCD(
                        err_code=err_code,
                        err_msg=f"{IFlyExecutor.__remove_first_traceback_line(stderr)}",
                    )
        except Exception as err:
            if isinstance(err, CustomExceptionCD):
                raise err
            else:
                raise CustomException(
                    err_code=CodeEnum.CODE_EXECUTION_ERROR, cause_error=err
                ) from err

        return runner_result

    async def _do_request(
        self,
        url: str,
        body: dict,
        params: dict,
        span: Span,
    ) -> tuple[int, str, dict, str]:
        """
        Make HTTP request to IFly code execution service.

        :param url: Service endpoint URL
        :param body: Request body containing code and timeout
        :param params: Query parameters (app_id, uid)
        :param span: Tracing span for logging
        :return: Tuple of (status_code, result, response_body, response_body_string)
        :raises CustomExceptionCD: If request fails with non-retryable error
        """
        async with ClientSession() as session:
            async with session.post(url, json=body, params=params) as resp:
                resp_body = json.loads(await resp.text())
                resp_body_str = json.dumps(resp_body, ensure_ascii=False)
                if resp.status == httpx.codes.OK:
                    span.add_info_events({"code execute result": resp_body_str})
                    runner_result = resp_body.get("data", {}).get("stdout", "")
                    # Remove trailing newline from result
                    if isinstance(runner_result, str) and runner_result.endswith("\n"):
                        runner_result = runner_result[:-1]
                    return resp.status, runner_result, resp_body, resp_body_str
                elif resp.status in [httpx.codes.INTERNAL_SERVER_ERROR]:
                    return resp.status, "", resp_body, resp_body_str
                else:
                    span.add_error_event(f"{resp_body_str}")
                    raise CustomExceptionCD(
                        err_code=CodeEnum.CODE_EXECUTION_ERROR.value[0],
                        err_msg=f"{resp_body_str}",
                    )

    @staticmethod
    def __remove_first_traceback_line(traceback_str: str) -> str:
        """
        Remove the first occurrence of stdin traceback line from error message.

        Removes lines like 'File "<stdin>", line 15, in <module>\n' from traceback
        strings to provide cleaner error messages to users.

        :param traceback_str: String containing traceback information
        :return: String with the specified traceback line removed
        """
        if "Traceback" in traceback_str:
            start_index = traceback_str.find('File "<stdin>", line')
            if start_index != -1:
                end_index = traceback_str.find("in <module>", start_index)
                if end_index != -1:
                    traceback_str = (
                        traceback_str[:start_index]
                        + traceback_str[end_index + len("in <module>") + 1 :]
                    )
        return traceback_str
