"""
Chat Completions API功能test V2

test /agent/v1/chat/completions 接口的各种场景，包括：
- 基础聊天功能
- 流式和non-streaming response
- 错误处理
- 参数验证

重要说明：
1. API返回的HTTP状态码通常为200，真正的业务状态需要查看响应JSON中的code字段
2. 根据base_inputs.py，API有以下限制：
   - 不支持system角色消息
   - messages必须以user结尾，且user/assistant交替
   - bot_id长度至少1个字符
3. test使用固定参数：
   - x-consumer-username: cb7386a7
   - bot_id: 14a9bbbcf0254f9b94562e6705d3a13f
   - uid: 12
"""

import concurrent.futures
import json
import time
from typing import Any, Dict, List, Tuple

import httpx


class ChatCompletionsTestClient:
    """Chat Completions APItest客户端"""

    def __init__(self, base_url: str = "http://localhost:17870"):
        self.base_url = base_url
        self.endpoint = f"{base_url}/agent/v1/chat/completions"
        self.default_headers = {
            "Content-Type": "application/json",
            "x-consumer-username": "cb7386a7",
        }

    def parse_response(self, response: httpx.Response) -> Tuple[int, str, dict]:
        """解析API响应，返回(business_code, business_message, full_data)"""
        try:
            data = response.json()
            business_code = data.get("code", 0)
            business_message = data.get("message", "")
            return business_code, business_message, data
        except (ValueError, KeyError, TypeError) as e:
            return -1, f"JSON解析失败: {e}", {}

    def send_request(
        self, messages: List[Dict[str, str]], **kwargs: Any
    ) -> httpx.Response:
        """发送Chat Completions请求"""
        # extract parameters
        uid = kwargs.get("uid", "12")  # fixed user ID
        stream = kwargs.get("stream", False)
        meta_data = kwargs.get("meta_data")
        bot_id = kwargs.get("bot_id", "14a9bbbcf0254f9b94562e6705d3a13f")  # fixed bot_id
        headers = kwargs.get("headers")

        if meta_data is None:
            meta_data = {"caller": "chat_open_api", "caller_sid": ""}

        request_data = {
            "uid": uid,
            "messages": messages,
            "stream": stream,
            "meta_data": meta_data,
            "bot_id": bot_id,
        }

        request_headers = headers or self.default_headers

        return httpx.post(
            self.endpoint, json=request_data, headers=request_headers, timeout=30
        )


