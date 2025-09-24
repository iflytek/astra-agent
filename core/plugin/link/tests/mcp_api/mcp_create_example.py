"""MCP Create Example Script.

This module demonstrates how to register a new MCP (Model Context Protocol) server
with the OpenStellar platform. It provides an example of creating an MCP server
registration with specific configuration including name, description, and server URL.
"""

import json

import requests

URL = "http://localhost:18888/api/v1/mcp"  # localhost url
# URL = "http://10.1.87.65:18888/api/v1/mcp" # dev url
# URL = "http://pre-agentbuilder.aipaasapi.cn/api/v1/mcp"  # pre url internal access
# URL = "http://agentbuilder.aipaasapi.cn/api/v1/mcp"  # pro url internal access

payload = json.dumps(
    {
        "app_id": "a01c2bc7",
        "name": "xmind",
        "description": "xmind",
        "mcp_server_url": "http://xingchen-agent-mcp-test.aicp.private/mcp/xmind/sse",
        "type": "node",
    }
)

headers = {"Content-Type": "application/json"}

if __name__ == "__main__":
    response = requests.request("POST", URL, headers=headers, data=payload, timeout=30)
    print(response.text)
