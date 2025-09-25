"""pytest全局配置文件."""

import asyncio
import os
import sys
from typing import Any, Dict, Generator, List, Optional
from unittest.mock import Mock, patch

import pytest

# Add project root directory to Python path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


# Mock rediscluster module
class MockRedisCluster:
    def __init__(self, *args: Any, **kwargs: Any) -> None:
        pass

    def ping(self) -> bool:
        return True

    def get(self, key: str) -> bytes:  # pylint: disable=unused-argument
        return b"test_value"

    def set(self, key: str, value: Any) -> bool:  # pylint: disable=unused-argument
        return True

    def delete(self, key: str) -> int:  # pylint: disable=unused-argument
        return 1


class MockClusterConnectionPool:
    def __init__(self, *args: Any, **kwargs: Any) -> None:
        pass


# Mock xingchen_utils module and related components
class MockBaseExc(Exception):
    def __init__(
        self,
        code: int = 0,
        message: str = "",
        additional_message: str = "",
        **kwargs: Any,
    ) -> None:
        self.code = code
        self.message = message
        if additional_message:
            self.message = f"{message},{additional_message}"
        # Handle additional keyword arguments like "on"
        if "on" in kwargs:
            self.message = f"{self.message},{kwargs['on']}"
        super().__init__(self.message)

    def __str__(self) -> str:
        return f"{self.code}: {self.message}"

    def __repr__(self) -> str:
        return self.__str__()


class MockSpan:
    def __init__(self) -> None:
        self.sid = "test_span_id"

    def start(  # pylint: disable=unused-argument
        self, name: Optional[str] = None
    ) -> "MockSpan":
        return self

    def __enter__(self) -> "MockSpan":
        return self

    def __exit__(self, *args: Any) -> None:
        pass

    def add_info_events(self, *args: Any, **kwargs: Any) -> None:
        pass

    def set_attributes(self, *args: Any, **kwargs: Any) -> None:
        pass

    @classmethod
    def __get_pydantic_core_schema__(  # pylint: disable=unused-argument
        cls, source: Any, handler: Any
    ) -> Any:
        """为Pydantic V2提供schema支持"""
        from pydantic_core import core_schema  # pylint: disable=import-outside-toplevel

        return core_schema.any_schema()


class MockNodeData:
    def __init__(  # pylint: disable=unused-argument
        self, *args: Any, **kwargs: Any
    ) -> None:
        # Set all passed keyword arguments as attributes
        for key, value in kwargs.items():
            setattr(self, key, value)


class MockNodeDataUsage:
    def __init__(
        self, *args: Any, **kwargs: Any
    ) -> None:  # pylint: disable=unused-argument
        pass


class MockNode:
    def __init__(  # pylint: disable=unused-argument
        self, *args: Any, **kwargs: Any
    ) -> None:
        # Set all passed keyword arguments as attributes
        for key, value in kwargs.items():
            setattr(self, key, value)


class MockNodeTrace:
    def __init__(  # pylint: disable=unused-argument
        self, *args: Any, **kwargs: Any
    ) -> None:
        self.trace: List[Any] = []


# Mock metric module
class MockMeter:
    def __init__(
        self, *args: Any, **kwargs: Any
    ) -> None:  # pylint: disable=unused-argument
        pass


# Mock polaris module
class MockConfigFilter:
    def __init__(
        self, *args: Any, **kwargs: Any
    ) -> None:  # pylint: disable=unused-argument
        pass


class MockPolaris:
    def __init__(
        self, *args: Any, **kwargs: Any
    ) -> None:  # pylint: disable=unused-argument
        pass

    def get_config(  # pylint: disable=unused-argument
        self, *args: Any, **kwargs: Any
    ) -> Dict[str, Any]:
        return {}


# Mock sid module
def mock_sid_generator2(  # pylint: disable=unused-argument
    *args: Any, **kwargs: Any
) -> str:
    return "mock_sid_12345"


# Create module mock
rediscluster_mock = Mock()
rediscluster_mock.RedisCluster = MockRedisCluster
rediscluster_mock.ClusterConnectionPool = MockClusterConnectionPool
sys.modules["rediscluster"] = rediscluster_mock

xingchen_utils_mock = Mock()
sys.modules["xingchen_utils"] = xingchen_utils_mock

exceptions_mock = Mock()
sys.modules["xingchen_utils.exceptions"] = exceptions_mock

