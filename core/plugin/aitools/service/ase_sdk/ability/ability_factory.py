"""
Ability factory module creating different types of AI capability clients.
"""

from plugin.aitools.service.ase_sdk.__base.power import Power
from plugin.aitools.service.ase_sdk.const.service_ids import ServiceIdEnum


class AbilityFactory:

    @staticmethod
    def creat(service_id: str, url: str, method: str = "GET") -> Power:
        if service_id == ServiceIdEnum.OCR_LLM.value:
            from plugin.aitools.service.ase_sdk.ability.ocr_llm.client_multithreading import (
                OcrLLMClientMultithreading,
            )

            return OcrLLMClientMultithreading(url, method)
        else:
            from plugin.aitools.service.ase_sdk.ability.common.client import (
                CommonClient,
            )

            return CommonClient(url, method)
