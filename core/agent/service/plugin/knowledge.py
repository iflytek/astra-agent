import asyncio
import json
from typing import Any, Dict, List

import aiohttp
from openai import BaseModel

from common_imports import Span
from exceptions.plugin_exc import KnowledgeQueryExc
from infra import agent_config
from service.plugin.base import BasePlugin


class KnowledgePlugin(BasePlugin):
    pass


class KnowledgePluginFactory(BaseModel):
    query: str
    top_k: int
    repo_ids: List[str]
    doc_ids: List[str]
    score_threshold: float
    rag_type: str

    def gen(self) -> KnowledgePlugin:
        return KnowledgePlugin(
            name="knowledge",
            description="knowledge plugin",
            schema_template="",
            typ="knowledge",
            run=self.retrieve,
        )

    async def retrieve(self, span: Span) -> Dict[str, Any]:
        with span.start("retrieve") as sp:
            data: Dict[str, Any] = {
                "query": self.query,
                "topN": str(self.top_k),
                "match": {"repoId": self.repo_ids, "threshold": self.score_threshold},
                "ragType": self.rag_type,
            }
            if self.rag_type == "CBG-RAG":
                if "match" not in data:
                    data["match"] = {}
                data["match"]["docIds"] = self.doc_ids

            sp.add_info_events({"request-data": json.dumps(data, ensure_ascii=False)})

            if not self.repo_ids:
                empty_resp: Dict[str, Any] = {}
                sp.add_info_events(
                    {"response-data": json.dumps(empty_resp, ensure_ascii=False)}
                )
                return empty_resp

            try:
                async with aiohttp.ClientSession() as session:
                    timeout = aiohttp.ClientTimeout(total=40)
                    async with session.post(
                        agent_config.CHUNK_QUERY_URL, json=data, timeout=timeout
                    ) as response:

                        sp.add_info_events(
                            {"response-data": str(await response.read())}
                        )

                        response.raise_for_status()
                        if response.status == 200:
                            resp: Dict[str, Any] = await response.json()
                            sp.add_info_events(
                                {"response-data": json.dumps(resp, ensure_ascii=False)}
                            )
                            return resp

                        raise KnowledgeQueryExc
            except asyncio.TimeoutError as e:
                raise KnowledgeQueryExc from e