exceptions_base_mock = Mock()
exceptions_base_mock.BaseExc = MockBaseExc
sys.modules["xingchen_utils.exceptions.base"] = exceptions_base_mock

otlp_mock = Mock()
sys.modules["xingchen_utils.otlp"] = otlp_mock

trace_mock = Mock()
sys.modules["xingchen_utils.otlp.trace"] = trace_mock

span_mock = Mock()
span_mock.Span = MockSpan
sys.modules["xingchen_utils.otlp.trace.span"] = span_mock

trace_trace_mock = Mock()
trace_trace_mock.logger = Mock()
sys.modules["xingchen_utils.otlp.trace.trace"] = trace_trace_mock

node_trace_mock = Mock()
sys.modules["xingchen_utils.otlp.node_trace"] = node_trace_mock

node_mock = Mock()
node_mock.Node = MockNode
node_mock.NodeData = MockNodeData
node_mock.NodeDataUsage = MockNodeDataUsage
sys.modules["xingchen_utils.otlp.node_trace.node"] = node_mock

node_trace_trace_mock = Mock()
node_trace_trace_mock.NodeTrace = MockNodeTrace
sys.modules["xingchen_utils.otlp.node_trace.node_trace"] = node_trace_trace_mock

util_mock = Mock()
sys.modules["xingchen_utils.otlp.util"] = util_mock

ip_mock = Mock()
ip_mock.ip = "127.0.0.1"
sys.modules["xingchen_utils.otlp.util.ip"] = ip_mock

metric_mock = Mock()
sys.modules["xingchen_utils.otlp.metric"] = metric_mock

meter_mock = Mock()
meter_mock.Meter = MockMeter
sys.modules["xingchen_utils.otlp.metric.meter"] = meter_mock

sid_mock = Mock()
sys.modules["xingchen_utils.otlp.sid"] = sid_mock

sid_generator2_mock = Mock()
sid_generator2_mock.sid_generator2 = mock_sid_generator2
sys.modules["xingchen_utils.otlp.sid.sid_generator2"] = sid_generator2_mock

polaris_mock = Mock()
sys.modules["xingchen_utils.polaris"] = polaris_mock

client_mock = Mock()
client_mock.ConfigFilter = MockConfigFilter
client_mock.Polaris = MockPolaris
sys.modules["xingchen_utils.polaris.client"] = client_mock

# Mock runtime module
runtime_mock = Mock()
sys.modules["xingchen_utils.runtime"] = runtime_mock

const_mock = Mock()
const_mock.DevelopmentEnv = "development"
const_mock.ProductionEnv = "production"
sys.modules["xingchen_utils.runtime.const"] = const_mock


@pytest.fixture
def mock_agent_config() -> Generator[Mock, None, None]:
    """Mock agent配置fixture."""
    with patch("infra.agent_config") as mock_config:
        # Default configuration values
        mock_config.is_dev.return_value = True
        mock_config.default_llm_timeout = 60
        mock_config.default_llm_max_token = 10000
        mock_config.default_llm_sk = "test_sk"
        mock_config.spark_x1_model_name = "x1"
        mock_config.spark_x1_model_sk = "x1_test_sk"
        mock_config.maas_sk_auth_url = "https://test.maas.url"
        mock_config.APP_AUTH_HOST = "test.auth.host"
        mock_config.APP_AUTH_ROUTER = "/auth"
        mock_config.APP_AUTH_PROT = "https"
        mock_config.APP_AUTH_API_KEY = "test_api_key"
        mock_config.APP_AUTH_SECRET = "test_secret"

        # Redis configuration - provide standalone address for tests
        mock_config.REDIS_CLUSTER_ADDR = ""
        mock_config.REDIS_ADDR = "localhost:6379"
        mock_config.REDIS_PASSWORD = "test_password"
        mock_config.REDIS_EXPIRE = 3600

        # MySQL configuration for tests
        mock_config.MYSQL_HOST = "localhost"
        mock_config.MYSQL_PORT = "3306"
        mock_config.MYSQL_USER = "test_user"
        mock_config.MYSQL_PASSWORD = "test_password"
        mock_config.MYSQL_DB = "test_db"

        yield mock_config


@pytest.fixture
def mock_span() -> Mock:
    """Mock span fixture用于追踪test."""
    span = Mock()
    span.start = Mock()
    span.add_info_events = Mock()

    # Setup context manager
    context_manager = Mock()
    context_manager.__enter__ = Mock(return_value=span)
    context_manager.__exit__ = Mock(return_value=None)
    span.start.return_value = context_manager

    return span


