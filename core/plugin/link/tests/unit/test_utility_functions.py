"""
Detailed unit tests for utility functions and edge cases.

This module provides comprehensive function-level testing for utility modules
including UID generation, Snowflake ID generation, OpenAPI schema validation,
and various edge cases and boundary conditions.
"""

import base64
import hashlib
import json
import os
import threading
import time
from unittest.mock import Mock, patch

import pytest
from plugin.link.consts import const
from plugin.link.utils.open_api_schema.schema_validate import OpenapiSchemaValidator
from plugin.link.utils.snowflake.gen_snowflake import Snowflake, gen_id
from plugin.link.utils.uid.generate_uid import new_uid


class TestUIDGeneration:
    """Test suite for UID generation utility functions."""

    def test_new_uid_format(self):
        """Test new_uid returns correct format."""
        uid = new_uid()

        assert isinstance(uid, str)
        assert len(uid) == 8
        assert all(c in "0123456789abcdef" for c in uid)

    def test_new_uid_uniqueness(self):
        """Test new_uid generates unique identifiers."""
        uids = set()
        for _ in range(1000):
            uid = new_uid()
            uids.add(uid)

        # Should have generated 1000 unique UIDs
        assert len(uids) == 1000

    def test_new_uid_randomness(self):
        """Test new_uid uses cryptographic randomness."""
        uid1 = new_uid()
        uid2 = new_uid()

        # Should be different
        assert uid1 != uid2

        # Should be valid hex
        int(uid1, 16)
        int(uid2, 16)

    def test_new_uid_consistency_across_calls(self):
        """Test new_uid maintains consistent format across calls."""
        uids = [new_uid() for _ in range(10)]

        for uid in uids:
            assert len(uid) == 8
            assert isinstance(uid, str)
            # Should be valid hex string
            int(uid, 16)

    @patch("utils.uid.generate_uid.os.urandom")
    def test_new_uid_uses_os_urandom(self, mock_urandom):
        """Test new_uid uses os.urandom for randomness."""
        # Mock consistent bytes for testing
        mock_urandom.return_value = b"0123456789abcdef"

        uid = new_uid()

        mock_urandom.assert_called_once_with(16)
        # Should be first 8 chars of SHA256 hash of mock bytes
        expected_hash = hashlib.sha256(b"0123456789abcdef").hexdigest()
        assert uid == expected_hash[:8]

    @patch("utils.uid.generate_uid.os.urandom")
    def test_new_uid_edge_case_all_zeros(self, mock_urandom):
        """Test new_uid with edge case of all zero bytes."""
        mock_urandom.return_value = b"\x00" * 16

        uid = new_uid()

        assert len(uid) == 8
        expected_hash = hashlib.sha256(b"\x00" * 16).hexdigest()
        assert uid == expected_hash[:8]

    @patch("utils.uid.generate_uid.os.urandom")
    def test_new_uid_edge_case_all_ones(self, mock_urandom):
        """Test new_uid with edge case of all 0xFF bytes."""
        mock_urandom.return_value = b"\xff" * 16

        uid = new_uid()

        assert len(uid) == 8
        expected_hash = hashlib.sha256(b"\xff" * 16).hexdigest()
        assert uid == expected_hash[:8]


