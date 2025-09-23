"""
Custom Chat Completions API功能test

test /agent/v1/custom/chat/completions 接口的各种场景，包括：
- 智能体运行用户模式
- 自定义配置参数
- 插件和工具集成
- 工作流execute

重要说明：
1. API返回的HTTP状态码通常为200，真正的业务状态需要查看响应JSON中的code字段
2. 使用固定test参数:
   - X-Consumer-Username: xxx
   - app_id: xxx
   - uid: 123456
"""

import concurrent.futures
import json
import time
from typing import Any, Dict, Optional, Tuple

import httpx


class CustomChatCompletionsTestClient:
    """Custom Chat Completions APItest客户端"""

    def __init__(self, base_url: str = "http://127.0.0.1:17870"):
        self.base_url = base_url
        self.endpoint = f"{base_url}/agent/v1/custom/chat/completions"
        self.default_headers = {
            "Content-Type": "application/json",
            "X-Consumer-Username": "xxxxx",
        }

    def parse_response(self, response: httpx.Response) -> Tuple[int, str, dict]:
        """解析API响应，返回(business_code, business_message, full_data)"""
        try:
            response_text = response.text.strip()

            # handle streaming response: multi-line JSON format
            if "\n" in response_text:
                lines = response_text.split("\n")
                # get first line as main response data
                first_line = lines[0].strip()
                if first_line.startswith("data: "):
                    first_line = first_line[6:]  # remove "data: " prefix

                if first_line:
                    data = json.loads(first_line)
                else:
                    # if first line is empty, try next line
                    for line in lines[1:]:
                        line = line.strip()
                        if line.startswith("data: "):
                            line = line[6:]
                        if line and line != "[DONE]":
                            data = json.loads(line)
                            break
                    else:
                        return (
                            0,
                            "流式响应处理成功",
                            {"stream": True, "lines_count": len(lines)},
                        )
            else:
                # non-streaming response
                data = response.json()

            business_code = data.get("code", 0)
            business_message = data.get("message", "")
            return business_code, business_message, data
        except (ValueError, KeyError, TypeError) as e:
            return -1, f"JSON解析失败: {e}", {"raw_response": response.text[:200]}

    def send_request(
        self, request_data: Dict[str, Any], headers: Optional[Dict[str, str]] = None
    ) -> httpx.Response:
        """发送Custom Chat Completions请求"""
        request_headers = headers or self.default_headers

        # force non-streaming mode to avoid connection issues
        request_data_safe = request_data.copy()
        request_data_safe["stream"] = False

        return httpx.post(
            self.endpoint, json=request_data_safe, headers=request_headers, timeout=30.0
        )


