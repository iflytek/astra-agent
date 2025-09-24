"""MCP Server Example Implementation.

This module provides a sample implementation of an MCP (Model Context Protocol) server
using FastMCP and FastAPI. It demonstrates how to create mathematical calculation tools
(addition and subtraction) that can be exposed through the MCP protocol for agent
interactions.
"""

import uvicorn
from fastapi import FastAPI
from mcp.server.fastmcp import FastMCP

mcp = FastMCP("calculator")
app = FastAPI()


# Tool for calculating addition
@mcp.tool()
def add(a: int, b: int) -> int:
    """Calculate the sum of two numbers
    Args:
        a: First number
        b: Second number
    """
    return a + b


@mcp.tool()
def sub(a: int, b: int) -> int:
    """Calculate the difference of two numbers
    Args:
        a: First number
        b: Second number
    """
    return a - b


# app.include_router(mcp.sse_app().router())
app.mount("/", app=mcp.sse_app())

if __name__ == "__main__":
    uvicorn.run("mcp_server_example:app", host="0.0.0.0", port=8086)
