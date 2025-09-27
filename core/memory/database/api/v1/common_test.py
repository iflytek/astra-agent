"""Unit tests for common database operations functionality."""

import json
from unittest.mock import AsyncMock, MagicMock, patch

import pytest
import sqlalchemy.exc
from memory.database.api.v1.common import (check_database_exists_by_did,
                                           check_database_exists_by_did_uid,
                                           check_space_id_and_get_uid)
from memory.database.exceptions.error_code import CodeEnum
from sqlmodel.ext.asyncio.session import AsyncSession


@pytest.mark.asyncio
async def test_check_database_exists_by_did_uid_success() -> None:
    """Test check_database_exists_by_did_uid function success scenario."""
    mock_db = AsyncMock(spec=AsyncSession)
    database_id = 123
    uid = "test_user"

    # Mock span context
    mock_span_context = MagicMock()
    mock_span_context.sid = "test-sid"
    mock_span_context.add_error_event = MagicMock()
    mock_span_context.record_exception = MagicMock()
    mock_span_context.report_exception = MagicMock()

    # Mock meter
    mock_meter = MagicMock()
    mock_meter.in_error_count = MagicMock()

    with patch(
        "memory.database.api.v1.common.get_id_by_did_uid", new_callable=AsyncMock
    ) as mock_get_id:
        mock_get_id.return_value = database_id

        with patch(
            "memory.database.api.v1.common.get_schema_name_by_did",
            new_callable=AsyncMock,
        ) as mock_get_schema:
            mock_get_schema.return_value = [["prod_schema"], ["test_schema"]]

            result, error = await check_database_exists_by_did_uid(
                mock_db, database_id, uid, mock_span_context, mock_meter
            )

            # Assertions
            assert result == [["prod_schema"], ["test_schema"]]
            assert error is None
            mock_get_id.assert_called_once_with(
                mock_db, database_id=database_id, uid=uid
            )
            mock_get_schema.assert_called_once_with(mock_db, database_id=database_id)
            mock_meter.in_error_count.assert_not_called()


@pytest.mark.asyncio
async def test_check_database_exists_by_did_uid_database_not_exist() -> None:
    """Test check_database_exists_by_did_uid when database doesn't exist."""
    mock_db = AsyncMock(spec=AsyncSession)
    database_id = 999
    uid = "nonexistent_user"

    # Mock span context
    mock_span_context = MagicMock()
    mock_span_context.sid = "test-sid"
    mock_span_context.add_error_event = MagicMock()

    # Mock meter
    mock_meter = MagicMock()
    mock_meter.in_error_count = MagicMock()

    with patch(
        "memory.database.api.v1.common.get_id_by_did_uid", new_callable=AsyncMock
    ) as mock_get_id:
        mock_get_id.return_value = None

        result, error = await check_database_exists_by_did_uid(
            mock_db, database_id, uid, mock_span_context, mock_meter
        )

        # Assertions
        assert result is None
        assert error is not None

        # Parse the response
        response_body = json.loads(error.body)
        assert response_body["code"] == CodeEnum.DatabaseNotExistError.code
        assert (
            f"uid: {uid} or database_id: {database_id} error, please verify"
            in response_body["message"]
        )
        assert response_body["sid"] == "test-sid"

        # Check that error was logged
        mock_meter.in_error_count.assert_called_once_with(
            CodeEnum.DatabaseNotExistError.code,
            lables={"uid": uid},
            span=mock_span_context,
        )
        mock_span_context.add_error_event.assert_called_once_with(
            f"User: {uid} does not have database: {database_id}"
        )


@pytest.mark.asyncio
async def test_check_database_exists_by_did_uid_schema_not_exist() -> None:
    """Test check_database_exists_by_did_uid when schemas don't exist."""
    mock_db = AsyncMock(spec=AsyncSession)
    database_id = 123
    uid = "test_user"

    # Mock span context
    mock_span_context = MagicMock()
    mock_span_context.sid = "test-sid"

    # Mock meter
    mock_meter = MagicMock()

    with patch(
        "memory.database.api.v1.common.get_id_by_did_uid", new_callable=AsyncMock
    ) as mock_get_id:
        mock_get_id.return_value = database_id

        with patch(
            "memory.database.api.v1.common.get_schema_name_by_did",
            new_callable=AsyncMock,
        ) as mock_get_schema:
            mock_get_schema.return_value = None

            result, error = await check_database_exists_by_did_uid(
                mock_db, database_id, uid, mock_span_context, mock_meter
            )

            # Assertions
            assert result is None
            assert error is not None

            # Parse the response
            response_body = json.loads(error.body)
            assert response_body["code"] == CodeEnum.DatabaseNotExistError.code
            assert response_body["message"] == CodeEnum.DatabaseNotExistError.msg
            assert response_body["sid"] == "test-sid"


