"""Tool retrieval test script using numbered prefix for test execution order."""

import requests

HOST_LOC = "http://localhost:18888"
HOST_DEV = "http://10.1.87.65:18888"
HOST_PRE = "http://pre-agentbuilder.aipaasapi.cn"
HOST_PRO = "http://agentbuilder.aipaasapi.cn"

# path = "/api/v1/versions"
PATH = "/api/v1/tools/versions"

URL = f"{HOST_LOC}{PATH}"

# Tool retrieval interface test script
# ********************
# path = "/api/v1/versions"
# metohd = GET
# ********************


def force_test():
    """Execute concurrent GET requests to test tool retrieval API performance.

    Performs concurrent API calls to retrieve tool information using
    ProcessPoolExecutor. Processes responses to extract success/error information and
    handles exceptions gracefully. Used for load testing and API reliability validation.
    """
    import concurrent.futures

    params = {
        "tool_ids": ["tool@8afcca659c21000"],
        "versions": ["V3.0"],
        "app_id": "12345678",
    }
    # NOTE: This concurrent testing pattern is duplicated across test files
    # (4_get_tool.py, 5_http_run.py) to provide consistent load testing capabilities for
    # different endpoints. The ProcessPoolExecutor setup and response handling pattern
    # ensures reliable concurrent API testing across test scenarios.
    with concurrent.futures.ProcessPoolExecutor(max_workers=10) as executor:
        futures = []
        for i in range(1):
            futures.append(executor.submit(requests.get, URL, params=params))

        # Standard response handling pattern for concurrent tests - duplicated to
        # ensure consistent error handling and success validation across all load
        # testing scenarios
        for future in concurrent.futures.as_completed(futures):
            try:
                response = future.result()
                if response.status_code == 200:
                    print(response.status_code, response.text)
                    # Handle successful response
                    json_data = response.json()
                    code = json_data.get("code")
                    message = json_data.get("message")
                    sid = json_data.get("sid")
                    if code == 0:
                        print("Success:", code, message, sid)
                    else:
                        print("Error:", code, message, sid)
                else:
                    print("Error:", response.json())
            except Exception as e:
                print("Error:", e)


if __name__ == "__main__":
    for i in range(1):
        import time

        time.sleep(0.5)  # Wait 1 second before the next request
        print(f"Request {i+1} sent")
        force_test()
