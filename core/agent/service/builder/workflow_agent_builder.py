import asyncio
import json
from dataclasses import dataclass
from typing import Any, cast

from api.schemas.workflow_agent_inputs import (
    CustomCompletionInputs,
    CustomCompletionPluginKnowledgeInputs,
)
from common_imports import Span
from engine.workflow_agent_runner import WorkflowAgentRunner
from service.builder.base_builder import BaseApiBuilder, CotRunnerParams, RunnerParams
from service.plugin.knowledge import KnowledgePluginFactory


@dataclass
class KnowledgeQueryParams:
    """知识查询参数"""

    repo_ids: list[str]
    doc_ids: list[str]
    top_k: int
    score_threshold: float
    rag_type: str


class WorkflowAgentRunnerBuilder(BaseApiBuilder):
    inputs: CustomCompletionInputs

    async def build(self) -> WorkflowAgentRunner:
        """构建"""
        with self.span.start("BuildRunner") as sp:
            model = await self.create_model(
                app_id=self.app_id,
                model_name=self.inputs.model_config_inputs.domain,
                base_url=self.inputs.model_config_inputs.api,
                api_key=self.inputs.model_config_inputs.api_key,
            )

            plugins = await self.build_plugins(
                tool_ids=self.inputs.plugin.tools,
                mcp_server_ids=self.inputs.plugin.mcp_server_ids,
                mcp_server_urls=self.inputs.plugin.mcp_server_urls,
                workflow_ids=self.inputs.plugin.workflow_ids,
            )
            metadata_list, knowledge = await self.query_knowledge_by_workflow(
                self.inputs.plugin.knowledge, sp
            )

            chat_params = RunnerParams(
                model=model,
                chat_history=self.inputs.get_chat_history(),
                instruct=self.inputs.instruction.answer,
                knowledge=knowledge,
                question=self.inputs.get_last_message_content(),
            )
            chat_runner = await self.build_chat_runner(chat_params)
            process_params = RunnerParams(
                model=model,
                chat_history=self.inputs.get_chat_history(),
                instruct=self.inputs.instruction.answer,
                knowledge=knowledge,
                question=self.inputs.get_last_message_content(),
            )
            process_runner = await self.build_process_runner(process_params)
            cot_params = CotRunnerParams(
                model=model,
                plugins=plugins,
                chat_history=self.inputs.get_chat_history(),
                instruct=self.inputs.instruction.reasoning,
                knowledge=knowledge,
                question=self.inputs.get_last_message_content(),
                process_runner=process_runner,
                max_loop=self.inputs.max_loop_count,
            )
            cot_runner = await self.build_cot_runner(cot_params)

            return WorkflowAgentRunner(
                chat_runner=chat_runner,
                cot_runner=cot_runner,
                plugins=plugins,
                knowledge_metadata_list=metadata_list,
            )

    async def query_knowledge_by_workflow(
        self, knowledge_list: list[CustomCompletionPluginKnowledgeInputs], span: Span
    ) -> tuple[list, str]:
        """查询知识库"""
        with span.start("QueryKnowledgeByWorkflow") as sp:
            tasks = self._create_knowledge_tasks(knowledge_list, sp)

            if not tasks:
                return [], ""

            results = await asyncio.gather(*tasks)
            metadata_list, _ = self._process_knowledge_results(results)
            backgrounds = self._extract_backgrounds(metadata_list)

            sp.add_info_events(
                {
                    "metadata-list": json.dumps(metadata_list, ensure_ascii=False),
                    "backgrounds": backgrounds,
                }
            )

            return metadata_list, backgrounds

    def _create_knowledge_tasks(
        self, knowledge_list: list[CustomCompletionPluginKnowledgeInputs], span: Span
    ) -> list:
        """创建知识查询任务"""
        repo_type_map = {
            "1": "AIUI-RAG2",
            "2": "CBG-RAG",
        }
        tasks = []

        for knowledge in knowledge_list:
            repo_ids = knowledge.match.repo_ids or []
            doc_ids = knowledge.match.doc_ids or []
            if not (repo_ids or doc_ids):
                continue

            top_k = knowledge.top_k or 3
            score_threshold = 0.3
            repo_type = (
                repo_type_map.get(str(knowledge.repo_type), "AIUI-RAG2")
                if knowledge.repo_type
                else "AIUI-RAG2"
            )

            params = KnowledgeQueryParams(
                repo_ids=repo_ids,
                doc_ids=doc_ids,
                top_k=top_k,
                score_threshold=score_threshold,
                rag_type=repo_type,
            )
            task = self.exec_query_knowledge(params, span)
            tasks.append(task)

        return tasks

    def _process_knowledge_results(
        self, results: list
    ) -> tuple[list, dict[str, list[dict[str, str]]]]:
        """处理知识查询结果"""
        metadata_list = []
        metadata_map: dict[str, list[dict[str, str]]] = {}

        for resp in results:
            for result in resp.get("data", {}).get("results", []):
                title = result.get("title", "")
                source_id = result.get("docId", "")
                content = result.get("content", "")
                references = result.get("references", {})

                content = self._process_content_references(content, references)

                if source_id not in metadata_map:
                    metadata_map[source_id] = []
                metadata_map[source_id].append({"chunk_context": f"{title}\n{content}"})

        for source_id, metadata in metadata_map.items():
            metadata_list.append({"source_id": source_id, "chunk": metadata})

        return metadata_list, metadata_map

    def _process_content_references(self, content: str, references: dict) -> str:
        """处理内容中的引用"""
        for ref_key, ref_value in references.items():
            if isinstance(ref_value, dict):
                ref_format = ref_value.get("format", "")
                if ref_format == "image":
                    content = content.replace(
                        f"<{ref_key}>",
                        f"![alt]({ref_value.get('link', '')})",
                    )
                elif ref_format == "table":
                    content = content.replace(
                        f"<{ref_key}>",
                        f"\n{ref_value.get('content', '')}\n",
                    )
            else:
                content = content.replace(f"{{{ref_key}}}", f"![alt]({ref_value})")
        return content

    def _extract_backgrounds(self, metadata_list: list) -> str:
        """提取背景信息"""
        background_list = []
        for metadata in metadata_list:
            chunk = metadata.get("chunk", [])
            for c in chunk:
                bg = c.get("chunk_context", "")
                background_list.append(bg)
        return "\n".join(background_list)

    async def exec_query_knowledge(
        self,
        params: KnowledgeQueryParams,
        span: Span,
    ) -> dict[str, Any]:
        knowledge_plugin = KnowledgePluginFactory(
            query=self.inputs.get_last_message_content(),
            top_k=params.top_k,
            repo_ids=params.repo_ids,
            doc_ids=params.doc_ids,
            score_threshold=params.score_threshold,
            rag_type=params.rag_type,
        ).gen()

        resp = await knowledge_plugin.run(span=span)

        return cast(dict[str, Any], resp)
