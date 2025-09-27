"""
Test module for text strategy output review functionality in the audit system.

This module contains integration tests for the text audit strategy, including
tests for frame processing, audit orchestration, and output review workflows.
"""

import asyncio
import logging
import os
from typing import List, Tuple

import pytest
from pydantic import BaseModel
from workflow.extensions.otlp.sid.sid_generator2 import init_sid
from workflow.extensions.otlp.trace.span import Span
from workflow.extensions.otlp.trace.trace import init_trace
from workflow.infra.audit_system.audit_api.base import AuditAPI, Stage
from workflow.infra.audit_system.audit_api.iflytek.ifly_audit_api import IFlyAuditAPI
from workflow.infra.audit_system.base import FrameAuditResult, OutputFrameAudit
from workflow.infra.audit_system.enums import Status
from workflow.infra.audit_system.orchestrator import AuditOrchestrator
from workflow.infra.audit_system.strategy.text_strategy import TextAuditStrategy

# Maximum string length for testing purposes
MAX_STR = (
    "->50字开始 它从开源社区中的诸多优秀 LLM 应用开发框架如 LangChain和50字结束<-取灵感"
)

# Environment configuration for audit API testing
os.environ["IFLYTEK_AUDIT_APP_ID"] = ""
os.environ["IFLYTEK_AUDIT_ACCESS_KEY_ID"] = ""
os.environ["IFLYTEK_AUDIT_ACCESS_KEY_SECRET"] = ""
os.environ["IFLYTEK_AUDIT_HOST"] = (
    "http://audit-api-spark-dx.iflyaisol.com,http://audit-api-spark-hu.iflyaisol.com"
)

# Initialize tracing and session ID for testing
init_trace(endpoint="172.30.209.27:4317", service_name="SparkFlowV2")
init_sid("spf", "hf", "127.0.0.1", "10000")

# List of audit APIs to use for testing
AUDIT_APIS: list[AuditAPI] = [
    # MockAuditAPI(),
    IFlyAuditAPI()
]


class Frame(BaseModel):
    """Base frame model for audit testing."""

    content: str


class ReasoningFrame(Frame):
    """Frame model representing reasoning content for audit testing."""

    pass


class AnswerFrame(Frame):
    """Frame model representing answer content for audit testing."""

    pass


def gen_output_frame_audit(frames: list[Frame]) -> Tuple[list[OutputFrameAudit], str]:
    """
    Generate output frame audit objects from input frames for testing.

    :param frames: List of Frame objects to convert to audit frames
    :return: Tuple containing list of OutputFrameAudit objects and concatenated audit content
    """
    output_frames: list[OutputFrameAudit] = []
    audit_content = ""
    for idx, frame in enumerate(frames):
        audit_content += frame.content
        if isinstance(frame, ReasoningFrame):
            stage = Stage.REASONING
        else:
            stage = Stage.ANSWER

        output_frames.append(
            OutputFrameAudit(
                content=frame.content,
                status=Status.NONE if idx + 1 != len(frames) else Status.STOP,
                stage=stage,
                source_frame=frame.dict(),
                frame_id=f"frame {idx + 1}",
            )
        )
    return output_frames, audit_content


async def base_output_review(frames: list[Frame]) -> None:
    """
    Base output review method for testing audit functionality.

    This method processes a list of frames through the audit system, simulating
    the complete audit workflow including frame processing and result collection.

    :param frames: List of Frame objects to be audited
    """
    span = Span()
    logging.info(span.sid)
    with span.start() as context_span:
        audit_strategy = TextAuditStrategy(chat_sid=span.sid, audit_apis=AUDIT_APIS)
        audit_orchestrator = AuditOrchestrator(audit_strategy)

        output_frames, audit_content = gen_output_frame_audit(frames)
        audited_content = ""

        async def product() -> None:
            """Producer coroutine that sends frames for audit processing."""
            for output_frame in output_frames:
                # await asyncio.sleep(1)
                logging.info(
                    f"\n---------------------------- "
                    f"{output_frame.frame_id} start "
                    f"---------------------------- \n"
                    f"Sending output frame for audit: {output_frame}\n"
                )
                await audit_orchestrator.process_output(output_frame, context_span)
                logging.info(
                    f"\n---------------------------- "
                    f"{output_frame.frame_id} end "
                    f"---------------------------- \n"
                )

        _ = asyncio.create_task(product())
        i = 0
        while True:
            frame_audit_result: FrameAuditResult = await asyncio.wait_for(
                audit_strategy.context.output_queue.get(), timeout=100
            )
            audited_content += frame_audit_result.content
            if frame_audit_result.error:
                logging.error(f"Audit error: {frame_audit_result.error}")
                # Print stack trace information
                logging.exception(frame_audit_result.error)
                break
            logging.info(
                f"\n"
                f"Audit content: {frame_audit_result.content}, \n"
                f"source_frame: {frame_audit_result.source_frame}, \n"
                "================================================\n"
            )
            i += 1
            if i == len(output_frames):
                break
        assert audited_content == audit_content
        logging.info("======================= end =========================")