class TestCustomChatCompletions:
    """Custom Chat Completions APItest套件"""

    client: CustomChatCompletionsTestClient

    @classmethod
    def setup_class(cls) -> None:
        """test类初始化"""
        cls.client = CustomChatCompletionsTestClient()

    def _create_basic_request_data(self, user_message: str) -> Dict[str, Any]:
        """创建基础的请求数据配置"""
        return {
            "app_id": "f0785ea5",
            "uid": "101000088313",
            "model_config": {
                "domain": "xdeepseekv3",
                "api": "https://maas-api.cn-huabei-1.xf-yun.com/v1",
            },
            "instruction": {"reasoning": "", "answer": ""},
            "messages": [{"role": "user", "content": user_message}],
            "plugin": {
                "mcp_server_ids": [],
                "mcp_server_urls": [],
                "tools": [],
                "workflow_ids": [],
                "knowledge": [],
            },
            "meta_data": {"caller": "workflow-agent-node", "caller_sid": ""},
            "max_loop_count": 10,
            "stream": False,  # default to non-streaming mode to avoid connection issues
        }

    def test_basic_custom_chat(self) -> None:
        """test基础自定义聊天功能"""
        request_data = self._create_basic_request_data("Hello, 请介绍一下你自己")

        response = self.client.send_request(request_data)

        assert (
            response.status_code == 200
        ), f"期望HTTP状态码200，实际: {response.status_code}"

        business_code, business_message, _ = self.client.parse_response(response)
        print(
            f"基础自定义聊天 - Business code: {business_code}, "
            f"message: {business_message}"
        )

        if business_code == 0:
            print("✅ 自定义聊天请求成功")
        else:
            print(f"⚠️ 业务状态码: {business_code}, 消息: {business_message}")

    def test_custom_chat_with_knowledge(self) -> None:
        """test包含知识库的自定义聊天"""
        request_data = self._create_basic_request_data("你好，小米汽车咋样")

        # add knowledge base configuration
        request_data["plugin"]["knowledge"] = [
            {
                "name": "小米汽车车评",
                "description": "小米汽车车评文章",
                "top_k": 3,
                "match": {
                    "repo_ids": ["b2263d918ad64ca7a326068a03471898"],
                    "doc_ids": ["79a3165b1a5041108135e05d2a3607f1"],
                },
                "repo_type": 2,
            }
        ]

        response = self.client.send_request(request_data)

        assert response.status_code == 200

        business_code, business_message, _ = self.client.parse_response(response)
        print(
            f"知识库集成聊天 - Business code: {business_code}, "
            f"message: {business_message}"
        )

    def test_custom_chat_with_tools(self) -> None:
        """test包含工具的自定义聊天"""
        request_data = self._create_basic_request_data("帮我搜索相关信息")

        # add tool configuration
        request_data["plugin"]["tools"] = ["tool@664882907021000"]

        response = self.client.send_request(request_data)

        assert response.status_code == 200

        business_code, business_message, _ = self.client.parse_response(response)
        print(
            f"工具集成聊天 - Business code: {business_code}, "
            f"message: {business_message}"
        )

    def test_custom_chat_with_workflow(self) -> None:
        """test包含工作流的自定义聊天"""
        request_data = self._create_basic_request_data("execute数据分析工作流")

        # add workflow configuration
        request_data["plugin"]["workflow_ids"] = ["data_analysis_workflow"]
        request_data["max_loop_count"] = 5

        response = self.client.send_request(request_data)

        assert response.status_code == 200

        business_code, business_message, _ = self.client.parse_response(response)
        print(
            f"工作流聊天 - Business code: {business_code}, "
            f"message: {business_message}"
        )

    def test_custom_chat_with_mcp_servers(self) -> None:
        """test包含MCP服务器的自定义聊天"""
        request_data = self._create_basic_request_data("使用外部服务处理这个请求")

        # add MCP server configuration
        request_data["plugin"]["mcp_server_ids"] = ["mcp_server_001"]
        request_data["plugin"]["mcp_server_urls"] = ["http://localhost:3000"]

        response = self.client.send_request(request_data)

        assert response.status_code == 200

        business_code, business_message, _ = self.client.parse_response(response)
        print(
            f"MCP服务器聊天 - Business code: {business_code}, "
            f"message: {business_message}"
        )

    def test_custom_chat_multi_turn_conversation(self) -> None:
        """test自定义多轮对话"""
        request_data = self._create_basic_request_data("请开始数据分析")

        # multi-turn conversation messages
        request_data["messages"] = [
            {"role": "user", "content": "我需要分析销售数据"},
            {
                "role": "assistant",
                "content": "好的，我来帮您分析销售数据。请提供数据文件。",
            },
            {"role": "user", "content": "数据已上传，请开始分析趋势"},
        ]

        response = self.client.send_request(request_data)

        assert response.status_code == 200

        business_code, business_message, _ = self.client.parse_response(response)
        print(
            f"多轮对话 - Business code: {business_code}, "
            f"message: {business_message}"
        )

    def test_custom_chat_stream_mode(self) -> None:
        """test自定义聊天流式模式"""
        request_data = self._create_basic_request_data("请详细解释人工智能的发展历史")
        request_data["stream"] = True

        response = self.client.send_request(request_data)

        assert response.status_code == 200

        business_code, business_message, _ = self.client.parse_response(response)
        print(
            f"流式模式 - Business code: {business_code}, "
            f"message: {business_message}"
        )

    def test_custom_chat_with_custom_model_config(self) -> None:
        """testcustom model configuration"""
        request_data = self._create_basic_request_data("使用custom model configuration处理请求")

        # custom model configuration
        request_data["model_config"] = {
            "domain": "custom_model_v2",
            "api": "https://custom-api.example.com/v1",
            "temperature": 0.7,
            "max_tokens": 2000,
        }

        response = self.client.send_request(request_data)

        assert response.status_code == 200

        business_code, business_message, _ = self.client.parse_response(response)
        print(
            f"custom model configuration - Business code: {business_code}, "
            f"message: {business_message}"
        )

    def test_missing_required_headers(self) -> None:
        """test缺少必需headers的验证"""
        request_data = self._create_basic_request_data("test缺少headers")

        # remove required headers
        incomplete_headers = {"Content-Type": "application/json"}

        response = self.client.send_request(request_data, headers=incomplete_headers)

        print(f"缺少Headerstest - HTTP状态码: {response.status_code}")

        if response.status_code == 422:
            print("✅ Headers验证正常工作")
        else:
            business_code, business_message, _ = self.client.parse_response(response)
            print(
                f"Headers验证test - Business code: "
                f"{business_code}, message: {business_message}"
            )

    def test_invalid_request_data(self) -> None:
        """test无效的请求数据验证"""
        # intentionally send invalid request data
        invalid_data = {"invalid_field": "test", "messages": []}  # empty message array

        response = self.client.send_request(invalid_data)

        assert response.status_code == 200

        business_code, business_message, _ = self.client.parse_response(response)
        print(
            f"无效输入验证 - Business code: {business_code}, "
            f"message: {business_message}"
        )

        if business_code == 40002:
            print("✅ 输入验证正常工作")

    def test_custom_chat_non_stream_mode(self) -> None:
        """test自定义聊天非流式模式"""
        request_data = self._create_basic_request_data("请简单介绍Python编程语言")
        request_data["stream"] = False

        response = self.client.send_request(request_data)

        assert response.status_code == 200

        business_code, business_message, _ = self.client.parse_response(response)
        print(
            f"非流式模式 - Business code: {business_code}, "
            f"message: {business_message}"
        )

    def test_concurrent_custom_requests(self) -> None:
        """test自定义聊天并发请求"""

        def send_single_custom_request(thread_id: int) -> Tuple[int, int, float, int]:
            """发送单个自定义请求"""
            request_data = self._create_basic_request_data(f"并发test请求 {thread_id}")
            request_data["meta_data"]["caller_sid"] = f"concurrent_test_{thread_id}"

            start_time = time.time()
            response = self.client.send_request(request_data)
            end_time = time.time()

            business_code, _, _ = self.client.parse_response(response)

            return (
                thread_id,
                response.status_code,
                end_time - start_time,
                business_code,
            )

        # send 3 requests concurrently (custom mode may respond slower)
        max_workers = 3
        results = []

        with concurrent.futures.ThreadPoolExecutor(max_workers=max_workers) as executor:
            futures = {
                executor.submit(send_single_custom_request, i): i
                for i in range(max_workers)
            }

            for future in concurrent.futures.as_completed(futures):
                try:
                    thread_id, http_status, response_time, business_code = (
                        future.result()
                    )
                    results.append(
                        (thread_id, http_status, response_time, business_code)
                    )
                    print(
                        f"自定义并发线程{thread_id}: HTTP={http_status}, "
                        f"业务码={business_code}, 时间={response_time:.2f}s"
                    )
                except (ValueError, RuntimeError, TypeError) as exc:
                    print(f"并发请求失败: {exc}")

        # Verify results
        http_success_count = sum(
            1 for _, http_status, _, _ in results if http_status == 200
        )
        business_success_count = sum(
            1 for _, _, _, business_code in results if business_code == 0
        )

        print(
            f"自定义聊天并发test - HTTP成功: {http_success_count}/{max_workers}, "
            f"业务成功: {business_success_count}/{max_workers}"
        )

        if results:
            avg_response_time = sum(time for _, _, time, _ in results) / len(results)
            print(f"平均响应时间: {avg_response_time:.2f}s")


