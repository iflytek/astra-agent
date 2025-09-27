"""Unit tests for data upload functionality."""

import io
import json
from unittest.mock import AsyncMock, MagicMock, patch

import pytest
from memory.database.api.schemas.upload_data_types import UploadDataInput
from memory.database.api.v1.upload_data import (insert_in_batches,
                                                parse_upload_file, upload_data)
from memory.database.exceptions.error_code import CodeEnum
from sqlmodel.ext.asyncio.session import AsyncSession
from starlette.responses import JSONResponse


@pytest.mark.asyncio
async def test_parse_upload_file_success_csv() -> None:
    """Test parse_upload_file function (success scenario: CSV file)."""
    csv_content = "name,age,city\nAlice,25,Beijing\nBob,30,Shanghai"
    mock_file = MagicMock()
    mock_file.filename = "test_data.csv"
    mock_file.read = AsyncMock(
        return_value=io.BytesIO(csv_content.encode("utf-8")).read()
    )

    columns, records, line_numbers = await parse_upload_file(file=mock_file)

    assert columns == ["name", "age", "city"]
    expected_records = [
        {"name": "Alice", "age": 25, "city": "Beijing"},
        {"name": "Bob", "age": 30, "city": "Shanghai"},
    ]
    assert len(records) == 2

    for i, record in enumerate(records):
        record["age"] = int(record["age"])
        assert record == expected_records[i]

    assert line_numbers == [2, 3]


@pytest.mark.asyncio
async def test_insert_in_batches_success() -> None:
    """Test insert_in_batches function (success scenario)."""
    mock_db = AsyncMock(spec=AsyncSession)
    mock_db.execute = AsyncMock(return_value=None)

    with patch("memory.database.api.v1.upload_data.get_id") as mock_get_id:
        mock_get_id.side_effect = [10001, 10002]

        table_name = "user_info"
        records = [{"name": "Alice", "age": 25}, {"name": "Bob", "age": 30}]
        line_numbers = [2, 3]
        uid = "u1"
        batch_size = 500
        fake_span_context = MagicMock()
        fake_span_context.add_info_events = MagicMock()

        success_rows, failed_rows = await insert_in_batches(
            db=mock_db,
            table_name=table_name,
            records=records,
            line_numbers=line_numbers,
            uid=uid,
            batch_size=batch_size,
            span_context=fake_span_context,
        )

        assert len(success_rows) == 2
        assert success_rows == [10001, 10002]
        assert len(failed_rows) == 0
        assert mock_db.execute.call_count == 2

        first_call_args = mock_db.execute.call_args_list[0][0]
        first_sql = first_call_args[0]
        first_params = first_call_args[1]
        assert 'INSERT INTO "user_info"' in str(first_sql)
        assert first_params == {
            "name": "Alice",
            "age": 25,
            "id": 10001,
            "uid": "u1",
        }

        second_call_args = mock_db.execute.call_args_list[1][0]
        second_params = second_call_args[1]
        assert second_params == {
            "name": "Bob",
            "age": 30,
            "id": 10002,
            "uid": "u1",
        }

        assert mock_get_id.call_count == 2
        fake_span_context.add_info_events.assert_called_once()


@pytest.mark.asyncio
async def test_upload_data_success() -> None:
    """Test upload_data endpoint (success scenario)."""
    mock_db = AsyncMock(spec=AsyncSession)
    mock_db.commit = AsyncMock(return_value=None)
    mock_db.rollback = AsyncMock(return_value=None)

    test_input = UploadDataInput(
        app_id="app_789",
        uid="user_999",
        database_id=5001,
        table_name="user_profiles",
        env="prod",
    )

    mock_file = MagicMock()
    mock_file.filename = "valid_user_data.csv"
    mock_file.read = AsyncMock(
        return_value=io.BytesIO(b"name,age\nAlice,25\nBob,30").read()
    )

    fake_span_context = MagicMock()
    fake_span_context.sid = "upload-data-sid-001"
    fake_span_context.add_info_events = MagicMock()
    fake_span_context.record_exception = MagicMock()
    fake_span_context.add_info_event = MagicMock()

    mock_span_instance = MagicMock()
    mock_span_instance.start.return_value.__enter__.return_value = fake_span_context

    mock_parse_file = AsyncMock()
    mock_parse_file.return_value = (
        ["name", "age"],
        [{"name": "Alice", "age": 25}, {"name": "Bob", "age": 30}],
        [2, 3],
    )

    mock_insert = AsyncMock()
    mock_insert.return_value = ([90001, 90002], [])

    mock_db_execute = AsyncMock(
        side_effect=[
            None,
            MagicMock(
                fetchall=MagicMock(
                    return_value=[("name",), ("age",), ("id",), ("uid",)]
                )
            ),
        ]
    )
    mock_db.execute = mock_db_execute

    mock_get_id = MagicMock(side_effect=[90001, 90002])

    # Mock meter instance
    mock_meter_instance = MagicMock()
    mock_meter_instance.in_success_count = MagicMock()
    mock_meter_instance.in_error_count = MagicMock()

    with patch(
        "memory.database.api.v1.upload_data.get_otlp_metric_service"
    ) as mock_metric_service_func:
        with patch(
            "memory.database.api.v1.upload_data.get_otlp_span_service"
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
                "memory.database.api.v1.upload_data.parse_upload_file",
                new=mock_parse_file,
            ):
                with patch(
                    "memory.database.api.v1.upload_data.insert_in_batches",
                    new=mock_insert,
                ):
                    with patch(
                        "memory.database.api.v1.upload_data.get_id", new=mock_get_id
                    ):
                        response = await upload_data(
                            app_id=test_input.app_id,
                            database_id=test_input.database_id,
                            uid=test_input.uid,
                            table_name=test_input.table_name,
                            env=test_input.env,
                            file=mock_file,
                            db=mock_db,
                        )

                        assert isinstance(response, JSONResponse)
                        response_body = json.loads(response.body)
                        assert response_body["code"] == CodeEnum.Successes.code
                        assert response_body["message"] == "success"
                        assert response_body["data"]["success_rows"] == [90001, 90002]
                        assert response_body["data"]["failed_rows"] == []

                        # Test completed successfully
                        mock_parse_file.assert_called_once_with(mock_file)

                    mock_insert.assert_called_once_with(
                        mock_db,
                        test_input.table_name,
                        [{"name": "Alice", "age": 25}, {"name": "Bob", "age": 30}],
                        [2, 3],
                        test_input.uid,
                        span_context=fake_span_context,
                    )

                    expected_schema = (
                        f"{test_input.env}_{test_input.uid}_{test_input.database_id}"
                    )
                    assert any(
                        str(call_args.args[0]).startswith(
                            f'SET search_path TO "{expected_schema}"'
                        )
                        for call_args in mock_db.execute.call_args_list
                    )

                    mock_db.commit.assert_called_once()
                    mock_db.rollback.assert_not_called()
