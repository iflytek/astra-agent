"""History service module for managing conversation history and chat records.

This module provides functionality to store and retrieve conversation history
for workflow nodes, with support for token limits and database constraints.
"""

import json
from typing import Any, Dict, List, Optional

from sqlalchemy import desc
from sqlmodel import Session, select  # type: ignore

from workflow.domain.models.history import History
from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.middleware.getters import get_session

# Maximum number of history records to keep per node
MAX_HISTORY_SIZE = 10
# Token limit for LLM processing (95% of 8192 tokens for safety margin)
TOKEN_LIMIT = 8192 * 0.95
# Database row length limit (95% of mediumText max length 16MB for safety margin)
DB_ROW_LENGTH_LIMIT = 16777215 * 0.95


def add_history(
    flow_id: str,
    node_id: str,
    uid: str,
    raw_question: dict,
    raw_answer: dict,
    chat_id: Optional[str] = None,
    **kwargs: Any,
) -> None:
    """Add a new conversation history record to the database.

    :param flow_id: Unique identifier for the workflow flow
    :param node_id: Unique identifier for the workflow node
    :param uid: User identifier
    :param raw_question: Question data as dictionary
    :param raw_answer: Answer data as dictionary
    :param chat_id: Optional chat session identifier
    :param kwargs: Additional keyword arguments
    :raises CustomException: If database operation fails
    """
    try:
        session: Session = next(get_session())
        # Truncate content if it exceeds database row length limit
        rq_content = raw_question.get("content")
        if (
            isinstance(rq_content, str)
            and len(rq_content.encode("utf-8")) > DB_ROW_LENGTH_LIMIT
        ):
            raw_question["content"] = rq_content[: int(DB_ROW_LENGTH_LIMIT)]
        ra_content = raw_answer.get("content")
        if (
            isinstance(ra_content, str)
            and len(ra_content.encode("utf-8")) > DB_ROW_LENGTH_LIMIT
        ):
            raw_answer["content"] = ra_content[: int(DB_ROW_LENGTH_LIMIT)]

        # Serialize question and answer data to JSON strings
        question_str = json.dumps(raw_question, ensure_ascii=False)
        answer_str = json.dumps(raw_answer, ensure_ascii=False)

        # Create and persist history record
        db_history = History(
            flow_id=flow_id,
            node_id=node_id,
            uid=uid,
            raw_question=question_str,
            raw_answer=answer_str,
            chat_id=chat_id,
        )
        session.add(db_history)
        session.commit()

        session.refresh(db_history)
    except Exception as e:
        raise CustomException(
            CodeEnum.ENG_RUN_ERROR,
            err_msg=f"add_history method failed to add LLM history; {e}",
            cause_error=f"err code : {CodeEnum.ENG_RUN_ERROR.code}. "
            f"message: add_history method failed to add LLM history; {e}",
        ) from e


def get_history(
    flow_id: str,
    uid: str,
    node_max_token: Optional[Dict[str, int]] = None,
    history_size: int = MAX_HISTORY_SIZE,
) -> List[Dict]:
    """Retrieve conversation history for a specific flow and user.

    :param flow_id: Unique identifier for the workflow flow
    :param uid: User identifier
    :param node_max_token: Optional dictionary mapping node IDs to token limits
    :param history_size: Maximum number of history records to retrieve per node
    :return: List of dictionaries containing node history with chat records
    :raises CustomException: If database operation fails
    """
    try:
        session: Session = next(get_session())
        # Get all unique node IDs for the specified flow
        node_id_query = (
            select(History.node_id)
            .where(History.flow_id == flow_id, History.uid == uid)
            .distinct()
        )
        node_ids = session.exec(node_id_query).all()
        results: Dict[str, List[Any]] = {}

        # Retrieve recent history records for each node
        for node_id in node_ids:
            # Get only the most recent MAX_HISTORY_SIZE records for each node_id
            query = (
                select(History.raw_question, History.raw_answer, History.create_time)
                .where(
                    History.flow_id == flow_id,
                    History.node_id == node_id,
                    History.uid == uid,
                )
                .order_by(desc("create_time"))
                .limit(history_size)
            )
            query_results = session.exec(query).all()
            results[node_id] = list(query_results)

        # Process and format history data with token limits
        history: List[Dict[str, Any]] = []
        node_history_dict: Dict[str, List[Dict[str, Any]]] = {}
        current_utf8_length = 0

        for node_id, results_content in results.items():
            if node_id not in node_history_dict:
                node_history_dict[node_id] = []

            # Process each history record for the current node
            for raw_question, raw_answer in results_content:
                # Check token limits and break if exceeded
                current_utf8_length += len(raw_question.encode("utf-8")) + len(
                    raw_answer.encode("utf-8")
                )
                max_token: Optional[float] = None
                if node_max_token is not None:
                    # Use 80% of the specified token limit for safety margin
                    max_token = (
                        float(node_max_token.get(node_id, int(TOKEN_LIMIT))) * 0.8
                    )

                if max_token is not None and current_utf8_length > max_token:
                    break

                # Parse JSON strings back to dictionaries
                question_dict = json.loads(raw_question)
                answer_dict = json.loads(raw_answer)

                # Add answer and question to history in chronological order
                node_history_dict[node_id].append(
                    {
                        "role": answer_dict.get("role"),
                        "content": answer_dict.get("content"),
                    }
                )
                node_history_dict[node_id].append(
                    {
                        "role": question_dict.get("role"),
                        "content": question_dict.get("content"),
                    }
                )
        # Format final history structure
        for node_id, chat_history in node_history_dict.items():
            # Reverse to get chronological order (oldest first)
            chat_history.reverse()
            history.append({"nodeID": node_id, "chat_history": chat_history})
        return history
    except Exception as e:
        raise CustomException(
            CodeEnum.ENG_RUN_ERROR,
            err_msg=f"get_history method failed to retrieve LLM history; {e}",
            cause_error=f"err code : {CodeEnum.ENG_RUN_ERROR.code}. "
            f"message: get_history method failed "
            f"to retrieve LLM history; {e}",
        ) from e
