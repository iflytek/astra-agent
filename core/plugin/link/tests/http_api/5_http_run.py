"""HTTP run test script using numbered prefix for test execution order."""

import json

import requests

HOST_LOC = "http://localhost:18888"
HOST_DEV = "http://10.1.87.65:18888"
HOST_PRE = "http://pre-agentbuilder.aipaasapi.cn"
HOST_PRO = "http://agentbuilder.aipaasapi.cn"

PATH = "/api/v1/tools/http_run"

URL = f"{HOST_LOC}{PATH}"

headers = {"Content-Type": "application/json", "sid": "my_sid"}
payload = json.dumps(
    {
        "header": {"app_id": "12345678"},
        "parameter": {
            "tool_id": "tool@88bc744d0821000",
            "operation_id": "天气查询-4x1cmt53",
            "version": "V1.0",
        },
        "payload": {"message": {"body": "eyJjaXR5IjogIlx1NTQwOFx1ODBhNSJ9"}},
    }
)


def force_test():
    """Execute concurrent POST requests to test HTTP run API performance.

    Performs concurrent API calls to execute tool operations using ProcessPoolExecutor.
    Processes responses to extract success/error information from the API response
    structure. Used for load testing and validating tool execution reliability under
    concurrent load.
    """
    import concurrent.futures

    with concurrent.futures.ProcessPoolExecutor(max_workers=1) as executor:
        futures = []
        for i in range(1):
            futures.append(
                executor.submit(requests.post, URL, headers=headers, data=payload)
            )

        for future in concurrent.futures.as_completed(futures):
            try:
                response = future.result()
                if response.status_code == 200:
                    print(response.status_code, response.text)
                    # Handle successful response
                    json_data = response.json()
                    # print("Response JSON:", json_data)
                    code = json_data.get("header").get("code")
                    message = json_data.get("header").get("message")
                    sid = json_data.get("header").get("sid")
                    if code == 0:
                        print("Success:", code, message, sid)
                    else:
                        print("Error: code:", code, message, sid)
                else:
                    print("Error http:", response.status_code, response.text)
            except Exception as e:
                print("Error:", e)


if __name__ == "__main__":
    # response = requests.request("POST", url, headers=headers, data=payload)
    # print(response.status_code, response.text)
    for i in range(1):
        # import time
        # time.sleep(0.5)  # Wait 1 second before the next request
        # print(f"Request {i+1} sent")
        force_test()
