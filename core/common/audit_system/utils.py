"""
utils.py
"""

import re
from typing import Tuple

END_SYMBOLS = [
    "\n",
    "\n\n",
    "。",
    ".",
    "！",
    "!",
    "？",
    "?",
    "；",
    ";",
]

NON_END_SYMBOLS = [
    "，",
    ",",
    "：",
    ":",
    "、",
    "》",
    ")",
    "）",
    "】",
    "]",
    "}",
    "……",
]

END_SYMBOLS_PATTERN = re.compile(
    rf'.+?(?:{"|".join(map(re.escape, END_SYMBOLS))})', re.DOTALL
)

NON_END_SYMBOL_SET = set(NON_END_SYMBOLS)

MAX_SENTENCE_LEN = 50
ALL_SENTENCE_LEN = -1


class Sentence:
    """
    处理文本内容的句子
    """

    @staticmethod
    def find_valid_sentence(
        content: str, fallback_length: int = MAX_SENTENCE_LEN
    ) -> tuple[list[str], str]:
        """
        查找文本中的首个有效句子，优先返回完整句子，如果没有完整句子则用非结束性标点分段，否则返回前50个字符
        :param content:
        :param fallback_length:
        :return: (首句, 剩余文本)
        """

        sentences: list = []
        remaining = ""

        if not content:
            return sentences, ""

        if fallback_length == ALL_SENTENCE_LEN:
            return [content], ""

        sentences, remaining = Sentence._extract_first_end_symbol(
            content, fallback_length
        )
        if sentences:
            return sentences, remaining

        sentence, remaining = Sentence._extract_before_last_non_end_symbol(
            content, fallback_length
        )
        if sentence:
            sentences.append(sentence)
            return sentences, remaining

        first_sentence = (
            content[:fallback_length]
            if fallback_length != ALL_SENTENCE_LEN
            else content
        )
        remaining = (
            content[fallback_length:] if fallback_length != ALL_SENTENCE_LEN else ""
        )
        sentences.append(first_sentence)
        return sentences, remaining

    @staticmethod
    def _extract_first_end_symbol(
        text: str, fallback_length: int = MAX_SENTENCE_LEN
    ) -> Tuple[list, str]:
        """
        提取文本中的句子，找到所有非结束性标点符号的句子
        :param text:
        :return:
        """

        sentences_temp = END_SYMBOLS_PATTERN.findall(text)
        sentences = []
        if fallback_length != ALL_SENTENCE_LEN:
            one = ""
            for _, s in enumerate(sentences_temp):
                if len(one) + len(s) <= fallback_length:
                    one += s
                else:
                    if one:
                        sentences.append(one)
                    one = s
            if one:
                sentences.append(one)

        matched_len = sum(len(s) for s in sentences)
        remainder = text[matched_len:]
        return sentences, remainder

    @staticmethod
    def _extract_before_last_non_end_symbol(
        text: str, fallback_length: int = MAX_SENTENCE_LEN
    ) -> Tuple[str, str]:
        """
        提取文本中最后一个非结束性符号之前的内容和剩余文本
        :param text:
        :return: 包含该符号的前缀 + 剩余文本
        """
        max_len = max(len(sym) for sym in NON_END_SYMBOLS)
        need_deal_text = (
            text[:fallback_length] if fallback_length != ALL_SENTENCE_LEN else text
        )
        for i in range(len(need_deal_text) - 1, -1, -1):
            for ll in range(1, max_len + 1):
                start = i - ll + 1
                if start < 0:
                    continue
                symbol = need_deal_text[start : i + 1]
                if symbol in NON_END_SYMBOL_SET:
                    sentence = need_deal_text[: i + 1]
                    remaining = text[len(sentence) :]
                    return sentence, remaining

        return "", ""

    @staticmethod
    def has_end_symbol(text: str) -> bool:
        """
        检查文本是否包含结束性标点符号
        :param text:
        :return:
        """
        return any(sym in text for sym in END_SYMBOLS)

    @staticmethod
    def split_and_keep_delimiters(text: str, separators: list[str]) -> list[str]:
        """
        拆分文本，保留分隔符
        :param text:
        :param separators:
        :return:
        """
        result = []
        current = ""

        for char in text:
            if char in separators:
                result.append(current + char)
                current = ""
            else:
                current += char

        if current:
            result.append(current)

        return result
