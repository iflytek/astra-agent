import json
import re
from typing import Any, List, Literal, Optional, Union

from pydantic import BaseModel, Field, field_validator
from pydantic_core.core_schema import ValidationInfo
from workflow.consts.database import DBMode, ExecuteEnv
from workflow.engine.entities.variable_pool import ParamKey, VariablePool
from workflow.engine.nodes.base_node import BaseNode
from workflow.engine.nodes.entities.node_run_result import (
    NodeRunResult,
    WorkflowNodeExecutionStatus,
)
from workflow.engine.nodes.pgsql.pgsql_client import PGSqlClient, PGSqlConfig
from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.otlp.log_trace.node_log import NodeLog
from workflow.extensions.otlp.trace.span import Span

# Default values for different data types when handling null/empty conditions
ZERO = {
    "string": "",
    "integer": 0,
    "number": 0.0,
    "boolean": False,
    "array": [],
    "object": {},
}


class Condition(BaseModel):
    # Column/field name used in WHERE clause
    fieldName: str = Field(min_length=1)
    # Variable index placeholder, e.g. ${varIndex}
    varIndex: str = Field(min_length=1)
    # SQL comparison operator
    selectCondition: Literal[
        "=",
        "!=",
        "like",
        "not like",
        "in",
        "not in",
        "null",
        "not null",
        "<",
        "<=",
        ">",
        ">=",
    ]
    # Optional data type hint for the field
    fieldType: str = ""


class Case(BaseModel):
    # Logical operator that combines multiple conditions
    logicalOperator: Literal["and", "or"]
    # List of individual WHERE conditions
    conditions: List[Condition]


class OrderItem(BaseModel):
    # Column name used in ORDER BY clause
    fieldName: str = Field(min_length=1)
    # Sort direction: ascending or descending
    order: Literal["asc", "desc"]


