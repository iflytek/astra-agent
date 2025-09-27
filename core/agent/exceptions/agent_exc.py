# Use unified common package import module
from common_imports import BaseExc
from exceptions.codes import (
    c_0,
    c_40001,
    c_40002,
    c_40003,
    c_40004,
    c_40005,
    c_40006,
    c_40500,
)


class AgentExc(BaseExc):
    pass


AgentNormalExc = AgentExc(*c_0)
AgentInternalExc = AgentExc(*c_40500)
BotConfigNotFoundExc = AgentExc(*c_40001)
ReceiveWsMsgExc = AgentExc(*c_40002)
BotConfigInvalidExc = AgentExc(*c_40003)
RequestSparkFlowExc = AgentExc(*c_40004)
RequestSparkLinkExc = AgentExc(*c_40005)
ReceiveHttpMsgExc = AgentExc(*c_40006)
