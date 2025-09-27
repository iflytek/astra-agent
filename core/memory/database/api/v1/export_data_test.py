"""Unit tests for data export functionality."""

from unittest.mock import AsyncMock, MagicMock, patch

import pytest
from fastapi.responses import StreamingResponse
from memory.database.api.schemas.export_data_types import ExportDataInput
from memory.database.api.v1.export_data import (_set_search_path_and_exec,
                                                export_data)
from sqlmodel.ext.asyncio.session import AsyncSession


@pytest.mark.asyncio
async def test_set_search_path_and_exec_success() -> None:
    """Test _set_search_path_and_exec function (success scenario)."""
    mock_db = AsyncMock(spec=AsyncSession)
    executed_calls = []

    async def mock_execute(sql, params=None):  # type: ignore[no-untyped-def]
        executed_calls.append((str(sql), params))
        if "SET search_path" in str(sql):
            return None
        mock_result = MagicMock()
        mock_result.fetchall.return_value = [
            (1, "u1", "test_data"),
            (2, "u1", "demo_data"),
        ]
        mock_result.keys.return_value = ["id", "uid", "content"]
        return mock_result

    mock_db.execute = AsyncMock(side_effect=mock_execute)

    database_id = 2001
    table_name = "user_data"
    env = "prod"
    uid = "u1"
    expected_schema = f"{env}_{uid}_{database_id}"

    fake_span_context = MagicMock()
    fake_span_context.add_info_event = MagicMock()
    fake_span_context.record_exception = MagicMock()

    mock_meter = MagicMock()
    mock_meter.in_error_count = MagicMock()

    rows, columns, error_resp = await _set_search_path_and_exec(
        db=mock_db,
        database_id=database_id,
        table_name=table_name,
        env=env,
        uid=uid,
        span_context=fake_span_context,
        m=mock_meter,
    )

    assert error_resp is None
    assert len(rows) == 2
    assert rows == [(1, "u1", "test_data"), (2, "u1", "demo_data")]
    assert columns == ["id", "uid", "content"]

    assert len(executed_calls) == 2
    set_call_sql, set_call_params = executed_calls[0]
    select_call_sql, select_call_params = executed_calls[1]

    expected_set_sql = f'SET search_path TO "{expected_schema}"'
    assert expected_set_sql in set_call_sql
    assert set_call_params is None or set_call_params == {}

    expected_select_table = f'FROM "{table_name}"'
    expected_select_where = "uid = :uid"
    assert expected_select_table in select_call_sql
    assert expected_select_where in select_call_sql
    assert select_call_params == {"uid": uid}

    fake_span_context.add_info_event.assert_called_once_with(
        f"schema: {expected_schema}"
    )
    mock_meter.in_error_count.assert_not_called()


@pytest.mark.asyncio
async def test_export_data_success() -> None:
    """Test export_data endpoint (success scenario)."""
    mock_db = AsyncMock(spec=AsyncSession)

    test_input = ExportDataInput(
        app_id="app999",
        uid="u1",
        database_id=2001,
        table_name="user_data",
        env="prod",
    )

    fake_span_context = MagicMock()
    fake_span_context.sid = "export-data-sid-001"
    fake_span_context.add_info_events = MagicMock()
    fake_span_context.record_exception = MagicMock()

    mock_span_instance = MagicMock()
    mock_span_instance.start.return_value.__enter__.return_value = fake_span_context

    mock_meter_instance = MagicMock()
    mock_meter_instance.in_success_count = MagicMock()
    mock_meter_instance.in_error_count = MagicMock()

    mock_set_exec = AsyncMock()
    mock_set_exec.return_value = (
        [(1, "u1", "test_data"), (2, "u1", "demo_data")],
        ["id", "uid", "content"],
        None,
    )

    with patch(
        "memory.database.api.v1.export_data.get_otlp_metric_service"
    ) as mock_metric_service_func:
        with patch(
            "memory.database.api.v1.export_data.get_otlp_span_service"
        ) as mock_span_service_func:
            # Mock the metric service
            mock_metric_service = MagicMock()
            mock_metric_service.get_meter.return_value = (
                lambda func: mock_meter_instance
            )
            mock_metric_service_func.return_value = mock_metric_service

            # Mock the span service
            mock_span_service = MagicMock()
            mock_span_service.get_span.return_value = lambda uid: mock_span_instance
            mock_span_service_func.return_value = mock_span_service

            with patch(
                "memory.database.api.v1.export_data._set_search_path_and_exec",
                new=mock_set_exec,
            ):
                response = await export_data(export_input=test_input, db=mock_db)

                assert isinstance(response, StreamingResponse)
                assert response.media_type == "text/csv"
                expected_filename = f"{test_input.table_name}_export.csv"
                assert (
                    response.headers["Content-Disposition"]
                    == f"attachment; filename={expected_filename}"
                )

                # Test completed successfully - service mocks work
                # Verify that export function was called properly
                mock_set_exec.assert_called_once()

                expected_info = {
                    "app_id": test_input.app_id,
                    "database_id": test_input.database_id,
                    "uid": test_input.uid,
                    "table_name": test_input.table_name,
                    "env": test_input.env,
                }
                fake_span_context.add_info_events.assert_called_once_with(expected_info)

                mock_set_exec.assert_called_once_with(
                    mock_db,
                    test_input.database_id,
                    test_input.table_name,
                    test_input.env,
                    test_input.uid,
                    fake_span_context,
                    mock_meter_instance,
                )

                mock_meter_instance.in_success_count.assert_called_once_with(
                    lables={"uid": test_input.uid}
                )
