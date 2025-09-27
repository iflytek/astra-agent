"""Module for creating and querying RPA tasks."""

import os
from typing import Optional, Tuple

import httpx
from fastapi import HTTPException
from loguru import logger
from plugin.rpa.consts import const
from plugin.rpa.errors.error_code import ErrorCode
from plugin.rpa.exceptions.config_exceptions import InvalidConfigException
from plugin.rpa.utils.urls.utl_util import is_valid_url


# Create task
async def create_task(
    access_token: str,
    project_id: str,
    exec_position: Optional[str],
    params: Optional[dict],
) -> str:
    """
    Create task.
    - Return task ID.
    """
    task_create_url = os.getenv(const.XIAOWU_RPA_TASK_CREATE_URL_KEY, None)
    if not is_valid_url(task_create_url):
        logger.error(f"Invalid task creation URL: {task_create_url}")
        raise InvalidConfigException(f"Invalid task creation URL: {task_create_url}")

    header = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {access_token}",
    }
    body = {"project_id": project_id, "exec_position": exec_position, "params": params}
    async with httpx.AsyncClient() as client:
        try:
            assert task_create_url is not None
            response = await client.post(task_create_url, headers=header, json=body)
            response.raise_for_status()

            response_data = response.json()
            print(f"create task response_data:\n {response_data}\n\n")

            code = response_data.get("code", "-1")
            msg = response_data.get("msg", None)
            data = response_data.get("data", None)

            if code != "0000" or not data:
                logger.error(f"Task creation failed: {msg}")
                raise HTTPException(
                    status_code=500, detail=f"Task creation failed: {msg}"
                )

            task_id = data.get("executionId", None)
            if not task_id:
                logger.error("Task creation failed: No task ID returned")
                raise HTTPException(
                    status_code=500,
                    detail=f"Task creation failed: No task ID returned: {response_data}",
                )

            return task_id
        except httpx.HTTPStatusError as e:
            logger.error(f"Task creation failed: {e.response.text}")
            raise HTTPException(
                status_code=e.response.status_code,
                detail=f"Task creation failed: {e.response.text}",
            ) from e
        except httpx.RequestError as e:
            logger.error(f"Task creation failed: {e}")
            raise HTTPException(
                status_code=500, detail=f"Task creation failed: {e}"
            ) from e


# Query task status
async def query_task_status(
    access_token: str, task_id: str
) -> Tuple[int, str, dict] | None:
    """
    Query task status.
    - If task is completed, return task result.
    - If task is not completed, return None.
    """
    task_query_url = os.getenv(const.XIAOWU_RPA_TASK_QUERY_URL_KEY, None)
    if not is_valid_url(task_query_url):
        logger.error(f"Invalid task query URL: {task_query_url}")
        raise InvalidConfigException(f"Invalid task query URL: {task_query_url}")

    async with httpx.AsyncClient() as client:
        try:
            response = await client.get(
                url=f"{task_query_url}/{task_id}",
                headers={"Authorization": f"Bearer {access_token}"},
            )
            response.raise_for_status()

            response_data = response.json()
            print(f"query task response_data:\n {response_data}\n\n")

            code = response_data.get("code", "-1")
            msg = response_data.get("msg", None)
            data = response_data.get("data", None)
            if code != "0000" or not data:
                logger.error(f"Task status query failed: {msg}")
                raise HTTPException(
                    status_code=500, detail=f"Task status query failed: {code}:{msg}"
                )

            execution = data.get("execution", {})
            if not execution:
                logger.error("Task status query failed: No task information returned")
                raise HTTPException(
                    status_code=500,
                    detail="Task status query failed: No task information returned",
                )

            status = execution.get("status", "")
            if status in ["COMPLETED"]:
                result = execution.get("result", {})
                data = result.get("data", {})
                return ErrorCode.SUCCESS.code, ErrorCode.SUCCESS.message, data

            if status in ["FAILED"]:
                msg = execution.get("error", "")
                return (
                    ErrorCode.QUERY_TASK_ERROR.code,
                    f"{ErrorCode.QUERY_TASK_ERROR.message}: {msg}",
                    {},
                )
            if status in ["PENDING"]:
                return None

            raise HTTPException(
                status_code=500, detail=f"Unknown task status: {status}"
            )

        except httpx.RequestError as e:
            logger.error(f"Task status query failed: {e}")
            raise HTTPException(
                status_code=500, detail=f"Task status query failed: {e}"
            ) from e
