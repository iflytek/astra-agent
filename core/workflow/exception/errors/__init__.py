"""
Error code definitions and conversion utilities.

This module provides a comprehensive error handling framework for the workflow
system,including standardized error codes, third-party API error mappings, and
conversion utilities.

Key Components:
- CodeEnum: Internal system error codes with descriptive messages organized
            by functional categories
- ThirdApiCodeEnum: Third-party service error codes from external APIs (Spark,
                    image generation, etc.)
- CodeConvert: Utility class for converting between different error code systems

Error Code Organization:
The error codes are systematically organized by functional categories with specific
ranges:
- HTTP status codes (200, 500): Basic HTTP response codes
- Application errors (20000-20008): Application-level configuration and binding issues
- Protocol errors (20100-20104): Protocol validation and management errors
- Flow errors (20201-20209): Workflow execution and management errors
- Spark model errors (20301-20376): AI model service errors including WebSocket, auth,
  and rate limiting
- WebSocket errors (20400): Real-time communication errors
- Knowledge base errors (20500-20502): Knowledge retrieval and processing errors
- Variable pool errors (20600-20602): Variable system management errors
- Database errors (20700): Database operation errors
- Authentication errors (20900-20905): Authentication and authorization errors
- Node-specific errors (22500-23900): Individual node type execution errors

This systematic organization ensures consistent error handling, easy debugging, and
maintainable error management across the entire workflow system.
"""
