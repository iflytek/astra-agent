"""Unit tests for database operator functionality."""

import json
from typing import Any
from unittest.mock import AsyncMock, MagicMock, patch

import pytest
from memory.database.api.schemas.clone_db_types import CloneDBInput
from memory.database.api.schemas.create_db_types import CreateDBInput
from memory.database.api.schemas.drop_db_types import DropDBInput
from memory.database.api.schemas.modify_db_desc_types import ModifyDBDescInput
from memory.database.api.v1.db_operator import (
    DatabaseInfo, clone_db, create_db, drop_db, exec_generate_schema,
    generate_copy_data_sql, generate_copy_table_structures_sql,
    modify_db_description)
from memory.database.domain.models.database_meta import DatabaseMeta
from memory.database.domain.models.schema_meta import SchemaMeta
from memory.database.exceptions.error_code import CodeEnum
from sqlmodel.ext.asyncio.session import AsyncSession


def test_generate_copy_table_structures_sql() -> None:
    """Test generate_copy_table_structures_sql function."""
    source_schema = "prod_u1_123"
    target_schema = "prod_u1_456"

    result_sql = generate_copy_table_structures_sql(source_schema, target_schema)

    assert "DO $$" in result_sql
    assert "DECLARE" in result_sql
    assert "tbl RECORD;" in result_sql
    assert "BEGIN" in result_sql
    assert "FOR tbl IN" in result_sql
    assert "SELECT tablename" in result_sql
    assert "FROM pg_tables" in result_sql
    assert "WHERE schemaname = " in result_sql
    assert "CREATE TABLE" in result_sql
    assert "LIKE" in result_sql
    assert "INCLUDING ALL" in result_sql
    assert "END LOOP;" in result_sql
    assert "END;" in result_sql
    assert "$$ LANGUAGE plpgsql;" in result_sql

    assert f"schemaname = '{source_schema}'" in result_sql
    assert f"CREATE TABLE {target_schema}." in result_sql
    assert f"LIKE {source_schema}." in result_sql


def test_generate_copy_data_sql() -> None:
    """Test generate_copy_data_sql function."""
    source_schema = "test_u1_789"
    target_schema = "test_u1_012"

    result_sql = generate_copy_data_sql(source_schema, target_schema)

    assert "DO $$" in result_sql
    assert "DECLARE" in result_sql
    assert "tbl RECORD;" in result_sql
    assert "BEGIN" in result_sql
    assert "FOR tbl IN" in result_sql
    assert "SELECT tablename" in result_sql
    assert "FROM pg_tables" in result_sql
    assert "WHERE schemaname = " in result_sql
    assert "INSERT INTO" in result_sql
    assert "SELECT * FROM" in result_sql
    assert "END LOOP;" in result_sql
    assert "END;" in result_sql
    assert "$$ LANGUAGE plpgsql;" in result_sql

    assert f"schemaname = '{source_schema}'" in result_sql
    assert f"INSERT INTO {target_schema}." in result_sql
    assert f"SELECT * FROM {source_schema}." in result_sql


