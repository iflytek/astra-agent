import base64
import hashlib
import hmac

from loguru import logger

from knowledge.exceptions.exception import ThirdPartyException


def get_signature(appid: str, ts: int, api_secret: str) -> str:
    """
    Generate API request signature.

    Args:
        appid: Application ID
        ts: Timestamp
        api_secret: API secret key

    Returns:
        Signature string
    """
    try:
        auth = md5(appid + str(ts))
        return hmac_sha1_encrypt(auth, api_secret)
    except Exception as e:
        logger.error(f"Signature generation failed: {e}")
        raise ThirdPartyException(f"Signature generation error: {e}")


def md5(cipher_text: str) -> str:
    """
    Generate MD5 hash value.

    Args:
        cipher_text: Text to be hashed

    Returns:
        MD5 hash string
    """
    try:
        data = cipher_text.encode("utf-8")
        md = hashlib.md5()
        md.update(data)
        return md.hexdigest()
    except Exception as e:
        logger.error(f"MD5 computation failed: {e}")
        raise ThirdPartyException(f"MD5 computation error: {e}")


def hmac_sha1_encrypt(encrypt_text: str, encrypt_key: str) -> str:
    """
    Encrypt text using HMAC-SHA1.

    Args:
        encrypt_text: Text to be encrypted
        encrypt_key: Encryption key

    Returns:
        Base64 encoded encryption result
    """
    try:
        secret_key = encrypt_key.encode("utf-8")
        text = encrypt_text.encode("utf-8")
        mac = hmac.new(secret_key, text, hashlib.sha1)
        return base64.b64encode(mac.digest()).decode("utf-8")
    except Exception as e:
        logger.error(f"HMAC-SHA1 encryption failed: {e}")
        raise ThirdPartyException(f"HMAC-SHA1 encryption error: {e}")
