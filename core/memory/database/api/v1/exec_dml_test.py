"""Unit tests for DML execution functionality."""

import datetime
import decimal
import json
import uuid
from typing import List
from unittest.mock import AsyncMock, MagicMock, patch

import pytest
from memory.database.api.schemas.exec_dml_types import ExecDMLInput
from memory.database.api.v1.exec_dml import (_dml_add_where,
                                             _dml_insert_add_params,
                                             _dml_split, _exec_dml_sql,
                                             _set_search_path, exec_dml,
                                             rewrite_dml_with_uid_and_limit,
                                             to_jsonable)
from sqlglot import parse_one
from sqlmodel.ext.asyncio.session import AsyncSession


def test_rewrite_dml_with_uid_and_limit() -> None:
    """Test SQL rewrite function (add WHERE conditions and LIMIT)."""
    span_context = MagicMock()
    test_dml = "SELECT * FROM users WHERE age > 18"
    app_id = "app123"
    uid = "user456"
    limit_num = 100
    env = "prod"

    rewritten_sql, insert_ids = rewrite_dml_with_uid_and_limit(
        dml=test_dml,
        app_id=app_id,
        uid=uid,
        limit_num=limit_num,
        env=env,
        span_context=span_context,
    )

    assert (
        "WHERE (age > 18) AND users.uid IN ('user456', 'app123:user456')"
        in rewritten_sql
    )
    assert "LIMIT 100" in rewritten_sql
    assert insert_ids == []


def test_to_jsonable() -> None:
    """Test data type conversion for JSON serialization."""
    test_data = {
        "datetime": datetime.datetime(2023, 1, 1, 12, 0, 0),
        "decimal": decimal.Decimal("100.50"),
        "uuid": uuid.UUID("123e4567-e89b-12d3-a456-426614174000"),
        "list": [datetime.datetime(2023, 1, 1), set([1, 2, 3])],
    }

    result = to_jsonable(test_data)

    assert result["datetime"] == "2023-01-01 12:00:00"
    assert result["decimal"] == 100.5
    assert result["uuid"] == "123e4567-e89b-12d3-a456-426614174000"
    assert result["list"][0] == "2023-01-01 00:00:00"
    assert sorted(result["list"][1]) == [1, 2, 3]


@pytest.mark.asyncio
async def test_set_search_path_success() -> None:
    """Test search path setting (success scenario)."""
    mock_db = AsyncMock(spec=AsyncSession)
    mock_span_context = MagicMock()
    mock_meter = MagicMock()

    with patch(
        "memory.database.api.v1.exec_dml.set_search_path_by_schema",
        new_callable=AsyncMock,
    ) as mock_set_search:
        mock_set_search.return_value = None

        schema, error = await _set_search_path(
            db=mock_db,
            schema_list=[["prod_u1_1001"], ["test_u1_1001"]],
            env="prod",
            uid="u1",
            span_context=mock_span_context,
            m=mock_meter,
        )

        assert error is None
        assert schema == "prod_u1_1001"
        mock_set_search.assert_called_once_with(mock_db, "prod_u1_1001")
        mock_span_context.add_info_event.assert_called_with("schema: prod_u1_1001")


@pytest.mark.asyncio
async def test_dml_split_success() -> None:
    """Test SQL splitting and validation (success scenario)."""
    mock_db = AsyncMock(spec=AsyncSession)
    mock_span_context = MagicMock()
    mock_meter = MagicMock()

    mock_result = MagicMock()
    mock_result.fetchall.return_value = [("users",)]
    with patch(
        "memory.database.api.v1.exec_dml.parse_and_exec_sql", new_callable=AsyncMock
    ) as mock_parse_exec:
        mock_parse_exec.return_value = mock_result

        dmls, error = await _dml_split(
            dml="SELECT * FROM users;",
            db=mock_db,
            schema="prod_u1_1001",
            uid="u1",
            span_context=mock_span_context,
            m=mock_meter,
        )

        assert error is None
        assert dmls == ["SELECT * FROM users;"]
        mock_parse_exec.assert_called_once()
        mock_span_context.add_info_event.assert_any_call(
            "Split DML statements: ['SELECT * FROM users;']"
        )


@pytest.mark.asyncio
async def test_exec_dml_sql_success() -> None:
    """Test SQL execution (success scenario)."""
    mock_db = AsyncMock(spec=AsyncSession)
    mock_span_context = MagicMock()
    mock_meter = MagicMock()

    mock_result = MagicMock()
    mock_result.mappings.return_value.all.return_value = []
    with patch(
        "memory.database.api.v1.exec_dml.exec_sql_statement", new_callable=AsyncMock
    ) as mock_exec:
        mock_exec.return_value = mock_result

        rewrite_dmls = [
            {
                "rewrite_dml": "INSERT INTO users (name) VALUES ('test')",
                "insert_ids": [9001, 9002],
            }
        ]

        result, exec_time, error = await _exec_dml_sql(
            db=mock_db,
            rewrite_dmls=rewrite_dmls,
            uid="u1",
            span_context=mock_span_context,
            m=mock_meter,
        )

        assert error is None
        assert result == [{"id": 9001}, {"id": 9002}]
        assert isinstance(exec_time, float)
        mock_exec.assert_called_once_with(
            mock_db, "INSERT INTO users (name) VALUES ('test')"
        )
        mock_db.commit.assert_called_once()


