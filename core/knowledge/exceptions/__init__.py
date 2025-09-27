# -*- coding: utf-8 -*-
"""
Exception handling module

Defines custom exception classes used in Knowledge Service
"""

from .exception import (
    BaseCustomException,
    CustomException,
    ProtocolParamException,
    ServiceException,
    ThirdPartyException,
)

__all__ = [
    "BaseCustomException",
    "ProtocolParamException",
    "ServiceException",
    "ThirdPartyException",
    "CustomException",
]
