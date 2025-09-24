# -*- coding:utf-8 -*-

import _thread as thread
import base64
import datetime
import hashlib
import hmac
import json
import os
import ssl
from time import mktime
from urllib.parse import urlencode
from wsgiref.handlers import format_date_time

import websocket

STATUS_FIRST_FRAME = 0  # 第一帧的标识
STATUS_CONTINUE_FRAME = 1  # 中间帧标识
STATUS_LAST_FRAME = 2  # 最后一帧的标识


class Ws_Param:
    # 初始化
    def __init__(self, APPID, APIKey, APISecret, Text, res_id, speed):
        """Initialize WebSocket parameters for TTS synthesis.

        All 6 parameters are essential for TTS authentication and configuration:

        Args:
            APPID (str): Application ID - Required for API authentication
            APIKey (str): API key - Required for request authorization
            APISecret (str): API secret - Required for signature generation
            Text (str): Text to synthesize - Required as synthesis input
            res_id (str): Resource/model ID - Required to specify voice model
            speed (int): Speech speed (1-100) - Required for synthesis control

        Note:
            Parameters are necessary for complete TTS functionality:
            - APPID/APIKey/APISecret: Authentication triplet
            - Text: Content to be synthesized
            - res_id: Specifies which trained voice model to use
            - speed: Controls synthesis rate for natural speech
        """
        self.APPID = APPID
        self.APIKey = APIKey
        self.APISecret = APISecret
        self.Text = Text
        self.res_id = res_id
        self.speed = speed

        # 公共参数(common)
        self.CommonArgs = {"app_id": self.APPID, "res_id": self.res_id, "status": 2}
        # 业务参数(business)，更多个性化参数可在官网查看
        self.BusinessArgs = {
            "tts": {
                "vcn": "x5_clone",  # 固定值
                "volume": 50,  # 设置音量大小
                "rhy": 0,
                "pybuffer": 1,
                "speed": self.speed,  # 设置合成语速，值越大，语速越快
                "pitch": 50,  # 设置振幅高低，可通过该参数调整效果
                "bgs": 0,
                "reg": 0,
                "rdn": 0,
                "audio": {
                    "encoding": "lame",  # 合成音频格式
                    "sample_rate": 16000,  # 合成音频采样率
                    "channels": 1,
                    "bit_depth": 16,
                    "frame_size": 0,
                },
                "pybuf": {"encoding": "utf8", "compress": "raw", "format": "plain"},
            }
        }
        self.Data = {
            "text": {
                "encoding": "utf8",
                "compress": "raw",
                "format": "plain",
                "status": 2,
                "seq": 0,
                "text": str(
                    base64.b64encode(self.Text.encode("utf-8")), "UTF8"
                ),  # 待合成文本base64格式
            }
        }


