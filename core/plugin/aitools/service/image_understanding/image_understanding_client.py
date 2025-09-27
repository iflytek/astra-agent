"""
Image understanding client module providing AI image analysis and
comprehension functionality.
"""

import _thread as thread
import base64
import hashlib
import hmac
import json
import ssl
from datetime import datetime
from time import mktime
from urllib.parse import urlencode, urlparse
from wsgiref.handlers import format_date_time

import requests
import websocket

from const.err_code.code import CodeEnum  # 引入错误码枚举类


class ImageUnderstandingClient:
    def __init__(self, app_id, api_key, api_secret, imageunderstanding_url):
        self.app_id = app_id
        self.api_key = api_key
        self.api_secret = api_secret
        self.imageunderstanding_url = imageunderstanding_url
        self.error_message = []

    def create_url(self):
        now = datetime.now()
        date = format_date_time(mktime(now.timetuple()))
        signature_origin = f"host: {urlparse(self.imageunderstanding_url).netloc}\n"\
        f"date: {date}\nGET {urlparse(self.imageunderstanding_url).path} HTTP/1.1"
        signature_sha = hmac.new(
            self.api_secret.encode("utf-8"),
            signature_origin.encode("utf-8"),
            digestmod=hashlib.sha256,
        ).digest()
        signature_sha_base64 = base64.b64encode(signature_sha).decode("utf-8")
        # print('signature_sha_base64:',signature_sha_base64)
        authorization_origin = f'api_key="{self.api_key}", algorithm="hmac-sha256",\
              headers="host date request-line", signature="{signature_sha_base64}"'
        authorization = base64.b64encode(authorization_origin.encode("utf-8")).decode(
            "utf-8"
        )
        v = {
            "authorization": authorization,
            "date": date,
            "host": urlparse(self.imageunderstanding_url).netloc,
        }
        url = self.imageunderstanding_url + "?" + urlencode(v)
        # print('url:',url)
        return url

    def on_error(self, _ws, error):
        # print("### error:", error,type(error))
        # appid未授权可能出现该报错
        self.error_message.append(str(error))

    def on_close(self, ws, one, two):
        pass

    def on_open(self, ws):
        thread.start_new_thread(self.run, (ws,))

    def run(self, ws, *_args):
        try:
            data = json.dumps(
                self.gen_params(question=self.question, image_url=self.image_url)
            )
            ws.send(data)
        except Exception as e:
            self.error_message.append(
                {"code": CodeEnum.IMAGE_UNDERSTANDING_ERROR.code, "message": str(e)}
            )
            ws.close()

    def on_message(self, ws, message):
        data = json.loads(message)
        # print('data:',data)
        code = data["header"]["code"]
        self.sid = data["header"]["sid"]
        msg = data["header"]["message"]
        if code != 0:
            # print(f'请求错误: {code}, {data}')
            ws.close()
            self.error_message.append(
                {
                    "sid": self.sid,
                    "code": code,
                    "msg": msg,
                    "data": data,
                }
            )
        else:
            choices = data["payload"]["choices"]
            status = choices["status"]
            content = choices["text"][0]["content"]
            self.answer += content
            if status == 2:
                ws.close()

    def gen_params(self, question, image_url):
        response = requests.get(image_url, timeout=30)
        imagedata = base64.b64encode(response.content).decode("utf-8")
        return {
            "header": {"app_id": self.app_id},
            "parameter": {
                "chat": {
                    "domain": "imagev3",
                    "temperature": 0.5,
                    "top_k": 4,
                    "max_tokens": 8192,
                    "auditing": "default",
                }
            },
            "payload": {
                "message": {
                    "text": [
                        {"role": "user", "content": imagedata, "content_type": "image"},
                        {"role": "user", "content": question},
                    ]
                }
            },
        }

    def get_answer(self, question, image_url):
        self.question = question
        self.image_url = image_url
        self.answer = ""
        self.sid = ""
        websocket.enableTrace(False)
        wsUrl = self.create_url()
        ws = websocket.WebSocketApp(
            wsUrl,
            on_message=self.on_message,
            on_error=self.on_error,
            on_close=self.on_close,
            on_open=self.on_open,
        )
        ws.run_forever(sslopt={"cert_reqs": ssl.CERT_NONE})
        return self.answer, self.sid, self.error_message
