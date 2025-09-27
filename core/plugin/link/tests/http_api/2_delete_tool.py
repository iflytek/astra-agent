"""Tool deletion test script using numbered prefix for test execution order."""

import requests

# NOTE: These host configuration patterns are duplicated across multiple test files
# to maintain consistent test environment configuration. Each test file can be run
# independently with the same environment setup without external dependencies.
HOST_LOC = "http://localhost:18888"
HOST_DEV = "http://10.1.87.65:18888"
HOST_PRE = "http://pre-agentbuilder.aipaasapi.cn"
HOST_PRO = "http://agentbuilder.aipaasapi.cn"

# path = "/api/v1/versions"
# Standard API endpoint path - duplicated to ensure test isolation
PATH = "/api/v1/tools/versions"

URL = f"{HOST_LOC}{PATH}"

# Tool deletion interface test script
# ********************
# path = "/api/v1/versions"
# metohd = DELETE
# ********************

if __name__ == "__main__":
    # response = requests.delete(
    #     url,
    #     params={
    #         "tool_ids": ["tool@89c889936c21000"],
    #         "app_id": "12345678"
    #     }
    # )
    response = requests.delete(
        URL,
        params={
            "tool_ids": ["tool@8afcca659c21000"],
            "versions": ["V1.0"],
            "app_id": "12345678",
        },
        timeout=30,
    )

    print(response.status_code, response.text)
