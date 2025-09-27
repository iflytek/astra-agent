"""
Exception handling module for the workflow system.

This module provides a comprehensive exception handling framework for the workflow
system,including custom exception classes, error handling utilities, and standardized
error codes.

Key Components:
- CustomException: Base exception class with error code and message support
- CustomExceptionCM: Exception with manual error code handling capabilities
- CustomExceptionInterrupt: Specialized exception for workflow interrupt scenarios
- CustomExceptionCD: Exception with simplified string representation for
  specific use cases
- Exception handlers: FastAPI-compatible handlers for request validation errors
- Error code definitions: Comprehensive error code system with categorized error types
- Code conversion utilities: Tools for mapping third-party API errors to internal codes

The module ensures consistent error handling across the entire workflow system
by providing standardized exception classes, error codes, and response formats
for different types of failures including validation errors, service errors,
and system-level exceptions.
"""