@pytest.mark.asyncio
async def test_clone_db_success() -> None:
    """Test clone_db endpoint success scenario."""
    mock_db = AsyncMock()
    mock_db.exec = AsyncMock(return_value=None)
    mock_db.commit = AsyncMock(return_value=None)
    mock_db.rollback = AsyncMock(return_value=None)

    mock_execute_result = MagicMock()
    mock_execute_result.first.return_value = ("u1", "old_db_name", "old_db_desc")
    mock_db.execute = AsyncMock(return_value=mock_execute_result)

    test_input = CloneDBInput(uid="u1", database_id=1, new_database_name="db2")

    fake_span_context = MagicMock()
    fake_span_context.sid = "clone-sid"
    fake_span_context.add_info_events = MagicMock()
    fake_span_context.record_exception = MagicMock()

    with patch(
        "memory.database.api.v1.db_operator.get_otlp_metric_service"
    ) as mock_metric_service_func:
        with patch(
            "memory.database.api.v1.db_operator.get_otlp_span_service"
        ) as mock_span_service_func:
            # Mock meter instance
            mock_meter_instance = MagicMock()
            mock_meter_instance.in_success_count = MagicMock()

            # Mock metric service
            mock_metric_service = MagicMock()
            mock_metric_service.get_meter.return_value = (
                lambda func: mock_meter_instance
            )
            mock_metric_service_func.return_value = mock_metric_service

            # Mock span service and instance
            mock_span_instance = MagicMock()
            mock_span_instance.start.return_value.__enter__.return_value = (
                fake_span_context
            )
            mock_span_service = MagicMock()
            mock_span_service.get_span.return_value = lambda uid: mock_span_instance
            mock_span_service_func.return_value = mock_span_service

            with patch(
                "memory.database.api.v1.db_operator.get_schema_name_by_did",
                new_callable=AsyncMock,
            ) as mock_get_schema:
                mock_get_schema.return_value = [["prod_schema"], ["test_schema"]]

                with patch(
                    "memory.database.api.v1.db_operator.exec_generate_schema",
                    new_callable=AsyncMock,
                ) as mock_exec_schema:

                    async def fake_exec_generate_schema(
                        *args: Any, **kwargs: Any
                    ) -> DatabaseInfo:  # pylint: disable=unused-argument
                        return DatabaseInfo(
                            database_id=456,
                            prod_schema="prod_new",
                            test_schema="test_new",
                        )

                    mock_exec_schema.side_effect = fake_exec_generate_schema

                    response = await clone_db(test_input, mock_db)

                    response_body = json.loads(response.body)
                    assert "code" in response_body
                    assert "data" in response_body
                    assert "sid" in response_body

                    assert response_body["code"] == 0
                    assert response_body["data"]["database_id"] == 456
                    assert response_body["sid"] == "clone-sid"

                    assert mock_get_schema.call_count == 1
                    mock_get_schema.assert_called_once_with(mock_db, 1)

                    mock_exec_schema.assert_called_once()
                    fake_span_context.add_info_events.assert_called()
                    mock_db.commit.assert_called_once()
                    mock_meter_instance.in_success_count.assert_called_once_with(
                        lables={"uid": "u1"}
                    )


@pytest.mark.asyncio
async def test_exec_generate_schema_success() -> None:
    """Test exec_generate_schema success scenario."""
    mock_db = AsyncMock(spec=AsyncSession)
    mock_db.exec = AsyncMock(return_value=None)
    mock_db.commit = AsyncMock(return_value=None)
    mock_db.add = AsyncMock(return_value=None)

    mock_span_context = MagicMock()
    mock_span_context.add_info_event = MagicMock()

    mock_snow_id = 1001
    with patch("memory.database.api.v1.db_operator.get_id", return_value=mock_snow_id):
        test_input = CreateDBInput(
            uid="u2",
            database_name="test_gen_schema_db",
            description="Test schema generation",
        )

        result = await exec_generate_schema(test_input, mock_span_context, mock_db)

        assert isinstance(result, DatabaseInfo)
        assert result.database_id == mock_snow_id
        assert result.prod_schema == f"prod_{test_input.uid}_{mock_snow_id}"
        assert result.test_schema == f"test_{test_input.uid}_{mock_snow_id}"

        exec_sql_texts = [
            call[0][0].text.strip() for call in mock_db.exec.call_args_list
        ]
        expected_prod_sql = 'CREATE SCHEMA IF NOT EXISTS "prod_u2_1001"'
        expected_test_sql = 'CREATE SCHEMA IF NOT EXISTS "test_u2_1001"'

        assert mock_db.exec.call_count == 2
        assert expected_prod_sql in exec_sql_texts
        assert expected_test_sql in exec_sql_texts

        added_records = [call[0][0] for call in mock_db.add.call_args_list]
        db_meta = next(rec for rec in added_records if isinstance(rec, DatabaseMeta))
        assert db_meta.id == mock_snow_id
        assert db_meta.uid == test_input.uid
        assert db_meta.name == test_input.database_name
        assert db_meta.description == test_input.description

        schema_metas = [rec for rec in added_records if isinstance(rec, SchemaMeta)]
        assert len(schema_metas) == 2
        assert {sm.schema_name for sm in schema_metas} == {
            "prod_u2_1001",
            "test_u2_1001",
        }
        assert all(sm.database_id == mock_snow_id for sm in schema_metas)

        mock_db.commit.assert_called_once()

        assert mock_span_context.add_info_event.call_count == 2
        mock_span_context.add_info_event.assert_any_call("prod_schema: prod_u2_1001")
        mock_span_context.add_info_event.assert_any_call("dev_schema: test_u2_1001")


