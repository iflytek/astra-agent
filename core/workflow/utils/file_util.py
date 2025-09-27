"""
File utility functions for handling file operations, image processing, and URL conversions.

This module provides utilities for generating unique filenames, extracting image extensions,
and converting image URLs to Base64 encoded strings.
"""

import base64
import os
import random
import string
import time
from typing import Optional
from urllib.parse import urlparse

import requests  # type: ignore


def generate_unique_filename(extension: str = "txt") -> str:
    """
    Generate a unique filename with timestamp and random string.

    :param extension: File extension (default: 'txt')
    :return: Unique filename string
    """
    timestamp = int(time.time() * 1000)  # Current timestamp in milliseconds
    random_str = "".join(
        random.choices(string.ascii_letters + string.digits, k=8)
    )  # Random string
    return f"{timestamp}_{random_str}.{extension}"


def get_image_extension(
    url: str, response: Optional[requests.Response] = None
) -> Optional[str]:
    """
    Extract image extension from URL or HTTP response headers.

    :param url: Image URL
    :param response: HTTP response object (optional)
    :return: Image extension string or None
    """
    # Extract extension from URL
    parsed_url = urlparse(url)
    path = parsed_url.path
    extension = path.split(".")[-1] if "." in path else None

    # If extension cannot be extracted from URL, try HTTP response headers
    if not extension and response:
        content_type = response.headers.get("Content-Type")
        if content_type:
            extension = content_type.split("/")[-1]
            if extension:
                extension = extension.replace("jpeg", "jpg")  # Normalize jpeg to jpg

    return extension


def url_to_base64(url: str, delete_file: bool = True) -> str:
    """
    Convert image URL to Base64 encoded string and optionally delete local file.

    :param url: Image URL to convert
    :param delete_file: Whether to delete local file after conversion (default: True)
    :return: Base64 encoded string
    :raises Exception: If image download fails
    """
    # Download image and save to local file
    response = requests.get(url)
    if response.status_code != 200:
        raise Exception(f"Failed to download image from {url}")

    # Get image extension
    extension = get_image_extension(url, response)

    # Generate unique filename
    if not extension:
        extension = "jpg"  # Default extension
    temp_file_path = generate_unique_filename(extension)

    # Save image to local temporary file
    with open(temp_file_path, "wb") as image_file:
        image_file.write(response.content)

    # Convert local image file to Base64 encoding
    with open(temp_file_path, "rb") as image_file:
        encoded_string = base64.b64encode(image_file.read()).decode("utf-8")

    # Delete local file if requested
    if delete_file:
        os.remove(temp_file_path)

    return encoded_string
