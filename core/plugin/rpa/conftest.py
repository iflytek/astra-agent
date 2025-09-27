"""Global test configuration file."""

import os
import tempfile
from typing import Any, Dict, Generator
from unittest.mock import patch

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient
from plugin.rpa.api.app import rpa_server_app


@pytest.fixture(scope="session")
def temp_env_file() -> Generator[str, None, None]:
    """Create temporary environment variable file for testing."""
    with tempfile.NamedTemporaryFile(mode="w", suffix=".env", delete=False) as f:
        f.write(
            """
LOG_LEVEL=DEBUG
LOG_PATH=./test_logs
UVICORN_WORKERS=1
UVICORN_RELOAD=false
UVICORN_WS_PING_INTERVAL=20.0
UVICORN_WS_PING_TIMEOUT=20.0
XIAOWU_RPA_TIMEOUT=300
XIAOWU_RPA_PING_INTERVAL=30
XIAOWU_RPA_TASK_QUERY_INTERVAL=10
XIAOWU_RPA_TASK_CREATE_URL=http://test.example.com/create
XIAOWU_RPA_TASK_QUERY_URL=http://test.example.com/query
"""
        )
        temp_file = f.name

    yield temp_file

    # Cleanup
    if os.path.exists(temp_file):
        os.unlink(temp_file)


@pytest.fixture(scope="session")
def mock_env_vars() -> Generator[Dict[str, str], None, None]:
    """Mock environment variables."""
    env_vars = {
        "LOG_LEVEL": "DEBUG",
        "LOG_PATH": "./test_logs",
        "UVICORN_WORKERS": "1",
        "UVICORN_RELOAD": "false",
        "UVICORN_WS_PING_INTERVAL": "20.0",
        "UVICORN_WS_PING_TIMEOUT": "20.0",
        "XIAOWU_RPA_TIMEOUT": "300",
        "XIAOWU_RPA_PING_INTERVAL": "30",
        "XIAOWU_RPA_TASK_QUERY_INTERVAL": "10",
        "XIAOWU_RPA_TASK_CREATE_URL": "http://test.example.com/create",
        "XIAOWU_RPA_TASK_QUERY_URL": "http://test.example.com/query",
    }

    with patch.dict(os.environ, env_vars):
        yield env_vars


@pytest.fixture
def rpa_app() -> FastAPI:
    """Create FastAPI application instance."""
    return rpa_server_app()


@pytest.fixture
def test_client(app: FastAPI) -> TestClient:
    """Create test client."""
    return TestClient(app)


@pytest.fixture
def sample_execution_request() -> Dict[str, Any]:
    """Sample execution request data."""
    return {
        "access_token": "test_token",
        "project_id": "test_project_123",
        "exec_position": "test_position",
        "params": {"key1": "value1", "key2": "value2"},
        "sid": "test_session_123",
    }
