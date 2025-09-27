from pydantic import Field
from pydantic_settings import BaseSettings


class RedisClusterConfig(BaseSettings):
    REDIS_CLUSTER_ADDR: str = Field(default="")
    REDIS_ADDR: str = Field(default="")
    REDIS_PASSWORD: str = Field(default="")
    REDIS_EXPIRE: int = Field(default=3600)


class MysqlClusterConfig(BaseSettings):
    MYSQL_HOST: str = Field(default="")
    MYSQL_PORT: str = Field(default="")
    MYSQL_USER: str = Field(default="")
    MYSQL_PASSWORD: str = Field(default="")
    MYSQL_DB: str = Field(default="")


class LinkConfig(BaseSettings):
    GET_LINK_URL: str = Field(default="")
    VERSIONS_LINK_URL: str = Field(default="")
    RUN_LINK_URL: str = Field(default="")


class WorkflowConfig(BaseSettings):
    GET_WORKFLOWS_URL: str = Field(default="")
    WORKFLOW_SSE_BASE_URL: str = Field(default="")


class KnowledgeConfig(BaseSettings):
    CHUNK_QUERY_URL: str = Field(default="")


class McpConfig(BaseSettings):
    LIST_MCP_PLUGIN_URL: str = Field(default="")
    RUN_MCP_PLUGIN_URL: str = Field(default="")


class AppAuthConfig(BaseSettings):
    APP_AUTH_HOST: str = Field(default="")
    APP_AUTH_ROUTER: str = Field(default="")
    APP_AUTH_PROT: str = Field(default="")
    APP_AUTH_API_KEY: str = Field(default="")
    APP_AUTH_SECRET: str = Field(default="")


class ElkUploadConfig(BaseSettings):
    UPLOAD_NODE_TRACE: bool = Field(default=False)
    UPLOAD_METRICS: bool = Field(default=False)


class MiddlewareConfig(
    RedisClusterConfig,
    MysqlClusterConfig,
    LinkConfig,
    WorkflowConfig,
    KnowledgeConfig,
    McpConfig,
    AppAuthConfig,
    ElkUploadConfig,
):
    pass
