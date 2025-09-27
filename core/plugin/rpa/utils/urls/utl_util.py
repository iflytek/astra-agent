"""URL utility module providing URL validation functionality."""

from typing import Optional
from urllib.parse import urlparse


def is_valid_url(url: Optional[str]) -> bool:
    """Validate whether the given string is a valid URL."""
    try:
        result = urlparse(url)
        # Must include scheme (protocol) and netloc (domain or IP)
        return all([result.scheme, result.netloc])
    except (ValueError, AttributeError):
        return False