@pytest.mark.asyncio
async def test_audit_one() -> None:
    """Test audit functionality with a single frame containing ending symbols."""
    frames: List[Frame] = [
        AnswerFrame(
            content="""# 角色定位：
你是一位资深全国导游，根据用户问题推荐景点名称，目的为用户感受到大自然的独特魅力、宁静治愈或原始野趣，提供区别于城市绿地、公园的深度自然体验
# 思考限制：根据用户问题
1、优先推荐满足下列4个特征条件之一的自然景点
     a)独特的地形地貌：如峡谷、溪涧、瀑布、岩壁、奇石、洞穴、岩缝、草甸、山坡、湿地、沙丘/沙漠、海岸、梯田等
     b)特色水域特征：如观湖点、溪流秘境、河湾风光、海滩、潮汐、温泉等。
     c)植被奇观：如古树、红树林、特色森林（竹林/枫林/樱花林等）、花海、芦苇荡等。
     d)特色动物栖息地：如大熊猫保护基地、红嘴鸥观鸟区、生态保护区等。
     e)如果以上类均不满足，可推荐其他自然景点。
2、评分要高，大众反映较好。
3、需避开下列地点：不安全/存在安全隐患的地点；大众化的热门公园，纯功能性绿地（如小区绿化带、普通道路绿化），普通休闲广场，不知名的河流等；口碑差或存在明显问题的地点。
# 输出限制：
1、只需要输出一个名称即可，如果用户指定了数量，严格按照用户要求的数量输出。
2、在输出地点名称前需要将地点名称所在的城市名称输出，格式为 城市名:地点名称。
3、不需要输出任何介绍,多个地点名称之间以顿号(、)隔开，严格按照要求输出。
4、以下为用户去过的地点名称，标记为历史黑名单，不要出现在你的回答中：
    武隆喀斯特旅游区、重庆:四面山国家级风景名胜区、金佛山国家级自然保护区、黑山谷景区、重庆:酉阳桃花源、缙云山国家级自然保护区、仙女山国家森林公园、南天湖景区、武陵山大裂谷、金刀峡、重庆:长寿湖、重庆:涪陵武陵山大裂谷、茶山竹海、重庆:大足龙水湖、重庆:彭水阿依河、长江三峡、重庆:巫山小三峡、重庆:雪玉洞、黄水国家森林公园、金佛山国家级自然保护区、丰都南天湖湿地公园、江津四面山、重庆:万盛黑山、大圆洞国家森林公园、芙蓉洞、铁山坪森林公园、重庆:统景温泉风景区、石柱黄水大风堡景区、石柱千野草场、红池坝国家森林公园、云阳龙缸国家地质公园、黎香湖国家湿地公园、南川山王坪喀斯特国家生态公园、铜锣山矿山公园、石笋山景区、梁平百里竹海、重庆:巴南圣灯山、重庆:合川涞滩古镇、金佛山国家级自然保护区、四面山国家级风景名胜区、金佛山碧潭幽谷、武陵山国家森林公园、金佛山南坡原始森林、乌江画廊、南川区金佛山西坡、玉峰山森林公园、明月山、金佛山喀斯特国家公园、九重山国家森林公园、红岩村峡谷、巫溪兰英大峡谷、重庆:石柱油草河、重庆:黔江小南海、铜梁黄桷门奇彩梦园、綦江古剑山、重庆:万州大瀑布群、巴岳山、重庆:铜梁安居国家湿地公园、金佛山天星小镇、重庆:石柱冷水风谷、铜梁黄桷门奇彩梦园、巫溪红池坝、重庆:石柱万寿山、重庆:开州汉丰湖国家湿地公园、铜梁黄桷门奇彩梦园、巴南羊鹿山、重庆:涪陵雨台山、万州大垭口森林公园、巴南丰盛古镇、重庆:綦江老瀛山国家地质公园、明月湖、金佛山西坡、巫溪阴条岭国家级自然保护区、蒲花暗河、万州潭獐峡、梁平双桂湖国家湿地公园、铁山坪森林公园、石柱黄水太阳湖、黔江蒲花暗河、巫溪兰英大峡谷、龙缸国家地质公园、彭水摩围山、重庆:涪陵武陵山大峡谷、重庆:石柱七曜山地质公园、巫溪兰英大峡谷、东温泉风景区、龚滩古镇、重庆:石柱广寒宫景区、巫溪兰英大峡谷、铁山坪森林公园、南川金佛山北坡、南山植物园、偏岩古镇、重庆:石柱冷水莼菜田园综合体、明月山、华蓥山国家森林公园、巫溪阴条岭国家级自然保护区、大足石刻、重庆:铜梁黄桷门奇彩梦园、石柱冷水风谷、大木花谷、巫溪阴条岭国家级自然保护区、綦江国家地质公园、大木林下花园、南川神龙峡、重庆:奉节天坑地缝、丰都澜天湖、南川金佛山北坡、南川金佛山南坡、重庆:石柱万寿寨、七鹿坪、巫溪阴条岭国家级自然保护区、华蓥山国家森林公园、蒲花暗河、云龟山景区、南山老君洞、白云山、重庆:南川黎香湖、重庆:巫溪大官山、白帝城瞿塘峡景区、南川金佛山北坡、南川金佛山北坡、石柱七曜山地质公园、江津清溪沟国家地质公园、金刀峡、重庆:红池坝国家森林公园、南川神龙峡、大足玉龙山国家森林公园、石柱冷水莼菜田园综合体、巫溪大官山、巫溪大官山、巫溪大官山、云阳龙缸国家地质公园、石柱大风堡原始森林、石柱油草河峡谷、巫溪阴条岭国家级自然保护区、大洪湖国家湿地公园、南川金佛山东坡、巫溪大宁河景区、万州西游洞、南川山王坪喀斯特国家生态公园、合川涞滩二佛寺、华蓥山国家森林公园、石柱大风堡原始森林、石柱冷水莼菜田园综合体、江津四面山少林寺景区、大足龙水湖、云阳龙缸国家地质公园、大圆洞国家森林公园、长江索道、铜梁黄桷门奇彩梦园、巫溪大宁河景区、巫溪阴条岭国家级自然保护区、青龙湖国家湿地公园、云阳三峡梯城、綦江国家地质公园、重庆汉海海洋公园、石柱云中花都、巫溪大官山、龙脊岭生态公园
5、要严格按照输出格式输出确认后的结果，不需要输出修正前的内容和修正过程。
# 示例：
用户问题：我在合肥市蜀山区高新技术产业开发区望江西路666号，问合肥有什么好玩的
输出:合肥:三河古镇
# 用户问题：
我在"重庆市""重庆市渝北区曙光路恒大两江总部智慧生态城西侧约70米"，现在是"2025-08-26 16:41"，问"自然之境\""""
        ),
        AnswerFrame(content="哈哈"),
    ]
    await base_output_review(frames)


