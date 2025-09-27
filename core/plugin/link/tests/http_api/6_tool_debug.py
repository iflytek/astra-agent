"""Tool debug test script using numbered prefix for test execution order."""

import json

import requests

HOST_LOC = "http://localhost:18888"
HOST_DEV = "http://10.1.87.65:18888"
HOST_PRE = "http://pre-agentbuilder.aipaasapi.cn"
HOST_PRO = "http://agentbuilder.aipaasapi.cn"

PATH = "/api/v1/tools/tool_debug"

URL = f"{HOST_LOC}{PATH}"

headers = {"Content-Type": "application/json"}

openapi_schema = """
{
    "info": {
        "title": "agentBuilder工具集",
        "version": "1.0.0",
        "x-is-official": false
    },
    "openapi": "3.1.0",
    "paths": {
        "/api/weather/city/101030100": {
            "get": {
                "description": "测试",
                "operationId": "测试-qp9663eq",
                "summary": "测试"
            }
        }
    },
    "servers": [
        {
            "description": "a server description",
            "url": "http://t.weather.sojson.com"
        }
    ]
}
"""

payload = json.dumps(
    {
        "server": "http://t.weather.sojson.com/api/weather/city/101030100",
        "method": "GET",
        "path": {},
        "query": {},
        "header": {},
        "body": {},
        "openapi_schema": openapi_schema,
    }
)

if __name__ == "__main__":
    response = requests.request("POST", URL, headers=headers, data=payload, timeout=30)
    print(response.status_code, response.text)
