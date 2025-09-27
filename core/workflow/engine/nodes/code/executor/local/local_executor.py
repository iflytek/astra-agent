import asyncio
import json
import multiprocessing
import traceback
import warnings
from typing import Any, Dict

from RestrictedPython import PrintCollector  # type: ignore
from RestrictedPython import (
    compile_restricted,
    limited_builtins,
    safe_builtins,
    utility_builtins,
)
from RestrictedPython.Eval import default_guarded_getattr  # type: ignore
from RestrictedPython.Eval import default_guarded_getitem

from workflow.engine.nodes.code.executor.base_executor import BaseExecutor
from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.otlp.trace.span import Span


class LocalExecutor(BaseExecutor):
    """
    Local code executor using RestrictedPython for secure execution.

    Executes Python code in a restricted environment with limited built-ins
    and forbidden modules to ensure security. Uses multiprocessing for isolation.
    """

    # Modules that are not allowed to be imported for security reasons
    NOT_ALLOWED_MODULES = {"requests", "os", "httpx"}

    async def execute(
        self, language: str, code: str, timeout: int, span: Span, **kwargs: Any
    ) -> str:
        """
        Execute code asynchronously using multiprocessing for isolation.

        :param language: Programming language (currently only python supported)
        :param code: Code string to execute
        :param timeout: Maximum execution time in seconds
        :param span: Tracing span for logging
        :param kwargs: Additional execution parameters
        :return: Execution result as string
        """
        loop = asyncio.get_running_loop()
        return await loop.run_in_executor(
            None,  # Use default thread pool
            self._execute_in_process,  # Wrapper for synchronous execution
            code,
            timeout,
        )

    def _execute_in_process(self, code: str, timeout: int) -> str:
        """
        Execute code in a separate process with timeout control.

        :param code: Code string to execute
        :param timeout: Maximum execution time in seconds
        :return: Execution result as string
        :raises CustomException: If execution times out or fails
        """
        with multiprocessing.Manager() as manager:
            result_dict = manager.dict()
            proc = multiprocessing.Process(
                target=self._safe_exec, args=(code, result_dict)
            )
            proc.start()
            proc.join(timeout)
            if proc.is_alive():
                proc.terminate()
                raise CustomException(err_code=CodeEnum.CODE_EXECUTION_TIMEOUT_ERROR)
            if "error" in result_dict:
                raise Exception(result_dict["error"])
            return result_dict.get("output", "")

    def _safe_exec(self, code: str, result_dict: dict) -> None:
        """
        Safely execute code using RestrictedPython with limited built-ins.

        :param code: Code string to execute
        :param result_dict: Shared dictionary to store execution results
        """
        try:
            # Suppress syntax warnings from RestrictedPython
            warnings.filterwarnings(
                "ignore", category=SyntaxWarning, module="RestrictedPython.compile"
            )

            locals_dict: Dict[str, Any] = {}
            restricted_globals = self._build_restricted_globals()
            code_bytes = compile_restricted(code, "<user_code>", "exec")
            exec(code_bytes, restricted_globals, locals_dict)

            result_dict["output"] = locals_dict.get("output", "")
        except Exception:
            result_dict["error"] = traceback.format_exc()

    def limited_import(self, name: str, *args: Any) -> Any:
        """
        Restricted import function that blocks dangerous modules.

        :param name: Module name to import
        :param args: Additional import arguments
        :return: Imported module
        :raises ImportError: If module is in the forbidden list
        """
        if name in self.NOT_ALLOWED_MODULES:
            raise ImportError(f"Import of module is forbidden: {name}")
        return __import__(name, *args)

    def _build_restricted_globals(self) -> dict:
        """
        Build restricted global namespace for code execution.

        Creates a safe execution environment with limited built-ins and
        custom functions to prevent security vulnerabilities.

        :return: Dictionary containing restricted global variables
        """
        custom_builtins = safe_builtins.copy()
        custom_builtins["__import__"] = self.limited_import

        restricted_globals = {
            "__builtins__": custom_builtins,
            "_utility_builtins": utility_builtins,
            "_limited_builtins": limited_builtins,
            "__name__": "__main__",
            # Replacement functions to prevent NameError
            "_print_": PrintCollector,
            "_apply_": lambda func, *args, **kwargs: func(*args, **kwargs),
            "_getattr_": default_guarded_getattr,
            "_getitem_": default_guarded_getitem,
            "_getiter_": lambda obj: iter(obj),
            "dict": dict,
            "list": list,
            "print": print,
            "set": set,
            "map": map,
            "type": type,
            "sum": sum,
            "json": json,
        }
        return restricted_globals
