from abc import ABC, abstractmethod
from typing import TYPE_CHECKING, List

from common.audit_system.audit_api.base import AuditAPI
from common.audit_system.base import AuditContext, InputFrameAudit, OutputFrameAudit

if TYPE_CHECKING:
    from common.otlp.trace.span import Span


class AuditStrategy(ABC):
    """
    抽象基类，定义审核策略的接口
    """

    def __init__(
        self,
        chat_sid: str,
        audit_apis: List[AuditAPI],
        template_id: str = "",
        chat_app_id: str = "",
        uid: str = "",
    ):
        """
        初始化审核策略。
        :param chat_sid:    会话ID，用于标识当前的聊天会话。
        :param audit_apis:  审核API列表，包含所有需要调用的审核API实例。
        :param template_id: 模板ID，用于管理控制台配置的审核策略模板，一个大模型场景使用一个模板ID，缺省则使用默认的标准策略模版。
        :param chat_app_id: 大模型应用ID，透传参数，上游分配给大模型调用方的账号标识，用于区分调用方。
        :param uid:         用户ID，透传参数，用于区分指定的用户。
        """
        self.context = AuditContext(
            chat_sid=chat_sid, template_id=template_id, chat_app_id=chat_app_id, uid=uid
        )
        self.audit_apis = audit_apis

    @abstractmethod
    async def input_review(self, input_frame: InputFrameAudit, span: "Span") -> None:
        """
        输入内容审核逻辑，子类需要实现具体的审核逻辑。
        :param input_frame:
        :param span:         Span对象，用于跟踪请求的上下文信息。
        :return:
        """
        raise NotImplementedError("Subclasses must implement this method")

    @abstractmethod
    async def output_review(self, output_frame: OutputFrameAudit, span: "Span") -> None:
        """
        输出内容审核逻辑，子类需要实现具体的审核逻辑。
        :param output_frame:
        :param span:
        :return:
        """
        raise NotImplementedError("Subclasses must implement this method")
