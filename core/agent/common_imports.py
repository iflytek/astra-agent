"""
Unified common package import configuration module.
Other files can import all common package modules by simply importing common_imports.
"""

import os
import sys

# Add parent directory to Python path to import common package
_current_dir = os.path.dirname(os.path.abspath(__file__))
_common_path = os.path.join(_current_dir, "..")
if _common_path not in sys.path:
    sys.path.insert(0, _common_path)

try:
    # Import commonly used modules from common package for use by other files
    from common.exceptions.base import BaseExc
    from common.initialize.initialize import initialize_services
    from common.otlp.ip import local_ip
    from common.otlp.log_trace.base import Usage
    from common.otlp.log_trace.node_log import Data, NodeLog
    from common.otlp.log_trace.node_trace_log import NodeTraceLog
    from common.otlp.log_trace.node_trace_log import Status as TraceStatus
    from common.otlp.metrics.meter import Meter
    from common.otlp.sid import sid_generator2
    from common.otlp.trace.span import Span
    from common.otlp.trace.trace import logger
    from common.settings.polaris import ConfigFilter, Polaris

    # Provide aliases for backward compatibility
    NodeTrace = NodeTraceLog
    Node = NodeLog  # xingchen_utils.Node -> common.NodeLog
    NodeData = Data  # xingchen_utils.NodeData -> common.Data
    NodeDataUsage = Usage  # xingchen_utils.NodeDataUsage -> common.Usage

    # Runtime constants - created for compatibility
    DEVELOPMENT_ENV = "development"
    PRODUCTION_ENV = "production"

    # Legacy aliases for backwards compatibility
    DevelopmentEnv = DEVELOPMENT_ENV  # pylint: disable=invalid-name
    ProductionEnv = PRODUCTION_ENV  # pylint: disable=invalid-name

    # IP alias - for compatibility
    ip = local_ip  # xingchen_utils.otlp.util.ip.ip -> common.otlp.ip.local_ip

    __all__ = [
        "BaseExc",
        "Meter",
        "NodeLog",
        "NodeTraceLog",
        "NodeTrace",  # alias
        "Node",  # alias
        "NodeData",  # alias
        "NodeDataUsage",  # alias
        "Data",
        "Usage",
        "TraceStatus",
        "Span",
        "sid_generator2",
        "logger",
        "initialize_services",
        "ConfigFilter",
        "Polaris",
        "DevelopmentEnv",
        "ProductionEnv",
        "local_ip",
        "ip",
    ]

except ImportError as e:
    # If import fails, provide a more useful error message
    raise ImportError(
        f"Failed to import common modules. "
        f"Please ensure the common package is properly installed. Error: {e}"
    ) from e