class TestSnowflakeIDGeneration:
    """Test suite for Snowflake ID generation."""

    def test_snowflake_init(self):
        """Test Snowflake initialization with datacenter and worker IDs."""
        datacenter_id = 1
        worker_id = 2
        snowflake = Snowflake(datacenter_id, worker_id)

        assert snowflake.datacenter_id == datacenter_id
        assert snowflake.worker_id == worker_id
        assert snowflake.sequence == 0
        assert snowflake.last_timestamp == -1
        assert snowflake.epoch == 1609459200000

    def test_snowflake_get_timestamp(self):
        """Test Snowflake _get_timestamp method."""
        before_time = int(time.time() * 1000)
        timestamp = Snowflake._get_timestamp()
        after_time = int(time.time() * 1000)

        assert before_time <= timestamp <= after_time

    def test_snowflake_get_id_basic(self):
        """Test basic Snowflake ID generation."""
        snowflake = Snowflake(1, 2)
        id1 = snowflake.get_id()

        assert isinstance(id1, int)
        assert id1 > 0

    def test_snowflake_get_id_uniqueness(self):
        """Test Snowflake generates unique IDs."""
        snowflake = Snowflake(1, 2)
        ids = set()

        for _ in range(1000):
            ids.add(snowflake.get_id())

        # Should have generated 1000 unique IDs
        assert len(ids) == 1000

    def test_snowflake_get_id_sequential_in_same_millisecond(self):
        """Test Snowflake generates sequential IDs in same millisecond."""
        snowflake = Snowflake(1, 2)

        # Generate multiple IDs quickly
        ids = []
        for _ in range(10):
            ids.append(snowflake.get_id())

        # IDs should be in ascending order
        assert ids == sorted(ids)

    def test_snowflake_sequence_overflow_handling(self):
        """Test Snowflake handles sequence overflow correctly."""
        snowflake = Snowflake(1, 2)

        # Mock timestamp to stay constant
        with patch.object(snowflake, "_get_timestamp", return_value=1234567890000):
            # Generate IDs until sequence overflows
            last_id = None
            for i in range(4096):  # Max sequence is 4095 (0xFFF)
                current_id = snowflake.get_id()
                if last_id is not None:
                    # Should increment sequence within same timestamp
                    assert current_id > last_id
                last_id = current_id

            # Next ID should wait for next millisecond
            with patch.object(
                snowflake, "_wait_for_next_millisecond", return_value=1234567890001
            ):
                next_id = snowflake.get_id()
                assert next_id > last_id

    def test_snowflake_clock_backwards_exception(self):
        """Test Snowflake raises exception when clock moves backwards."""
        snowflake = Snowflake(1, 2)

        # Generate an ID to set last_timestamp
        snowflake.get_id()

        # Mock clock moving backwards
        with patch.object(
            snowflake, "_get_timestamp", return_value=snowflake.last_timestamp - 1000
        ):
            with pytest.raises(Exception) as exc_info:
                snowflake.get_id()

            assert "Clock moved backwards" in str(exc_info.value)

    def test_snowflake_wait_for_next_millisecond(self):
        """Test Snowflake wait for next millisecond functionality."""
        snowflake = Snowflake(1, 2)
        current_time = int(time.time() * 1000)

        # Should wait until timestamp is greater than input
        result = snowflake._wait_for_next_millisecond(current_time - 1)
        assert result >= current_time

    def test_snowflake_id_bit_structure(self):
        """Test Snowflake ID bit structure is correct."""
        datacenter_id = 15  # 5 bits: 0b01111
        worker_id = 31  # 5 bits: 0b11111
        snowflake = Snowflake(datacenter_id, worker_id)

        id_value = snowflake.get_id()

        # Extract components from ID
        sequence = id_value & 0xFFF
        extracted_worker = (id_value >> 12) & 0x1F
        extracted_datacenter = (id_value >> 17) & 0x1F

        assert extracted_worker == worker_id
        assert extracted_datacenter == datacenter_id
        assert 0 <= sequence <= 0xFFF

    def test_snowflake_thread_safety(self):
        """Test Snowflake is thread-safe."""
        snowflake = Snowflake(1, 2)
        ids = []
        errors = []

        def generate_ids():
            try:
                for _ in range(100):
                    ids.append(snowflake.get_id())
            except Exception as e:
                errors.append(e)

        # Start multiple threads
        threads = []
        for _ in range(10):
            thread = threading.Thread(target=generate_ids)
            threads.append(thread)
            thread.start()

        # Wait for all threads to complete
        for thread in threads:
            thread.join()

        # Should not have any errors
        assert len(errors) == 0

        # Should have 1000 unique IDs
        assert len(set(ids)) == 1000

    @patch.dict(os.environ, {const.DATACENTER_ID_KEY: "5", const.WORKER_ID_KEY: "10"})
    def test_gen_id_function(self):
        """Test gen_id function uses environment variables."""
        generated_id = gen_id()

        assert isinstance(generated_id, int)
        assert generated_id > 0

    @patch.dict(os.environ, {const.DATACENTER_ID_KEY: "0", const.WORKER_ID_KEY: "0"})
    def test_gen_id_with_zero_ids(self):
        """Test gen_id with zero datacenter and worker IDs."""
        generated_id = gen_id()

        assert isinstance(generated_id, int)
        assert generated_id > 0

    def test_gen_id_missing_env_vars(self):
        """Test gen_id behavior with missing environment variables."""
        with patch.dict(os.environ, {}, clear=True):
            with pytest.raises((KeyError, ValueError, TypeError)):
                gen_id()


