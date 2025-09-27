import json
import os
import time
from typing import Any, Dict

from workflow.consts.database import ExecuteEnv
from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.otlp.trace.span import Span


class PGSqlConfig:
    """Configuration class for PostgreSQL database operations.

    This class holds all the necessary configuration parameters required
    for executing database operations through the PostgreSQL service.
    """

    def __init__(
        self,
        appId: str,
        apiKey: str,
        database_id: int,
        uid: str,
        spaceId: str,
        dml: str,
        env: str = ExecuteEnv.TEXT.value,
    ):
        """Initialize PostgreSQL configuration.

        :param appId: Application identifier for authentication
        :param apiKey: API key for service authentication
        :param database_id: Unique identifier of the target database
        :param uid: User identifier for the operation
        :param spaceId: Workspace or space identifier
        :param dml: Data Manipulation Language statement to execute
        :param env: Execution environment (defaults to TEXT environment)
        """
        self.appId = appId
        self.apiKey = apiKey
        self.database_id = database_id
        self.uid = uid
        self.spaceId = spaceId
        self.dml = dml
        self.env = env
        self.url = os.getenv("PGSQL_URL")


class PGSqlClient:
    """Client for executing PostgreSQL database operations.

    This client handles the communication with the PostgreSQL service,
    including authentication, request formatting, and response processing.
    """

    def __init__(self, *, config: PGSqlConfig):
        """Initialize the PostgreSQL client.

        :param config: Configuration object containing database connection parameters
        """
        self.config = config

    async def exec_dml(self, span: Span) -> Dict[str, Any]:
        """Execute Data Manipulation Language (DML) statement.

        Sends a POST request to the PostgreSQL service with the configured
        DML statement and returns the execution result.

        :param span: Tracing span for monitoring and logging
        :return: Dictionary containing the execution result and response data
        :raises CustomException: If environment variable is not set or request fails
        """
        # Validate that the PostgreSQL service URL is configured
        url = self.config.url
        if url is None:
            raise CustomException(
                CodeEnum.ENG_RUN_ERROR,
                err_msg="PGSQL_URL environment variable is not set",
            )
        # Prepare request payload and headers
        payload = self.payload()
        headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer ${self.config.apiKey}",
            "X-Consumer-Username": self.config.appId,
        }
        # Add space_id to payload if provided
        if self.config.spaceId:
            payload["space_id"] = self.config.spaceId
        # Start tracing span for request monitoring
        with span.start(
            func_name="exec_dml_request", add_source_function_name=True
        ) as request_span:
            # Log request details for tracing
            request_span.add_info_events({"url": url})
            request_span.add_info_events(
                {"request_data": json.dumps(payload, ensure_ascii=False)}
            )
            try:
                from aiohttp import ClientSession

                # Execute HTTP POST request to PostgreSQL service
                async with ClientSession() as session:
                    start_time = time.time()
                    async with session.post(url, headers=headers, json=payload) as resp:
                        background_json = await resp.json()
                        # Log execution time and response for monitoring
                        request_span.add_info_events(
                            {"cost_time": f"{(time.time() - start_time) * 1000}"}
                        )
                        request_span.add_info_events(
                            {
                                "response": json.dumps(
                                    background_json, ensure_ascii=False
                                )
                            }
                        )
                        # Check for service-level errors in response
                        if background_json.get("code") != 0:
                            msg = (
                                f"err code {background_json.get('code')}, "
                                f"reason {background_json.get('message')}, sid {background_json.get('sid')}"
                            )
                            request_span.add_error_event(msg)
                            raise CustomException(
                                err_code=CodeEnum.PG_SQL_REQUEST_ERROR,
                                err_msg=f"{msg}",
                            )
                        return background_json
            except CustomException as e:
                # Re-raise custom exceptions as-is
                raise e
            except Exception as e:
                # Handle unexpected errors during request execution
                err = str(e)
                request_span.add_error_event(err)
                raise CustomException(
                    err_code=CodeEnum.PG_SQL_REQUEST_ERROR,
                    err_msg=f"Database POST request failed: {err}",
                    cause_error=f"Database POST request failed: {err}",
                ) from e

    def payload(self) -> Dict[str, Any]:
        """Construct the request payload for database operations.

        Builds a dictionary containing all necessary parameters for
        the PostgreSQL service request.

        :return: Dictionary containing request parameters
        """

        # Build payload with required database operation parameters
        _payload = {
            "app_id": self.config.appId,
            "database_id": self.config.database_id,
            "uid": self.config.uid,
            "dml": self.config.dml,
            "env": self.config.env,
        }

        return _payload