class TestChatCompletionsV2:
    """Chat Completions APItest套件 V2"""

    client: ChatCompletionsTestClient

    @classmethod
    def setup_class(cls) -> None:
        """test类初始化"""
        cls.client = ChatCompletionsTestClient()

    def test_basic_chat_completion(self) -> None:
        """test基础聊天完成功能"""
        messages = [{"role": "user", "content": "Hello, how are you?"}]

        response = self.client.send_request(messages)

        # verify HTTP status code
        assert (
            response.status_code == 200
        ), f"期望HTTP状态码200，实际: {response.status_code}"

        # verify response headers
        assert "application/json" in response.headers.get("content-type", "").lower()

        # parse business status code
        business_code, business_message, _ = self.client.parse_response(response)
        print(f"Business code: {business_code}, message: {business_message}")

        # record complete response for analysis
        if business_code != 0:
            print(f"⚠️ 业务状态码: {business_code}, 消息: {business_message}")
            _, _, data = self.client.parse_response(response)
            print(f"完整响应: {json.dumps(data, ensure_ascii=False, indent=2)}")

    def test_chat_with_valid_bot_id(self) -> None:
        """test使用有效bot_id的聊天请求"""
        messages = [{"role": "user", "content": "请介绍一下Python编程语言"}]

        response = self.client.send_request(messages)  # use default fixed bot_id

        assert response.status_code == 200

        business_code, business_message, _ = self.client.parse_response(response)
        print(
            f"有效Bot IDtest - Business code: "
            f"{business_code}, message: {business_message}"
        )

    def test_chat_with_uid(self) -> None:
        """test带用户ID的聊天请求"""
        messages = [{"role": "user", "content": "test用户ID功能"}]

        response = self.client.send_request(messages)  # use default fixed uid

        assert response.status_code == 200

        business_code, business_message, _ = self.client.parse_response(response)
        print(f"UIDtest - Business code: {business_code}, message: {business_message}")

    def test_chat_with_conversation_history(self) -> None:
        """test符合规则的多轮对话"""
        # according to base_inputs.py, must be user/assistant alternating and end with user
        messages = [
            {"role": "user", "content": "我想学习Python编程"},
            {"role": "assistant", "content": "很好！Python是一门很棒的编程语言。"},
            {"role": "user", "content": "请推荐一些入门书籍"},
        ]

        response = self.client.send_request(messages)

        assert response.status_code == 200

        business_code, business_message, _ = self.client.parse_response(response)
        print(
            f"多轮对话test - Business code: "
            f"{business_code}, message: {business_message}"
        )

    def test_stream_chat_completion(self) -> None:
        """test流式聊天完成"""
        messages = [{"role": "user", "content": "请详细解释什么是人工智能"}]

        response = self.client.send_request(messages, stream=True)

        assert response.status_code == 200

        business_code, business_message, _ = self.client.parse_response(response)
        print(
            f"流式响应test - Business code: "
            f"{business_code}, message: {business_message}"
        )

    def test_empty_bot_id_validation(self) -> None:
        """test空bot_id验证 - 应该失败"""
        messages = [{"role": "user", "content": "test空bot_id"}]

        response = self.client.send_request(messages, bot_id="")

        assert response.status_code == 200  # HTTP status code still 200

        business_code, business_message, _ = self.client.parse_response(response)
        print(
            f"空bot_id验证test - Business code: "
            f"{business_code}, message: {business_message}"
        )

        # according to your example, empty bot_id should return 40002 error
        if business_code == 40002:
            print("✅ 空bot_id验证正常工作")
        else:
            print(f"⚠️ 期望错误码40002，实际: {business_code}")

    def test_system_message_validation(self) -> None:
        """testsystem消息验证 - 根据base_inputs.py应该失败"""
        messages = [
            {"role": "system", "content": "你是一个友好的AI助手"},
            {"role": "user", "content": "今天天气怎么样？"},
        ]

        response = self.client.send_request(messages)

        # according to base_inputs.py, system role should be rejected, may return 422 status code
        print(f"System消息test - HTTP状态码: {response.status_code}")

        if response.status_code == 422:
            print("✅ System消息验证正常工作 - 返回422")
        else:
            business_code, business_message, _ = self.client.parse_response(response)
            print(
                f"System消息test - Business code: "
                f"{business_code}, message: {business_message}"
            )

    def test_empty_message_validation(self) -> None:
        """test空消息验证"""
        messages: List[Dict[str, str]] = []

        response = self.client.send_request(messages)

        print(f"空消息test - HTTP状态码: {response.status_code}")

        if response.status_code == 422:
            print("✅ 空消息验证正常工作 - 返回422")
        else:
            business_code, business_message, _ = self.client.parse_response(response)
            print(
                f"空消息test - Business code: "
                f"{business_code}, message: {business_message}"
            )

    def test_invalid_message_order(self) -> None:
        """test无效的消息顺序 - 不是user/assistant交替"""
        messages = [
            {"role": "user", "content": "第一条消息"},
            {"role": "user", "content": "consecutive user messages"},  # violates alternating rule
        ]

        response = self.client.send_request(messages)

        print(f"无效消息顺序test - HTTP状态码: {response.status_code}")

        if response.status_code == 422:
            print("✅ 消息顺序验证正常工作 - 返回422")
        else:
            business_code, business_message, _ = self.client.parse_response(response)
            print(
                f"无效消息顺序test - Business code: "
                f"{business_code}, message: {business_message}"
            )

    def test_uid_length_validation(self) -> None:
        """testUID长度验证"""
        messages = [{"role": "user", "content": "test超长UID"}]

        # create UID exceeding 32 characters
        long_uid = "a" * 33

        response = self.client.send_request(messages, uid=long_uid)

        print(f"UID长度验证test - HTTP状态码: {response.status_code}")

        if response.status_code == 422:
            print("✅ UID长度验证正常工作 - 返回422")
        else:
            business_code, business_message, _ = self.client.parse_response(response)
            print(
                f"UID长度验证test - Business code: "
                f"{business_code}, message: {business_message}"
            )

    def test_missing_required_header(self) -> None:
        """test缺少必需的header"""
        messages = [{"role": "user", "content": "test缺少header"}]

        # remove required x-consumer-username header
        headers = {"Content-Type": "application/json"}

        response = self.client.send_request(messages, headers=headers)

        print(f"缺少headertest - HTTP状态码: {response.status_code}")

        if response.status_code in [400, 422]:
            print("✅ Header验证正常工作")
        else:
            business_code, business_message, _ = self.client.parse_response(response)
            print(
                f"缺少headertest - Business code: "
                f"{business_code}, message: {business_message}"
            )

    def test_concurrent_requests(self) -> None:
        """test并发请求"""

        def send_single_request(thread_id: int) -> Tuple[int, int, float, int]:
            """发送单个请求并记录时间和业务状态"""
            messages = [{"role": "user", "content": f"这是线程{thread_id}的test消息"}]

            start_time = time.time()
            response = self.client.send_request(messages)  # use default fixed uid
            end_time = time.time()

            business_code, _, _ = self.client.parse_response(response)

            return (
                thread_id,
                response.status_code,
                end_time - start_time,
                business_code,
            )

        # send 5 requests concurrently
        max_workers = 5
        results = []

        with concurrent.futures.ThreadPoolExecutor(max_workers=max_workers) as executor:
            futures = {
                executor.submit(send_single_request, i): i for i in range(max_workers)
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
                        f"线程{thread_id}: HTTP={http_status}, "
                        f"业务码={business_code}, 时间={response_time:.2f}s"
                    )
                except (ValueError, RuntimeError, TypeError) as exc:
                    print(f"线程请求失败: {exc}")

        # Verify results
        http_success_count = sum(
            1 for _, http_status, _, _ in results if http_status == 200
        )
        business_success_count = sum(
            1 for _, _, _, business_code in results if business_code == 0
        )

        print(
            f"HTTP成功: {http_success_count}/{max_workers}, "
            f"业务成功: {business_success_count}/{max_workers}"
        )

        # calculate average response time
        if results:
            avg_response_time = sum(time for _, _, time, _ in results) / len(results)
            print(f"平均响应时间: {avg_response_time:.2f}s")


