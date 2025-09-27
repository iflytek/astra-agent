"""Redis客户端单元test模块."""

import asyncio
import json
from unittest.mock import AsyncMock, Mock, patch

import pytest

from cache.redis_client import RedisClientCache, RedisClusterClient


class TestRedisClientCache:
    """RedisClientCachetest类."""

    def setup_method(self) -> None:
        """Test setup method."""
        self.cache = (
            RedisClientCache()
        )  # pylint: disable=attribute-defined-outside-init

    def test_init_empty_client(self) -> None:
        """test空客户端初始化."""
        assert self.cache.client is None

    def test_init_with_client(self) -> None:
        """test带客户端初始化."""
        mock_client = Mock()
        cache = RedisClientCache(client=mock_client)
        assert cache.client == mock_client

    def test_config_arbitrary_types_allowed(self) -> None:
        """test配置允许任意类型."""
        # Access config through model_config in Pydantic V2
        assert hasattr(self.cache, "__config__") or hasattr(
            self.cache.__class__, "model_config"
        )

    def test_cache_serialization(self) -> None:
        """test缓存序列化."""
        cache_dict = self.cache.model_dump()
        assert isinstance(cache_dict, dict)
        assert "client" in cache_dict

    def test_cache_with_mock_client_attrs(self) -> None:
        """test缓存与mock客户端属性."""
        mock_client = Mock()
        mock_client.connection_pool = Mock()
        mock_client.nodes = [{"host": "localhost", "port": 7000}]

        cache = RedisClientCache(client=mock_client)
        assert cache.client == mock_client
        assert hasattr(cache.client, "connection_pool")
        assert hasattr(cache.client, "nodes")