class PGSqlNode(BaseNode):
    """PostgreSQL database operation node for workflow execution.

    This node handles various database operations including INSERT, UPDATE,
    SELECT, DELETE, and custom SQL execution through the PostgreSQL service.
    """

    # --- authentication & basic settings ---
    appId: str = Field(min_length=1, max_length=10)  # App identifier for API auth
    apiKey: str = Field(min_length=1)  # API key for service auth
    uid: str = Field(max_length=64, pattern=r"^[0-9a-zA-Z]+")  # User identifier
    dbId: int  # Target database ID
    mode: Literal[0, 1, 2, 3, 4]  # 0=CUSTOM,1=ADD,2=UPDATE,3=SEARCH,4=DELETE

    # --- optional SQL building blocks ---
    tableName: str | None = None  # Table name (required except for CUSTOM)
    spaceId: int | str | None = None  # Workspace/space identifier
    sql: str | None = Field(default=None, min_length=1)  # Raw SQL for CUSTOM mode
    cases: List[Case] = Field(default_factory=list)  # WHERE conditions
    assignmentList: List[str] = Field(default_factory=list)  # Columns for SELECT/UPDATE
    orderData: List[OrderItem] = Field(default_factory=list)  # ORDER BY configuration
    limit: int = Field(default=0, ge=0)  # LIMIT clause for SELECT

    # --- conditional validation ---
    @field_validator("sql", mode="after")
    def _check_custom_sql(cls, v: str | None, info: ValidationInfo) -> str | None:
        """
        Check if the SQL is valid for CUSTOM mode.

        :param v: SQL string
        :param info: ValidationInfo
        :return: SQL string
        :raises ValueError: If the SQL is invalid
        """
        if info.data.get("mode") == 0 and (v is None or v.strip() == ""):
            raise ValueError(
                "When mode=0 (CUSTOM), sql is required and must not be empty."
            )
        return v

    @field_validator("tableName", mode="after")
    def _check_table_name(cls, v: str | None, info: ValidationInfo) -> str | None:
        """
        Check if the table name is valid for the given mode.

        :param v: Table name string
        :param info: ValidationInfo
        :return: Table name string
        :raises ValueError: If the table name is invalid
        """
        mode = info.data.get("mode")
        if mode in (1, 2, 3, 4) and (v is None or v.strip() == ""):
            raise ValueError(f"When mode={mode}, tableName is required.")
        return v

    @field_validator("cases", mode="after")
    def _check_cases_for_update_delete(
        cls, v: List[Case], info: ValidationInfo
    ) -> List[Case]:
        """
        Check if the cases are valid for the given mode.

        :param v: Cases list
        :param info: ValidationInfo
        :return: Cases list
        :raises ValueError: If the cases are invalid
        """
        mode = info.data.get("mode")
        if mode in (2, 4) and not v:
            raise ValueError(
                "When mode=2 (UPDATE) or mode=4 (DELETE), cases cannot be empty."
            )
        return v

    @property
    def run_s(self) -> WorkflowNodeExecutionStatus:
        """Get the success execution status.

        :return: SUCCEEDED status for successful operations
        """
        return WorkflowNodeExecutionStatus.SUCCEEDED

    @property
    def run_f(self) -> WorkflowNodeExecutionStatus:
        """Get the failure execution status.

        :return: FAILED status for failed operations
        """
        return WorkflowNodeExecutionStatus.FAILED

    def replace_placeholders(self, template: str, replacements: dict) -> str:
        """Replace placeholder variables in SQL template with actual values.

        :param template: SQL template string containing {{variable}} placeholders
        :param replacements: Dictionary mapping variable names to their values
        :return: SQL string with placeholders replaced by actual values
        """
        # Compile regex pattern to match {{variable}} placeholders
        pattern = re.compile(r"\{\{(\w+)\}\}")

        def replacer(match: re.Match[str]) -> str:
            key = match.group(1)
            # Replace with actual value or keep original if not found
            return str(replacements.get(key, match.group(0)))

        return pattern.sub(replacer, template)

    def generate_insert_statement(self, data: dict) -> str:
        """Generate INSERT SQL statement from input data.

        :param data: Dictionary containing column names as keys and values to insert
        :return: Formatted INSERT SQL statement
        """
        # Build column names and values for INSERT statement
        columns = ", ".join(data.keys())
        values = ", ".join(
            [
                f"'{value}'" if isinstance(value, str) else str(value)
                for value in data.values()
            ]
        )
        return f"INSERT INTO {self.tableName} ({columns}) VALUES ({values});"

    def generate_update_statement(self, data: dict, case: Case) -> str:
        """Generate UPDATE SQL statement with SET clause and WHERE conditions.

        :param data: Dictionary containing column names and new values to update
        :param condition: Dictionary containing WHERE clause conditions
        :return: Formatted UPDATE SQL statement
        :raises CustomException: If WHERE conditions are empty or invalid
        """
        # Build SET clause for UPDATE statement
        set_clause = ", ".join(
            [
                f"{key} = '{value}'" if isinstance(value, str) else f"{key} = {value}"
                for key, value in data.items()
            ]
        )
        # Build WHERE clause conditions
        parts = []
        for condition in case.conditions:
            # Process each condition in the WHERE clause
            var_index = condition.varIndex
            if condition.selectCondition in ["null", "not null"]:
                # Handle NULL/NOT NULL conditions with zero value fallback
                part = f"{condition.fieldName} IS {condition.selectCondition.upper()}"
                field_type = condition.fieldType
                if field_type:
                    field_type = field_type.lower()
                    zero_value = ZERO.get(field_type)
                    if zero_value is not None:
                        # Add zero value comparison for better null handling
                        logic_op = (
                            "OR" if condition.selectCondition == "null" else "AND"
                        )
                        comp_op = "=" if condition.selectCondition == "null" else "!="
                        zero_str = {
                            "string": f"'{zero_value}'",
                            "integer": str(zero_value),
                            "number": str(zero_value),
                            "boolean": str(zero_value).upper(),
                        }.get(field_type)
                        if zero_str is not None:
                            part = (
                                "("
                                + part
                                + f" {logic_op} {condition.fieldName} {comp_op} {zero_str})"
                            )
            elif isinstance(var_index, str):
                # Handle string comparisons with LIKE/NOT LIKE support
                if condition.selectCondition in ["like", "not like"]:
                    var_index = f"%{var_index}%"
                part = f"{condition.fieldName} {condition.selectCondition.upper()} '{var_index}'"
            else:
                # Handle numeric and other comparisons
                part = f"{condition.fieldName} {condition.selectCondition.upper()} {var_index}"
            parts.append(part)

        # Combine conditions with logical operator
        where_clause = f" {case.logicalOperator.upper()} ".join(parts)
        if where_clause:
            return f"UPDATE {self.tableName} SET {set_clause} WHERE {where_clause};"
        else:
            raise CustomException(
                err_code=CodeEnum.PG_SQL_NODE_EXECUTION_ERROR,
                err_msg="Database DML statement generation failed: WHERE condition is empty",
                cause_error="Database DML statement generation failed: WHERE condition is empty",
            )

    def generate_delete_statement(self, case: Case) -> str:
        """Generate DELETE SQL statement with WHERE conditions.

        :param condition: Dictionary containing WHERE clause conditions
        :return: Formatted DELETE SQL statement
        :raises CustomException: If WHERE conditions are empty or invalid
        """
        # Build WHERE clause conditions for DELETE statement
        parts = []
        for condition in case.conditions:
            # Process each condition in the WHERE clause
            var_index = condition.varIndex
            if condition.selectCondition in ["null", "not null"]:
                # Handle NULL/NOT NULL conditions with zero value fallback
                part = f"{condition.fieldName} IS {condition.selectCondition.upper()}"
                field_type = condition.fieldType
                if field_type:
                    field_type = field_type.lower()
                    zero_value = ZERO.get(field_type)
                    if zero_value is not None:
                        # Add zero value comparison for better null handling
                        logic_op = (
                            "OR" if condition.selectCondition == "null" else "AND"
                        )
                        comp_op = "=" if condition.selectCondition == "null" else "!="
                        zero_str = {
                            "string": f"'{zero_value}'",
                            "integer": str(zero_value),
                            "boolean": str(zero_value).upper(),
                        }.get(field_type)
                        if zero_str is not None:
                            part = (
                                "("
                                + part
                                + f" {logic_op} {condition.fieldName} {comp_op} {zero_str})"
                            )
            elif isinstance(var_index, str):
                # Handle string comparisons with LIKE/NOT LIKE support
                if condition.selectCondition in ["like", "not like"]:
                    var_index = f"%{var_index}%"
                part = f"{condition.fieldName} {condition.selectCondition.upper()} '{var_index}'"
            else:
                # Handle numeric and other comparisons
                part = f"{condition.fieldName} {condition.selectCondition.upper()} {var_index}"
            parts.append(part)

        # Combine conditions with logical operator
        where_clause = f" {case.logicalOperator.upper()} ".join(parts)
        if where_clause:
            return f"DELETE FROM {self.tableName} WHERE {where_clause};"
        else:
            raise CustomException(
                err_code=CodeEnum.PG_SQL_NODE_EXECUTION_ERROR,
                err_msg="Database DML statement generation failed: WHERE condition is empty",
                cause_error="Database DML statement generation failed: WHERE condition is empty",
            )

    # ---------- Helper Methods for SQL Generation ----------
    def _build_where(
        self,
        case: Optional[Case] = None,
    ) -> str:
        """Build WHERE clause from condition dictionary.

        :param condition: Dictionary containing conditions and logical operator
        :return: Formatted WHERE clause string
        """
        if not case:
            return ""
        # Extract conditions from the condition dictionary
        conditions = case.conditions
        # Build condition parts, filtering out invalid conditions
        parts = [self._build_condition(c) for c in conditions]
        if not parts:
            return ""
        # Join conditions with logical operator
        logical_op = f" {case.logicalOperator.upper()} "
        return f" WHERE {logical_op.join(parts)}"

    def _build_condition(self, condition: Condition) -> str:
        """Build a single SQL condition from condition dictionary.

        :param c: Condition dictionary containing field, operator, and value
        :return: Formatted SQL condition string
        """
        field = condition.fieldName
        op = condition.selectCondition.upper()
        var = condition.varIndex
        ft = condition.fieldType.lower()

        # Handle NULL/NOT NULL conditions with zero value fallback
        if op in ("NULL", "NOT NULL"):
            base = f"{field} IS {op}"
            if ft in ZERO:
                # Add zero value comparison for better null handling
                logic = "OR" if op == "NULL" else "AND"
                comp = "=" if op == "NULL" else "!="
                zero_str = self._zero_literal(ft)
                return f"({base} {logic} {field} {comp} {zero_str})"
            return base

        # Handle string comparisons with LIKE/NOT LIKE support
        if isinstance(var, str):
            if op in ("LIKE", "NOT LIKE"):
                var = f"%{var}%"
            return f"{field} {op} '{var}'"

        # Handle numeric and other comparisons
        return f"{field} {op} {var}"

    def _zero_literal(self, ft: str) -> str:
        """Convert zero value to SQL literal based on field type.

        :param ft: Field type (string, integer, number, boolean)
        :return: SQL literal representation of zero value
        """
        z = ZERO[ft]
        if ft == "string":
            return f"'{z}'"
        if ft == "boolean":
            return str(z).upper()
        return str(z)

    def _build_order(self, order_by: List[OrderItem]) -> str:
        """Build ORDER BY clause from order configuration.

        :param order_by: Order configuration (list of dictionaries or string)
        :return: Formatted ORDER BY clause string
        """
        # Build order items from order configuration
        items = [f"{it.fieldName} {it.order.upper()}" for it in order_by]
        return f" ORDER BY {', '.join(items)}" if items else ""

    def _build_columns(self, columns: Union[str, List[str]]) -> str:
        """Build column list for SELECT statement.

        :param columns: Column names (string, list, or None)
        :return: Formatted column list string
        """
        if isinstance(columns, list):
            return ", ".join(columns) if columns else "*"
        return columns or "*"

    def generate_select_statement(
        self,
        columns: Union[str, List[str]] = "*",
        case: Optional[Case] = None,
        order_by: List[OrderItem] = [],
        limit: Optional[int] = None,
    ) -> str:
        """Generate SELECT SQL statement with optional WHERE, ORDER BY, and LIMIT clauses.

        :param columns: Column names to select (default: "*")
        :param condition: WHERE clause conditions
        :param order_by: ORDER BY clause configuration
        :param limit: LIMIT clause value
        :return: Formatted SELECT SQL statement
        """
        # Build all components of the SELECT statement
        cols = self._build_columns(columns)
        where = self._build_where(case)
        order = self._build_order(order_by)
        lim = f" LIMIT {limit}" if isinstance(limit, int) else ""
        return f"SELECT {cols} FROM {self.tableName}{where}{order}{lim};"

    async def generate_dml(self, inputs: dict, span: Span) -> str:
        """Generate DML statement based on operation mode and input data.

        :param inputs: Input data dictionary containing variable values
        :param span: Tracing span for monitoring
        :return: Generated DML statement string
        :raises CustomException: If operation mode is invalid or generation fails
        """
        with span.start(
            func_name="exec_dml_request", add_source_function_name=True
        ) as request_span:
            try:
                # Replace variable placeholders in conditions with actual values
                if self.cases and "conditions" in self.cases[0].conditions:
                    for case in self.cases[0].conditions:
                        if case.varIndex in inputs:
                            case.varIndex = inputs[case.varIndex]
                # Build update values from assignment list
                update_values = {f: inputs[f] for f in self.assignmentList or []}
                first_case = self.cases[0] if self.cases else None
                # Generate SQL based on operation mode
                compiled_sql = {
                    DBMode.ADD.value: lambda: self.generate_insert_statement(inputs),
                    DBMode.UPDATE.value: lambda: self.generate_update_statement(
                        update_values, self.cases[0]
                    ),
                    DBMode.SEARCH.value: lambda: self.generate_select_statement(
                        self.assignmentList or [],
                        first_case,
                        self.orderData,
                        self.limit,
                    ),
                    DBMode.DELETE.value: lambda: self.generate_delete_statement(
                        self.cases[0]
                    ),
                }.get(
                    self.mode,
                    lambda: (_ for _ in ()).throw(  # Throw exception for invalid mode
                        CustomException(
                            err_code=CodeEnum.PG_SQL_PARAM_ERROR,
                            err_msg="Mode is out of range",
                            cause_error="Mode is out of range",
                        )
                    ),
                )()
                # Log generated SQL for tracing
                request_span.add_info_events({"sql_string": compiled_sql})
                return compiled_sql
            except Exception as e:
                # Handle any errors during SQL generation
                err = str(e)
                request_span.add_error_event(err)
                raise CustomException(
                    err_code=CodeEnum.PG_SQL_NODE_EXECUTION_ERROR,
                    err_msg=f"Database DML statement generation failed: {err}",
                    cause_error=f"Database DML statement generation failed: {err}",
                ) from e

    async def generate_config(
        self,
        inputs: dict,
        is_release: bool,
        span: Span,
    ) -> PGSqlConfig:
        """Generate PostgreSQL configuration for database operations.

        :param inputs: Input data dictionary containing variable values
        :param is_release: Whether this is a production release
        :param span: Tracing span for monitoring
        :return: Configured PGSqlConfig object
        :raises CustomException: If required parameters are missing
        """
        # Validate required parameters based on operation mode
        if self.mode == DBMode.CUSTOM.value and not self.sql:
            raise CustomException(
                err_code=CodeEnum.PG_SQL_PARAM_ERROR,
                err_msg="Database input SQL is empty",
            )
        if self.mode != DBMode.CUSTOM.value and not self.tableName:
            raise CustomException(
                err_code=CodeEnum.PG_SQL_PARAM_ERROR,
                err_msg="Database input tableName is empty",
            )
        # Create PostgreSQL configuration object
        pgsql_config = PGSqlConfig(
            appId=self.appId,
            apiKey=self.apiKey,
            database_id=self.dbId,
            uid=self.uid,
            spaceId=str(self.spaceId) if self.spaceId else "",
            dml="",
        )
        # Set environment based on release status
        if is_release:
            pgsql_config.env = ExecuteEnv.PROD.value
        # Generate DML statement based on mode
        if self.mode == DBMode.CUSTOM.value:
            pgsql_config.dml = self.replace_placeholders(self.sql or "", inputs)
        else:
            pgsql_config.dml = await self.generate_dml(inputs, span)
        return pgsql_config

    async def async_execute(
        self,
        variable_pool: VariablePool,
        span: Span,
        event_log_node_trace: NodeLog | None = None,
        **kwargs: Any,
    ) -> NodeRunResult:
        """Asynchronous execution method.

        :param variable_pool: Variable pool containing input/output data
        :param span: Tracing span for monitoring
        :param event_log_node_trace: Optional node log trace
        :param kwargs: Additional keyword arguments
        :return: Node execution result
        """
        # Set user ID from span
        self.uid = span.uid
        return await self.execute(variable_pool, span)

    async def execute(
        self,
        variable_pool: VariablePool,
        span: Span,
    ) -> NodeRunResult:
        """Execute the PostgreSQL database operation.

        :param variable_pool: Variable pool containing input/output data
        :param span: Tracing span for monitoring
        :return: Node execution result with status and output data
        """
        inputs, outputs = {}, {}
        # Get input variables from variable pool
        inputs.update(
            {
                k: variable_pool.get_variable(
                    node_id=self.node_id, key_name=k, span=span
                )
                for k in self.input_identifier
            }
        )
        status = self.run_s
        # Log input data for tracing
        span.add_info_events({"inputs": json.dumps(inputs, ensure_ascii=False)})
        outputList = []
        try:
            # Get release status and generate PostgreSQL configuration
            is_release = variable_pool.system_params.get(ParamKey.IsRelease)
            pgsql_config = await self.generate_config(inputs, is_release, span)
            exec_result = await PGSqlClient(config=pgsql_config).exec_dml(span)
            # INSERT and UPDATE statements only return IDs, need to fetch full records for outputList
            if self.mode in [
                DBMode.CUSTOM.value,
                DBMode.ADD.value,
                DBMode.UPDATE.value,
            ]:
                if self.mode == DBMode.ADD.value:
                    for pgsql_result in exec_result.get("data", {}).get(
                        "exec_success", []
                    ):
                        pgsql_id = pgsql_result.get("id", "")
                        pgsql_config.dml = (
                            f"SELECT * FROM {self.tableName} WHERE id = {pgsql_id};"
                        )
                        exec_result = await PGSqlClient(config=pgsql_config).exec_dml(
                            span
                        )
                if self.mode == DBMode.UPDATE.value:
                    where_conditions = pgsql_config.dml[
                        pgsql_config.dml.find("WHERE") :
                    ]
                    pgsql_config.dml = (
                        f"SELECT * FROM {self.tableName} {where_conditions}"
                    )
                    exec_result = await PGSqlClient(config=pgsql_config).exec_dml(span)
                node_protocol = variable_pool.get_node_protocol(
                    node_id=self.node_id,
                )
                schema: dict[str, Any] = next(
                    (
                        k.output_schema
                        for k in node_protocol.outputs
                        if k.name == "outputList"
                    ),
                    {},
                )
                required = schema.get("items", {}).get("required", [])
                if self.mode == DBMode.CUSTOM.value and len(required) == 0:
                    outputList = exec_result.get("data", {}).get("exec_success", [])
                else:
                    if (
                        len(exec_result.get("data", {}).get("exec_success", [])) > 0
                        and len(required) > 0
                    ):
                        defaults = {
                            k: ZERO[v["type"]]
                            for k, v in schema["items"]["properties"].items()
                        }
                        outputList = [
                            {key: item.get(key, defaults[key]) for key in required}
                            for item in exec_result.get("data", {}).get(
                                "exec_success", []
                            )
                        ]
            else:
                outputList = exec_result.get("data", {}).get("exec_success", [])
            # DELETE statement does not need outputList
            outputs = {
                "isSuccess": True,
                "message": exec_result.get("message", ""),
                **(
                    {}
                    if self.mode == DBMode.DELETE.value
                    else {"outputList": outputList}
                ),
            }
            span.add_info_events({"outputs": json.dumps(outputs, ensure_ascii=False)})
        except CustomException as e:
            status = self.run_f
            span.add_error_event(str(e))
            span.record_exception(e)
            return NodeRunResult(
                status=status,
                error=e,
                node_id=self.node_id,
                alias_name=self.alias_name,
                node_type=self.node_type,
            )
        except Exception as e:
            status = self.run_f
            span.add_error_event(str(e))
            return NodeRunResult(
                status=status,
                error=CustomException(
                    CodeEnum.PG_SQL_NODE_EXECUTION_ERROR,
                    cause_error=e,
                ),
                node_id=self.node_id,
                alias_name=self.alias_name,
                node_type=self.node_type,
            )
        return NodeRunResult(
            status=status,
            inputs=inputs,
            outputs=outputs,
            node_id=self.node_id,
            alias_name=self.alias_name,
            node_type=self.node_type,
        )