@pytest.mark.asyncio
async def test_check_database_exists_by_did_uid_dbapi_error() -> None:
    """Test check_database_exists_by_did_uid with SQLAlchemy DBAPI error."""
    mock_db = AsyncMock(spec=AsyncSession)
    mock_db.rollback = AsyncMock()
    database_id = 123
    uid = "test_user"

    # Mock span context
    mock_span_context = MagicMock()
    mock_span_context.sid = "test-sid"
    mock_span_context.record_exception = MagicMock()

    # Mock meter
    mock_meter = MagicMock()
    mock_meter.in_error_count = MagicMock()

    # Create a mock DBAPIError
    mock_cause = Exception("Database connection failed")
    mock_dbapi_error = sqlalchemy.exc.DBAPIError("statement", {}, mock_cause)

    with patch(
        "memory.database.api.v1.common.get_id_by_did_uid", new_callable=AsyncMock
    ) as mock_get_id:
        mock_get_id.side_effect = mock_dbapi_error

        result, error = await check_database_exists_by_did_uid(
            mock_db, database_id, uid, mock_span_context, mock_meter
        )

        # Assertions
        assert result is None
        assert error is not None

        # Parse the response
        response_body = json.loads(error.body)
        assert response_body["code"] == CodeEnum.DatabaseExecutionError.code
        assert "Database execution failed" in response_body["message"]
        assert response_body["sid"] == "test-sid"

        # Check that rollback was called
        mock_db.rollback.assert_called_once()
        mock_span_context.record_exception.assert_called_once_with(mock_dbapi_error)
        mock_meter.in_error_count.assert_called_once_with(
            CodeEnum.DatabaseExecutionError.code,
            lables={"uid": uid},
            span=mock_span_context,
        )


@pytest.mark.asyncio
async def test_check_database_exists_by_did_uid_general_exception() -> None:
    """Test check_database_exists_by_did_uid with general exception."""
    mock_db = AsyncMock(spec=AsyncSession)
    database_id = 123
    uid = "test_user"

    # Mock span context
    mock_span_context = MagicMock()
    mock_span_context.sid = "test-sid"
    mock_span_context.report_exception = MagicMock()

    # Mock meter
    mock_meter = MagicMock()
    mock_meter.in_error_count = MagicMock()

    # Create a mock general exception
    mock_cause = Exception("Unexpected error")
    mock_exception = Exception("General failure")
    mock_exception.__cause__ = mock_cause

    with patch(
        "memory.database.api.v1.common.get_id_by_did_uid", new_callable=AsyncMock
    ) as mock_get_id:
        mock_get_id.side_effect = mock_exception

        result, error = await check_database_exists_by_did_uid(
            mock_db, database_id, uid, mock_span_context, mock_meter
        )

        # Assertions
        assert result is None
        assert error is not None

        # Parse the response
        response_body = json.loads(error.body)
        assert response_body["code"] == CodeEnum.DatabaseExecutionError.code
        assert response_body["sid"] == "test-sid"

        mock_span_context.report_exception.assert_called_once_with(mock_exception)
        mock_meter.in_error_count.assert_called_once_with(
            CodeEnum.DatabaseExecutionError.code,
            lables={"uid": uid},
            span=mock_span_context,
        )


@pytest.mark.asyncio
async def test_check_database_exists_by_did_success() -> None:
    """Test check_database_exists_by_did function success scenario."""
    mock_db = AsyncMock(spec=AsyncSession)
    database_id = 456
    uid = "test_user"

    # Mock span context
    mock_span_context = MagicMock()
    mock_span_context.sid = "test-sid"

    # Mock meter
    mock_meter = MagicMock()

    with patch(
        "memory.database.api.v1.common.get_id_by_did", new_callable=AsyncMock
    ) as mock_get_id:
        mock_get_id.return_value = database_id

        with patch(
            "memory.database.api.v1.common.get_schema_name_by_did",
            new_callable=AsyncMock,
        ) as mock_get_schema:
            mock_get_schema.return_value = [["prod_schema"], ["test_schema"]]

            result, error = await check_database_exists_by_did(
                mock_db, database_id, uid, mock_span_context, mock_meter
            )

            # Assertions
            assert result == [["prod_schema"], ["test_schema"]]
            assert error is None
            mock_get_id.assert_called_once_with(mock_db, database_id)
            mock_get_schema.assert_called_once_with(mock_db, database_id)


