"""Unit tests for DDL execution functionality."""

import json
from unittest.mock import AsyncMock, MagicMock, patch

import pytest
from memory.database.api.schemas.exec_ddl_types import ExecDDLInput
from memory.database.api.v1.exec_ddl import (_ddl_split, _reset_uid, exec_ddl,
                                             is_ddl_allowed)
from memory.database.exceptions.error_code import CodeEnum
from sqlmodel.ext.asyncio.session import AsyncSession


def test_is_ddl_allowed_allowed_statements() -> None:
    """Test allowed DDL statements (CREATE TABLE/ALTER TABLE etc)."""
    allowed_sql_cases = [
        "CREATE TABLE users (id INT);",
        "ALTER TABLE users ADD COLUMN name TEXT;",
        "DROP TABLE users;",
        "DROP DATABASE old_db;",
        "COMMENT ON COLUMN users.name IS 'Username';",
        "ALTER TABLE users RENAME TO new_users;",
        "alter table users add age int;",
    ]
    mock_span_context = MagicMock()

    for sql in allowed_sql_cases:
        result = is_ddl_allowed(sql, mock_span_context)
        assert result is True, f"Allowed SQL[{sql}] was incorrectly rejected"
        mock_span_context.add_info_event.assert_any_call(f"sql: {sql}")


@pytest.mark.asyncio
async def test_reset_uid_with_valid_space_id_reset_success() -> None:
    """Test _reset_uid with valid space_id resets to new uid."""
    mock_db = AsyncMock(spec=AsyncSession)
    mock_span_context = MagicMock()
    mock_meter = MagicMock()

    # Use non-string type to test type conversion
    mock_new_uid = 123
    with patch(
        "memory.database.api.v1.exec_ddl.check_space_id_and_get_uid",
        new_callable=AsyncMock,
    ) as mock_check_space:
        # Return format needs to match actual code [(uid,)] structure
        mock_check_space.return_value = ([(mock_new_uid,)], None)

        database_id = 2002
        space_id = "space_001"  # Ensure not empty to execute space_id related logic
        original_uid = "u_original"

        result_uid, error = await _reset_uid(
            db=mock_db,
            database_id=database_id,
            space_id=space_id,
            uid=original_uid,
            span_context=mock_span_context,
            m=mock_meter,
        )

        # Verify return value
        assert error is None
        assert result_uid == str(mock_new_uid)  # Verify type conversion

        # Verify check_space_id_and_get_uid call parameters
        mock_check_space.assert_called_once_with(
            mock_db, database_id, space_id, mock_span_context, mock_meter
        )

        # Verify meter was not called incorrectly
        mock_meter.in_error_count.assert_not_called()


@pytest.mark.asyncio
async def test_ddl_split_success() -> None:
    """Test successful DDL splitting (multiple valid statements)."""
    mock_span_context = MagicMock()
    mock_meter = MagicMock()

    with patch("memory.database.api.v1.exec_ddl.is_ddl_allowed", return_value=True):
        raw_ddl = """
            CREATE TABLE users (id INT);
            ALTER TABLE users ADD COLUMN name TEXT;
            DROP TABLE old_users;
        """
        uid = "u1"

        ddls, error_resp = await _ddl_split(raw_ddl, uid, mock_span_context, mock_meter)

        assert error_resp is None
        assert len(ddls) == 3
        assert ddls[0].strip() == "CREATE TABLE users (id INT)"
        assert ddls[1].strip() == "ALTER TABLE users ADD COLUMN name TEXT"
        assert ddls[2].strip() == "DROP TABLE old_users"

        mock_span_context.add_info_event.assert_any_call(
            f"Split DDL statements: {ddls}"
        )
        mock_meter.in_error_count.assert_not_called()


@pytest.mark.asyncio
async def test_exec_ddl_success() -> None:
    """Test successful exec_ddl endpoint (valid DDL + database exists)."""
    mock_db = AsyncMock(spec=AsyncSession)
    mock_db.commit = AsyncMock(return_value=None)
    mock_db.rollback = AsyncMock(return_value=None)

    test_input = ExecDDLInput(
        uid="u1",
        database_id=3001,
        ddl="CREATE TABLE users (id INT); ALTER TABLE users ADD COLUMN name TEXT;",
        space_id="",
    )

    fake_span_context = MagicMock()
    fake_span_context.sid = "exec-ddl-sid-123"
    fake_span_context.add_info_events = MagicMock()
    fake_span_context.add_info_event = MagicMock()
    fake_span_context.record_exception = MagicMock()
    fake_span_context.add_error_event = MagicMock()

    with patch("memory.database.api.v1.exec_ddl.Span") as mock_span_cls:
        mock_span_instance = MagicMock()
        mock_span_instance.start.return_value.__enter__.return_value = fake_span_context
        mock_span_cls.return_value = mock_span_instance

        with patch(
            "memory.database.api.v1.exec_ddl.check_database_exists_by_did_uid",
            new_callable=AsyncMock,
        ) as mock_check_db:
            mock_check_db.return_value = ([["prod_u1_3001"], ["test_u1_3001"]], None)

            with patch(
                "memory.database.api.v1.exec_ddl._ddl_split", new_callable=AsyncMock
            ) as mock_ddl_split:
                mock_ddl_split.return_value = (
                    [
                        "CREATE TABLE users (id INT)",
                        "ALTER TABLE users ADD COLUMN name TEXT",
                    ],
                    None,
                )

                with patch(
                    "memory.database.api.v1.exec_ddl.set_search_path_by_schema",
                    new_callable=AsyncMock,
                ) as mock_set_search:
                    mock_set_search.return_value = None

                    with patch(
                        "memory.database.api.v1.exec_ddl.exec_sql_statement",
                        new_callable=AsyncMock,
                    ) as mock_exec_sql:
                        mock_exec_sql.return_value = None

                        with patch(
                            "memory.database.api.v1.exec_ddl.get_otlp_metric_service"
                        ) as mock_metric_service_func:
                            with patch(
                                "memory.database.api.v1.exec_ddl.get_otlp_span_service"
                            ) as mock_span_service_func:
                                # Mock meter instance
                                mock_meter_inst = MagicMock()
                                mock_meter_inst.in_success_count = MagicMock()

                                # Mock metric service
                                mock_metric_service = MagicMock()
                                mock_metric_service.get_meter.return_value = (
                                    lambda func: mock_meter_inst
                                )
                                mock_metric_service_func.return_value = (
                                    mock_metric_service
                                )

                                # Mock span service and instance
                                mock_span_instance = MagicMock()
                                mock_span_instance.start.return_value.__enter__.return_value = (
                                    fake_span_context
                                )
                                mock_span_service = MagicMock()
                                mock_span_service.get_span.return_value = (
                                    lambda uid: mock_span_instance
                                )
                                mock_span_service_func.return_value = mock_span_service

                                response = await exec_ddl(test_input, mock_db)

                                response_body = json.loads(response.body)
                                assert "code" in response_body
                                assert "message" in response_body
                                assert "sid" in response_body

                                assert response_body["code"] == CodeEnum.Successes.code
                                assert (
                                    response_body["message"] == CodeEnum.Successes.msg
                                )
