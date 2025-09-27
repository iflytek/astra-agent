"""
查询本机ip地址
"""

import socket


def get_host_ip() -> str:
    """
    Query local ip address
    :return: ip
    """
    ip = ""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
    finally:
        s.close()
        return ip


local_ip = get_host_ip()