@pytest.mark.asyncio
async def test_check_database_exists_by_did_not_found() -> None:
    """Test check_database_exists_by_did when database is not found."""
    mock_db = AsyncMock(spec=AsyncSession)
    database_id = 999
    uid = "test_user"

    # Mock span context
    mock_span_context = MagicMock()
    mock_span_context.sid = "test-sid"
    mock_span_context.add_error_event = MagicMock()

    # Mock meter
    mock_meter = MagicMock()
    mock_meter.in_error_count = MagicMock()

    with patch(
        "memory.database.api.v1.common.get_id_by_did", new_callable=AsyncMock
    ) as mock_get_id:
        mock_get_id.return_value = None

        result, error = await check_database_exists_by_did(
            mock_db, database_id, uid, mock_span_context, mock_meter
        )

        # Assertions
        assert result is None
        assert error is not None

        # Parse the response
        response_body = json.loads(error.body)
        assert response_body["code"] == CodeEnum.DatabaseNotExistError.code
        assert (
            f"database_id: {database_id} error, please verify"
            in response_body["message"]
        )
        assert response_body["sid"] == "test-sid"

        mock_span_context.add_error_event.assert_called_once_with(
            f"Database does not exist: {database_id}"
        )
        mock_meter.in_error_count.assert_called_once_with(
            CodeEnum.DatabaseNotExistError.code,
            lables={"uid": uid},
            span=mock_span_context,
        )


@pytest.mark.asyncio
async def test_check_database_exists_by_did_schema_not_found() -> None:
    """Test check_database_exists_by_did when schemas are not found."""
    mock_db = AsyncMock(spec=AsyncSession)
    database_id = 456
    uid = "test_user"

    # Mock span context
    mock_span_context = MagicMock()
    mock_span_context.sid = "test-sid"

    # Mock meter
    mock_meter = MagicMock()
    mock_meter.in_error_count = MagicMock()

    with patch(
        "memory.database.api.v1.common.get_id_by_did", new_callable=AsyncMock
    ) as mock_get_id:
        mock_get_id.return_value = database_id

        with patch(
            "memory.database.api.v1.common.get_schema_name_by_did",
            new_callable=AsyncMock,
        ) as mock_get_schema:
            mock_get_schema.return_value = None

            result, error = await check_database_exists_by_did(
                mock_db, database_id, uid, mock_span_context, mock_meter
            )

            # Assertions
            assert result is None
            assert error is not None

            # Parse the response
            response_body = json.loads(error.body)
            assert response_body["code"] == CodeEnum.DatabaseNotExistError.code
            assert response_body["message"] == CodeEnum.DatabaseNotExistError.msg
            assert response_body["sid"] == "test-sid"

            mock_meter.in_error_count.assert_called_once_with(
                CodeEnum.DatabaseNotExistError.code,
                lables={"uid": uid},
                span=mock_span_context,
            )


@pytest.mark.asyncio
async def test_check_database_exists_by_did_general_exception() -> None:
    """Test check_database_exists_by_did with general exception."""
    mock_db = AsyncMock(spec=AsyncSession)
    database_id = 456
    uid = "test_user"

    # Mock span context
    mock_span_context = MagicMock()
    mock_span_context.sid = "test-sid"
    mock_span_context.record_exception = MagicMock()

    # Mock meter
    mock_meter = MagicMock()
    mock_meter.in_error_count = MagicMock()

    mock_exception = Exception("Database error")

    with patch(
        "memory.database.api.v1.common.get_id_by_did", new_callable=AsyncMock
    ) as mock_get_id:
        mock_get_id.side_effect = mock_exception

        result, error = await check_database_exists_by_did(
            mock_db, database_id, uid, mock_span_context, mock_meter
        )

        # Assertions
        assert result is None
        assert error is not None

        # Parse the response
        response_body = json.loads(error.body)
        assert response_body["code"] == CodeEnum.DatabaseExecutionError.code
        assert response_body["message"] == "Database execution failed"
        assert response_body["sid"] == "test-sid"

        mock_span_context.record_exception.assert_called_once_with(mock_exception)
        mock_meter.in_error_count.assert_called_once_with(
            CodeEnum.DatabaseExecutionError.code,
            lables={"uid": uid},
            span=mock_span_context,
        )


