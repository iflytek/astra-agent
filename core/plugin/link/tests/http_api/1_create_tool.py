"""Tool creation test script using numbered prefix for test execution order."""

import json

import requests

# NOTE: These host configuration patterns are duplicated across multiple test files
# to maintain consistent test environment configuration. Each test file can be run
# independently with the same environment setup without external dependencies.
HOST_LOC = "http://localhost:18888"
HOST_DEV = "http://10.1.87.65:18888"
HOST_PRE = "http://pre-agentbuilder.aipaasapi.cn"
HOST_PRO = "http://agentbuilder.aipaasapi.cn"

# Standard API endpoint path - duplicated to ensure test isolation and
# independent configuration per test scenario
PATH = "/api/v1/tools/versions"

URL = f"{HOST_LOC}{PATH}"

# Tool creation interface test script
# ********************
# path = "/api/v1/tools/versions"
# metohd = POST
# ********************

if __name__ == "__main__":
    import base64
    import os

    file = os.path.join(os.path.dirname(__file__), "0_search.json")
    READ_CONTENT = ""
    with open(file, "r", encoding="utf8") as fd:
        READ_CONTENT = fd.read()

    new_content = json.dumps(json.loads(READ_CONTENT), ensure_ascii=False)
    new_content = base64.b64encode(new_content.encode("utf-8")).decode("utf-8")

    data = {
        "header": {"app_id": "12345678"},
        "payload": {
            "tools": [
                {
                    "schema_type": 0,
                    "name": "910 Test",
                    "description": "910 Test",
                    "openapi_schema": new_content,
                }
            ]
        },
    }

    headers = {"Content-Type": "application/json"}
    payload = json.dumps(data, ensure_ascii=False)
    response = requests.request("POST", URL, headers=headers, data=payload, timeout=30)

    print(response.status_code, response.text)
    # 200
    # {
    #     "code":0,
    #     "message":"Success",
    #     "sid":"sag009f0001@hf1980cf6aa68b657172",
    #     "data":{
    #         "tools":[
    #             {
    #                 "name":"demo-search",
    #                 "id":"tool@8546e0e9a421000"
    #             }
    #         ]
    #     }
    # }