@pytest.fixture
def mock_mysql_client() -> Mock:
    """Mock MySQL客户端fixture."""
    client = Mock()
    session = Mock()

    # Setup session context manager
    context_manager = Mock()
    context_manager.__enter__ = Mock(return_value=session)
    context_manager.__exit__ = Mock(return_value=None)
    client.session_getter.return_value = context_manager

    return client


@pytest.fixture
def mock_redis_client() -> Mock:
    """Mock Redis客户端fixture."""
    client = Mock()
    client.ping.return_value = True
    client.get.return_value = b"test_value"
    client.set.return_value = True
    client.delete.return_value = 1
    client.ttl.return_value = 3600
    return client


@pytest.fixture
def mock_openai_client() -> Any:
    """Mock OpenAI客户端fixture."""
    from unittest.mock import AsyncMock  # pylint: disable=import-outside-toplevel

    client = AsyncMock()
    client.chat.completions.create = AsyncMock()
    return client


@pytest.fixture
def sample_messages() -> List[Dict[str, str]]:
    """示例messagefixture."""
    return [
        {"role": "user", "content": "test用户message"},
        {"role": "assistant", "content": "test助手回复"},
    ]


@pytest.fixture
def sample_bot_config() -> Dict[str, Any]:
    """示例Bot配置fixture."""
    return {
        "app_id": "test_app",
        "bot_id": "test_bot",
        "knowledge_config": {"database": "test_db"},
        "model_config": {"model": "test_model"},
        "regular_config": {"rules": ["rule1"]},
        "tool_ids": ["tool1", "tool2"],
        "mcp_server_ids": ["mcp1"],
        "mcp_server_urls": ["https://mcp1.com"],
        "flow_ids": ["flow1"],
    }


@pytest.fixture
def sample_plugin_inputs() -> Dict[str, Any]:
    """示例插件输入fixture."""
    return {
        "query": "test查询",
        "params": {"param1": "value1"},
        "options": {"timeout": 30},
    }


# Test markers
pytest_plugins: List[str] = []

# Configure test environment variables
os.environ.setdefault("TESTING", "True")
os.environ.setdefault("PYTEST_CURRENT_TEST", "True")

# Async test configuration
asyncio.set_event_loop_policy(asyncio.DefaultEventLoopPolicy())


def pytest_configure(config: Any) -> None:  # pylint: disable=unused-argument
    """pytest配置钩子."""
    # Add custom markers
    config.addinivalue_line("markers", "unit: 单元Test markers")
    config.addinivalue_line("markers", "integration: 集成Test markers")
    config.addinivalue_line("markers", "slow: 慢速Test markers")
    config.addinivalue_line("markers", "redis: 需要Redis的test")
    config.addinivalue_line("markers", "mysql: 需要MySQL的test")


def pytest_collection_modifyitems(  # pylint: disable=unused-argument
    config: Any, items: List[Any]
) -> None:
    """modify test项目收集."""
    # Add markers for unit tests
    for item in items:
        if "unit" in str(item.fspath):
            item.add_marker(pytest.mark.unit)

        if "integration" in str(item.fspath):
            item.add_marker(pytest.mark.integration)

        # Add markers for slow tests
        if any(marker in item.name for marker in ["concurrent", "large", "timeout"]):
            item.add_marker(pytest.mark.slow)


@pytest.fixture(autouse=True)
def setup_test_environment() -> Generator[None, None, None]:
    """自动设置test环境."""
    # Setup test environment variables
    original_env = os.environ.copy()
    os.environ["TESTING"] = "True"
    os.environ["LOG_LEVEL"] = "ERROR"  # Reduce log output during testing

    yield

    # Restore environment variables
    os.environ.clear()
    os.environ.update(original_env)


@pytest.fixture
def temp_test_file(tmp_path: Any) -> str:
    """创建临时test文件fixture."""
    test_file = tmp_path / "test_file.txt"
    test_file.write_text("test文件内容", encoding="utf-8")
    return str(test_file)


@pytest.fixture
def unicode_test_data() -> Dict[str, str]:
    """Unicodetest数据fixture."""
    return {
        "chinese": "中文test数据",
        "emoji": "test数据🚀✅🔧",
        "mixed": "Mixed中英文Test数据123",
        "special_chars": "special chars: @#$%^&*()_+-=[]{}|;':\",./<>?",
    }
