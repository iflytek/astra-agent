"""Tool update test script using numbered prefix for test execution order."""

import json

import requests

HOST_LOC = "http://localhost:18888"
HOST_DEV = "http://10.1.87.65:18888"
HOST_PRE = "http://pre-agentbuilder.aipaasapi.cn"
HOST_PRO = "http://agentbuilder.aipaasapi.cn"

PATH = "/api/v1/tools/versions"

URL = f"{HOST_LOC}{PATH}"

# Tool update interface test script
# ********************
# path = "/api/v1/tools/versions"
# metohd = PUT
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
                    "id": "tool@8afcca659c21000",
                    "schema_type": 0,
                    "version": "V3.0",
                    "name": "909 Test",
                    "description": "demo-aggregated search-v2",
                    "openapi_schema": new_content,
                }
            ]
        },
    }

    headers = {"Content-Type": "application/json"}
    payload = json.dumps(data, ensure_ascii=False)
    response = requests.put(URL, headers=headers, data=payload, timeout=30)

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