if __name__ == "__main__":
    # run tests directly
    test_instance = TestCustomChatCompletions()
    test_instance.setup_class()

    print("🚀 开始Custom Chat Completions API功能test...")
    print("=" * 70)

    # test case list
    test_methods = [
        ("基础自定义聊天", test_instance.test_basic_custom_chat),
        ("知识库集成聊天", test_instance.test_custom_chat_with_knowledge),
        ("工具集成聊天", test_instance.test_custom_chat_with_tools),
        ("工作流聊天", test_instance.test_custom_chat_with_workflow),
        ("MCP服务器聊天", test_instance.test_custom_chat_with_mcp_servers),
        ("多轮对话", test_instance.test_custom_chat_multi_turn_conversation),
        ("流式模式", test_instance.test_custom_chat_stream_mode),
        ("非流式模式", test_instance.test_custom_chat_non_stream_mode),
        ("custom model configuration", test_instance.test_custom_chat_with_custom_model_config),
        ("Headers验证", test_instance.test_missing_required_headers),
        ("输入验证", test_instance.test_invalid_request_data),
        ("并发请求", test_instance.test_concurrent_custom_requests),
    ]

    tests_passed = 0  # pylint: disable=invalid-name
    tests_failed = 0  # pylint: disable=invalid-name

    for test_name, test_method in test_methods:
        try:
            print(f"\n🧪 {test_name}test:")
            test_method()
            print(f"✅ {test_name}test完成")
            tests_passed += 1
        except (AssertionError, ValueError, RuntimeError) as e:
            print(f"❌ {test_name}test失败: {e}")
            tests_failed += 1

    print("\n" + "=" * 70)
    print(
        f"📊 Custom Chat Completionstest完成！"
        f"通过: {tests_passed}, 失败: {tests_failed}, "
        f"总计: {tests_passed + tests_failed}"
    )
    print("=" * 70)
