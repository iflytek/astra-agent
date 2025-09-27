"""
Utility module for getting the host IP address.
"""

import socket


def get_host_ip() -> str:
    """
    Query local IP address
    :return: ip
    """
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip_address = s.getsockname()[0]
    finally:
        s.close()

    return ip_address  # type: ignore[no-any-return]


ip = get_host_ip()