@pytest.mark.asyncio
async def test_create_db_success() -> None:
    """Test create_db endpoint success scenario."""
    mock_db = AsyncMock()
    mock_db.exec = AsyncMock(return_value=None)
    mock_db.commit = AsyncMock(return_value=None)
    mock_db.rollback = AsyncMock(return_value=None)
    mock_db.add = AsyncMock(return_value=None)

    test_input = CreateDBInput(
        uid="u1",
        database_name="new_test_db",
        description="Test database for create API",
        space_id="",
    )

    fake_span_context = MagicMock()
    fake_span_context.sid = "create-sid"
    fake_span_context.add_info_events = MagicMock()
    fake_span_context.add_info_event = MagicMock()
    fake_span_context.record_exception = MagicMock()

    with patch(
        "memory.database.api.v1.db_operator.get_otlp_metric_service"
    ) as mock_metric_service_func:
        with patch(
            "memory.database.api.v1.db_operator.get_otlp_span_service"
        ) as mock_span_service_func:
            # Mock meter instance
            mock_meter_instance = MagicMock()
            mock_meter_instance.in_success_count = MagicMock()

            # Mock metric service
            mock_metric_service = MagicMock()
            mock_metric_service.get_meter.return_value = (
                lambda func: mock_meter_instance
            )
            mock_metric_service_func.return_value = mock_metric_service

            # Mock span service and instance
            mock_span_instance = MagicMock()
            mock_span_instance.start.return_value.__enter__.return_value = (
                fake_span_context
            )
            mock_span_service = MagicMock()
            mock_span_service.get_span.return_value = lambda uid: mock_span_instance
            mock_span_service_func.return_value = mock_span_service

            with patch(
                "memory.database.api.v1.db_operator.exec_generate_schema",
                new_callable=AsyncMock,
            ) as mock_exec_schema:
                mock_exec_schema.return_value = DatabaseInfo(
                    database_id=789,
                    prod_schema="prod_u1_789",
                    test_schema="test_u1_789",
                )

                response = await create_db(test_input, mock_db)

                response_body = json.loads(response.body)
                assert "code" in response_body
                assert "data" in response_body
                assert "sid" in response_body

                assert response_body["code"] == CodeEnum.Successes.code
                assert response_body["data"]["database_id"] == 789
                assert response_body["sid"] == "create-sid"


@pytest.mark.asyncio
async def test_drop_db_success() -> None:
    """Test drop_db endpoint success scenario."""
    mock_db = AsyncMock()
    mock_db.exec = AsyncMock(return_value=None)
    mock_db.commit = AsyncMock(return_value=None)
    mock_db.rollback = AsyncMock(return_value=None)

    test_input = DropDBInput(uid="u1", database_id=123, space_id="")

    fake_span_context = MagicMock()
    fake_span_context.sid = "drop-sid"
    fake_span_context.add_info_events = MagicMock()
    fake_span_context.record_exception = MagicMock()
    fake_span_context.report_exception = MagicMock()

    with patch(
        "memory.database.api.v1.db_operator.get_otlp_metric_service"
    ) as mock_metric_service_func:
        with patch(
            "memory.database.api.v1.db_operator.get_otlp_span_service"
        ) as mock_span_service_func:
            # Mock meter instance
            mock_meter_instance = MagicMock()
            mock_meter_instance.in_success_count = MagicMock()

            # Mock metric service
            mock_metric_service = MagicMock()
            mock_metric_service.get_meter.return_value = (
                lambda func: mock_meter_instance
            )
            mock_metric_service_func.return_value = mock_metric_service

            # Mock span service and instance
            mock_span_instance = MagicMock()
            mock_span_instance.start.return_value.__enter__.return_value = (
                fake_span_context
            )
            mock_span_service = MagicMock()
            mock_span_service.get_span.return_value = lambda uid: mock_span_instance
            mock_span_service_func.return_value = mock_span_service

            with patch(
                "memory.database.api.v1.db_operator.check_database_exists_by_did_uid",
                new_callable=AsyncMock,
            ) as mock_check_db:
                mock_check_db.return_value = (
                    [["prod_u1_123"], ["test_u1_123"]],
                    None,
                )

                with patch(
                    "memory.database.api.v1.db_operator.del_database_meta_by_did",
                    new_callable=AsyncMock,
                ) as mock_del_db_meta:
                    mock_del_db_meta.return_value = None

                    with patch(
                        "memory.database.api.v1.db_operator.del_schema_meta_by_did",
                        new_callable=AsyncMock,
                    ) as mock_del_schema_meta:
                        mock_del_schema_meta.return_value = None

                        response = await drop_db(test_input, mock_db)

                        response_body = json.loads(response.body)
                        assert "code" in response_body
                        assert "sid" in response_body
                        assert response_body["code"] == CodeEnum.Successes.code
                        assert response_body["sid"] == "drop-sid"
                        assert "data" not in response_body


