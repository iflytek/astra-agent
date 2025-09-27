"""Test constants definition module."""

from plugin.rpa.consts import const
from plugin.rpa.consts.log.log_keys import LOG_LEVEL_KEY
from plugin.rpa.consts.rpa.rpa_keys import XIAOWU_RPA_TIMEOUT_KEY


class TestConstModule:
    """Test cases for constants module."""

    def test_uvicorn_constants_exist(self) -> None:
        """Test that Uvicorn-related constants exist."""
        uvicorn_constants = [
            "UVICORN_RELOAD_KEY",
            "UVICORN_WORKERS_KEY",
            "UVICORN_WS_PING_INTERVAL_KEY",
            "UVICORN_WS_PING_TIMEOUT_KEY",
        ]

        for constant in uvicorn_constants:
            assert hasattr(const, constant), f"Constant {constant} should exist"
            value = getattr(const, constant)
            assert isinstance(value, str), f"Constant {constant} should be a string"
            assert len(value.strip()) > 0, f"Constant {constant} should not be empty"

    def test_log_constants_exist(self) -> None:
        """Test that log-related constants exist."""
        log_constants = ["LOG_LEVEL_KEY", "LOG_PATH_KEY"]

        for constant in log_constants:
            assert hasattr(const, constant), f"Constant {constant} should exist"
            value = getattr(const, constant)
            assert isinstance(value, str), f"Constant {constant} should be a string"
            assert len(value.strip()) > 0, f"Constant {constant} should not be empty"

    def test_rpa_constants_exist(self) -> None:
        """Test that RPA-related constants exist."""
        rpa_constants = [
            "XIAOWU_RPA_PING_INTERVAL_KEY",
            "XIAOWU_RPA_TASK_CREATE_URL_KEY",
            "XIAOWU_RPA_TASK_QUERY_INTERVAL_KEY",
            "XIAOWU_RPA_TASK_QUERY_URL_KEY",
            "XIAOWU_RPA_TIMEOUT_KEY",
        ]

        for constant in rpa_constants:
            assert hasattr(const, constant), f"Constant {constant} should exist"
            value = getattr(const, constant)
            assert isinstance(value, str), f"Constant {constant} should be a string"
            assert len(value.strip()) > 0, f"Constant {constant} should not be empty"

    def test_all_constants_in_all_list(self) -> None:
        """Test that all constants are in __all__ list."""
        expected_constants = [
            "LOG_LEVEL_KEY",
            "LOG_PATH_KEY",
            "XIAOWU_RPA_PING_INTERVAL_KEY",
            "XIAOWU_RPA_TASK_CREATE_URL_KEY",
            "XIAOWU_RPA_TASK_QUERY_INTERVAL_KEY",
            "XIAOWU_RPA_TASK_QUERY_URL_KEY",
            "XIAOWU_RPA_TIMEOUT_KEY",
        ]

        assert hasattr(const, "__all__"), "Module should have __all__ list"
        assert isinstance(const.__all__, list), "__all__ should be a list"

        for constant in expected_constants:
            assert (
                constant in const.__all__
            ), f"Constant {constant} should be in __all__"

    def test_all_list_completeness(self) -> None:
        """Test completeness of __all__ list."""
        # Get all public attributes in module (not starting with underscore)
        public_attributes = [attr for attr in dir(const) if not attr.startswith("_")]

        # Filter out imported modules and other non-constant attributes
        constants = [
            attr
            for attr in public_attributes
            if attr.isupper() and attr.endswith("_KEY")
        ]

        for constant in constants:
            assert (
                constant in const.__all__
            ), f"Public constant {constant} should be in __all__"

    def test_constant_naming_convention(self) -> None:
        """Test constant naming convention."""
        for constant_name in const.__all__:
            # Constants should be all uppercase
            assert (
                constant_name.isupper()
            ), f"Constant {constant_name} should be uppercase"

            # Constants should end with _KEY
            assert constant_name.endswith(
                "_KEY"
            ), f"Constant {constant_name} should end with _KEY"

            # Constant names should contain underscore separators
            assert (
                "_" in constant_name
            ), f"Constant {constant_name} should contain underscores"

    def test_constant_values_uniqueness(self) -> None:
        """Test uniqueness of constant values."""
        values = []
        for constant_name in const.__all__:
            value = getattr(const, constant_name)
            values.append(value)

        # Check if there are duplicate values
        unique_values = set(values)
        assert len(values) == len(unique_values), "All constant values should be unique"

    def test_constant_values_format(self) -> None:
        """Test format of constant values."""
        for constant_name in const.__all__:
            value = getattr(const, constant_name)

            # All constant values should be strings
            assert isinstance(
                value, str
            ), f"Constant {constant_name} value should be a string"

            # Constant values should not be empty
            assert (
                len(value.strip()) > 0
            ), f"Constant {constant_name} value should not be empty"

            # Constant values should be valid environment variable name format
            assert (
                value.replace("_", "").replace("-", "").isalnum()
            ), f"Constant {constant_name} value should be a valid env var name"

    def test_import_structure(self) -> None:
        """Test correctness of import structure."""
        # Verify that constants can be imported from submodules
        # Verify that imported constants match those in main module
        assert LOG_LEVEL_KEY == const.LOG_LEVEL_KEY
        assert XIAOWU_RPA_TIMEOUT_KEY == const.XIAOWU_RPA_TIMEOUT_KEY

    def test_module_docstring(self) -> None:
        """Test module docstring."""
        assert const.__doc__ is not None, "Module should have a docstring"
        assert (
            "Constants definition module" in const.__doc__
        ), "Docstring should describe the module purpose"
