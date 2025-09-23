"""
Test data fixtures and factories for generating test data.
"""

from datetime import datetime
from typing import Any, Dict, List, Optional

from faker import Faker

fake = Faker("zh_CN")


class TestDataFactory:
    """test数据工厂类.

    提供各种test数据的生成方法，支持Bot配置、聊天消息、工作流等数据的创建。
    """

    @staticmethod
    def create_bot_config(
        bot_id: Optional[str] = None, bot_name: Optional[str] = None, **kwargs: Any
    ) -> Dict[str, Any]:
        """Create a test bot configuration."""
        return {
            "app_id": f"app_{fake.uuid4()[:8]}",
            "bot_id": bot_id or f"bot_{fake.uuid4()[:8]}",
            "bot_name": bot_name or fake.company(),
            "knowledge_config": {
                "enabled": True,
                "knowledge_base_id": f"kb_{fake.uuid4()[:8]}",
                "similarity_threshold": 0.8,
            },
            "model_config": {
                "model_name": "gpt-3.5-turbo",
                "temperature": 0.7,
                "max_tokens": 1000,
                "top_p": 1.0,
                "frequency_penalty": 0.0,
                "presence_penalty": 0.0,
            },
            "regular_config": {
                "rules": [],
                "filters": {},
            },
            "tool_ids": [],
            "mcp_server_ids": [],
            "mcp_server_urls": [],
            "flow_ids": [],
            "is_deleted": 0,
            "create_at": datetime.now().isoformat(),
            "update_at": datetime.now().isoformat(),
            **kwargs,
        }

    @staticmethod
    def create_chat_message(
        role: str = "user", content: Optional[str] = None, **kwargs: Any
    ) -> Dict[str, Any]:
        """Create a test chat message."""
        return {
            "role": role,
            "content": content or fake.text(max_nb_chars=100),
            "timestamp": datetime.utcnow().isoformat(),
            "message_id": fake.uuid4(),
            **kwargs,
        }

    @staticmethod
    def create_chat_completion_request(
        messages: Optional[List[Dict[str, Any]]] = None, **kwargs: Any
    ) -> Dict[str, Any]:
        """Create a test chat completion request."""
        if messages is None:
            messages = [TestDataFactory.create_chat_message()]

        return {
            "model": "gpt-3.5-turbo",
            "messages": messages,
            "temperature": 0.7,
            "max_tokens": 1000,
            "stream": False,
            "bot_id": f"bot_{fake.uuid4()[:8]}",
            **kwargs,
        }

    @staticmethod
    def create_workflow_node(
        node_id: Optional[str] = None, node_type: str = "chat", **kwargs: Any
    ) -> Dict[str, Any]:
        """Create a test workflow node."""
        return {
            "id": node_id or f"node_{fake.uuid4()[:8]}",
            "type": node_type,
            "name": fake.word().title(),
            "config": {
                "model": "gpt-3.5-turbo",
                "temperature": 0.7,
                "max_tokens": 1000,
            },
            "position": {
                "x": fake.random_int(0, 1000),
                "y": fake.random_int(0, 1000),
            },
            **kwargs,
        }

    @staticmethod
    def create_workflow_config(
        workflow_id: Optional[str] = None, num_nodes: int = 3, **kwargs: Any
    ) -> Dict[str, Any]:
        """Create a test workflow configuration."""
        nodes = [TestDataFactory.create_workflow_node() for _ in range(num_nodes)]

        # Create simple linear edges
        edges = []
        for i in range(len(nodes) - 1):
            edges.append(
                {
                    "id": f"edge_{i}",
                    "source": nodes[i]["id"],
                    "target": nodes[i + 1]["id"],
                }
            )

        return {
            "workflow_id": workflow_id or f"workflow_{fake.uuid4()[:8]}",
            "name": fake.catch_phrase(),
            "description": fake.text(max_nb_chars=200),
            "nodes": nodes,
            "edges": edges,
            "enabled": True,
            "created_at": datetime.utcnow().isoformat(),
            "updated_at": datetime.utcnow().isoformat(),
            **kwargs,
        }

    @staticmethod
    def create_plugin_config(
        plugin_type: str = "knowledge", **kwargs: Any
    ) -> Dict[str, Any]:
        """Create a test plugin configuration."""
        return {
            "plugin_id": f"plugin_{fake.uuid4()[:8]}",
            "plugin_type": plugin_type,
            "name": fake.word().title(),
            "enabled": True,
            "config": {
                "api_key": fake.password(),
                "endpoint": fake.url(),
                "timeout": 30,
            },
            **kwargs,
        }

    @staticmethod
    def create_llm_response(
        content: Optional[str] = None, **kwargs: Any
    ) -> Dict[str, Any]:
        """Create a test LLM response."""
        return {
            "id": f"resp_{fake.uuid4()}",
            "object": "chat.completion",
            "created": int(datetime.utcnow().timestamp()),
            "model": "gpt-3.5-turbo",
            "choices": [
                {
                    "index": 0,
                    "message": {
                        "role": "assistant",
                        "content": content or fake.text(max_nb_chars=200),
                    },
                    "finish_reason": "stop",
                }
            ],
            "usage": {
                "prompt_tokens": fake.random_int(10, 100),
                "completion_tokens": fake.random_int(10, 200),
                "total_tokens": fake.random_int(20, 300),
            },
            **kwargs,
        }


# Common test data constants
VALID_MODEL_NAMES = [
    "gpt-3.5-turbo",
    "gpt-4",
    "gpt-4-turbo",
    "gpt-4o",
]

VALID_ROLES = ["system", "user", "assistant"]

VALID_NODE_TYPES = ["chat", "cot", "cot_process"]

VALID_PLUGIN_TYPES = ["knowledge", "link", "mcp", "workflow"]