@pytest.mark.asyncio
async def test_modify_db_description_success() -> None:
    """Test modify_db_description endpoint success scenario."""
    mock_db = AsyncMock()
    mock_db.commit = AsyncMock(return_value=None)
    mock_db.rollback = AsyncMock(return_value=None)

    test_input = ModifyDBDescInput(
        uid="u1",
        database_id=123,
        description="Updated test database description",
        space_id="",
    )

    fake_span_context = MagicMock()
    fake_span_context.sid = "modify-desc-sid"
    fake_span_context.add_info_events = MagicMock()
    fake_span_context.add_info_event = MagicMock()
    fake_span_context.record_exception = MagicMock()

    with patch(
        "memory.database.api.v1.db_operator.get_otlp_metric_service"
    ) as mock_metric_service_func:
        with patch(
            "memory.database.api.v1.db_operator.get_otlp_span_service"
        ) as mock_span_service_func:
            # Mock meter instance
            mock_meter_instance = MagicMock()
            mock_meter_instance.in_success_count = MagicMock()

            # Mock metric service
            mock_metric_service = MagicMock()
            mock_metric_service.get_meter.return_value = (
                lambda func: mock_meter_instance
            )
            mock_metric_service_func.return_value = mock_metric_service

            # Mock span service and instance
            mock_span_instance = MagicMock()
            mock_span_instance.start.return_value.__enter__.return_value = (
                fake_span_context
            )
            mock_span_service = MagicMock()
            mock_span_service.get_span.return_value = lambda uid: mock_span_instance
            mock_span_service_func.return_value = mock_span_service

            with patch(
                "memory.database.api.v1.db_operator.get_id_by_did_uid",
                new_callable=AsyncMock,
            ) as mock_get_id:
                mock_get_id.return_value = [123]  # Database exists

                with patch(
                    "memory.database.api.v1.db_operator."
                    "update_database_meta_by_did_uid",
                    new_callable=AsyncMock,
                ) as mock_update_db_meta:
                    mock_update_db_meta.return_value = None

                    response = await modify_db_description(test_input, mock_db)

                    response_body = json.loads(response.body)
                    assert "code" in response_body
                    assert "message" in response_body
                    assert "sid" in response_body

                    assert response_body["code"] == CodeEnum.Successes.code
                    assert response_body["message"] == CodeEnum.Successes.msg
                    assert response_body["sid"] == "modify-desc-sid"

                    mock_get_id.assert_called_once_with(
                        mock_db, database_id=123, uid="u1"
                    )
                    mock_update_db_meta.assert_called_once_with(
                        mock_db,
                        database_id=123,
                        uid="u1",
                        description="Updated test database description",
                    )
                    mock_db.commit.assert_called_once()
                    mock_meter_instance.in_success_count.assert_called_once_with(
                        lables={"uid": "u1"}
                    )
                    fake_span_context.add_info_events.assert_called()


@pytest.mark.asyncio
async def test_modify_db_description_database_not_exist() -> None:
    """Test modify_db_description endpoint when database does not exist."""
    mock_db = AsyncMock()
    mock_db.rollback = AsyncMock(return_value=None)

    test_input = ModifyDBDescInput(
        uid="u1",
        database_id=999,
        description="New description",
        space_id="",
    )

    fake_span_context = MagicMock()
    fake_span_context.sid = "modify-desc-error-sid"
    fake_span_context.add_info_events = MagicMock()
    fake_span_context.add_info_event = MagicMock()
    fake_span_context.add_error_event = MagicMock()

    with patch(
        "memory.database.api.v1.db_operator.get_otlp_metric_service"
    ) as mock_metric_service_func:
        with patch(
            "memory.database.api.v1.db_operator.get_otlp_span_service"
        ) as mock_span_service_func:
            # Mock meter instance
            mock_meter_instance = MagicMock()
            mock_meter_instance.in_error_count = MagicMock()

            # Mock metric service
            mock_metric_service = MagicMock()
            mock_metric_service.get_meter.return_value = (
                lambda func: mock_meter_instance
            )
            mock_metric_service_func.return_value = mock_metric_service

            # Mock span service and instance
            mock_span_instance = MagicMock()
            mock_span_instance.start.return_value.__enter__.return_value = (
                fake_span_context
            )
            mock_span_service = MagicMock()
            mock_span_service.get_span.return_value = lambda uid: mock_span_instance
            mock_span_service_func.return_value = mock_span_service

            with patch(
                "memory.database.api.v1.db_operator.get_id_by_did_uid",
                new_callable=AsyncMock,
            ) as mock_get_id:
                mock_get_id.return_value = None  # Database does not exist

                response = await modify_db_description(test_input, mock_db)

                response_body = json.loads(response.body)
                assert "code" in response_body
                assert "message" in response_body
                assert "sid" in response_body

                assert response_body["code"] == CodeEnum.DatabaseNotExistError.code
                assert (
                    "uid: u1 or database_id: 999 error, please verify"
                    in response_body["message"]
                )
                assert response_body["sid"] == "modify-desc-error-sid"

                mock_get_id.assert_called_once_with(mock_db, database_id=999, uid="u1")
                mock_meter_instance.in_error_count.assert_called_once_with(
                    CodeEnum.DatabaseNotExistError.code,
                    lables={"uid": "u1"},
                    span=fake_span_context,
                )
                fake_span_context.add_error_event.assert_called_with(
                    "User: u1 does not have database: 999"
                )