def test_dml_add_where() -> None:
    """Test WHERE condition addition."""
    dml = "UPDATE users SET name = 'test' WHERE age > 18"
    parsed = parse_one(dml)
    tables = ["users"]
    app_id = "app123"
    uid = "user456"

    _dml_add_where(parsed, tables, app_id, uid)

    where_sql = parsed.args["where"].sql()
    assert "(age > 18)" in where_sql
    assert "users.uid IN ('user456', 'app123:user456')" in where_sql


def test_dml_insert_add_params() -> None:
    """Test INSERT statement parameter addition."""
    dml = "INSERT INTO users (name) VALUES ('test')"
    parsed = parse_one(dml)
    insert_id: List[int] = []
    app_id = "app123"
    uid = "user456"

    _dml_insert_add_params(parsed, insert_id, app_id, uid)

    columns = [col.name for col in parsed.args["this"].expressions]
    assert "id" in columns
    assert "uid" in columns
    assert "name" in columns
    assert len(insert_id) == 1
    assert isinstance(insert_id[0], int)


@pytest.mark.asyncio
async def test_exec_dml_success() -> None:
    """Test exec_dml endpoint (success scenario)."""
    mock_db = AsyncMock(spec=AsyncSession)
    mock_db.commit = AsyncMock(return_value=None)
    mock_db.rollback = AsyncMock(return_value=None)

    test_input = ExecDMLInput(
        app_id="app789",
        uid="u1",
        database_id=1001,
        dml="SELECT name FROM users WHERE age > 18;",
        env="prod",
        space_id="",
    )

    fake_span_context = MagicMock()
    fake_span_context.sid = "exec-dml-sid-001"
    fake_span_context.add_info_events = MagicMock()
    fake_span_context.add_info_event = MagicMock()
    fake_span_context.record_exception = MagicMock()
    fake_span_context.add_error_event = MagicMock()

    with patch("memory.database.api.v1.exec_dml.Span") as mock_span_cls:
        mock_span_instance = MagicMock()
        mock_span_instance.start.return_value.__enter__.return_value = fake_span_context
        mock_span_cls.return_value = mock_span_instance

        with patch(
            "memory.database.api.v1.exec_dml.check_space_id_and_get_uid",
            new_callable=AsyncMock,
        ) as mock_check_space:
            mock_check_space.return_value = None

            with patch(
                "memory.database.api.v1.exec_dml.check_database_exists_by_did",
                new_callable=AsyncMock,
            ) as mock_check_db:
                mock_check_db.return_value = (
                    [["prod_u1_1001"], ["test_u1_1001"]],
                    None,
                )

                with patch(
                    "memory.database.api.v1.exec_dml._dml_split", new_callable=AsyncMock
                ) as mock_dml_split:
                    mock_dml_split.return_value = (
                        ["SELECT name FROM users WHERE age > 18;"],
                        None,
                    )

                    with patch(
                        "memory.database.api.v1.exec_dml._set_search_path",
                        new_callable=AsyncMock,
                    ) as mock_set_search:
                        mock_set_search.return_value = ("prod_u1_1001", None)

                        with patch(
                            "memory.database.api.v1.exec_dml.rewrite_dml_with_uid_and_limit"
                        ) as mock_rewrite:
                            mock_rewrite.return_value = (
                                "SELECT name FROM users WHERE age > 18 "
                                "AND users.uid IN ('u1', 'app789:u1') LIMIT 100",
                                [],
                            )

                            with patch(
                                "memory.database.api.v1.exec_dml.exec_sql_statement",
                                new_callable=AsyncMock,
                            ) as mock_exec_sql:
                                select_result = MagicMock()
                                select_result.mappings.return_value.all.return_value = [
                                    {"name": "test_user"}
                                ]
                                mock_exec_sql.return_value = select_result

                                with patch(
                                    "memory.database.api.v1.exec_dml.get_otlp_metric_service"
                                ) as mock_metric_service_func:
                                    with patch(
                                        "memory.database.api.v1.exec_dml.get_otlp_span_service"
                                    ) as mock_span_service_func:
                                        # Mock meter instance
                                        mock_meter_instance = MagicMock()
                                        mock_meter_instance.in_success_count = (
                                            MagicMock()
                                        )
                                        mock_meter_instance.in_error_count = MagicMock()

                                        # Mock metric service
                                        mock_metric_service = MagicMock()
                                        mock_metric_service.get_meter.return_value = (
                                            lambda func: mock_meter_instance
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
                                        mock_span_service_func.return_value = (
                                            mock_span_service
                                        )

                                        response = await exec_dml(test_input, mock_db)

                                        resp_body = json.loads(response.body)
                                        assert "code" in resp_body
                                        assert "message" in resp_body
                                        assert "sid" in resp_body
                                        assert "data" in resp_body
