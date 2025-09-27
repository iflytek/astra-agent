"""
File utility module for handling file operations including URL to Base64 conversion.
"""

import base64
import os
import random
import string
import time
from typing import Optional
from urllib.parse import urlparse

import requests


def generate_unique_filename(extension: str = "txt") -> str:
    """
    Generate unique filename
    :param extension: File extension (default is 'txt')
    :return: Unique filename
    """
    timestamp = int(time.time() * 1000)  # Current timestamp (milliseconds)
    random_str = "".join(
        random.choices(string.ascii_letters + string.digits, k=8)
    )  # Random string
    return f"{timestamp}_{random_str}.{extension}"


def get_image_extension(
    image_url: str, response: Optional[requests.Response] = None
) -> str:
    """
    Get image extension from URL or HTTP response
    :param image_url: Image URL
    :param response: HTTP response object (optional)
    :return: Image extension
    """
    # Get extension from URL
    parsed_url = urlparse(image_url)
    path = parsed_url.path
    extension = path.split(".")[-1] if "." in path else None

    # If extension cannot be obtained from URL, try to get it from HTTP response headers
    if not extension and response:
        content_type = response.headers.get("Content-Type")
        if content_type:
            extension = content_type.split("/")[-1]
            if extension:
                extension = extension.replace(
                    "jpeg", "jpg"
                )  # Handle jpeg and jpg unification

    return extension or "jpg"


def url_to_base64(image_url: str, delete_file: bool = True) -> str:
    """
    Convert image URL to Base64 encoding and delete local file after completion
    :param image_url: Image URL
    :param delete_file: Whether to delete local file after conversion (default is True)
    :return: Base64 encoded string
    """
    # Download image and save to local
    try:
        response = requests.get(image_url, timeout=10)
        if response.status_code != 200:
            raise ValueError(f"Failed to download image from {image_url}")

        # Get image extension
        extension = get_image_extension(image_url, response)

        # Generate unique filename
        temp_file_path = generate_unique_filename(extension)

        # Save image to local temporary file
        with open(temp_file_path, "wb") as image_file:
            image_file.write(response.content)

        # Convert local image file to Base64 encoding
        with open(temp_file_path, "rb") as image_file:
            encoded_string = base64.b64encode(image_file.read()).decode("utf-8")

        # Delete local file (if needed)
        if delete_file:
            os.remove(temp_file_path)

        return encoded_string
    except requests.exceptions.RequestException as e:
        raise ValueError(f"Error downloading image: {str(e)}") from e
