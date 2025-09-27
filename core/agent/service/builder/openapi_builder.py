import json
from typing import cast

from api.schemas.openapi_inputs import CompletionInputs
from common_imports import Span
from repository.bot_config_client import BotConfig
from service.builder.base_builder import BaseApiBuilder, CotRunnerParams, RunnerParams
from service.plugin.base import BasePlugin
from service.plugin.knowledge import KnowledgePluginFactory
from service.runner.openapi_runner import OpenAPIRunner


class OpenAPIRunnerBuilder(BaseApiBuilder):
    inputs: CompletionInputs

    async def build(self) -> OpenAPIRunner:
        """构建"""

        with self.span.start("BuildRunner") as sp:
            bot_config = await self.build_bot_config(self.inputs.bot_id)
            plan_model = await self.create_model(
                app_id=self.app_id,
                model_name=bot_config.model_config_.plan.domain,
                base_url=bot_config.model_config_.plan.api,
            )
            summary_model = await self.create_model(
                app_id=self.app_id,
                model_name=bot_config.model_config_.summary.domain,
                base_url=bot_config.model_config_.summary.api,
            )
            metadata_list, knowledge = await self.query_knowledge(bot_config, sp)

            plugins = await self.build_plugins(
                tool_ids=bot_config.tool_ids,
                mcp_server_ids=bot_config.mcp_server_ids,
                mcp_server_urls=bot_config.mcp_server_urls,
                workflow_ids=bot_config.flow_ids,
            )

            chat_runner = await self.build_chat_runner(
                RunnerParams(
                    model=summary_model,
                    chat_history=self.inputs.get_chat_history(),
                    instruct=bot_config.model_config_.instruct,
                    knowledge=knowledge,
                    question=self.inputs.get_last_message_content(),
                )
            )

            process_runner = await self.build_process_runner(
                RunnerParams(
                    model=summary_model,
                    chat_history=self.inputs.get_chat_history(),
                    instruct=bot_config.model_config_.instruct,
                    knowledge=knowledge,
                    question=self.inputs.get_last_message_content(),
                )
            )

            cot_runner = await self.build_cot_runner(
                CotRunnerParams(
                    model=plan_model,
                    plugins=cast(list[BasePlugin], plugins),
                    chat_history=self.inputs.get_chat_history(),
                    instruct=bot_config.model_config_.instruct,
                    knowledge=knowledge,
                    question=self.inputs.get_last_message_content(),
                    process_runner=process_runner,
                )
            )

            return OpenAPIRunner(
                chat_runner=chat_runner,
                cot_runner=cot_runner,
                plugins=cast(list[BasePlugin], plugins),
                knowledge_metadata_list=metadata_list,
            )

    async def query_knowledge(
        self, bot_config: BotConfig, span: Span
    ) -> tuple[list, str]:
        """查询知识库"""

        with span.start("QueryKnowledge") as sp:
            repo_ids = bot_config.regular_config.match.repoId or []
            doc_ids = bot_config.regular_config.match.docId or []
            rag_type = bot_config.regular_config.rag.type or "AIUI-RAG2"

            if not (repo_ids or doc_ids):
                return [], ""

            knowledge_plugin = KnowledgePluginFactory(
                query=self.inputs.get_last_message_content(),
                top_k=bot_config.knowledge_config.top_k,
                repo_ids=repo_ids,
                doc_ids=doc_ids,
                score_threshold=bot_config.knowledge_config.score_threshold,
                rag_type=rag_type,
            ).gen()

            resp = await knowledge_plugin.run(span=sp)
            results = resp.get("data", {}).get("results", [])

            metadata_list = self._process_knowledge_results(results)
            backgrounds = self._extract_backgrounds(metadata_list)

            sp.add_info_events(
                {
                    "metadata-list": json.dumps(metadata_list, ensure_ascii=False),
                    "backgrounds": backgrounds,
                }
            )

            return metadata_list, backgrounds

    def _process_knowledge_results(self, results: list) -> list:
        """处理知识库查询结果"""
        metadata_map: dict[str, list] = {}

        for result in results:
            title = result.get("title", "")
            source_id = result.get("docId", "")
            content = self._process_references(
                result.get("content", ""), result.get("references", {})
            )

            if source_id not in metadata_map:
                metadata_map[source_id] = []
            metadata_map[source_id].append({"chunk_context": f"{title}\n{content}"})

        return [
            {"source_id": source_id, "chunk": metadata}
            for source_id, metadata in metadata_map.items()
        ]

    def _process_references(self, content: str, references: dict) -> str:
        """处理内容中的引用"""
        for ref_key, ref_value in references.items():
            ref_format = ref_value.get("format", "")
            if ref_format == "image":
                content = content.replace(
                    f"<{ref_key}>", f"![alt]({ref_value.get('link', '')})"
                )
            elif ref_format == "table":
                content = content.replace(
                    f"<{ref_key}>", f"\n{ref_value.get('content', '')}\n"
                )
        return content

    def _extract_backgrounds(self, metadata_list: list) -> str:
        """提取背景信息"""
        backgrounds = []
        for metadata in metadata_list:
            for chunk in metadata.get("chunk", []):
                backgrounds.append(chunk.get("chunk_context", ""))
        return "\n".join(backgrounds)
