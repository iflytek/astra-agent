from typing import Union

from exceptions.codes import (
    c_10000,
    c_10001,
    c_10002,
    c_10003,
    c_10004,
    c_10005,
    c_10006,
    c_10007,
    c_10008,
    c_10009,
    c_10010,
    c_10011,
    c_10012,
    c_10013,
    c_10014,
    c_10015,
    c_10016,
    c_10017,
    c_10019,
    c_10110,
    c_10163,
    c_10222,
    c_10907,
    c_11200,
    c_11201,
    c_11202,
    c_11203,
    c_40301,
    c_40303,
    c_40350,
    c_40351,
    c_40352,
    c_40353,
    c_40354,
    c_40355,
    c_40356,
    c_40357,
    c_40358,
    c_40359,
    c_40360,
    c_40361,
    c_40362,
    c_40363,
    c_40364,
    c_40365,
    c_40366,
    c_40367,
    c_40368,
    c_40369,
    c_40370,
    c_40371,
    c_40372,
    c_40373,
    c_40374,
    c_40375,
    c_40376,
)


class IfyTekLLMCodes:
    # Spark errors
    SparkWSError = c_10000
    SparkWSReadError = c_10001
    SparkWSSendError = c_10002
    SparkMessageFormatError = c_10003
    SparkSchemaError = c_10004
    SparkParamError = c_10005
    SparkConcurrencyError = c_10006
    SparkTrafficLimitError = c_10007
    SparkCapacityError = c_10008
    SparkEngineConnectionError = c_10009
    SparkEngineReceiveError = c_10010
    SparkEngineSendError = c_10011
    SparkEngineInternalError = c_10012
    SparkContentAuditError = c_10013
    SparkOutputAuditError = c_10014
    SparkAppIdBlacklistError = c_10015
    SparkAppIdAuthError = c_10016
    SparkClearHistoryError = c_10017
    SparkViolationError = c_10019
    SparkBusyError = c_10110
    SparkEngineParamsError = c_10163
    SparkEngineNetworkError = c_10222
    SparkTokenLimitError = c_10907
    SparkAuthError = c_11200
    SparkDailyLimitError = c_11201
    SparkSecondLimitError = c_11202
    SparkConcurrencyLimitError = c_11203


class WorkflowLLMCodes:
    # Spark exceptions
    SparkFunctionNotChoiceError = c_40301
    SparkRequestError = c_40303
    # Spark service error code mapping 20350-20376
    SparkWSError = c_40350
    SparkWSReadError = c_40351
    SparkWSSendError = c_40352
    SparkMessageFormatError = c_40353
    SparkSchemaError = c_40354
    SparkParamError = c_40355
    SparkConcurrencyError = c_40356
    SparkTrafficLimitError = c_40357
    SparkCapacityError = c_40358
    SparkEngineConnectionError = c_40359
    SparkEngineReceiveError = c_40360
    SparkEngineSendError = c_40361
    SparkEngineInternalError = c_40362
    SparkContentAuditError = c_40363
    SparkOutputAuditError = c_40364
    SparkAppIdBlacklistError = c_40365
    SparkAppIdAuthError = c_40366
    SparkClearHistoryError = c_40367
    SparkViolationError = c_40368
    SparkBusyError = c_40369
    SparkEngineParamsError = c_40370
    SparkEngineNetworkError = c_40371
    SparkTokenLimitError = c_40372
    SparkAuthError = c_40373
    SparkDailyLimitError = c_40374
    SparkSecondLimitError = c_40375
    SparkConcurrencyLimitError = c_40376


# Error code mapping dictionary for better maintainability
_CODE_MAPPING: dict[Union[str, int, tuple[int, str]], tuple[int, str]] = {
    "invalid_text_request": WorkflowLLMCodes.SparkParamError,
    "one_api_error": WorkflowLLMCodes.SparkAppIdAuthError,
    IfyTekLLMCodes.SparkWSError: WorkflowLLMCodes.SparkWSError,
    IfyTekLLMCodes.SparkWSReadError: WorkflowLLMCodes.SparkWSReadError,
    IfyTekLLMCodes.SparkWSSendError: WorkflowLLMCodes.SparkWSSendError,
    IfyTekLLMCodes.SparkMessageFormatError: WorkflowLLMCodes.SparkMessageFormatError,
    IfyTekLLMCodes.SparkSchemaError: WorkflowLLMCodes.SparkSchemaError,
    IfyTekLLMCodes.SparkParamError: WorkflowLLMCodes.SparkParamError,
    IfyTekLLMCodes.SparkConcurrencyError: WorkflowLLMCodes.SparkConcurrencyError,
    IfyTekLLMCodes.SparkTrafficLimitError: WorkflowLLMCodes.SparkTrafficLimitError,
    IfyTekLLMCodes.SparkCapacityError: WorkflowLLMCodes.SparkCapacityError,
    IfyTekLLMCodes.SparkEngineConnectionError: WorkflowLLMCodes.SparkEngineConnectionError,
    IfyTekLLMCodes.SparkEngineReceiveError: WorkflowLLMCodes.SparkEngineReceiveError,
    IfyTekLLMCodes.SparkEngineSendError: WorkflowLLMCodes.SparkEngineSendError,
    IfyTekLLMCodes.SparkEngineInternalError: WorkflowLLMCodes.SparkEngineInternalError,
    IfyTekLLMCodes.SparkContentAuditError: WorkflowLLMCodes.SparkContentAuditError,
    IfyTekLLMCodes.SparkOutputAuditError: WorkflowLLMCodes.SparkOutputAuditError,
    IfyTekLLMCodes.SparkAppIdBlacklistError: WorkflowLLMCodes.SparkAppIdBlacklistError,
    IfyTekLLMCodes.SparkAppIdAuthError: WorkflowLLMCodes.SparkAppIdAuthError,
    IfyTekLLMCodes.SparkClearHistoryError: WorkflowLLMCodes.SparkClearHistoryError,
    IfyTekLLMCodes.SparkViolationError: WorkflowLLMCodes.SparkViolationError,
    IfyTekLLMCodes.SparkBusyError: WorkflowLLMCodes.SparkBusyError,
    IfyTekLLMCodes.SparkEngineParamsError: WorkflowLLMCodes.SparkEngineParamsError,
    IfyTekLLMCodes.SparkEngineNetworkError: WorkflowLLMCodes.SparkEngineNetworkError,
    IfyTekLLMCodes.SparkTokenLimitError: WorkflowLLMCodes.SparkTokenLimitError,
    IfyTekLLMCodes.SparkAuthError: WorkflowLLMCodes.SparkAuthError,
    IfyTekLLMCodes.SparkDailyLimitError: WorkflowLLMCodes.SparkDailyLimitError,
    IfyTekLLMCodes.SparkSecondLimitError: WorkflowLLMCodes.SparkSecondLimitError,
    IfyTekLLMCodes.SparkConcurrencyLimitError: WorkflowLLMCodes.SparkConcurrencyLimitError,
}


def ify_code_convert(code: Union[int, str, tuple[int, str]]) -> tuple[int, str]:
    """Convert IFlyTek error codes to workflow error codes.

    Args:
        code: Error code to convert (can be string, int, or tuple)

    Returns:
        Tuple of (error_code, error_message)
    """
    return _CODE_MAPPING.get(code, WorkflowLLMCodes.SparkRequestError)
