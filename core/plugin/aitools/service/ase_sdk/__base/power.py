import asyncio
import json
import queue
import threading
from abc import ABC, abstractmethod
from urllib.parse import urlencode

import requests
from plugin.aitools.service.ase_sdk.__base.entities.req_data import (
    BaseReqSourceData,
    ReqData,
)
from plugin.aitools.service.ase_sdk.__base.entities.result import Result
from plugin.aitools.service.ase_sdk.const.data_status import DataStatusEnum
from websockets import connect


class Power(ABC):
    """
    抽象基类，定义不同请求类型的执行框架
    """

    def __init__(self, url: str, method: str = "GET", stream=False):
        self.queue = queue.Queue()
        self.url = url
        self.method = method
        self.stream = stream

    @abstractmethod
    def invoke(self, req_source_data: BaseReqSourceData):
        """
        能力执行

        Args:
            req_source_data:    请求原始参数

        Returns:

        """
        # convert BaseReqSourceData to ReqData
        req_data = ReqData()
        return self._invoke(req_data)

    @abstractmethod
    def _subscribe(self):
        """
        订阅能力执行结果，支持流式处理

        Returns:
            Generator: 迭代的执行结果
        """
        raise NotImplementedError

    def handle_generate_response(self):
        result = []
        for index, content in enumerate(self._subscribe()):
            if content:
                try:
                    content_dict = json.loads(content)
                    result.append(content_dict)
                except json.JSONDecodeError:
                    result.append(content)
        return json.dumps(result, ensure_ascii=False)

    def handle_generate_stream_response(self):
        return self._subscribe()

    def _invoke(self, req_data: ReqData):
        """
        内部方法，根据请求类型执行不同的请求

        Args:
            url: 请求地址
            req_data: 请求参数
            method: 请求方法

        Returns:
        """
        try:
            if self.url.startswith("ws"):
                thread = threading.Thread(
                    target=asyncio.run, args=(self._invoke_ws(req_data),)
                )
                thread.start()
                thread.join()
                return None
            elif self.url.startswith("http"):
                return self._invoke_http(req_data)
            else:
                raise ValueError("Unsupported protocol, only support ws and http.")
        except Exception as e:
            self.queue.put(e)
            return None

    async def _invoke_ws(self, req_data: ReqData):
        url = (
            self.url + "?" + urlencode(req_data.params) if req_data.params else self.url
        )
        result_arr: list[str] = []
        wb_handler = None
        try:
            async with connect(
                url, ping_interval=None, ping_timeout=None, timeout=20
            ) as wb_handler:
                await wb_handler.send(json.dumps(req_data.body, ensure_ascii=False))
                while True:
                    raw_data = await wb_handler.recv()
                    if not raw_data:
                        one = Result(status=DataStatusEnum.END.value)
                        self.queue.put(one)
                        break
                    resp = json.loads(raw_data)
                    # print(f'resp:{resp}')
                    result_arr.append(raw_data)
                    header = resp.get("header", {})
                    code = header.get("code", 0)
                    status = (
                        header.get("status", DataStatusEnum.START.value)
                        if code == 0
                        else DataStatusEnum.END.value
                    )
                    one = Result(status=status, data=raw_data)
                    self.queue.put(one)
                    if status == DataStatusEnum.END.value or code != 0:
                        await wb_handler.close()
                        break
        except Exception as e:
            self.queue.put(e)
        finally:
            if wb_handler and not wb_handler.closed:
                await wb_handler.close()

    def _invoke_http(self, req_data: ReqData):
        kwargs = {
            "headers": req_data.headers,
            "params": req_data.params,
            "json": req_data.body,
            "cookies": req_data.cookies,
            "stream": self.stream,
        }
        if self.method.upper() == "GET":
            response = requests.get(self.url, timeout=30, **kwargs)
        elif self.method.upper() == "POST":
            response = requests.post(self.url, timeout=30, **kwargs)
        elif self.method.upper() == "PUT":
            response = requests.put(self.url, timeout=30, **kwargs)
        elif self.method.upper() == "DELETE":
            response = requests.delete(self.url, timeout=30, **kwargs)
        else:
            raise ValueError("Unsupported method, only support GET, POST, PUT, DELETE.")

        if response.status_code == 200:
            if self.stream:
                for i, chunk in enumerate(response.iter_content(chunk_size=1024)):
                    if chunk:
                        one = Result(
                            status=(
                                DataStatusEnum.START.value
                                if i == 0
                                else DataStatusEnum.PROCESSING.value
                            ),
                            data=chunk,
                        )
                        self.queue.put(one)
                self.queue.put(Result(status=DataStatusEnum.END.value))
            else:
                self.queue.put(
                    Result(status=DataStatusEnum.END.value, data=response.text)
                )
        else:
            self.queue.put(
                ValueError(
                    f"Request failed, status code: {response.status_code}, \
                    response: {response.text}"
                )
            )
