import sys
import time

import uvicorn
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from api.schemas.completion_chunk import ReasonChatCompletionChunk
from api.v1.bot_config_mgr_api import bot_config_mgr_router
from api.v1.openapi import openapi_router
from api.v1.workflow_agent import workflow_agent_router

# Use unified common package import module
from common_imports import initialize_services, logger, sid_generator2
from infra.config import agent_config

logger.remove()  # Remove handler after importing logger to avoid duplicate output
handler_id = logger.add(sys.stderr, level="ERROR")  # Add a modifiable handler

app = FastAPI()


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(
    _request: Request, exc: RequestValidationError
) -> JSONResponse:
    try:
        # Safely get the first error message
        errors = exc.errors()
        err = errors[0] if errors else {}
    except (IndexError, AttributeError):
        err = exc.body or {}
    message = f"{err['type']}, {err['loc'][-1]}, {err['msg']}"
    rs = JSONResponse(
        content=ReasonChatCompletionChunk(
            code=40002,
            message=message,
            id=sid_generator2.gen(),
            choices=[],
            created=int(time.time()),
            model="",
            object="chat.completion.chunk",
        ).model_dump()
    )
    return rs


app.include_router(openapi_router)
app.include_router(workflow_agent_router)
app.include_router(bot_config_mgr_router)

if __name__ == "__main__":
    # Initialize common services (equivalent to xingchen_utils initialization)
    services_to_init = ["otlp_sid_service", "otlp_metric_service", "settings_service"]
    initialize_services(services=services_to_init)

    uvicorn_server = uvicorn.Server(
        uvicorn.Config(
            app=agent_config.uvicorn_app,
            host=agent_config.uvicorn_host,
            port=agent_config.uvicorn_port,
            workers=agent_config.uvicorn_workers,
            reload=agent_config.uvicorn_reload,
            ws_ping_interval=None,
            ws_ping_timeout=None,
        )
    )
    uvicorn_server.run()
