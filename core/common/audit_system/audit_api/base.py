from abc import ABC, abstractmethod
from enum import Enum
from typing import Any, List, Literal

from pydantic import BaseModel, Field

from common.otlp.trace.span import Span


class Stage(str, Enum):
    REASONING = "reasoning"
    ANSWER = "answer"


class ContentType(str, Enum):
    TEXT = "text"
    IMAGE = "image"
    VIDEO = "video"
    AUDIO = "audio"


class ResourceList(BaseModel):
    """
    跟问答关联的资源信息，作用于降低上下文结合风险。
    """

    data_id: str
    content_type: ContentType
    res_desc: str
    ocr_text: str


class ContextList(BaseModel):
    """
    多轮对话场景下历史对话信息，作用于降低上下文结合风险，按照交互对话顺序传递。
    """

    role: str
    content: str
    resource_list: List[ResourceList] = Field(default_factory=list)


class AuditAPI(ABC):

    audit_name: str = "BaseAuditAPI"

    @abstractmethod
    async def input_text(
        self,
        content: str,
        chat_sid: str,
        span: Span,
        chat_app_id: str = "",
        uid: str = "",
        template_id: str = "",
        context_list: List[ContextList] = [],
        **kwargs: Any,
    ) -> None:
        """
        大模型内容安全场景中，该接口用于检测用户输入prompt内容的安全性，并针对高风险和中低风险内容提供对应的处置建议。

        :param content:         审核内容，需要送审的文本信息。
        :param chat_sid:        本轮对话ID，用于标识一次大模型对话。注意：本轮问答对话chat_sid需保持一致。
        :param span:            Span对象，用于跟踪请求的上下文信息。
        :param chat_app_id:     大模型应用ID，透传参数，上游分配给大模型调用方的账号标识，用于区分调用方。
        :param uid:             用户ID，透传参数，用于区分指定的用户。
        :param template_id:     模板ID，用于管理控制台配置的审核策略模板，一个大模型场景使用一个模板ID，缺省则使用默认的标准策略模版。
        :param context_list:    多轮对话场景下历史对话信息，作用于降低上下文结合风险，按照交互对话顺序传递。
        :param kwargs:
        :return:
        """
        raise NotImplementedError("Subclasses must implement this method")

    @abstractmethod
    async def output_text(
        self,
        stage: Stage,
        content: str,
        pindex: int,
        span: Span,
        is_pending: Literal[0, 1],
        is_stage_end: Literal[0, 1],
        is_end: Literal[0, 1],
        chat_sid: str,
        chat_app_id: str = "",
        uid: str = "",
        **kwargs: Any,
    ) -> None:
        """
        大模型内容安全场景中，该接口用于检测大模型输出内容的安全性，并针对高风险和中低风险内容提供对应的处置建议。

        :param stage:               审核环节，用于区分大模型各使用场景下的具体环节，
                                    基于不同的环节提供更细粒度的审核控制。缺省值answer，枚举值参考附录。
        :param content:             审核内容，需要送审的文本。
        :param pindex:              输出文本片段索引，表明当前文本是第几片文本，从1开始，建议业务方按照结束标点或段落拆分片段。
        :param span:
        :param is_pending:          分片文本内容不完整标识，用于首句快速上屏等场景。0：完整分片（缺省值），1：不完整分片
        :param is_stage_end:        当前审核环节分片是否为最后分片标识，0：非最后一段（缺省值），1：最后一段
        :param is_end:              当前分片是否为最后分片标识，0：非最后一段（缺省值），1：最后一段
        :param chat_sid:            本轮对话ID，用于标识一次大模型对话。注意：本轮问答对话chat_sid需保持一致。
        :param chat_app_id:         大模型应用ID，透传参数，上游分配给大模型调用方的账号标识，用于区分调用方。
        :param uid:                 用户ID，透传参数，用于区分指定的用户。
        :param kwargs:
        :return:
        """
        raise NotImplementedError("Subclasses must implement this method")

    @abstractmethod
    async def input_media(self, text: str, **kwargs: Any) -> None:
        """
        大模型内容安全场景中，对用户输入的文本、图片、视频、文档等进行过滤、检测和识别，并根据安全策略进行相应的处理和响应。
        :param text:
        :param kwargs:
        :return:
        """
        raise NotImplementedError("Subclasses must implement this method")

    @abstractmethod
    async def output_media(self, text: str, **kwargs: Any) -> None:
        """
        大模型内容安全场景中，对大模型输出的图片、视频、音频等进行过滤、检测和识别，并根据安全策略进行相应的处理和响应。
        :param text:
        :param kwargs:
        :return:
        """
        raise NotImplementedError("Subclasses must implement this method")

    @abstractmethod
    async def know_ref(self, text: str, **kwargs: Any) -> None:
        """
        大模型内容安全场景中，对大模型答复过程中引用的网站、知识库等数据进行过滤、检测和识别，并根据安全策略进行相应的处理和响应。
        :param text:
        :param kwargs:
        :return:
        """
        raise NotImplementedError("Subclasses must implement this method")
