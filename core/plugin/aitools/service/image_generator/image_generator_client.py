"""
Image generation client module providing AI text-to-image functionality.
"""

import base64
import hashlib
import hmac
import json
from datetime import datetime
from io import BytesIO
from time import mktime
from urllib.parse import urlencode
from wsgiref.handlers import format_date_time

import requests
from PIL import Image


class AssembleHeaderException(Exception):
    def __init__(self, msg):
        super().__init__(msg)


class Url:
    def __init__(self, host, path, schema):
        self.host = host
        self.path = path
        self.schema = schema


def sha256base64(data):
    sha256 = hashlib.sha256()
    sha256.update(data)
    digest = base64.b64encode(sha256.digest()).decode(encoding="utf-8")
    return digest


def parse_url(request_url):
    stidx = request_url.index("://")
    host = request_url[stidx + 3 :]
    schema = request_url[: stidx + 3]
    edidx = host.index("/")
    if edidx <= 0:
        raise AssembleHeaderException("invalid request url:" + request_url)
    path = host[edidx:]
    host = host[:edidx]
    u = Url(host, path, schema)
    return u


def assemble_ws_auth_url(request_url, method="GET", api_key="", api_secret=""):
    u = parse_url(request_url)
    host = u.host
    path = u.path
    now = datetime.now()
    date = format_date_time(mktime(now.timetuple()))
    signature_origin = f"host: {host}\ndate: {date}\n{method} {path} HTTP/1.1"
    signature_sha = hmac.new(
        api_secret.encode("utf-8"),
        signature_origin.encode("utf-8"),
        digestmod=hashlib.sha256,
    ).digest()
    signature_sha = base64.b64encode(signature_sha).decode(encoding="utf-8")
    authorization_origin = (
        f'api_key="{api_key}", algorithm="hmac-sha256", '
        f'headers="host date request-line", signature="{signature_sha}"'
    )
    authorization = base64.b64encode(authorization_origin.encode("utf-8")).decode(
        encoding="utf-8"
    )
    values = {"host": host, "date": date, "authorization": authorization}

    return request_url + "?" + urlencode(values)


def get_body(app_id, text):
    body = {
        "header": {"app_id": app_id, "uid": "123456789", "patch_id": ["0"]},
        "parameter": {
            "chat": {
                "domain": "xskolorss2b6",
                "width": 1024,
                "height": 1024,
                "seed": 42,
                "num_inference_steps": 20,
                "guidance_scale": 5.0,
                "scheduler": "Euler",
            }
        },
        "payload": {
            "message": {"text": [{"role": "user", "content": text}]},
            "negative_prompts": {"text": "black and white"},
        },
    }
    return body


class ImageGenerator:
    def __init__(self, app_id, api_key, api_secret, description):
        self.app_id = app_id
        self.api_key = api_key
        self.api_secret = api_secret
        self.description = description
        self.base_url = "https://xingchen-api.cn-huabei-1.xf-yun.com/v2.1/tti"

        self.image_base64 = None
        self.sid = ""
        self.error_message = []

    def generate_image(self):
        url = assemble_ws_auth_url(
            self.base_url,
            method="POST",
            api_key=self.api_key,
            api_secret=self.api_secret,
        )
        body = get_body(self.app_id, self.description)
        response = requests.post(
            url, json=body, headers={"content-type": "application/json"}, timeout=30
        ).text
        self._parse_message(response)

        return self.image_base64, self.sid, self.error_message

    def _parse_message(self, message):
        data = json.loads(message)
        code = data["header"]["code"]
        msg = data["header"]["message"]
        self.sid = data["header"]["sid"]
        if code != 0:
            # print(f'请求错误: {code}, {data}')
            self.error_message.append(
                {
                    "sid": self.sid,
                    "code": code,
                    "msg": msg,
                    "data": data,
                }
            )
        else:
            text = data["payload"]["choices"]["text"]
            image_content = text[0]
            image_base = image_content["content"]
            self.image_base64 = image_base
            # self._save_image(image_base)

    def _save_image(self, base64_data, save_path="generated_image.jpg"):
        img_data = base64.b64decode(base64_data)
        img = Image.open(BytesIO(img_data))
        img.save(save_path)
        # print("图片保存路径：" + save_path)

    # def get_image_base64(self):
    #     return self.image_base64, self.sid, self.error_message