@pytest.mark.asyncio
async def test_check_space_id_and_get_uid_success() -> None:
    """Test check_space_id_and_get_uid function success scenario."""
    mock_db = AsyncMock(spec=AsyncSession)
    database_id = 789
    space_id = "space123"

    # Mock span context
    mock_span_context = MagicMock()
    mock_span_context.sid = "test-sid"
    mock_span_context.add_info_event = MagicMock()

    # Mock meter
    mock_meter = MagicMock()

    expected_uid = "found_user"

    with patch(
        "memory.database.api.v1.common.get_uid_by_did_space_id", new_callable=AsyncMock
    ) as mock_get_uid:
        mock_get_uid.return_value = expected_uid

        result, error = await check_space_id_and_get_uid(
            mock_db, database_id, space_id, mock_span_context, mock_meter
        )

        # Assertions
        assert result == expected_uid
        assert error is None

        mock_span_context.add_info_event.assert_called_once_with(
            f"space_id: {space_id}"
        )
        mock_get_uid.assert_called_once_with(mock_db, database_id, space_id)


@pytest.mark.asyncio
async def test_check_space_id_and_get_uid_not_found() -> None:
    """Test check_space_id_and_get_uid when space ID is not found."""
    mock_db = AsyncMock(spec=AsyncSession)
    database_id = 789
    space_id = "nonexistent_space"

    # Mock span context
    mock_span_context = MagicMock()
    mock_span_context.sid = "test-sid"
    mock_span_context.add_info_event = MagicMock()
    mock_span_context.add_error_event = MagicMock()

    # Mock meter
    mock_meter = MagicMock()
    mock_meter.in_error_count = MagicMock()

    with patch(
        "memory.database.api.v1.common.get_uid_by_did_space_id", new_callable=AsyncMock
    ) as mock_get_uid:
        mock_get_uid.return_value = None

        result, error = await check_space_id_and_get_uid(
            mock_db, database_id, space_id, mock_span_context, mock_meter
        )

        # Assertions
        assert result is None
        assert error is not None

        # Parse the response
        response_body = json.loads(error.body)
        assert response_body["code"] == CodeEnum.SpaceIDNotExistError.code
        assert (
            f"space_id: {space_id} does not contain database_id: {database_id}"
            in response_body["message"]
        )
        assert response_body["sid"] == "test-sid"

        mock_span_context.add_info_event.assert_called_once_with(
            f"space_id: {space_id}"
        )
        mock_span_context.add_error_event.assert_called_once_with(
            f"space_id: {space_id} does not contain database_id: {database_id}"
        )
        mock_meter.in_error_count.assert_called_once_with(
            CodeEnum.SpaceIDNotExistError.code,
            lables={"space_id": space_id},
            span=mock_span_context,
        )


@pytest.mark.asyncio
async def test_check_space_id_and_get_uid_edge_cases() -> None:
    """Test check_space_id_and_get_uid with edge cases."""
    mock_db = AsyncMock(spec=AsyncSession)
    database_id = 0  # Edge case: zero database_id
    space_id = ""  # Edge case: empty space_id

    # Mock span context
    mock_span_context = MagicMock()
    mock_span_context.sid = "test-sid"
    mock_span_context.add_info_event = MagicMock()
    mock_span_context.add_error_event = MagicMock()

    # Mock meter
    mock_meter = MagicMock()
    mock_meter.in_error_count = MagicMock()

    with patch(
        "memory.database.api.v1.common.get_uid_by_did_space_id", new_callable=AsyncMock
    ) as mock_get_uid:
        mock_get_uid.return_value = None

        result, error = await check_space_id_and_get_uid(
            mock_db, database_id, space_id, mock_span_context, mock_meter
        )

        # Assertions
        assert result is None
        assert error is not None

        # Verify the function still handles edge cases properly
        mock_span_context.add_info_event.assert_called_once_with(
            f"space_id: {space_id}"
        )
        mock_get_uid.assert_called_once_with(mock_db, database_id, space_id)
