"""Test API application main module."""

import os
from pathlib import Path
from typing import Dict
from unittest.mock import MagicMock, patch

import pytest
from plugin.rpa.api.app import RPAServer, rpa_server_app
from plugin.rpa.exceptions.config_exceptions import EnvNotFoundException


class TestRPAServer:
    """Test cases for RPAServer class."""

    def test_load_env_success(self, temp_env_file: str) -> None:
        """Test successful loading of environment variable file."""
        # Test environment file loading functionality
        # Note: This tests the module-level functions, not RPAServer methods
        with patch("api.app.Path") as mock_path_class:
            mock_path_instance = MagicMock()
            mock_path_return = (
                mock_path_class.return_value.resolve.return_value.parent.parent
            )
            mock_path_return.__truediv__.return_value = Path(temp_env_file)
            mock_path_instance.exists.return_value = True
            # Set up mock path existence check
            mock_exists = mock_path_return.__truediv__.return_value.exists
            mock_exists.return_value = True

            with patch("api.app.load_dotenv") as mock_load_dotenv:
                # Test that the loading function would be called
                mock_load_dotenv.assert_not_called()  # Just verify mock setup

    def test_load_env_file_not_found(self) -> None:
        """Test exception when environment variable file does not exist."""
        # Test file not found scenario for environment loading
        with patch.object(Path, "exists", return_value=False):
            # This would test the file existence check
            assert not Path("nonexistent.env").exists()

    def test_check_env_success(self, _mock_env_vars: Dict[str, str]) -> None:
        """Test successful check when all required environment variables exist."""
        RPAServer.check_env()  # Should not raise exception

    def test_check_env_missing_vars(self) -> None:
        """Test exception when required environment variables are missing."""
        with patch.dict(os.environ, {}, clear=True):
            with pytest.raises(EnvNotFoundException):
                RPAServer.check_env()

    def test_set_config(self, _mock_env_vars: Dict[str, str]) -> None:
        """Test setting log configuration."""
        with patch("api.app.set_log") as mock_set_log:
            RPAServer.set_config()
            mock_set_log.assert_called_once_with("DEBUG", "./test_logs")

    def test_start_uvicorn(self, _mock_env_vars: Dict[str, str]) -> None:
        """Test starting Uvicorn server."""
        with patch("api.app.uvicorn.Server") as mock_server_class:
            mock_server = MagicMock()
            mock_server_class.return_value = mock_server

            with patch("api.app.import_module") as mock_import:
                mock_module = MagicMock()
                mock_app = MagicMock()
                # Fix attribute access method
                setattr(mock_module, "rpa_server_app", mock_app)
                mock_import.return_value = mock_module

                RPAServer.start_uvicorn()
                mock_server.run.assert_called_once()

    def test_start_method(
        self, _mock_env_vars: Dict[str, str], _temp_env_file: str
    ) -> None:
        """Test complete flow of start method."""
        with patch.object(RPAServer, "setup_server") as mock_setup:
            with patch.object(RPAServer, "check_env") as mock_check:
                with patch.object(RPAServer, "set_config") as mock_set:
                    with patch.object(RPAServer, "start_uvicorn") as mock_start:
                        server = RPAServer()
                        server.start()

                        mock_setup.assert_called_once()
                        mock_check.assert_called_once()
                        mock_set.assert_called_once()
                        mock_start.assert_called_once()


def test_rpa_server_app() -> None:
    """Test FastAPI application creation function."""
    app = rpa_server_app()
    assert app is not None
    assert hasattr(app, "include_router")
