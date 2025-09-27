import base64
import hashlib
import hmac
from datetime import datetime
from time import mktime
from urllib import parse
from urllib.parse import urlencode, urlparse
from wsgiref.handlers import format_date_time


class HMACAuth:

    @staticmethod
    def build_auth_request_url(
        request_url: str, method: str = "GET", api_key: str = "", api_secret: str = ""
    ) -> str:
        values = HMACAuth.build_auth_params(request_url, method, api_key, api_secret)
        return request_url + "?" + urlencode(values)

    @staticmethod
    def build_auth_params(
        request_url: str, method: str = "GET", api_key: str = "", api_secret: str = ""
    ) -> dict:
        url_result = parse.urlparse(request_url)
        date = format_date_time(mktime(datetime.now().timetuple()))
        signature_origin = "host: {}\ndate: {}\n{} {} HTTP/1.1".format(
            url_result.hostname, date, method, url_result.path
        )
        signature_sha_bytes = hmac.new(
            api_secret.encode("utf-8"),
            signature_origin.encode("utf-8"),
            digestmod=hashlib.sha256,
        ).digest()
        signature_sha_str = base64.b64encode(signature_sha_bytes).decode(
            encoding="utf-8"
        )
        authorization_origin = (
            'api_key="%s", algorithm="%s", headers="%s", signature="%s"'
            % (api_key, "hmac-sha256", "host date request-line", signature_sha_str)
        )
        authorization = base64.b64encode(authorization_origin.encode("utf-8")).decode(
            encoding="utf-8"
        )
        values = {
            "host": url_result.hostname,
            "date": date,
            "authorization": authorization,
        }
        return values

    @staticmethod
    def build_auth_header(
        request_url: str, method: str = "GET", api_key: str = "", api_secret: str = ""
    ) -> dict:
        u = urlparse(request_url)
        host = u.hostname
        path = u.path
        now = datetime.now()
        date = format_date_time(mktime(now.timetuple()))
        m = hashlib.sha256(bytes("".encode(encoding="utf-8"))).digest()
        digest_bytes = "SHA256=" + base64.b64encode(m).decode(encoding="utf-8")
        signatureStr = "host: " + str(host) + "\n"
        signatureStr += "date: " + date + "\n"
        signatureStr += method + " " + path + " " + "HTTP/1.1" + "\n"
        # signatureStr += self.HttpMethod + " " + " " + self.HttpProto + "\n"
        signatureStr += "digest: " + digest_bytes
        # print("signatureStr:%s  **********" % signatureStr)
        signature = hmac.new(
            bytes(api_secret, encoding="UTF-8"),
            bytes(signatureStr, encoding="UTF-8"),
            digestmod=hashlib.sha256,
        ).digest()
        sign = base64.b64encode(signature).decode(encoding="utf-8")
        # print("result:%s    ************" % result)

        authHeader = (
            'api_key="%s", algorithm="%s", '
            'headers="host date request-line digest", '
            'signature="%s"' % (api_key, "hmac-sha256", sign)
        )
        # print(authHeader)
        headers = {
            "Method": method,
            "Host": host,
            "Date": date,
            "Digest": digest_bytes,
            "Authorization": authHeader,
        }
        return headers
