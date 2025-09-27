import json
import traceback
from contextlib import asynccontextmanager
from typing import Any, AsyncIterator, Callable, Dict, Optional

from fastapi import APIRouter, Query

from api.schemas.bot_config import BotConfig
from api.schemas.bot_config_mgr_response import GeneralResponse

# Use unified common package import module
from common_imports import Span
from exceptions.agent_exc import AgentExc, AgentInternalExc
from exceptions.bot_config_exc import BotConfigMgrExc
from repository.bot_config_client import BotConfigClient

bot_config_mgr_router = APIRouter(prefix="/agent/v1")


@asynccontextmanager
async def tracing_context(
    app_id: str, operation_name: str, input_data: Optional[Dict[str, Any]] = None
) -> AsyncIterator[Span]:
    """Unified tracing context manager"""
    span = Span(app_id=app_id)
    with span.start(operation_name) as sp:
        try:
            sp.set_attribute("app_id", app_id)
            if input_data:
                sp.add_info_events(
                    {f"{operation_name.lower()}-inputs": json.dumps(input_data)}
                )
            yield sp
        except (BotConfigMgrExc, AgentExc) as e:
            response = GeneralResponse(code=e.c, message=e.m)
            sp.add_info_events(
                {
                    f"{operation_name.lower()}-outputs": response.model_dump_json(
                        by_alias=True
                    )
                }
            )
            raise
        except (ValueError, TypeError, KeyError) as e:
            # Handle data validation and processing related exceptions
            agent_internal_exc = AgentInternalExc()
            response = GeneralResponse(
                code=agent_internal_exc.c,
                message=f"{agent_internal_exc.m}: Data processing error - {str(e)}",
            )
            sp.add_info_events(
                {
                    f"{operation_name.lower()}-outputs": response.model_dump_json(
                        by_alias=True
                    )
                }
            )
            raise BotConfigMgrExc(c=response.code, m=response.message) from e
        except Exception as e:
            # Final exception capture, log detailed information for debugging
            traceback.print_exc()
            agent_internal_exc = AgentInternalExc()
            response = GeneralResponse(
                code=agent_internal_exc.c,
                message=f"{agent_internal_exc.m}: Unexpected error - {str(e)}",
            )
            sp.add_info_events(
                {
                    f"{operation_name.lower()}-outputs": response.model_dump_json(
                        by_alias=True
                    )
                }
            )
            raise BotConfigMgrExc(c=response.code, m=response.message) from e


async def handle_bot_config_operation(
    operation_func: Callable,
    app_id: str,
    bot_id: Optional[str],
    operation_name: str,
    input_data: Optional[Dict[str, Any]] = None,
) -> GeneralResponse:
    """Unified bot config operation handler function"""
    try:
        input_dict = {"app_id": app_id}
        if bot_id:
            input_dict["bot_id"] = bot_id
        if input_data:
            input_dict.update(input_data)

        async with tracing_context(app_id, operation_name, input_dict) as sp:
            if bot_id:
                sp.set_attribute("bot_id", bot_id)

            result = await operation_func(sp)

            if isinstance(result, dict):
                response = GeneralResponse(data=result)
            else:
                response = (
                    result
                    if isinstance(result, GeneralResponse)
                    else GeneralResponse(data=result)
                )

            sp.add_info_events(
                {
                    f"{operation_name.lower()}-outputs": response.model_dump_json(
                        by_alias=True
                    )
                }
            )
            return response

    except (BotConfigMgrExc, AgentExc) as e:
        return GeneralResponse(code=e.c, message=e.m)
    except Exception as e:  # pylint: disable=broad-exception-caught
        # Keep broad exception catching here to ensure API won't crash due to
        # unknown errors
        # This is the last line of defense for Web API, ensuring always return
        # valid HTTP responses
        # Complies with microservice architecture best practices, ensuring
        # high service availability
        traceback.print_exc()
        agent_internal_exc = AgentInternalExc()
        return GeneralResponse(
            code=agent_internal_exc.c,
            message=f"{agent_internal_exc.m}: Service error - {str(e)}",
        )


@bot_config_mgr_router.post("/bot-config")
async def create_bot_config(bot_config: BotConfig) -> GeneralResponse:
    """Create new bot config"""

    async def _create_operation(sp: Span) -> BotConfig:
        return await BotConfigClient(
            app_id=bot_config.app_id, bot_id=bot_config.bot_id, span=sp
        ).add(bot_config)

    return await handle_bot_config_operation(
        operation_func=_create_operation,
        app_id=bot_config.app_id,
        bot_id=bot_config.bot_id,
        operation_name="CreateBotConfig",
        input_data=bot_config.model_dump(by_alias=True),
    )


@bot_config_mgr_router.delete("/bot-config")
async def delete_bot_config(
    app_id: str = Query(min_length=1, max_length=64),
    bot_id: str = Query(min_length=1, max_length=64),
) -> GeneralResponse:
    """Delete bot config"""

    async def _delete_operation(sp: Span) -> GeneralResponse:
        await BotConfigClient(app_id=app_id, bot_id=bot_id, span=sp).delete()
        return GeneralResponse()

    return await handle_bot_config_operation(
        operation_func=_delete_operation,
        app_id=app_id,
        bot_id=bot_id,
        operation_name="DeleteBotConfig",
    )


@bot_config_mgr_router.put("/bot-config")
async def update_bot_config(bot_config: BotConfig) -> GeneralResponse:
    """Update bot config"""

    async def _update_operation(sp: Span) -> BotConfig:
        return await BotConfigClient(
            app_id=bot_config.app_id, bot_id=bot_config.bot_id, span=sp
        ).update(bot_config)

    return await handle_bot_config_operation(
        operation_func=_update_operation,
        app_id=bot_config.app_id,
        bot_id=bot_config.bot_id,
        operation_name="UpdateBotConfig",
        input_data=bot_config.model_dump(by_alias=True),
    )


@bot_config_mgr_router.get("/bot-config")
async def get_bot_config(
    app_id: str = Query(min_length=1, max_length=64),
    bot_id: str = Query(min_length=1, max_length=64),
) -> GeneralResponse:
    """Query bot config"""

    async def _get_operation(sp: Span) -> Dict[str, Any]:
        bot_config_result = await BotConfigClient(
            app_id=app_id, bot_id=bot_id, span=sp
        ).pull(raw=True)

        # Ensure returning dict type
        if isinstance(bot_config_result, dict):
            return bot_config_result
        return bot_config_result.model_dump()

    return await handle_bot_config_operation(
        operation_func=_get_operation,
        app_id=app_id,
        bot_id=bot_id,
        operation_name="GetBotConfig",
    )
