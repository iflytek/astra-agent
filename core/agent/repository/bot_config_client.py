import json
from typing import Any, Optional

from pydantic import BaseModel, ConfigDict, Field

from api.schemas.bot_config import (
    BotConfig,
    BotKnowledgeConfig,
    BotModelConfig,
    BotRegularConfig,
)
from api.utils.snowfake import get_snowflake_id
from cache.redis_client import BaseRedisClient, create_redis_client

# pylint: disable=no-member
from common_imports import Span
from domain.models.bot_config_table import TbBotConfig
from exceptions.agent_exc import AgentExc
from exceptions.bot_config_exc import BotConfigMgrExc
from infra import agent_config
from repository.mysql_client import MysqlClient


class BotConfigClient(BaseModel):
    app_id: str
    bot_id: str
    span: Span
    redis_client: Optional[BaseRedisClient] = Field(default=None)
    mysql_client: Optional[MysqlClient] = Field(default=None)

    model_config = ConfigDict(arbitrary_types_allowed=True)

    def model_post_init(self, __context: Any) -> None:
        """Initialize Redis and MySQL clients after instance creation."""
        if self.redis_client is None:
            self.redis_client = create_redis_client(
                cluster_addr=agent_config.REDIS_CLUSTER_ADDR,
                standalone_addr=agent_config.REDIS_ADDR,
                password=agent_config.REDIS_PASSWORD,
            )
        if self.mysql_client is None:
            self.mysql_client = MysqlClient(
                database_url=(
                    f"mysql+pymysql://{agent_config.MYSQL_USER}:"
                    f"{agent_config.MYSQL_PASSWORD}@{agent_config.MYSQL_HOST}:"
                    f"{agent_config.MYSQL_PORT}/{agent_config.MYSQL_DB}?charset=utf8mb4"
                )
            )

    def redis_key(self) -> str:
        return f"spark_bot:bot_config:{self.bot_id}"

    async def pull_from_redis(self, span: Span) -> Optional[BotConfig]:
        with span.start("PullFromRedis") as sp:
            assert self.redis_client is not None
            redis_value = await self.redis_client.get(self.redis_key())
            if not redis_value:
                sp.add_info_events({"redis-value": ""})
                return None

            # For keys with expiration time, reset to configured expiration
            ex_seconds = agent_config.REDIS_EXPIRE
            await self.refresh_redis_ttl(
                ex_seconds,
                (
                    redis_value.decode("utf-8")
                    if isinstance(redis_value, bytes)
                    else redis_value
                ),
            )

            try:
                config = json.loads(
                    redis_value.decode("utf-8")
                    if isinstance(redis_value, bytes)
                    else redis_value
                )
            except json.decoder.JSONDecodeError as exc:
                raise AgentExc(
                    40003,
                    "invalid bot config",
                    on=f"app_id:{self.app_id} bot_id:{self.bot_id}",
                ) from exc

            result = await self.build_bot_config(value=config)
            sp.add_info_events({"redis-value": result.model_dump_json(by_alias=True)})

            return result

    async def set_to_redis(self, value: str, ex: int | None = None) -> None:
        if ex is None:
            ex = agent_config.REDIS_EXPIRE
        assert self.redis_client is not None
        redis_set_value = await self.redis_client.set(self.redis_key(), value, ex=ex)
        if not redis_set_value:
            raise AgentExc(
                40001,
                "failed to retrieve bot config",
                on=f"app_id:{self.app_id} bot_id:{self.bot_id}",
            )

    async def refresh_redis_ttl(self, ex: int, value: str) -> None:
        assert self.redis_client is not None
        ttl_key = await self.redis_client.get_ttl(self.redis_key())
        if ttl_key is not None and ttl_key > 0:
            await self.set_to_redis(value, ex)

    @staticmethod
    async def build_bot_config(value: dict[str, Any] | TbBotConfig) -> BotConfig:
        if isinstance(value, dict):
            return BotConfig(**value)

        # Handle TbBotConfig database record with proper type conversion
        bot_config = BotConfig(
            app_id=str(value.app_id),
            bot_id=str(value.bot_id),
            knowledge_config=BotKnowledgeConfig(
                **json.loads(str(value.knowledge_config))
            ),
            model_config=BotModelConfig(**json.loads(str(value.model_config))),
            regular_config=BotRegularConfig(**json.loads(str(value.regular_config))),
            tool_ids=json.loads(str(value.tool_ids)),
            mcp_server_ids=json.loads(str(value.mcp_server_ids)),
            mcp_server_urls=json.loads(str(value.mcp_server_urls)),
            flow_ids=json.loads(str(value.flow_ids)),
        )

        return bot_config

    async def pull_from_mysql(self, span: Span) -> Optional[BotConfig]:
        with span.start("PullFromMySQL") as sp:
            assert self.mysql_client is not None
            with self.mysql_client.session_getter() as session:
                record = (
                    session.query(TbBotConfig)
                    .filter_by(app_id=self.app_id, bot_id=self.bot_id)
                    .first()
                )
                if not record:
                    sp.add_info_events({"mysql-value": ""})
                    return None
                bot_config = await self.build_bot_config(value=record)
                sp.add_info_events(
                    {"mysql-value": bot_config.model_dump_json(by_alias=True)}
                )
                # MySQL has value, write to Redis cache with configured expiration
                ex_seconds = agent_config.REDIS_EXPIRE
                await self.set_to_redis(
                    bot_config.model_dump_json(by_alias=True), ex_seconds
                )

                return bot_config

    async def pull(self, raw: bool = False) -> BotConfig | dict:
        with self.span.start("Pull") as sp:
            bot_config = await self.pull_from_redis(sp) or await self.pull_from_mysql(
                sp
            )
            if not bot_config:
                raise AgentExc(
                    40001,
                    "failed to retrieve bot config",
                    on=f"app_id:{self.app_id} bot_id:{self.bot_id}",
                )  # MySQL also didn't find any records
            if bot_config.app_id != self.app_id:
                raise AgentExc(
                    40001,
                    "failed to retrieve bot config",
                    on=f"app_id:{self.app_id} bot_id:{self.bot_id}",
                )

            if raw:
                return bot_config.model_dump(by_alias=True)

            return bot_config

    async def add(self, bot_config: BotConfig) -> BotConfig:
        with self.span.start("Add") as sp:
            value = await self.pull_from_redis(sp) or await self.pull_from_mysql(sp)
            if value:
                raise BotConfigMgrExc(
                    40053,
                    "bot config already exists, cannot create",
                    on=f"app_id:{self.app_id} bot_id:{self.bot_id}",
                )

            assert self.mysql_client is not None
            with self.mysql_client.session_getter() as session:

                record = TbBotConfig(
                    id=get_snowflake_id(),
                    app_id=bot_config.app_id,
                    bot_id=bot_config.bot_id,
                    knowledge_config=bot_config.knowledge_config.model_dump_json(),
                    model_config=bot_config.model_config_.model_dump_json(),
                    regular_config=bot_config.regular_config.model_dump_json(),
                    tool_ids=json.dumps(bot_config.tool_ids, ensure_ascii=False),
                    mcp_server_ids=json.dumps(
                        bot_config.mcp_server_ids, ensure_ascii=False
                    ),
                    mcp_server_urls=json.dumps(
                        bot_config.mcp_server_urls, ensure_ascii=False
                    ),
                    flow_ids=json.dumps(bot_config.flow_ids, ensure_ascii=False),
                    is_deleted=False,
                )
                session.add(record)

            return bot_config

    async def delete(self) -> None:
        with self.span.start("Delete") as sp:
            assert self.mysql_client is not None
            with self.mysql_client.session_getter() as session:
                value = await self.pull_from_redis(sp) or await self.pull_from_mysql(sp)
                if not value:
                    raise AgentExc(
                        40001,
                        "failed to retrieve bot config",
                        on=f"app_id:{self.app_id} bot_id:{self.bot_id}",
                    )
                if value.app_id != self.app_id:
                    raise AgentExc(
                        40001,
                        "failed to retrieve bot config",
                        on=f"app_id:{self.app_id} bot_id:{self.bot_id}",
                    )

                session.query(TbBotConfig).filter_by(
                    app_id=self.app_id, bot_id=self.bot_id
                ).delete()

                # Check if exists in Redis
                redis_value = await self.pull_from_redis(sp)

                if redis_value:
                    try:
                        assert self.redis_client is not None
                        await self.redis_client.delete(self.redis_key())
                    except Exception as e:
                        raise BotConfigMgrExc(
                            40051, "failed to delete bot config", on=str(e)
                        ) from e

    async def update(self, bot_config: BotConfig) -> BotConfig:
        with self.span.start("Update") as sp:
            assert self.mysql_client is not None
            with self.mysql_client.session_getter() as session:
                value = await self.pull_from_redis(sp) or await self.pull_from_mysql(sp)
                if not value:
                    raise AgentExc(
                        40001,
                        "failed to retrieve bot config",
                        on=f"app_id:{self.app_id} bot_id:{self.bot_id}",
                    )
                if value.app_id != self.app_id:
                    raise AgentExc(
                        40001,
                        "failed to retrieve bot config",
                        on=f"app_id:{self.app_id} bot_id:{self.bot_id}",
                    )

                record = (
                    session.query(TbBotConfig)
                    .filter_by(app_id=self.app_id, bot_id=self.bot_id)
                    .first()
                )
                if record:
                    # Update record attributes properly
                    setattr(
                        record,
                        "knowledge_config",
                        bot_config.knowledge_config.model_dump_json(),
                    )
                    setattr(
                        record,
                        "model_config",
                        bot_config.model_config_.model_dump_json(),
                    )
                    setattr(
                        record,
                        "regular_config",
                        bot_config.regular_config.model_dump_json(),
                    )
                    record.tool_ids = json.dumps(  # type: ignore
                        bot_config.tool_ids, ensure_ascii=False
                    )
                    record.mcp_server_ids = json.dumps(  # type: ignore
                        bot_config.mcp_server_ids, ensure_ascii=False
                    )
                    record.mcp_server_urls = json.dumps(  # type: ignore
                        bot_config.mcp_server_urls, ensure_ascii=False
                    )
                    record.flow_ids = json.dumps(  # type: ignore
                        bot_config.flow_ids, ensure_ascii=False
                    )

                    session.add(record)

                redis_value = await self.pull_from_redis(sp)
                if redis_value:
                    assert self.redis_client is not None
                    ttl_key = await self.redis_client.get_ttl(self.redis_key())
                    bot_config_value = bot_config.model_dump_json(by_alias=True)
                    if ttl_key == -1:
                        await self.set_to_redis(bot_config_value, ex=None)
                    elif ttl_key is not None and ttl_key > 0:
                        await self.set_to_redis(bot_config_value)

            return bot_config
