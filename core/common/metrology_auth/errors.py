from common.exceptions.base import BaseExc


class XingchenUtilsMASDKException(BaseExc):
    pass


class ErrorCode:
    Successes = 0
    MASDKClosedError = 0
    InitParamInvalidError = 9101
    AuthorizationCheckError = 9102
    AuthorizationCheckV2Error = 9103
    MetrologyCalcError = 9104
    ConcurrentAcquireConcError = 9105
    ConcurrentReleaseConcError = 9106
    CntInitFailedError = 9107
    ConcInitFailError = 9108
    MASDKUnknownError = 9999


Successes = XingchenUtilsMASDKException(0, "成功")
MASDKClosedError = XingchenUtilsMASDKException(0, "计量鉴权SDK功能关闭")
InitParamInvalidError = XingchenUtilsMASDKException(
    9101, "计量鉴权SDK初始化参数非法异常"
)
AuthorizationCheckError = XingchenUtilsMASDKException(
    9102, "计量鉴权SDK鉴权Check调用失败"
)
AuthorizationCheckV2Error = XingchenUtilsMASDKException(
    9103, "计量鉴权SDK鉴权CheckV2调用失败"
)
MetrologyCalcError = XingchenUtilsMASDKException(9104, "计量鉴权SDK计量调用失败")
ConcurrentAcquireConcError = XingchenUtilsMASDKException(
    9105, "计量鉴权SDK精准并发调用失败"
)
ConcurrentReleaseConcError = XingchenUtilsMASDKException(
    9106, "计量鉴权SDK精准并发释放失败"
)
CntInitFailedError = XingchenUtilsMASDKException(9107, "计量鉴权SDK计量鉴权初始化异常")
ConcInitFailError = XingchenUtilsMASDKException(9108, "计量鉴权SDK并发鉴权初始化异常")
MASDKUnknownError = XingchenUtilsMASDKException(9999, "未知异常")


class MASDKErrors:
    @classmethod
    def get_error(cls, code: int) -> XingchenUtilsMASDKException:
        for err in [
            Successes,
            MASDKClosedError,
            InitParamInvalidError,
            AuthorizationCheckError,
            AuthorizationCheckV2Error,
            MetrologyCalcError,
            ConcurrentAcquireConcError,
            ConcurrentReleaseConcError,
            CntInitFailedError,
            ConcInitFailError,
        ]:
            if err.c == code:
                return err
        return MASDKUnknownError
