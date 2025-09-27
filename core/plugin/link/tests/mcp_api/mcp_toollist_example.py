"""MCP Tool List Example Script.

This module demonstrates how to retrieve available tools from MCP
(Model Context Protocol) servers. It provides an example of querying the tool_list
API endpoint to discover what tools are available from registered MCP servers.
"""

import json

import requests

URL = "http://localhost:18888/api/v1/mcp/tool_list"  # localhost url
# URL = "http://10.1.87.65:18888/api/v1/mcp/tool_list" # dev url
# URL = "http://pre-agentbuilder.aipaasapi.cn/api/v1/mcp/tool_list"  # pre url access
# URL = "http://agentbuilder.aipaasapi.cn/api/v1/mcp/tool_list"  # pre url access

payload = json.dumps(
    {"mcp_server_urls": ["http://xingchen-api.xf-yun.com/mcp/7358780514674724864/sse"]}
)

headers = {"Content-Type": "application/json"}

if __name__ == "__main__":
    response = requests.request("POST", URL, headers=headers, data=payload, timeout=30)
    print(response.text)