class TTSClient:
    """TTS (Text-to-Speech) synthesis client with WebSocket communication.

    Manages complete TTS workflow including authentication, WebSocket connection,
    message handling, and audio data collection for voice synthesis operations.

    Instance Attributes Organization:

    Authentication Credentials:
        - app_id: Application identifier for service access
        - api_key: API access key for authorization
        - api_secret: Secret key for HMAC signature generation

    Synthesis Configuration:
        - text: Input text content for speech synthesis
        - res_id: Voice resource/model identifier
        - speed: Speech synthesis speed setting

    WebSocket Protocol:
        - wsParam: WebSocket parameter configuration object

    Data Collection:
        - messages: List storing all WebSocket response messages
        - audio_data: Bytearray accumulating synthesized audio data

    Each attribute group serves specific purposes in the TTS synthesis pipeline,
    from authentication through configuration to data collection and output.
    """

    def __init__(self, app_id, api_key, api_secret, text, res_id, speed):
        """Initialize TTS client with authentication and synthesis parameters.

        All 6 parameters are required for complete TTS functionality:

        Args:
            app_id (str): Application identifier - Required for service authentication
            api_key (str): API access key - Required for request authorization
            api_secret (str): API secret key - Required for HMAC signature generation
            text (str): Input text to synthesize - Required as synthesis content
            res_id (str): Voice resource ID - Required to specify trained voice model
            speed (int): Synthesis speed (1-100) - Required for speech rate control

        Note:
            Each parameter serves a critical role:
            - app_id/api_key/api_secret: Form complete authentication credentials
            - text: Defines what content to synthesize into speech
            - res_id: Selects specific voice characteristics/model
            - speed: Controls timing and naturalness of generated speech
        """
        self.app_id = app_id
        self.api_key = api_key
        self.api_secret = api_secret
        self.text = text
        self.res_id = res_id
        self.speed = speed
        self.wsParam = Ws_Param(
            self.app_id,
            self.api_key,
            self.api_secret,
            self.text,
            self.res_id,
            self.speed,
        )

        self.messages = []  # 存储所有消息
        self.audio_data = bytearray()  # 用于存储所有音频数据

    def synthesize(self):
        def on_message(ws, message):
            try:
                message = json.loads(message)
                self.messages.append(message)  # 存储消息
                code = message["header"]["code"]
                _ = message["header"]["sid"]
                if "payload" in message:
                    audio = message["payload"]["audio"]["audio"]
                    audio = base64.b64decode(audio)
                    status = message["payload"]["audio"]["status"]
                    # print(message)
                    if status == 2:
                        # print("ws is closed")
                        ws.close()
                    if code != 0:
                        _ = message["message"]
                        # print("sid:%s call error:%s code is:%s" % (sid, errMsg, code))
                    else:
                        self.audio_data.extend(audio)  # 将音频数据追加到bytearray中
                        with open("./demo.mp3", "ab") as f:
                            f.write(audio)
            except (json.JSONDecodeError, KeyError, base64.binascii.Error):
                pass

        def on_error(_ws, _error):
            pass

        def on_close(_ws, _ts, _end):
            pass

        def on_open(ws):
            def run(*_args):
                d = {
                    "header": self.wsParam.CommonArgs,
                    "parameter": self.wsParam.BusinessArgs,
                    "payload": self.wsParam.Data,
                }
                d = json.dumps(d)
                # print("------>开始发送文本数据")
                ws.send(d)
                if os.path.exists("./demo.mp3"):
                    os.remove("./demo.mp3")

            thread.start_new_thread(run, ())

        websocket.enableTrace(False)
        requrl = "wss://cbm01.cn-huabei-1.xf-yun.com/v1/private/s06a6b848"
        wsUrl = self.assemble_ws_auth_url(requrl, "GET", self.api_key, self.api_secret)
        ws = websocket.WebSocketApp(
            wsUrl, on_message=on_message, on_error=on_error, on_close=on_close
        )
        ws.on_open = on_open
        ws.run_forever(sslopt={"cert_reqs": ssl.CERT_NONE})

        return self.messages, self.audio_data  # 返回消息和文件路径

    @staticmethod
    def assemble_ws_auth_url(requset_url, method="GET", api_key="", api_secret=""):
        class Url:
            def __init__(self, host, path, schema):
                self.host = host
                self.path = path
                self.schema = schema

        def parse_url(requset_url):
            stidx = requset_url.index("://")
            host = requset_url[stidx + 3 :]
            schema = requset_url[: stidx + 3]
            edidx = host.index("/")
            if edidx <= 0:
                raise ValueError("invalid request url:" + requset_url)
            path = host[edidx:]
            host = host[:edidx]
            return Url(host, path, schema)

        def sha256base64(data):
            sha256 = hashlib.sha256()
            sha256.update(data)
            digest = base64.b64encode(sha256.digest()).decode(encoding="utf-8")
            return digest

        u = parse_url(requset_url)
        host = u.host
        path = u.path
        now = datetime.datetime.now()
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

        return requset_url + "?" + urlencode(values)