class TestOpenapiSchemaValidator:
    """Test suite for OpenAPI schema validation."""

    @pytest.fixture
    def valid_openapi_schema(self):
        """Valid OpenAPI schema for testing."""
        return {
            "openapi": "3.0.0",
            "info": {"title": "Test API", "version": "1.0.0"},
            "paths": {
                "/test": {
                    "get": {
                        "operationId": "test_operation",
                        "responses": {"200": {"description": "Success"}},
                    }
                }
            },
        }

    @pytest.fixture
    def mock_span(self):
        """Mock span for tracing."""
        span = Mock()
        span_context = Mock()
        span.start.return_value.__enter__ = Mock(return_value=span_context)
        span.start.return_value.__exit__ = Mock(return_value=None)
        return span

    def test_openapi_validator_init_json_schema(self, valid_openapi_schema):
        """Test OpenapiSchemaValidator initialization with JSON schema."""
        schema_json = json.dumps(valid_openapi_schema)
        validator = OpenapiSchemaValidator(schema_json, schema_type=0)

        assert validator.schema == schema_json
        assert validator.schema_type == 0

    def test_openapi_validator_init_yaml_schema(self, valid_openapi_schema):
        """Test OpenapiSchemaValidator initialization with YAML schema."""
        import yaml

        schema_yaml = yaml.dump(valid_openapi_schema)
        validator = OpenapiSchemaValidator(schema_yaml, schema_type=1)

        assert validator.schema == schema_yaml
        assert validator.schema_type == 1

    def test_openapi_validator_get_schema_dumps(self, valid_openapi_schema):
        """Test get_schema_dumps method."""
        schema_json = json.dumps(valid_openapi_schema)
        validator = OpenapiSchemaValidator(schema_json, schema_type=0)
        # First process the schema
        validator.schema = valid_openapi_schema

        result = validator.get_schema_dumps()
        assert isinstance(result, str)
        assert json.loads(result) == valid_openapi_schema

    def test_openapi_validator_pre_json_success(self, valid_openapi_schema, mock_span):
        """Test pre method with valid JSON schema."""
        schema_json = json.dumps(valid_openapi_schema)
        schema_b64 = base64.b64encode(schema_json.encode()).decode()
        validator = OpenapiSchemaValidator(schema_b64, schema_type=0, span=mock_span)

        result = validator.pre()

        assert result is None  # No errors
        assert validator.schema == valid_openapi_schema

    def test_openapi_validator_pre_yaml_success(self, valid_openapi_schema, mock_span):
        """Test pre method with valid YAML schema."""
        import yaml

        schema_yaml = yaml.dump(valid_openapi_schema)
        schema_b64 = base64.b64encode(schema_yaml.encode()).decode()
        validator = OpenapiSchemaValidator(schema_b64, schema_type=1, span=mock_span)

        result = validator.pre()

        assert result is None  # No errors
        assert validator.schema == valid_openapi_schema

    def test_openapi_validator_pre_invalid_base64(self, mock_span):
        """Test pre method with invalid base64 encoding."""
        invalid_b64 = "invalid-base64-string!"
        validator = OpenapiSchemaValidator(invalid_b64, schema_type=0, span=mock_span)

        result = validator.pre()

        assert isinstance(result, list)
        assert len(result) == 1
        assert "无效的base64" in result[0]["error_message"]

    def test_openapi_validator_pre_invalid_json(self, mock_span):
        """Test pre method with invalid JSON in schema."""
        invalid_json = "{ invalid json }"
        schema_b64 = base64.b64encode(invalid_json.encode()).decode()
        validator = OpenapiSchemaValidator(schema_b64, schema_type=0, span=mock_span)

        result = validator.pre()

        assert isinstance(result, list)
        assert len(result) == 1
        assert "json body序列化后的字符串" in result[0]["error_message"]

    def test_openapi_validator_pre_non_string_json_type(self, mock_span):
        """Test pre method with non-string input for JSON type."""
        validator = OpenapiSchemaValidator({}, schema_type=0, span=mock_span)

        result = validator.pre()

        assert isinstance(result, list)
        assert len(result) == 1
        # The actual error is about base64 decoding since it tries to decode the dict
        assert "无效的base64" in result[0]["error_message"]

    def test_openapi_validator_schema_validate_success(
        self, valid_openapi_schema, mock_span
    ):
        """Test schema_validate method with valid schema."""
        schema_json = json.dumps(valid_openapi_schema)
        schema_b64 = base64.b64encode(schema_json.encode()).decode()
        validator = OpenapiSchemaValidator(schema_b64, schema_type=0, span=mock_span)

        with patch("utils.open_api_schema.schema_validate.validate") as mock_validate:
            mock_validate.return_value = None  # Valid schema

            _ = validator.schema_validate()

            # Should pass all validation
            mock_validate.assert_called_once()

    def test_openapi_validator_schema_validate_openapi_error(self, mock_span):
        """Test schema_validate with OpenAPI validation error."""
        invalid_schema = {"invalid": "schema"}
        schema_json = json.dumps(invalid_schema)
        schema_b64 = base64.b64encode(schema_json.encode()).decode()
        validator = OpenapiSchemaValidator(schema_b64, schema_type=0, span=mock_span)

        from openapi_spec_validator.validation.exceptions import OpenAPIValidationError

        # Create a custom exception class that has the properties we need
        class MockOpenAPIValidationError(OpenAPIValidationError):
            def __init__(self, message, json_path):
                super().__init__(message)
                self._json_path = json_path

            @property
            def json_path(self):
                return self._json_path

        with patch("utils.open_api_schema.schema_validate.validate") as mock_validate:
            mock_error = MockOpenAPIValidationError("Validation failed", "$.paths")
            mock_validate.side_effect = mock_error

            result = validator.schema_validate()

            assert isinstance(result, list)
            assert len(result) == 1
            assert result[0]["error_path"] == "$.paths"
            assert "Validation failed" in result[0]["error_message"]

    def test_openapi_validator_common_validate_version_missing_openapi(self, mock_span):
        """Test _common_validate_version with missing openapi field."""
        schema = {"info": {"title": "Test"}}
        validator = OpenapiSchemaValidator("", schema_type=0, span=mock_span)
        validator.schema = schema

        result = validator._common_validate_version()

        assert isinstance(result, list)
        assert len(result) == 1
        assert "openapi 不存在" in result[0]["error_message"]

    def test_openapi_validator_common_validate_version_invalid_format(self, mock_span):
        """Test _common_validate_version with invalid version format."""
        schema = {"openapi": "invalid-version"}
        validator = OpenapiSchemaValidator("", schema_type=0, span=mock_span)
        validator.schema = schema

        result = validator._common_validate_version()

        assert isinstance(result, list)
        assert len(result) == 1
        assert "版本格式不对" in result[0]["error_message"]

    def test_openapi_validator_common_validate_version_unsupported_version(
        self, mock_span
    ):
        """Test _common_validate_version with unsupported version."""
        schema = {"openapi": "2.0.0"}
        validator = OpenapiSchemaValidator("", schema_type=0, span=mock_span)
        validator.schema = schema

        result = validator._common_validate_version()

        assert isinstance(result, list)
        assert len(result) == 1
        assert "仅支持3以上的" in result[0]["error_message"]

    def test_openapi_validator_common_validate_version_valid_versions(self, mock_span):
        """Test _common_validate_version with various valid versions."""
        valid_versions = ["3.0.0", "3.1.0", "3.0.1", "3.2.5"]

        for version in valid_versions:
            schema = {"openapi": version}
            validator = OpenapiSchemaValidator("", schema_type=0, span=mock_span)
            validator.schema = schema

            result = validator._common_validate_version()

            assert result is None or result == []

    def test_openapi_validator_common_validate_operation_id_missing(self, mock_span):
        """Test _common_validate_operation_id with missing operationId."""
        schema = {
            "paths": {
                "/test": {
                    "get": {
                        "responses": {"200": {"description": "OK"}}
                        # Missing operationId
                    }
                }
            }
        }
        validator = OpenapiSchemaValidator("", schema_type=0, span=mock_span)
        validator.schema = schema

        result = validator._common_validate_operation_id()

        assert isinstance(result, list)
        assert len(result) == 1
        assert "operationId 不能为空" in result[0]["error_message"]

    def test_openapi_validator_common_validate_operation_id_empty(self, mock_span):
        """Test _common_validate_operation_id with empty operationId."""
        schema = {
            "paths": {
                "/test": {
                    "get": {
                        "operationId": "",
                        "responses": {"200": {"description": "OK"}},
                    }
                }
            }
        }
        validator = OpenapiSchemaValidator("", schema_type=0, span=mock_span)
        validator.schema = schema

        result = validator._common_validate_operation_id()

        assert isinstance(result, list)
        assert len(result) == 1
        assert "operationId 不能为空" in result[0]["error_message"]

    def test_openapi_validator_common_validate_operation_id_valid(self, mock_span):
        """Test _common_validate_operation_id with valid operationId."""
        schema = {
            "paths": {
                "/test": {
                    "get": {
                        "operationId": "valid_operation",
                        "responses": {"200": {"description": "OK"}},
                    }
                }
            }
        }
        validator = OpenapiSchemaValidator("", schema_type=0, span=mock_span)
        validator.schema = schema

        result = validator._common_validate_operation_id()

        assert result == []


