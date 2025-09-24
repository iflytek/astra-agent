from datetime import datetime

from plugin.link.consts import const
from plugin.link.domain.entity.tool_schema import Tools
from plugin.link.domain.models.utils import DatabaseService, session_getter
from plugin.link.exceptions.sparklink_exceptions import ToolNotExistsException
from plugin.link.utils.errors.code import ErrCode
from plugin.link.utils.otlp.trace.span import Span
from sqlalchemy import desc
from sqlalchemy.exc import IntegrityError, NoResultFound
from sqlmodel import select


class ToolCrudOperation:
    """
    Tool CRUD operations for managing tool lifecycle in the database.

    This class provides methods to create, read, update, and delete tools
    from the database, including versioning and MCP tool management.
    """

    def __init__(self, engine: DatabaseService):
        self.engine = engine

    def add_tools(self, tool_info: list):
        """
        description: Create tools
        """
        with session_getter(self.engine) as session:
            for tool in tool_info:
                tool_inst = Tools(
                    app_id=tool.get("app_id"),
                    tool_id=tool.get("tool_id"),
                    name=tool.get("name"),
                    description=tool.get("description"),
                    open_api_schema=tool.get("schema"),
                    version=tool.get("version", const.DEF_VER),
                    is_deleted=tool.get("is_deleted", const.DEF_DEL),
                )
                session.add(tool_inst)
                session.commit()

    def add_mcp(self, mcp_info: dict):
        """
        description: Create MCP tool, update if tool already exists
        """
        with session_getter(self.engine) as session:
            app_id = mcp_info.get("app_id")
            tool_id = mcp_info.get("tool_id")
            name = mcp_info.get("name")
            description = mcp_info.get("description")
            schema = mcp_info.get("schema")
            mcp_server_url = mcp_info.get("mcp_server_url", "")
            version = mcp_info.get("version", const.DEF_VER)
            is_deleted = mcp_info.get("is_deleted", const.DEF_DEL)
            try:
                query = (
                    select(Tools)
                    .where(Tools.tool_id == tool_id)
                    .order_by(desc(Tools.update_at))
                )
                tool_inst = session.exec(query).first()
            except NoResultFound:
                # Catch exception when no records found
                # print("Tool not found")
                tool_inst = None  # Ensure tool_inst has a default value

            if tool_inst:
                tool_inst.app_id = app_id
                tool_inst.name = name
                tool_inst.description = description
                tool_inst.schema = schema
                tool_inst.mcp_server_url = mcp_server_url
                tool_inst.version = version
                tool_inst.is_deleted = is_deleted
            else:
                tool_inst = Tools(
                    app_id=app_id,
                    tool_id=tool_id,
                    name=name,
                    description=description,
                    schema=schema,
                    mcp_server_url=mcp_server_url,
                    version=version,
                    is_deleted=is_deleted,
                )
            session.add(tool_inst)
            session.commit()

    def update_tools(self, tool_info: list):
        """
        description: Update tools
        """
        with session_getter(self.engine) as session:
            for tool in tool_info:
                tool_id = tool.get("tool_id")
                version = (tool.get("version", const.DEF_VER),)
                is_deleted = tool.get("is_deleted", const.DEF_DEL)
                query = (
                    select(Tools)
                    .where(
                        Tools.tool_id == tool_id,
                        Tools.version == version,
                        Tools.is_deleted == is_deleted,
                    )
                    .order_by(desc(Tools.update_at))
                )
                tool_inst = session.exec(query).first()
                if tool_inst is None:
                    raise ToolNotExistsException(
                        code=ErrCode.TOOL_NOT_EXIST_ERR.code,
                        err_pre=ErrCode.TOOL_NOT_EXIST_ERR.msg,
                        err="tools don't exist!",
                    )

                if tool.get("name"):
                    tool_inst.name = tool.get("name")
                if tool.get("description"):
                    tool_inst.description = tool.get("description")
                if tool.get("open_api_schema"):
                    tool_inst.open_api_schema = tool.get("open_api_schema")
                session.add(tool_inst)
                session.commit()

    def add_tool_version(self, tool_info: list):
        """
        description: Add tool version
        """
        with session_getter(self.engine) as session:
            for tool in tool_info:
                try:
                    tool_inst = Tools(
                        app_id=tool.get("app_id"),
                        tool_id=tool.get("tool_id"),
                        name=tool.get("name"),
                        description=tool.get("description"),
                        open_api_schema=tool.get("open_api_schema"),
                        version=tool.get("version", const.DEF_VER),
                        is_deleted=tool.get("is_deleted", const.DEF_DEL),
                    )
                    session.add(tool_inst)
                    session.commit()
                except IntegrityError as e:
                    session.rollback()
                    raise Exception("Version already exists!") from e

    def delete_tools(self, tool_info: list):
        """
        description: Delete tools
        """
        with session_getter(self.engine) as session:
            for tool in tool_info:
                tool_id = tool.get("tool_id", "")
                version = (tool.get("version", ""),)
                if isinstance(version, tuple):
                    # If it's a tuple, take the first element
                    version = version[0]

                is_deleted = tool.get("is_deleted", const.DEF_DEL)
                if version:
                    query = (
                        select(Tools)
                        .where(
                            Tools.tool_id == tool_id,
                            Tools.version == version,
                            Tools.is_deleted == is_deleted,
                        )
                        .order_by(desc(Tools.update_at))
                    )
                else:
                    query = (
                        select(Tools)
                        .where(Tools.tool_id == tool_id, Tools.is_deleted == is_deleted)
                        .order_by(desc(Tools.update_at))
                    )
                query_result = session.exec(query).all()
                if query_result:
                    for tool in query_result:
                        tool.is_deleted = int(datetime.now().timestamp())
                    session.commit()

    def get_tools(self, tool_info: list, span: Span):
        """
        description: Get tools
        """
        result = []
        with span.start(func_name="db_get") as span_context:
            span_context.add_info_event(f"tool_info:{str(tool_info)}")
            with session_getter(self.engine) as session:
                for tool in tool_info:
                    tool_id = tool.get("tool_id", "")
                    version = tool.get("version", const.DEF_VER)
                    if isinstance(version, tuple):
                        version = version[0]

                    is_deleted = tool.get("is_deleted", const.DEF_DEL)
                    query = (
                        select(Tools)
                        .where(
                            Tools.tool_id == tool_id,
                            Tools.version == version,
                            Tools.is_deleted == is_deleted,
                        )
                        .order_by(desc(Tools.update_at))
                    )
                    query_result = session.exec(query).first()
                    if query_result:
                        result.append(query_result)
                    else:
                        err = f"{tool_id} {str(version)} does not exist"
                        raise ToolNotExistsException(
                            code=ErrCode.TOOL_NOT_EXIST_ERR.code,
                            err_pre=ErrCode.TOOL_NOT_EXIST_ERR.msg,
                            err=err,
                        )
            span_context.add_info_event(f"result:{str(result)}")
        return result