@pytest.mark.asyncio
async def test_modify_db_description_with_space_id() -> None:
    """Test modify_db_description endpoint with space_id."""
    mock_db = AsyncMock()
    mock_db.commit = AsyncMock(return_value=None)
    mock_db.rollback = AsyncMock(return_value=None)

    test_input = ModifyDBDescInput(
        uid="u1",
        database_id=123,
        description="Updated description for team space",
        space_id="space123",
    )

    fake_span_context = MagicMock()
    fake_span_context.sid = "modify-desc-space-sid"
    fake_span_context.add_info_events = MagicMock()
    fake_span_context.add_info_event = MagicMock()
    fake_span_context.record_exception = MagicMock()

    with patch(
        "memory.database.api.v1.db_operator.get_otlp_metric_service"
    ) as mock_metric_service_func:
        with patch(
            "memory.database.api.v1.db_operator.get_otlp_span_service"
        ) as mock_span_service_func:
            # Mock meter instance
            mock_meter_instance = MagicMock()
            mock_meter_instance.in_success_count = MagicMock()

            # Mock metric service
            mock_metric_service = MagicMock()
            mock_metric_service.get_meter.return_value = (
                lambda func: mock_meter_instance
            )
            mock_metric_service_func.return_value = mock_metric_service

            # Mock span service and instance
            mock_span_instance = MagicMock()
            mock_span_instance.start.return_value.__enter__.return_value = (
                fake_span_context
            )
            mock_span_service = MagicMock()
            mock_span_service.get_span.return_value = lambda uid: mock_span_instance
            mock_span_service_func.return_value = mock_span_service

            with patch(
                "memory.database.api.v1.db_operator.get_uid_by_did_space_id",
                new_callable=AsyncMock,
            ) as mock_get_uid_by_space:
                mock_get_uid_by_space.return_value = [["team_u1"]]

                with patch(
                    "memory.database.api.v1.db_operator.get_id_by_did_uid",
                    new_callable=AsyncMock,
                ) as mock_get_id:
                    mock_get_id.return_value = [123]  # Database exists

                    with patch(
                        "memory.database.api.v1.db_operator."
                        "update_database_meta_by_did_uid",
                        new_callable=AsyncMock,
                    ) as mock_update_db_meta:
                        mock_update_db_meta.return_value = None

                        response = await modify_db_description(test_input, mock_db)

                        response_body = json.loads(response.body)
                        assert "code" in response_body
                        assert "message" in response_body
                        assert "sid" in response_body

                        assert response_body["code"] == CodeEnum.Successes.code
                        assert response_body["message"] == CodeEnum.Successes.msg
                        assert response_body["sid"] == "modify-desc-space-sid"

                        mock_get_uid_by_space.assert_called_once_with(
                            mock_db, 123, "space123"
                        )
                        mock_get_id.assert_called_once_with(
                            mock_db, database_id=123, uid="team_u1"
                        )
                        mock_update_db_meta.assert_called_once_with(
                            mock_db,
                            database_id=123,
                            uid="team_u1",
                            description=("Updated description for team space"),
                        )
                        mock_db.commit.assert_called_once()
                        fake_span_context.add_info_event.assert_any_call(
                            "space_id: space123"
                        )
