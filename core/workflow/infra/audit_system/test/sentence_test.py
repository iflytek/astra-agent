# -*- coding: utf-8 -*-
"""
Test module for sentence processing functionality in the audit system.

This module contains unit tests for the Sentence class, including tests for
sentence detection, splitting, and delimiter handling.
"""
import json

import pytest

from workflow.infra.audit_system.utils import END_SYMBOLS, NON_END_SYMBOLS, Sentence


@pytest.mark.parametrize(
    "content,expected_sentences,expected_remaining",
    [
        # Carriage return and line feed scenario
        (
            "** (Significant events, codes, references in media...)\n*",
            ["** (Significant events, codes,"],
            " references in media...)\n*",
        ),
        # Normal complete sentences
        (
            "你好，这是一个测试句子。这是第二句。xxx",
            ["你好，这是一个测试句子。", "这是第二句。"],
            "xxx",
        ),
        # Only non-ending punctuation
        ("你好，这是一个测试", ["你好，"], "这是一个测试"),
        # No punctuation, fallback case
        (
            "试这是一段没有任何标点的文本用于测试这是一段没有任何标点的文试这是一段"
            + "没有任何标点的文本用于测试这是一段没有任何标点的文",
            [
                "试这是一段没有任何标点的文本用于测试这是一段没有任何标点的文试这是一段没有任何标点的文本用于测试这是"
            ],
            "一段没有任何标点的文",
        ),
        # Empty string
        ("", [], ""),
        # Only ending punctuation
        ("。", ["。"], ""),
        # Multiple sentences, ensure only first sentence is taken
        ("第一句！第二句？第三句。", ["第一句！", "第二句？", "第三句。"], ""),
        # First sentence contains non-ending punctuation, overall exceeds 50 characters but has ending punctuation
        (
            "这是第一句，第二句是一个很长的句子试这是一段没有任何标点的文本用于测试这是一段"
            + "没有任何标点的文试这是一段没有任何标点的文本用于测试这是一段没有任何标点的文。",
            ["这是第一句，"],
            "第二句是一个很长的句子试这是一段没有任何标点的文本用于测试这是一段没有任何标点"
            + "的文试这是一段没有任何标点的文本用于测试这是一段没有任何标点的文。",
        ),
        # First sentence contains ending punctuation but exceeds 50 characters
        (
            "第一句是一个很长的句子试这是一段没有任何标点的文本用于测试这是一段没有任何标点的"
            + "文试这是一段没有任何标点的文本用于测试这是一段没有任何标点的文。xxxx",
            [
                "第一句是一个很长的句子试这是一段没有任何标点的文本用于测试这是一段没有任何标点的文试这是一段没有任何"
            ],
            "标点的文本用于测试这是一段没有任何标点的文。xxxx",
        ),
    ],
)
def test_find_valid_sentence(
    content: str, expected_sentences: list[str], expected_remaining: str
) -> None:
    """
    Test the find_valid_sentence method with various input scenarios.

    :param content: Input text content to be processed
    :param expected_sentences: Expected list of valid sentences extracted
    :param expected_remaining: Expected remaining text after sentence extraction
    """
    try:
        sentences, remaining = Sentence.find_valid_sentence(content)
        assert sentences == expected_sentences
        assert remaining == expected_remaining
    except Exception as e:
        pytest.fail(f"Unexpected exception: {e}")


@pytest.mark.parametrize("text", ["这是句子。", "Hello world!", "测试换行符\n下一句"])
def test_has_end_symbol_true(text: str) -> None:
    """
    Test that has_end_symbol returns True for texts containing ending symbols.

    :param text: Text content that should contain ending symbols
    """
    assert Sentence.has_end_symbol(text) is True


@pytest.mark.parametrize("text", ["没有结束标点的句子", "你好，测试：继续测试"])
def test_has_end_symbol_false(text: str) -> None:
    """
    Test that has_end_symbol returns False for texts without ending symbols.

    :param text: Text content that should not contain ending symbols
    """
    assert Sentence.has_end_symbol(text) is False


def test_split_and_keep_delimiters() -> None:
    """
    Test the split_and_keep_delimiters method with complex text containing various delimiters.

    This test verifies that the method correctly splits text while preserving delimiter characters
    and handles complex scenarios with multiple types of content.
    """
    text = (
        "->50字开始 它从开源社区中的诸多优秀 LLM 应用开发框架如 LangChain和50字结束<-取灵感\n->5"
        + "0字开始 它从开源社区中的诸多优秀 LLM 应用开发框架如 "
        "LangChain和50字结束<-取灵感\n->50字开始 它从开源社区中的诸多优秀 LLM 应用开发框架如 "
        "LangChain和50字结束<-取灵感\n《暖心小狗》\n\n"
        "街角蜷缩着一只脏兮兮的小狗，毛发打结，眼神怯生生。小女孩朵朵路过，心生怜悯，将它抱回了家。"
        + "她打了温水，轻轻梳理它纠结的毛，又喂了温热的粥。小狗初时发抖，渐渐放松下来，尾巴开始摇摆。"
        + "日子一天天过去，小狗变得雪白蓬松，像团柔软的云朵。每当朵朵放学归来，它总会欢叫着扑进怀里；"
        + "夜里守在床边，宛如忠诚的小卫士。有一次朵朵发高烧昏迷不醒，是小狗焦急地抓门呼救，引得邻居及时"
        + "发现送医。从此，他们更是形影不离，小狗成了朵朵生命中最温暖的陪伴。\n\n====== 消息2 结束"
        + " =======《暖心小狗》\n\n街角蜷缩着一只脏兮兮的小狗，毛发打结，眼神怯生生。小女孩朵朵路过，"
        + "心生怜悯，将它抱回了家。她打了温水，轻轻梳理它纠结的毛，又喂了温热的粥。小狗初时发抖，渐渐"
        + "放松下来，尾巴开始摇摆。日子一天天过去，小狗变得雪白蓬松，像团柔软的云朵。每当朵朵放学归来，"
        + "它总会欢叫着扑进怀里；夜里守在床边，宛如忠诚的小卫士。有一次朵朵发高烧昏迷不醒，是小狗焦急"
        + "地抓门呼救，引得邻居及时发现送医。从此，他们更是形影不离，小狗成了朵朵生命中最温暖的陪伴。x"
        + "xxxxx # 《可爱的小花猫》\r\n我家有只小花猫，浑身雪白缀着黄斑，像团会动的棉花糖。它眼睛圆溜溜"
        + "的，总是好奇地打量世界。白天常趴在窗台晒太阳，偶尔追着自己晃动的影子玩耍，憨态可掬。一到晚上"
        + "，便精神抖擞地巡逻，老鼠刚露头就被它敏捷地擒住。它最爱玩毛线球，用爪子拨来拨去，把自己缠成"
        + "个滑稽的模样。吃饭时狼吞虎咽，还发出满足的呼噜声。这只小花猫给我家带来无尽欢乐，它是我童年最"
        + "好的伙伴，陪伴我度过一个个美好的时光。\n\n问答节点 {{q}}"
    )
    sentences = Sentence.split_and_keep_delimiters(text, END_SYMBOLS + NON_END_SYMBOLS)
    print(json.dumps(sentences, ensure_ascii=False, indent=4))
