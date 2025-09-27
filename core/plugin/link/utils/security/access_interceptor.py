import ipaddress
import os
import re
from urllib.parse import urlparse, urlunparse

from plugin.link.consts import const


def is_in_black_domain(url: str):
    """Check if URL contains any blacklisted domains.

    Args:
        url: The URL to check against domain blacklist

    Returns:
        bool: True if URL contains blacklisted domain, False otherwise
    """
    # Get environment variable and handle unset or empty cases
    black_list_str = os.getenv(const.DOMAIN_BLACK_LIST_KEY, "")
    if not black_list_str:
        return False

    # Split blacklist string into list
    domain_black_list = [domain.strip().lower() for domain in black_list_str.split(",")]

    # Convert URL to lowercase to avoid case sensitivity issues
    url_lower = url.lower()

    # Check if any domain in blacklist is present in URL
    for black_domain in domain_black_list:
        # Ensure matching complete domain names, not substrings
        if black_domain.lower() in url_lower:
            return True

    return False


def _get_blacklist_config():
    """Get blacklist configuration from environment variables.

    Returns:
        tuple: (segment_black_list, ip_black_list)
    """
    segment_black_list = []
    for black_seg in os.getenv(const.SEGMENT_BLACK_LIST_KEY).split(","):
        if black_seg:
            segment_black_list.append(ipaddress.ip_network(black_seg))
    ip_black_list = []
    for black_id in os.getenv(const.IP_BLACK_LIST_KEY).split(","):
        if black_id:
            ip_black_list.append(black_id)
    return segment_black_list, ip_black_list


def _extract_host_from_url(url):
    """Extract host/IP from URL.

    Args:
        url: The URL to parse

    Returns:
        str: The host/IP, or None if not found
    """
    match = re.search(r"://([^/?#]+)", url)
    if not match:
        return None

    host = match.group(1)
    # Handle cases that may include port numbers
    if ":" in host:
        return host.split(":")[0]
    else:
        return host


def _is_ip_blacklisted(ip, ip_black_list, segment_black_list):
    """Check if IP is in blacklist or blacklisted network segments.

    Args:
        ip: IP address to check
        ip_black_list: List of blacklisted IPs
        segment_black_list: List of blacklisted network segments

    Returns:
        bool: True if IP is blacklisted
    """
    # Check direct IP blacklist
    for i_ip in ip_black_list:
        if ip == i_ip:
            return True

    # Check network segments
    try:
        ip_obj = ipaddress.ip_address(ip)
        for subnet in segment_black_list:
            if ip_obj in subnet:
                return True
        return False
    except ValueError:
        return False


def is_in_blacklist(url):
    """Check if URL is in security blacklist (domains, IPs, network segments).

    Args:
        url: The URL to validate against blacklists

    Returns:
        bool: True if URL is blacklisted, False otherwise
    """
    # NOTE: This security validation pattern is duplicated across multiple files
    # (access_interceptor.py and tool_executor/process.py) to ensure consistent
    # security policy enforcement at different layers of the system architecture.

    # Domain blacklist filtering
    if is_in_black_domain(str(url)):
        return True

    # Get actual request URL
    parsed = urlparse(url)
    url = urlunparse((parsed.scheme, parsed.hostname, parsed.path, "", "", ""))

    if not url:
        return False

    # Get blacklist configuration
    segment_black_list, ip_black_list = _get_blacklist_config()

    # Extract host/IP from URL
    ip = _extract_host_from_url(url)
    if not ip:
        return False

    # Check if IP is blacklisted
    return _is_ip_blacklisted(ip, ip_black_list, segment_black_list)


# Check if it's a loopback address
def is_local_url(url):
    """Check if URL points to a local/loopback address.

    Args:
        url: The URL to check for local address

    Returns:
        bool: True if URL is local/loopback address, False otherwise
    """
    try:
        parsed = urlparse(url)
        hostname = parsed.hostname

        if not hostname:
            return False

        if hostname.lower() == "localhost":
            return True

        try:
            ip = ipaddress.ip_address(hostname)
        except ValueError:
            return False

        # Check if it's a loopback address (IPv4: 127.0.0.0/8, IPv6: ::1/128)
        if ip.is_loopback:
            return True

        return False

    except Exception:
        return False
