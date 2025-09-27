"""MCP Call Tool Example Script.

This module demonstrates how to interact with the MCP (Model Context Protocol)
call_tool API endpoint. It provides an example of calling the generate-mind-map
tool with sample data to create mind maps for project planning purposes.
"""

import json

import requests

URL = "http://localhost:18888/api/v1/mcp/call_tool"  # localhost url
# URL = "http://10.1.87.65:18888/api/v1/mcp/call_tool" # dev url
# URL = "http://pre-agentbuilder.aipaasapi.cn/api/v1/mcp/call_tool"  # pre url access
# URL = "http://agentbuilder.aipaasapi.cn/api/v1/mcp/call_tool"  # pre url access

calc_payload = json.dumps(
    {
        "mcp_server_id": "",
        "mcp_server_url": "http://xingchen-api.xf-yun.com/mcp/7369188804684468224/sse",
        "tool_name": "工具名",
        "tool_args": {"name": "nihao"},
    }
)

headers = {"Content-Type": "application/json"}

if __name__ == "__main__":
    # Print current time, accurate to seconds
    import datetime

    print("Begin time:", datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"))
    response = requests.request(
        "POST", URL, headers=headers, data=calc_payload, timeout=30
    )
    print(response.text)
    print("Finish time:", datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"))
