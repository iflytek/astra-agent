from typing import Any

from langchain_sandbox import PyodideSandbox

from workflow.engine.nodes.code.executor.base_executor import BaseExecutor
from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.otlp.trace.span import Span


# TODO: Add deno dependency during build process
class LangchainExecutor(BaseExecutor):
    """
    Code executor using Langchain Pyodide sandbox.

    Executes Python code in a browser-based sandbox environment using Pyodide,
    providing isolation and security through the Langchain sandbox implementation.
    """

    async def execute(
        self, language: str, code: str, timeout: int, span: Span, **kwargs: Any
    ) -> str:
        """
        Execute code using Langchain Pyodide sandbox.

        :param language: Programming language (currently only python supported)
        :param code: Code string to execute
        :param timeout: Maximum execution time in seconds (not used in sandbox)
        :param span: Tracing span for logging
        :param kwargs: Additional execution parameters
        :return: Execution result as string
        :raises CustomException: If code execution fails
        """
        try:
            # Create Pyodide sandbox instance for secure code execution
            sandbox = PyodideSandbox()
            result = await sandbox.execute(code)
            if result.status == "success":
                return result.stdout if result.stdout else ""
            raise CustomException(
                err_code=CodeEnum.CODE_EXECUTION_ERROR,
                err_msg=result.stderr if result.stderr else "",
            )

        except CustomException as e:
            raise e

        except Exception as e:
            raise CustomException(
                err_code=CodeEnum.CODE_EXECUTION_ERROR,
                cause_error=e,
            ) from e