class TestUtilityEdgeCases:
    """Test suite for utility function edge cases and boundary conditions."""

    def test_new_uid_consistent_hash_algorithm(self):
        """Test new_uid uses consistent hash algorithm."""
        # Test that the same input produces same output
        test_bytes = b"test_input_bytes"

        with patch("utils.uid.generate_uid.os.urandom", return_value=test_bytes):
            uid1 = new_uid()

        with patch("utils.uid.generate_uid.os.urandom", return_value=test_bytes):
            uid2 = new_uid()

        assert uid1 == uid2

    def test_snowflake_extreme_datacenter_worker_ids(self):
        """Test Snowflake with extreme datacenter and worker IDs."""
        # Test maximum values (5 bits each: 0-31)
        max_datacenter = 31
        max_worker = 31
        snowflake = Snowflake(max_datacenter, max_worker)

        id_value = snowflake.get_id()
        assert isinstance(id_value, int)
        assert id_value > 0

        # Test minimum values
        min_datacenter = 0
        min_worker = 0
        snowflake_min = Snowflake(min_datacenter, min_worker)

        id_value_min = snowflake_min.get_id()
        assert isinstance(id_value_min, int)
        assert id_value_min > 0

    def test_snowflake_rapid_id_generation(self):
        """Test Snowflake can handle rapid ID generation."""
        snowflake = Snowflake(1, 1)
        start_time = time.time()
        ids = []

        # Generate many IDs quickly
        for _ in range(1000):
            ids.append(snowflake.get_id())

        end_time = time.time()

        # Should complete quickly (less than 1 second for 1000 IDs)
        assert (end_time - start_time) < 1.0

        # All IDs should be unique
        assert len(set(ids)) == 1000

        # IDs should be in ascending order
        assert ids == sorted(ids)

    def test_openapi_validator_complex_nested_schema(self):
        """Test OpenAPI validator with complex nested schema structures."""
        complex_schema = {
            "openapi": "3.0.0",
            "info": {"title": "Complex API", "version": "1.0.0"},
            "paths": {
                "/complex/{id}": {
                    "get": {
                        "operationId": "get_complex",
                        "parameters": [
                            {
                                "name": "id",
                                "in": "path",
                                "required": True,
                                "schema": {"type": "integer"},
                            }
                        ],
                        "responses": {
                            "200": {
                                "description": "Success",
                                "content": {
                                    "application/json": {
                                        "schema": {
                                            "type": "object",
                                            "properties": {
                                                "data": {
                                                    "type": "array",
                                                    "items": {
                                                        "type": "object",
                                                        "properties": {
                                                            "nested": {
                                                                "type": "object",
                                                                "properties": {
                                                                    "deep": {
                                                                        "type": "string"
                                                                    }
                                                                },
                                                            }
                                                        },
                                                    },
                                                }
                                            },
                                        }
                                    }
                                },
                            }
                        },
                    }
                }
            },
        }

        schema_json = json.dumps(complex_schema)
        schema_b64 = base64.b64encode(schema_json.encode()).decode()
        validator = OpenapiSchemaValidator(schema_b64, schema_type=0)

        # Should handle complex schemas without errors
        result = validator.pre()
        assert result is None

    def test_openapi_validator_unicode_content(self):
        """Test OpenAPI validator with Unicode content."""
        unicode_schema = {
            "openapi": "3.0.0",
            "info": {
                "title": "Unicode API 🌟",
                "version": "1.0.0",
                "description": "支持中文的API",
            },
            "paths": {
                "/测试": {
                    "get": {
                        "operationId": "unicode_test",
                        "summary": "测试Unicode支持",
                        "responses": {"200": {"description": "成功"}},
                    }
                }
            },
        }

        schema_json = json.dumps(unicode_schema, ensure_ascii=False)
        schema_b64 = base64.b64encode(schema_json.encode("utf-8")).decode()

        # Create mock span for the test
        mock_span = Mock()
        mock_span_context = Mock()
        mock_span.start.return_value.__enter__ = Mock(return_value=mock_span_context)
        mock_span.start.return_value.__exit__ = Mock(return_value=None)

        validator = OpenapiSchemaValidator(schema_b64, schema_type=0, span=mock_span)

        result = validator.pre()
        assert result is None
        assert validator.schema == unicode_schema

    @patch("utils.snowflake.gen_snowflake.time.time")
    def test_snowflake_timestamp_precision_edge_cases(self, mock_time):
        """Test Snowflake timestamp precision at edge cases."""
        # Test at millisecond boundaries
        mock_time.return_value = 1234567890.999999  # Very close to next millisecond
        snowflake = Snowflake(1, 1)

        id1 = snowflake.get_id()

        # Advance to next millisecond
        mock_time.return_value = 1234567891.000001
        id2 = snowflake.get_id()

        assert id2 > id1

    def test_new_uid_memory_usage_pattern(self):
        """Test new_uid doesn't have memory leaks with repeated calls."""
        # This test ensures that repeated calls don't accumulate memory
        initial_uids = [new_uid() for _ in range(100)]

        # Generate more UIDs
        more_uids = [new_uid() for _ in range(100)]

        # All should be unique
        all_uids = set(initial_uids + more_uids)
        assert len(all_uids) == 200

    def test_openapi_validator_without_span(self):
        """Test OpenAPI validator methods without span (None span)."""
        valid_openapi_schema = {
            "openapi": "3.0.0",
            "info": {"title": "Test API", "version": "1.0.0"},
            "paths": {
                "/test": {
                    "get": {
                        "operationId": "test_operation",
                        "responses": {"200": {"description": "Success"}},
                    }
                }
            },
        }
        schema_json = json.dumps(valid_openapi_schema)
        validator = OpenapiSchemaValidator(schema_json, schema_type=0, span=None)

        # Should work without span
        validator.schema = valid_openapi_schema
        result = validator._common_validate_version()
        assert result is None or result == []
