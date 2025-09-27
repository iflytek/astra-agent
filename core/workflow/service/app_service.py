import json
import os

import requests  # type: ignore
from sqlmodel import Session  # type: ignore
from workflow.cache.app import get_app_by_app_id, set_app_by_app_id
from workflow.domain.models.ai_app import App
from workflow.domain.models.app_source import AppSource
from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.otlp.trace.span import Span
from workflow.utils.hmac_auth import HMACAuth


def _gen_app_auth_header(url: str) -> dict[str, str]:
    """
    Generate authentication headers for the application management platform.

    :param url: The request URL for which to generate authentication headers
    :return: Dictionary containing authentication headers, empty dict if credentials are missing
    """
    # Retrieve API credentials from environment variables
    api_key = os.getenv("APP_MANAGE_PLAT_KEY", "")
    api_secret = os.getenv("APP_MANAGE_PLAT_SECRET", "")

    # Return empty dict if credentials are not configured
    if not api_key or not api_secret:
        return {}

    return HMACAuth.build_auth_header(
        request_url=url,
        api_key=api_key,
        api_secret=api_secret,
    )


def get_app_source_id(app_id: str, span: Span) -> str:
    """
    Retrieve the source ID for a given application from the application management platform.

    :param app_id: The application ID to query
    :param span: Tracing span for logging and monitoring
    :return: The source ID of the application
    :raises CustomException: If the API request fails or returns an error
    """
    # Get the application list API endpoint from environment variables
    url = os.getenv("APP_MANAGE_PLAT_APP_LIST")
    if not url:
        raise CustomException(
            CodeEnum.APP_GET_WITH_REMOTE_FAILED_ERROR,
            err_msg="APP_MANAGE_PLAT_APP_LIST not configured",
        )

    # Make authenticated request to get application list
    resp = requests.get(
        url, headers=_gen_app_auth_header(url), params={"app_ids": app_id}
    )

    # Check HTTP response status
    if resp.status_code != 200:
        raise CustomException(
            CodeEnum.APP_GET_WITH_REMOTE_FAILED_ERROR, cause_error=resp.text
        )

    # Check API response code
    code = resp.json().get("code")
    if code != 0:
        raise CustomException(
            CodeEnum.APP_GET_WITH_REMOTE_FAILED_ERROR,
            cause_error=json.dumps(resp.json(), ensure_ascii=False),
        )

    # Log the response data for debugging
    span.add_info_event(
        "Application management platform response: "
        + json.dumps(resp.json(), ensure_ascii=False)
    )

    # Extract and return the source ID from the response
    return resp.json().get("data", [{}])[0].get("source", "")


def get_app_source_detail(app_id: str, span: Span) -> tuple[str, str, str, str]:
    """
    Retrieve detailed application information including name, description, and API credentials.

    :param app_id: The application ID to query
    :param span: Tracing span for logging and monitoring
    :return: Tuple containing (name, description, api_key, api_secret)
    :raises CustomException: If the API request fails or required data is missing
    """
    # Get the application details API endpoint from environment variables
    url = os.getenv("APP_MANAGE_PLAT_APP_DETAILS")
    if not url:
        raise CustomException(
            CodeEnum.APP_GET_WITH_REMOTE_FAILED_ERROR,
            err_msg="APP_MANAGE_PLAT_APP_DETAILS not configured",
        )

    # Make authenticated request to get application details
    resp = requests.get(
        url, headers=_gen_app_auth_header(url), params={"app_ids": app_id}
    )

    # Check HTTP response status
    if resp.status_code != 200:
        raise CustomException(
            CodeEnum.APP_GET_WITH_REMOTE_FAILED_ERROR, cause_error=resp.text
        )

    # Check API response code
    code = resp.json().get("code")
    if code != 0:
        raise CustomException(
            CodeEnum.APP_GET_WITH_REMOTE_FAILED_ERROR,
            cause_error=json.dumps(resp.json(), ensure_ascii=False),
        )

    # Extract response data and validate
    data = resp.json().get("data", [{}])
    if not data:
        raise CustomException(
            CodeEnum.APP_GET_WITH_REMOTE_FAILED_ERROR, cause_error="data is null"
        )

    # Log the response data for debugging
    span.add_info_event(
        "Application management platform response: "
        + json.dumps(resp.json(), ensure_ascii=False)
    )

    # Extract application basic information
    name = data[0].get("name")
    desc = data[0].get("desc")

    # Extract API credentials from auth_list
    api_key = data[0].get("auth_list", [{}])[0].get("api_key")
    api_secret = data[0].get("auth_list", [{}])[0].get("api_secret")

    # Validate that API credentials are present
    if not api_key or not api_secret:
        raise CustomException(
            CodeEnum.APP_GET_WITH_REMOTE_FAILED_ERROR,
            cause_error="api_key or api_secret is null",
        )

    return name, desc, api_key, api_secret


def get_info(app_id: str, session: Session, span: Span) -> App:
    """
    Retrieve application information from cache, database, or external API.

    This function implements a three-tier lookup strategy:
    1. Check cache first for performance
    2. Query local database if not in cache
    3. Fetch from external API and create new record if not found locally

    :param app_id: The application ID to retrieve
    :param session: Database session for queries and transactions
    :param span: Tracing span for logging and monitoring
    :return: App object containing application information
    :raises CustomException: If application cannot be found or created
    """
    # First, try to get from cache
    app_info = get_app_by_app_id(app_id)
    if not app_info:
        # If not in cache, query the database
        app_info = session.query(App).filter_by(alias_id=app_id).first()
        if not app_info:
            # If not in database, fetch from external API
            span.add_info_event(
                "Fetching application source information from management platform"
            )
            source_id = get_app_source_id(app_id, span)
            if not source_id:
                raise CustomException(
                    CodeEnum.APP_TENANT_NOT_FOUND_ERROR,
                    err_msg="source_id not found",
                )

            # Find the corresponding app source in database
            app_source = session.query(AppSource).filter_by(source_id=source_id).first()
            if not app_source:
                raise CustomException(
                    CodeEnum.APP_TENANT_NOT_FOUND_ERROR,
                    err_msg="app_source not found",
                )

            # Get detailed application information from external API
            name, desc, api_key, api_secret = get_app_source_detail(app_id, span)

            # Create new App record with fetched information
            app_info = App(
                name=name,
                description=desc,
                alias_id=app_id,
                api_key=api_key,
                api_secret=api_secret,
                source=app_source.source,
                actual_source=app_source.source,
            )

            # Persist the new application record
            session.add(app_info)
            session.commit()
            session.refresh(app_info)

        # Cache the retrieved application information
        set_app_by_app_id(app_id, app_info)

    return app_info