class TestRedisClusterClient:  # pylint: disable=too-many-public-methods
    """RedisClusterClienttest类."""

    def setup_method(self) -> None:
        """test方法初始化."""
        self.nodes = [  # pylint: disable=attribute-defined-outside-init
            {"host": "localhost", "port": 7000},
            {"host": "localhost", "port": 7001},
        ]
        # pylint: disable=attribute-defined-outside-init
        self.password = "test_password"
        self.client = RedisClusterClient(
            nodes="localhost:7000,localhost:7001", password=self.password
        )

    @pytest.mark.asyncio
    @patch("cache.redis_client.ClusterConnectionPool")
    @patch("cache.redis_client.RedisCluster")
    async def test_create_client_success(
        self,
        mock_redis_cluster: Mock,
        mock_pool: Mock,
    ) -> None:
        """test成功创建客户端."""
        # Arrange
        mock_redis = Mock()
        mock_redis.ping.return_value = True
        mock_redis_cluster.return_value = mock_redis

        # Act
        result = await self.client.create_client()

        # Assert
        assert result == mock_redis
        mock_pool.assert_called_once()
        mock_redis_cluster.assert_called_once()

    @pytest.mark.asyncio
    async def test_create_client_reuse_existing(self) -> None:
        """test复用现有客户端."""
        # Set existing client
        mock_existing_client = Mock()
        self.client._client = mock_existing_client  # pylint: disable=protected-access

        with patch(
            "cache.redis_client.RedisClusterClient.is_connected", new_callable=AsyncMock
        ) as mock_is_connected:
            mock_is_connected.return_value = True

            result = await self.client.create_client()

            # Verify reusing existing client
            assert result == mock_existing_client

    @pytest.mark.asyncio
    @patch("cache.redis_client.ClusterConnectionPool")
    @patch("cache.redis_client.RedisCluster")
    async def test_create_client_node_parsing(
        self, mock_redis_cluster: Mock, mock_pool: Mock
    ) -> None:
        """test节点解析逻辑."""
        # Arrange
        mock_redis = Mock()
        mock_redis.ping.return_value = True
        mock_redis_cluster.return_value = mock_redis

        client = RedisClusterClient(
            nodes="192.168.1.1:6379,192.168.1.2:6380", password="test"
        )

        # Act
        await client.create_client()

        # Assert
        call_args = mock_pool.call_args
        startup_nodes = call_args[1]["startup_nodes"]
        assert len(startup_nodes) == 2
        assert startup_nodes[0] == {"host": "192.168.1.1", "port": 6379}
        assert startup_nodes[1] == {"host": "192.168.1.2", "port": 6380}

    @pytest.mark.asyncio
    async def test_get_success(self) -> None:
        """test成功获取值."""
        test_key = "test_key"
        test_value = b"test_value"

        # Directly set mock client
        mock_client = Mock()
        mock_client.get.return_value = test_value
        self.client._client = mock_client  # pylint: disable=protected-access

        with patch(
            "cache.redis_client.RedisClusterClient.is_connected", new_callable=AsyncMock
        ) as mock_is_connected:
            mock_is_connected.return_value = True

            result = await self.client.get(test_key)

            assert result == test_value
            mock_client.get.assert_called_once_with(test_key)

    @pytest.mark.asyncio
    async def test_get_non_bytes_result(self) -> None:
        """test获取非bytes结果."""
        test_key = "test_key"

        # Directly set mock client
        mock_client = Mock()
        mock_client.get.return_value = "string_value"  # Non-bytes type
        self.client._client = mock_client  # pylint: disable=protected-access

        with patch(
            "cache.redis_client.RedisClusterClient.is_connected", new_callable=AsyncMock
        ) as mock_is_connected:
            mock_is_connected.return_value = True

            result = await self.client.get(test_key)

            assert result is None

    @pytest.mark.asyncio
    async def test_get_none_result(self) -> None:
        """test获取None结果."""
        test_key = "nonexistent_key"

        mock_client = Mock()
        mock_client.get.return_value = None
        self.client._client = mock_client  # pylint: disable=protected-access

        with patch(
            "cache.redis_client.RedisClusterClient.is_connected", new_callable=AsyncMock
        ) as mock_is_connected:
            mock_is_connected.return_value = True

            result = await self.client.get(test_key)

            assert result is None

    @pytest.mark.asyncio
    async def test_set_success(self) -> None:
        """test成功设置值."""
        test_key = "test_key"
        test_value = "test_value"

        # Directly set mock client
        mock_client = Mock()
        mock_client.set.return_value = True
        self.client._client = mock_client  # pylint: disable=protected-access

        with patch(
            "cache.redis_client.RedisClusterClient.is_connected", new_callable=AsyncMock
        ) as mock_is_connected:
            mock_is_connected.return_value = True

            result = await self.client.set(test_key, test_value)

            assert result is True
            mock_client.set.assert_called_once_with(
                test_key, test_value, ex=None, px=None, nx=False, xx=False
            )

    @pytest.mark.asyncio
    async def test_set_with_options(self) -> None:
        """test带选项设置值."""
        test_key = "test_key"
        test_value = "test_value"

        # Directly set mock client
        mock_client = Mock()
        mock_client.set.return_value = True
        self.client._client = mock_client  # pylint: disable=protected-access

        with patch(
            "cache.redis_client.RedisClusterClient.is_connected", new_callable=AsyncMock
        ) as mock_is_connected:
            mock_is_connected.return_value = True

            result = await self.client.set(
                test_key, test_value, ex=3600, px=None, nx=True, xx=False
            )

            assert result is True
            mock_client.set.assert_called_once_with(
                test_key, test_value, ex=3600, px=None, nx=True, xx=False
            )

    @pytest.mark.asyncio
    async def test_set_failure(self) -> None:
        """test设置失败."""
        test_key = "test_key"
        test_value = "test_value"

        mock_client = Mock()
        mock_client.set.return_value = False
        self.client._client = mock_client  # pylint: disable=protected-access

        with patch(
            "cache.redis_client.RedisClusterClient.is_connected", new_callable=AsyncMock
        ) as mock_is_connected:
            mock_is_connected.return_value = True

            result = await self.client.set(test_key, test_value)

            assert result is False

    @pytest.mark.asyncio
    async def test_delete_success(self) -> None:
        """test成功删除键."""
        test_key = "test_key"

        # Directly set mock client
        mock_client = Mock()
        mock_client.delete.return_value = 1
        self.client._client = mock_client  # pylint: disable=protected-access

        with patch(
            "cache.redis_client.RedisClusterClient.is_connected", new_callable=AsyncMock
        ) as mock_is_connected:
            mock_is_connected.return_value = True

            result = await self.client.delete(test_key)

            assert result == 1
            mock_client.delete.assert_called_once_with(test_key)

    @pytest.mark.asyncio
    async def test_delete_nonexistent_key(self) -> None:
        """test删除不存在的键."""
        test_key = "nonexistent_key"

        mock_client = Mock()
        mock_client.delete.return_value = 0
        self.client._client = mock_client  # pylint: disable=protected-access

        with patch(
            "cache.redis_client.RedisClusterClient.is_connected", new_callable=AsyncMock
        ) as mock_is_connected:
            mock_is_connected.return_value = True

            result = await self.client.delete(test_key)

            assert result == 0

    @pytest.mark.asyncio
    async def test_get_ttl_with_valid_result(self) -> None:
        """test获取有效TTL."""
        test_key = "test_key"
        expected_ttl = 3600

        # Directly set mock client
        mock_client = Mock()
        mock_client.ttl.return_value = expected_ttl
        self.client._client = mock_client  # pylint: disable=protected-access

        with patch(
            "cache.redis_client.RedisClusterClient.is_connected", new_callable=AsyncMock
        ) as mock_is_connected:
            mock_is_connected.return_value = True

            result = await self.client.get_ttl(test_key)

            assert result == expected_ttl
            mock_client.ttl.assert_called_once_with(test_key)

    @pytest.mark.asyncio
    async def test_get_ttl_with_non_int_result(self) -> None:
        """test获取非整数TTL结果."""
        test_key = "test_key"

        # Directly set mock client
        mock_client = Mock()
        mock_client.ttl.return_value = "string_ttl"  # Non-integer type
        self.client._client = mock_client  # pylint: disable=protected-access

        with patch(
            "cache.redis_client.RedisClusterClient.is_connected", new_callable=AsyncMock
        ) as mock_is_connected:
            mock_is_connected.return_value = True

            result = await self.client.get_ttl(test_key)

            assert result is None

    @pytest.mark.asyncio
    async def test_is_connected_success(self) -> None:
        """test连接检查成功."""
        mock_client = Mock()
        mock_client.ping.return_value = True

        result = await RedisClusterClient.is_connected(mock_client)

        assert result is True
        mock_client.ping.assert_called_once()

    @pytest.mark.asyncio
    async def test_is_connected_failure(self) -> None:
        """test连接检查失败."""
        mock_client = Mock()
        mock_client.ping.side_effect = Exception("Connection failed")

        # Import exception class for testing
        from exceptions.middleware_exc import (  # pylint: disable=import-outside-toplevel
            MiddlewareExc,
        )

        with pytest.raises(MiddlewareExc):
            await RedisClusterClient.is_connected(mock_client)

    @pytest.mark.asyncio
    async def test_is_connected_false_ping(self) -> None:
        """testping返回False的情况."""
        mock_client = Mock()
        mock_client.ping.return_value = False

        from exceptions.middleware_exc import (  # pylint: disable=import-outside-toplevel
            PingRedisExc,
        )

        # Redis ping normally doesn't return False, but test edge case
        result = await RedisClusterClient.is_connected(mock_client)
        assert result is True  # Source code only checks ping() doesn't throw exception

    def test_nodes_parsing(self) -> None:
        """test节点解析."""
        nodes = "192.168.1.1:6379,192.168.1.2:6380,192.168.1.3:6381"
        client = RedisClusterClient(nodes=nodes, password="test")

        # Verify node string is saved correctly
        assert client.nodes == nodes
        assert client.password == "test"

    def test_client_initialization_attributes(self) -> None:
        """test客户端初始化属性."""
        client = RedisClusterClient(nodes="localhost:6379", password="secret")

        assert client.nodes == "localhost:6379"
        assert client.password == "secret"
        assert client._client is None  # pylint: disable=protected-access

    @pytest.mark.asyncio
    async def test_concurrent_operations(self) -> None:
        """test并发操作."""
        # setup mock client
        mock_client = Mock()
        mock_client.get.return_value = b"test_value"
        mock_client.set.return_value = True
        mock_client.delete.return_value = 1
        self.client._client = mock_client  # pylint: disable=protected-access

        with patch(
            "cache.redis_client.RedisClusterClient.is_connected", new_callable=AsyncMock
        ) as mock_is_connected:
            mock_is_connected.return_value = True

            # Create concurrent tasks - handle get and set operations separately
            get_tasks = []
            set_tasks = []
            for i in range(5):
                get_tasks.append(self.client.get(f"key_{i}"))
                set_tasks.append(self.client.set(f"key_{i}", f"value_{i}", ex=3600))

            # Execute concurrent operations
            get_results = await asyncio.gather(*get_tasks)
            set_results = await asyncio.gather(*set_tasks)

            # Verify results
            assert len(get_results) == 5
            assert len(set_results) == 5
            for get_result in get_results:
                assert get_result == b"test_value"
            for set_result in set_results:
                assert set_result is True

    @pytest.mark.asyncio
    async def test_unicode_handling(self) -> None:
        """testUnicode内容处理."""
        unicode_key = "test键名🔑"
        unicode_value = "test值内容🚀"

        mock_client = Mock()
        mock_client.set.return_value = True
        mock_client.get.return_value = unicode_value.encode("utf-8")
        self.client._client = mock_client  # pylint: disable=protected-access

        with patch(
            "cache.redis_client.RedisClusterClient.is_connected", new_callable=AsyncMock
        ) as mock_is_connected:
            mock_is_connected.return_value = True

            # Set Unicode value
            set_result = await self.client.set(unicode_key, unicode_value)
            assert set_result is True

            # Get Unicode value
            get_result = await self.client.get(unicode_key)
            assert get_result == unicode_value.encode("utf-8")

    def test_config_validation(self) -> None:
        """test配置验证."""
        # Test normal configuration
        client = RedisClusterClient(nodes="127.0.0.1:6379", password="password")
        assert client.nodes == "127.0.0.1:6379"
        assert client.password == "password"
        assert client._client is None  # pylint: disable=protected-access

    @pytest.mark.asyncio
    async def test_error_handling_in_operations(self) -> None:
        """test操作中的错误处理."""
        mock_client = Mock()
        mock_client.get.side_effect = Exception("Redis error")
        self.client._client = mock_client  # pylint: disable=protected-access

        with patch(
            "cache.redis_client.RedisClusterClient.is_connected", new_callable=AsyncMock
        ) as mock_is_connected:
            mock_is_connected.return_value = True

            # Verify exception propagation
            with pytest.raises(Exception, match="Redis error"):
                await self.client.get("test_key")

    @pytest.mark.asyncio
    async def test_connection_error_propagation(self) -> None:
        """test连接错误传播."""
        mock_client = Mock()
        mock_client.ping.side_effect = ConnectionError("Network timeout")
        self.client._client = mock_client  # pylint: disable=protected-access

        from exceptions.middleware_exc import (  # pylint: disable=import-outside-toplevel
            MiddlewareExc,
        )

        with pytest.raises(MiddlewareExc):
            await self.client.get("test_key")

    @pytest.mark.asyncio
    async def test_ttl_edge_cases(self) -> None:
        """testTTL边缘情况."""
        test_cases = [
            (-2, -2),  # key does not exist
            (-1, -1),  # key exists but no expiration time
            (0, 0),  # key is about to expire
            (3600, 3600),  # Normal TTL
        ]

        mock_client = Mock()
        self.client._client = mock_client  # pylint: disable=protected-access

        with patch(
            "cache.redis_client.RedisClusterClient.is_connected", new_callable=AsyncMock
        ) as mock_is_connected:
            mock_is_connected.return_value = True

            for expected_ttl, mock_return in test_cases:
                mock_client.ttl.return_value = mock_return
                result = await self.client.get_ttl("test_key")
                assert result == expected_ttl

    @pytest.mark.asyncio
    async def test_json_data_handling(self) -> None:
        """testJSON数据处理."""
        test_data = {"name": "test", "value": 123, "list": [1, 2, 3]}
        json_str = json.dumps(test_data, ensure_ascii=False)

        mock_client = Mock()
        mock_client.set.return_value = True
        mock_client.get.return_value = json_str.encode("utf-8")
        self.client._client = mock_client  # pylint: disable=protected-access

        with patch(
            "cache.redis_client.RedisClusterClient.is_connected", new_callable=AsyncMock
        ) as mock_is_connected:
            mock_is_connected.return_value = True

            # Set JSON data
            await self.client.set("json_key", json_str)

            # Get and verify JSON data
            result = await self.client.get("json_key")
            assert result is not None
            parsed_data = json.loads(result.decode("utf-8"))
            assert parsed_data == test_data

    @pytest.mark.asyncio
    async def test_redis_cluster_configuration(self) -> None:
        """testRedis集群配置."""
        complex_nodes = "node1:7000,node2:7001,node3:7002,node4:7003"
        client = RedisClusterClient(nodes=complex_nodes, password="cluster_pass")

        with (
            patch("cache.redis_client.ClusterConnectionPool") as mock_pool,
            patch("cache.redis_client.RedisCluster") as mock_cluster,
        ):

            mock_redis = Mock()
            mock_redis.ping.return_value = True
            mock_cluster.return_value = mock_redis

            await client.create_client()

            # Verify connection pool configuration
            call_kwargs = mock_pool.call_args[1]
            startup_nodes = call_kwargs["startup_nodes"]
            assert len(startup_nodes) == 4
            assert call_kwargs["password"] == "cluster_pass"

            # Verify node parsing
            expected_nodes = [
                {"host": "node1", "port": 7000},
                {"host": "node2", "port": 7001},
                {"host": "node3", "port": 7002},
                {"host": "node4", "port": 7003},
            ]
            assert startup_nodes == expected_nodes

    @pytest.mark.asyncio
    async def test_client_reuse_optimization(self) -> None:
        """test客户端复用优化."""
        mock_client = Mock()
        mock_client.ping.return_value = True

        # First creation
        with (
            patch("cache.redis_client.ClusterConnectionPool"),
            patch("cache.redis_client.RedisCluster", return_value=mock_client),
        ):

            client1 = await self.client.create_client()
            # Second should reuse
            client2 = await self.client.create_client()

            assert client1 is client2
            # ping only called in is_connected, called once per create_client
            assert mock_client.ping.call_count >= 2

    @pytest.mark.asyncio
    async def test_network_resilience(self) -> None:
        """test网络弹性."""
        mock_client = Mock()
        # Simulate intermittent network failure
        mock_client.ping.side_effect = [
            Exception("Network timeout"),
            True,  # Retry successful
        ]

        from exceptions.middleware_exc import (  # pylint: disable=import-outside-toplevel
            MiddlewareExc,
        )

        # First connection failed
        with pytest.raises(MiddlewareExc):
            await RedisClusterClient.is_connected(mock_client)

        # Second connection successful
        result = await RedisClusterClient.is_connected(mock_client)
        assert result is True

    def test_model_serialization(self) -> None:
        """test模型序列化."""
        client_dict = self.client.model_dump()

        assert isinstance(client_dict, dict)
        assert "nodes" in client_dict
        assert "password" in client_dict
        assert client_dict["nodes"] == "localhost:7000,localhost:7001"
        assert client_dict["password"] == "test_password"

    def test_password_security(self) -> None:
        """test密码安全性."""
        sensitive_password = "super_secret_password_123!@#"
        client = RedisClusterClient(nodes="localhost:6379", password=sensitive_password)

        # Password should be stored correctly (security considerations needed in actual use)
        assert client.password == sensitive_password

        # Password also included during serialization (may need special handling in production)
        client_dict = client.model_dump()
        assert client_dict["password"] == sensitive_password
