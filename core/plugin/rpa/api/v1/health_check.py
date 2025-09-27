"""RPA Service Health Check API"""

from fastapi import APIRouter

health_router = APIRouter(tags=["rpa health check api"])


@health_router.get("/ping")
async def pong() -> str:
    return "pong"
