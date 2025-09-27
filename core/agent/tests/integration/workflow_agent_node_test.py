"""
工作流代理节点性能test

这个test脚本用于test工作流代理节点的并发性能，通过多线程并发发送请求
来评估系统在高负载下的表现和响应时间。

主要功能：
- 并发testOpenAI API调用
- 测量首帧响应时间
- 支持20个并发请求的压力test
"""

import asyncio
import concurrent.futures
import time
from typing import Any, Dict, List

from openai import AsyncOpenAI
from openai.types.chat import ChatCompletionUserMessageParam

first_frame_times: List[str] = []

# note: the following is a backup HTTP request implementation (commented)
# if you need to use aiohttp version, uncomment and add aiohttp dependency


async def do_request(url: str, request_data: Dict[str, Any], i: int) -> None:
    # ignore unused parameters, this is a placeholder for test function
    del url, request_data, i
    # TODO: move API key to environment variables or configuration file
    client = AsyncOpenAI(
        api_key="sk-test-key-replace-with-env-var",
        base_url="https://maas-api.cn-huabei-1.xf-yun.com/v1",
    )
    messages: List[ChatCompletionUserMessageParam] = [
        ChatCompletionUserMessageParam(
            role="user",
            content=(
                "我想要在上海火车站到东方明珠之间找一个酒店，"
                "酒店应该在这两者之间的任意一个地铁站附近2公里范围内，"
                "价格在500到750之间，你能帮我找到所有符合条件的酒店吗？"
            ),
        )
    ]
    start_time = time.time()
    response = await client.chat.completions.create(
        model="xdeepseekv3",
        messages=messages,
        stream=True,
    )
    first = True
    async for chunk in response:
        if first:
            end_time = time.time()
            print(chunk)
            print(end_time - start_time)
            first = False


def task(url: str, request_data: Dict[str, Any], i: int) -> None:
    loop = asyncio.new_event_loop()
    loop.run_until_complete(do_request(url, request_data, i))
    loop.close()


if __name__ == "__main__":
    # create a thread pool with maximum 20 worker threads
    # URL = "https://maas-api.cn-huabei-1.xf-yun.com/v1/chat/completions"
    URL = "http://127.0.0.1:17870/agent/v1/custom/chat/completions"
    data = {
        "model_config": {
            "domain": "xdeepseekv3",
            "api": "https://maas-api.cn-huabei-1.xf-yun.com/v1",
        },
        "instruction": {"reasoning": "优先使用工具", "answer": ""},
        "plugin": {
            "tools": [],
            "mcp_server_ids": [],
            "mcp_server_urls": [],
            "workflow_ids": [],
        },
        "uid": "",
        "messages": [
            {
                "role": "user",
                "content": (
                    "我想要在上海火车站到东方明珠之间找一个酒店，"
                    "酒店应该在这两者之间的任意一个地铁站附近2公里范围内，"
                    "价格在500到750之间，你能帮我找到所有符合条件的酒店吗？"
                ),
            }
        ],
        "meta_data": {
            "caller": "workflow-agent-node",
            "caller_sid": "spf0011029a@hf19615f729015741782",
        },
        "stream": True,
        "max_loop_count": 10,
    }

    with concurrent.futures.ThreadPoolExecutor(max_workers=20) as executor:
        # submit 20 tasks to thread pool
        future_to_task = {executor.submit(task, URL, data, i + 1): i for i in range(20)}
        # wait for all tasks to complete
        for future in concurrent.futures.as_completed(future_to_task):
            task_id = future_to_task[future]
            try:
                future.result()
                print(f"Task {task_id + 1} completed successfully.")
            except (RuntimeError, OSError, ValueError) as exc:
                print(f"Task {task_id + 1} failed with exception: {exc}")
            except Exception as exc:  # pylint: disable=broad-exception-caught
                print(f"Task {task_id + 1} failed with unexpected exception: {exc}")
