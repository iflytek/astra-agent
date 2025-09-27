import _thread as thread
import base64
import hashlib
import hmac
import json
import os
import ssl
from datetime import datetime
from time import mktime
from urllib.parse import urlencode
from wsgiref.handlers import format_date_time

import websocket


class SmartTTSClient:
    """Smart TTS WebSocket client for advanced speech synthesis operations.

    Provides enhanced TTS functionality with dynamic URL generation, message handling,
    and comprehensive audio data management for high-quality voice synthesis.

    Instance Attributes Organization:

    Authentication & Connection:
        - app_id: Application identifier for service authentication
        - api_key: API access key for request authorization
        - api_secret: API secret for HMAC signature generation
        - request_url: Base WebSocket endpoint URL
        - ws_url: Complete authenticated WebSocket URL
        - ws: WebSocket connection instance

    Synthesis Configuration:
        - text: Input text content for speech conversion
        - vcn: Voice character name/model identifier
        - speed: Speech synthesis rate control

    Processing & Output:
        - nowtime: Timestamp for session identification
        - messages: Collection of all WebSocket response messages
        - audio_data: Accumulated synthesized audio data buffer

    Attributes are organized to support the complete TTS workflow from
    authentication setup through synthesis processing to audio output collection.
    """

    def __init__(self, app_id, api_key, api_secret, text, vcn, speed):
        """Initialize Smart TTS client with authentication and synthesis configuration.

        All 6 parameters are essential for TTS service operation:

        Args:
            app_id (str): Application identifier - Required for service authentication
            api_key (str): API access key - Required for request authorization
            api_secret (str): API secret key - Required for HMAC signature generation
            text (str): Input text content - Required as synthesis material
            vcn (str): Voice character name - Required to specify voice model/speaker
            speed (int): Speech rate (1-100) - Required for synthesis speed control

        Note:
            Each parameter serves a critical function:
            - app_id/api_key/api_secret: Complete authentication credential set
            - text: Defines the content to be converted to speech
            - vcn: Selects specific voice characteristics and speaker identity
            - speed: Controls timing and naturalness of speech output
        """
        self.app_id = app_id
        self.api_key = api_key
        self.api_secret = api_secret
        self.text = text
        self.request_url = os.getenv("TTS_URL")
        self.ws_url = None
        self.ws = None
        self._prepare_ws_url()

        self.vcn = vcn
        self.speed = speed
        self.nowtime = datetime.now().strftime("%Y-%m-%d-%H-%M-%S")
        self.messages = []  # 存储所有消息
        self.audio_data = bytearray()  # 用于存储所有音频数据

    def _prepare_ws_url(self):
        self.ws_url = self.assemble_ws_auth_url(
            self.request_url, "GET", self.api_key, self.api_secret
        )

    def parse_url(self, request_url):
        stidx = request_url.index("://")
        host = request_url[stidx + 3 :]
        schema = request_url[: stidx + 3]
        edidx = host.index("/")
        if edidx <= 0:
            raise ValueError("Invalid request URL: " + request_url)
        path = host[edidx:]
        host = host[:edidx]
        return type("Url", (object,), {"host": host, "path": path, "schema": schema})

    def assemble_ws_auth_url(self, request_url, method, api_key, api_secret):
        u = self.parse_url(request_url)
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
        authorization_origin = f'api_key="{api_key}", algorithm="hmac-sha256",\
              headers="host date request-line", signature="{signature_sha}"'
        authorization = base64.b64encode(authorization_origin.encode("utf-8")).decode(
            encoding="utf-8"
        )
        values = {"host": host, "date": date, "authorization": authorization}
        return request_url + "?" + urlencode(values)

    def on_message(self, ws, message):
        try:
            message = json.loads(message)
            # print(111,message)
            self.messages.append(message)  # 存储消息
            code = message["header"]["code"]
            if "payload" in message:
                audio = base64.b64decode(message["payload"]["audio"]["audio"])
                status = message["payload"]["audio"]["status"]
                if status == 2:
                    # print("Connection closed by server")
                    ws.close()
                if code == 0:
                    self.audio_data.extend(audio)  # 将音频数据追加到bytearray中
                    # with open('./demo.MP3', 'ab') as f:
                    #     f.write(audio)
                # else:
                #     errMsg = message["message"]
                #     print(f"sid:{sid} call error:{errMsg} code is:{code}")

        except Exception as e:
            raise e

    def on_error(self, ws, error):
        pass

    def on_close(self, ws, close_status_code, close_msg):
        pass

    def on_open(self, ws):
        def run(*_args):
            common_args = {"app_id": self.app_id, "status": 2}
            business_args = {
                "tts": {
                    "vcn": self.vcn,
                    "volume": 50,
                    "rhy": 0,
                    "speed": self.speed,
                    "pitch": 50,
                    "bgs": 0,
                    "reg": 0,
                    "rdn": 0,
                    "audio": {
                        "encoding": "lame",
                        "sample_rate": 24000,
                        "channels": 1,
                        "bit_depth": 16,
                        "frame_size": 0,
                    },
                }
            }
            data = {
                "text": {
                    "encoding": "utf8",
                    "compress": "raw",
                    "format": "plain",
                    "status": 2,
                    "seq": 0,
                    "text": str(base64.b64encode(self.text.encode("utf-8")), "UTF8"),
                }
            }
            d = {"header": common_args, "parameter": business_args, "payload": data}
            d = json.dumps(d)
            # print("Starting to send text data...")
            ws.send(d)
            if os.path.exists("./demo.mp3"):
                os.remove("./demo.mp3")

        thread.start_new_thread(run, ())

    def start(self):
        websocket.enableTrace(False)
        self.ws = websocket.WebSocketApp(
            self.ws_url,
            on_message=self.on_message,
            on_error=self.on_error,
            on_close=self.on_close,
        )
        self.ws.on_open = self.on_open
        self.ws.run_forever(sslopt={"cert_reqs": ssl.CERT_NONE})

        return self.messages, self.audio_data  # 返回消息和文件路径