@pytest.mark.asyncio
async def test_first_sentence_audit_with_end_symbol() -> None:
    """Test first sentence audit functionality when ending symbols are present."""
    frames = [
        AnswerFrame(content="1"),
        AnswerFrame(content="2"),
        AnswerFrame(content="3333."),
        ReasoningFrame(content="123"),
        ReasoningFrame(content="456."),
        ReasoningFrame(content="789." f"{MAX_STR}"),
        ReasoningFrame(content="10,"),
        AnswerFrame(content="11"),
        AnswerFrame(content="12."),
    ]
    await base_output_review(frames)


@pytest.mark.asyncio
async def test_first_sentence_audit_with_max_frames() -> None:
    """Test first sentence audit functionality when maximum frame count is reached."""
    frames = [
        AnswerFrame(content="1"),
        AnswerFrame(content="2"),
        AnswerFrame(content="3"),
        AnswerFrame(content="4"),
        AnswerFrame(content="5"),
        AnswerFrame(content=MAX_STR),
        AnswerFrame(content="7;"),
        AnswerFrame(content="8"),
        AnswerFrame(content="9."),
        ReasoningFrame(content="123"),
        ReasoningFrame(content="456."),
        ReasoningFrame(content="789." f"{MAX_STR}"),
        ReasoningFrame(content="10,"),
        AnswerFrame(content="11"),
        AnswerFrame(content="12."),
    ]
    await base_output_review(frames)
