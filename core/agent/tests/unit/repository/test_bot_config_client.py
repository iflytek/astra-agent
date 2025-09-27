"""
Unit tests for repository.bot_config_client
"""

import json
from typing import Any, Dict, Tuple
from unittest.mock import AsyncMock, MagicMock, Mock, patch

import pytest

from api.schemas.bot_config import BotConfig
from cache.redis_client import RedisClusterClient
from common_imports import Span
from domain.models.bot_config_table import TbBotConfig
from repository.bot_config_client import BotConfigClient
from repository.mysql_client import MysqlClient
from tests.fixtures.test_data import TestDataFactory


class TestBotConfigClient:  # pylint: disable=too-many-public-methods
    """Test cases for BotConfigClient."""

    @pytest.fixture
    def mock_span(self) -> Mock:
        """Create mock span."""
        mock_span = Mock(spec=Span)
        mock_span_context = Mock()
        mock_span_context.add_info_events = Mock()

        # Create a proper context manager mock
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_span_context)
        context_manager.__exit__ = Mock(return_value=None)
        mock_span.start = Mock(return_value=context_manager)
        return mock_span

    @pytest.fixture
    def mock_redis_client(self) -> AsyncMock:
        """Create mock Redis client."""
        mock_client = AsyncMock(spec=RedisClusterClient)
        # Configure async methods as proper AsyncMocks
        mock_client.get = AsyncMock()
        mock_client.set = AsyncMock()
        mock_client.delete = AsyncMock()
        mock_client.get_ttl = AsyncMock()
        return mock_client

    @pytest.fixture
    def mock_mysql_client(self) -> Tuple[Mock, MagicMock]:
        """Create mock MySQL client."""
        mock_client = Mock(spec=MysqlClient)
        mock_session = MagicMock()

        # Create a proper context manager mock
        context_manager = Mock()
        context_manager.__enter__ = Mock(return_value=mock_session)
        context_manager.__exit__ = Mock(return_value=None)
        mock_client.session_getter = Mock(return_value=context_manager)
        return mock_client, mock_session

    @pytest.fixture
    def bot_config_client(
        self,
        mock_span: Mock,
        mock_redis_client: AsyncMock,
        mock_mysql_client: Tuple[Mock, MagicMock],
    ) -> BotConfigClient:
        """Create BotConfigClient instance for testing."""
        mysql_client, _ = mock_mysql_client
        return BotConfigClient(
            app_id="test_app_001",
            bot_id="test_bot_001",
            span=mock_span,
            redis_client=mock_redis_client,
            mysql_client=mysql_client,
        )

    @pytest.fixture
    def sample_bot_config(self) -> Dict[str, Any]:
        """Sample bot configuration."""
        config = TestDataFactory.create_bot_config(
            bot_id="test_bot_001", bot_name="Test Bot"
        )
        # Ensure app_id matches the bot_config_client fixture
        config["app_id"] = "test_app_001"
        return config

    @pytest.mark.unit
    def test_bot_config_client_creation(
        self,
        mock_span: Mock,
        mock_redis_client: AsyncMock,
        mock_mysql_client: Tuple[Mock, MagicMock],
    ) -> None:
        """Test BotConfigClient creation with valid data."""
        # Arrange
        mysql_client, _ = mock_mysql_client

        # Act
        client = BotConfigClient(
            app_id="test_app",
            bot_id="test_bot",
            span=mock_span,
            redis_client=mock_redis_client,
            mysql_client=mysql_client,
        )

        # Assert
        assert client.app_id == "test_app"
        assert client.bot_id == "test_bot"
        assert client.span == mock_span
        assert client.redis_client == mock_redis_client
        assert client.mysql_client == mysql_client

    @pytest.mark.unit
    def test_redis_key_generation(self, bot_config_client: BotConfigClient) -> None:
        """Test Redis key generation."""
        # Act
        key = bot_config_client.redis_key()

        # Assert
        assert key == "spark_bot:bot_config:test_bot_001"

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_pull_from_redis_success(
        self, bot_config_client: BotConfigClient, sample_bot_config: Dict[str, Any]
    ) -> None:
        """Test successful pull from Redis."""
        # Arrange
        redis_value = json.dumps(sample_bot_config).encode("utf-8")

        with (
            patch.object(
                bot_config_client.redis_client, "get", new_callable=AsyncMock
            ) as mock_get,
            patch.object(
                bot_config_client.redis_client, "get_ttl", new_callable=AsyncMock
            ) as mock_get_ttl,
            patch.object(
                bot_config_client.redis_client, "set", new_callable=AsyncMock
            ) as mock_set,
        ):

            mock_get.return_value = redis_value
            mock_get_ttl.return_value = 1200
            mock_set.return_value = True

            # Act
            result = await bot_config_client.pull_from_redis(bot_config_client.span)

            # Assert
            assert result is not None
            assert isinstance(result, BotConfig)
            assert result.bot_id == "test_bot_001"
            mock_get.assert_called_once_with("spark_bot:bot_config:test_bot_001")

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_pull_from_redis_no_value(
        self, bot_config_client: BotConfigClient
    ) -> None:
        """Test pull from Redis when no value exists."""
        # Arrange
        with patch.object(
            bot_config_client.redis_client, "get", new_callable=AsyncMock
        ) as mock_get:
            mock_get.return_value = None

            # Act
            result = await bot_config_client.pull_from_redis(bot_config_client.span)

            # Assert
            assert result is None

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_pull_from_redis_invalid_json(
        self, bot_config_client: BotConfigClient
    ) -> None:
        """Test pull from Redis with invalid JSON."""
        # Arrange
        invalid_json = b"invalid json data"

        with (
            patch.object(
                bot_config_client.redis_client, "get", new_callable=AsyncMock
            ) as mock_get,
            patch.object(
                bot_config_client.redis_client, "get_ttl", new_callable=AsyncMock
            ) as mock_get_ttl,
            patch.object(
                bot_config_client.redis_client, "set", new_callable=AsyncMock
            ) as mock_set,
        ):

            mock_get.return_value = invalid_json
            mock_get_ttl.return_value = 1200
            mock_set.return_value = True

            # Act & Assert
            with pytest.raises(Exception):
                await bot_config_client.pull_from_redis(bot_config_client.span)

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_set_to_redis_success(
        self, bot_config_client: BotConfigClient
    ) -> None:
        """Test successful set to Redis."""
        # Arrange
        test_value = "test_config_value"

        with patch.object(
            bot_config_client.redis_client, "set", new_callable=AsyncMock
        ) as mock_set:
            mock_set.return_value = True

            # Act
            await bot_config_client.set_to_redis(test_value, ex=1200)

            # Assert
            mock_set.assert_called_once_with(
                "spark_bot:bot_config:test_bot_001", test_value, ex=1200
            )

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_set_to_redis_failure(
        self, bot_config_client: BotConfigClient
    ) -> None:
        """Test set to Redis failure."""
        # Arrange
        test_value = "test_config_value"

        with patch.object(
            bot_config_client.redis_client, "set", new_callable=AsyncMock
        ) as mock_set:
            mock_set.return_value = False

            # Act & Assert
            with pytest.raises(Exception):
                await bot_config_client.set_to_redis(test_value)

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_refresh_redis_ttl_with_existing_ttl(
        self, bot_config_client: BotConfigClient
    ) -> None:
        """Test refreshing Redis TTL when TTL exists."""
        # Arrange
        with (
            patch.object(
                bot_config_client.redis_client, "get_ttl", new_callable=AsyncMock
            ) as mock_get_ttl,
            patch.object(
                bot_config_client.redis_client, "set", new_callable=AsyncMock
            ) as mock_set,
        ):

            mock_get_ttl.return_value = 600  # 10 minutes
            mock_set.return_value = True

            # Act
            await bot_config_client.refresh_redis_ttl(1200, "test_value")

            # Assert
            mock_get_ttl.assert_called_once()
            mock_set.assert_called_once_with(
                "spark_bot:bot_config:test_bot_001", "test_value", ex=1200
            )

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_refresh_redis_ttl_no_ttl(
        self, bot_config_client: BotConfigClient
    ) -> None:
        """Test refreshing Redis TTL when no TTL exists."""
        # Arrange
        with (
            patch.object(
                bot_config_client.redis_client, "get_ttl", new_callable=AsyncMock
            ) as mock_get_ttl,
            patch.object(
                bot_config_client.redis_client, "set", new_callable=AsyncMock
            ) as mock_set,
        ):

            mock_get_ttl.return_value = -1  # No TTL

            # Act
            await bot_config_client.refresh_redis_ttl(1200, "test_value")

            # Assert
            mock_get_ttl.assert_called_once()
            mock_set.assert_not_called()

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_build_bot_config_from_dict(self) -> None:
        """Test building BotConfig from dictionary."""
        # Arrange
        config_dict = TestDataFactory.create_bot_config()

        # Act
        result = await BotConfigClient.build_bot_config(config_dict)

        # Assert
        assert isinstance(result, BotConfig)
        assert result.bot_id == config_dict["bot_id"]
        assert result.app_id == config_dict["app_id"]

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_build_bot_config_from_tb_bot_config(self) -> None:
        """Test building BotConfig from TbBotConfig database record."""
        # Arrange
        mock_tb_config = Mock(spec=TbBotConfig)
        mock_tb_config.app_id = "test_app_001"
        mock_tb_config.bot_id = "test_bot_001"
        mock_tb_config.knowledge_config = json.dumps({"knowledge_base_id": "kb_001"})
        mock_tb_config.model_config = json.dumps({"model_name": "gpt-3.5-turbo"})
        mock_tb_config.regular_config = json.dumps({"enabled": True})
        mock_tb_config.tool_ids = json.dumps(["tool_001"])
        mock_tb_config.mcp_server_ids = json.dumps(["mcp_001"])
        mock_tb_config.mcp_server_urls = json.dumps(["http://localhost:8080"])
        mock_tb_config.flow_ids = json.dumps(["flow_001"])

        # Act
        result = await BotConfigClient.build_bot_config(mock_tb_config)

        # Assert
        assert isinstance(result, BotConfig)
        assert result.bot_id == "test_bot_001"
        assert result.app_id == "test_app_001"
        assert len(result.tool_ids) == 1
        assert result.tool_ids[0] == "tool_001"

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_pull_from_mysql_success(
        self,
        bot_config_client: BotConfigClient,
        mock_mysql_client: Tuple[Mock, MagicMock],
        sample_bot_config: Dict[str, Any],
    ) -> None:
        """Test successful pull from MySQL."""
        # Arrange
        _, mock_session = mock_mysql_client

        mock_record = Mock(spec=TbBotConfig)
        mock_record.app_id = "test_app_001"
        mock_record.bot_id = "test_bot_001"
        mock_record.knowledge_config = json.dumps(sample_bot_config["knowledge_config"])
        mock_record.model_config = json.dumps(sample_bot_config["model_config"])
        mock_record.regular_config = json.dumps(sample_bot_config["regular_config"])
        mock_record.tool_ids = json.dumps(sample_bot_config["tool_ids"])
        mock_record.mcp_server_ids = json.dumps(sample_bot_config["mcp_server_ids"])
        mock_record.mcp_server_urls = json.dumps(sample_bot_config["mcp_server_urls"])
        mock_record.flow_ids = json.dumps(sample_bot_config["flow_ids"])

        mock_query = Mock()
        mock_session.query.return_value = mock_query
        mock_query.filter_by.return_value = mock_query
        mock_query.first.return_value = mock_record

        with patch.object(
            bot_config_client.redis_client, "set", new_callable=AsyncMock
        ) as mock_set:
            mock_set.return_value = True

            # Act
            result = await bot_config_client.pull_from_mysql(bot_config_client.span)

            # Assert
            assert result is not None
            assert isinstance(result, BotConfig)
            assert result.bot_id == "test_bot_001"
            mock_session.query.assert_called_once_with(TbBotConfig)
            mock_query.filter_by.assert_called_once_with(
                app_id="test_app_001", bot_id="test_bot_001"
            )

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_pull_from_mysql_no_record(
        self,
        bot_config_client: BotConfigClient,
        mock_mysql_client: Tuple[Mock, MagicMock],
    ) -> None:
        """Test pull from MySQL when no record exists."""
        # Arrange
        _, mock_session = mock_mysql_client

        mock_query = Mock()
        mock_session.query.return_value = mock_query
        mock_query.filter_by.return_value = mock_query
        mock_query.first.return_value = None

        # Act
        result = await bot_config_client.pull_from_mysql(bot_config_client.span)

        # Assert
        assert result is None

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_pull_success_from_redis(
        self, bot_config_client: BotConfigClient, sample_bot_config: Dict[str, Any]
    ) -> None:
        """Test successful pull operation (Redis hit)."""
        # Arrange
        mock_bot_config = BotConfig(**sample_bot_config)

        # Use patch to mock both methods at class level
        with (
            patch.object(
                bot_config_client.__class__, "pull_from_redis", new_callable=AsyncMock
            ) as mock_redis,
            patch.object(
                bot_config_client.__class__, "pull_from_mysql", new_callable=AsyncMock
            ) as mock_mysql,
        ):

            mock_redis.return_value = mock_bot_config
            mock_mysql.return_value = None

            # Act
            result = await bot_config_client.pull()

            # Assert
            assert result == mock_bot_config
            mock_redis.assert_called_once()
            # Should not call MySQL if Redis returns data
            mock_mysql.assert_not_called()

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_pull_success_from_mysql(
        self, bot_config_client: BotConfigClient, sample_bot_config: Dict[str, Any]
    ) -> None:
        """Test successful pull operation (MySQL fallback)."""
        # Arrange
        mock_bot_config = BotConfig(**sample_bot_config)

        # Use patch to mock both methods at class level
        with (
            patch.object(
                bot_config_client.__class__, "pull_from_redis", new_callable=AsyncMock
            ) as mock_redis,
            patch.object(
                bot_config_client.__class__, "pull_from_mysql", new_callable=AsyncMock
            ) as mock_mysql,
        ):

            mock_redis.return_value = None
            mock_mysql.return_value = mock_bot_config

            # Act
            result = await bot_config_client.pull()

            # Assert
            assert result == mock_bot_config
            mock_redis.assert_called_once()
            mock_mysql.assert_called_once()

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_pull_not_found(self, bot_config_client: BotConfigClient) -> None:
        """Test pull operation when config not found."""
        # Arrange
        with (
            patch(
                "repository.bot_config_client.BotConfigClient.pull_from_redis"
            ) as mock_redis,
            patch(
                "repository.bot_config_client.BotConfigClient.pull_from_mysql"
            ) as mock_mysql,
        ):

            mock_redis.return_value = None
            mock_mysql.return_value = None

            # Act & Assert
            with pytest.raises(Exception):
                await bot_config_client.pull()

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_pull_app_id_mismatch(
        self, bot_config_client: BotConfigClient, sample_bot_config: Dict[str, Any]
    ) -> None:
        """Test pull operation with app_id mismatch."""
        # Arrange
        sample_bot_config["app_id"] = "different_app"
        mock_bot_config = BotConfig(**sample_bot_config)

        with patch(
            "repository.bot_config_client.BotConfigClient.pull_from_redis"
        ) as mock_redis:
            mock_redis.return_value = mock_bot_config

            # Act & Assert
            with pytest.raises(Exception):
                await bot_config_client.pull()

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_pull_raw_format(
        self, bot_config_client: BotConfigClient, sample_bot_config: Dict[str, Any]
    ) -> None:
        """Test pull operation with raw=True."""
        # Arrange
        mock_bot_config = BotConfig(**sample_bot_config)

        # Use patch to mock both methods at class level
        with (
            patch.object(
                bot_config_client.__class__, "pull_from_redis", new_callable=AsyncMock
            ) as mock_redis,
            patch.object(
                bot_config_client.__class__, "pull_from_mysql", new_callable=AsyncMock
            ) as mock_mysql,
        ):

            mock_redis.return_value = mock_bot_config
            mock_mysql.return_value = None

            # Act
            result = await bot_config_client.pull(raw=True)

            # Assert
            assert isinstance(result, dict)
            assert result["bot_id"] == "test_bot_001"

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_add_success(
        self,
        bot_config_client: BotConfigClient,
        mock_mysql_client: Tuple[Mock, MagicMock],
        sample_bot_config: Dict[str, Any],
    ) -> None:
        """Test successful add operation."""
        # Arrange
        _, mock_session = mock_mysql_client
        mock_bot_config = BotConfig(**sample_bot_config)

        with (
            patch(
                "repository.bot_config_client.BotConfigClient.pull_from_redis"
            ) as mock_redis,
            patch(
                "repository.bot_config_client.BotConfigClient.pull_from_mysql"
            ) as mock_mysql,
            patch("repository.bot_config_client.get_snowflake_id") as mock_snowflake,
        ):

            mock_redis.return_value = None
            mock_mysql.return_value = None
            mock_snowflake.return_value = 123456789

            # Act
            result = await bot_config_client.add(mock_bot_config)

            # Assert
            assert result == mock_bot_config
            mock_session.add.assert_called_once()
            # Verify TbBotConfig was created with correct data
            added_record = mock_session.add.call_args[0][0]
            assert isinstance(added_record, TbBotConfig)
            assert added_record.bot_id == "test_bot_001"

    @pytest.mark.unit
    @pytest.mark.asyncio
    async def test_add_already_exists(
        self, bot_config_client: BotConfigClient, sample_bot_config: Dict[str, Any]
    ) -> None:
        """Test add operation when config already exists."""
        # Arrange
        mock_bot_config = BotConfig(**sample_bot_config)

        with patch(
            "repository.bot_config_client.BotConfigClient.pull_from_redis"
        ) as mock_redis:
            mock_redis.return_value = mock_bot_config

            # Act & Assert
            with pytest.raises(Exception):
                await bot_config_client.add(mock_bot_config)
