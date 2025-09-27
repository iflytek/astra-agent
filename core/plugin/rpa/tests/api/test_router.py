"""Test main router module."""

from plugin.rpa.api.router import router


def test_router_creation() -> None:
    """Test router creation."""
    assert router is not None
    assert router.prefix == "/rpa/v1"


def test_router_includes_execution_routes() -> None:
    """Test router includes execution routes."""
    # Check if router contains execution-related routes
    found_exec_route = False
    for route in router.routes:
        if hasattr(route, "path_regex") and "/exec" in str(route.path_regex.pattern):
            found_exec_route = True
            break
        if hasattr(route, "path") and "/exec" in route.path:
            found_exec_route = True
            break

    assert found_exec_route, "Should include execution route"