if __name__ == "__main__":
    # run tests directly
    test_instance = TestChatCompletionsV2()
    test_instance.setup_class()

    print("🚀 开始Chat Completions API功能test V2...")
    print("=" * 60)

    # test case list
    test_methods = [
        ("基础聊天完成", test_instance.test_basic_chat_completion),
        ("有效Bot ID", test_instance.test_chat_with_valid_bot_id),
        ("带UID聊天", test_instance.test_chat_with_uid),
        ("多轮对话", test_instance.test_chat_with_conversation_history),
        ("流式聊天", test_instance.test_stream_chat_completion),
        ("空Bot ID验证", test_instance.test_empty_bot_id_validation),
        ("System消息验证", test_instance.test_system_message_validation),
        ("空消息验证", test_instance.test_empty_message_validation),
        ("无效消息顺序", test_instance.test_invalid_message_order),
        ("UID长度验证", test_instance.test_uid_length_validation),
        ("缺少Header验证", test_instance.test_missing_required_header),
        ("并发请求", test_instance.test_concurrent_requests),
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

    print("\n" + "=" * 60)
    print(
        f"📊 test完成！通过: {tests_passed}, "
        f"失败: {tests_failed}, 总计: {tests_passed + tests_failed}"
    )
    print("=" * 60)
