"""
SQL utility module for converting SQL statements to parametric format.
"""

import itertools
from typing import Any, Dict, Iterator, Tuple

from sqlglot import exp, parse_one


def generate_param_name(index: int) -> str:
    """Generate parameter name with given index.

    Args:
        index: Parameter index number

    Returns:
        str: Generated parameter name in format 'param{index}'
    """
    return f"param{index}"


def replace_literals_with_params(
    expression: exp.Expression, param_map: Dict[str, Any], counter: Iterator[int]
) -> exp.Expression:
    """Replace literals in SQL expression with parameters.

    Args:
        expression: SQL expression to process
        param_map: Dictionary to store parameter mappings
        counter: Iterator for generating parameter indices

    Returns:
        exp.Expression: Expression with literals replaced by parameters
    """
    if isinstance(expression, exp.Boolean):
        key = generate_param_name(next(counter))
        param_map[key] = (
            expression.this is True or str(expression.this).upper() == "TRUE"
        )
        return exp.Var(this=f":{key}")

    if isinstance(expression, exp.Literal):
        key, value = _handle_literal(expression, counter)
        param_map[key] = value
        return exp.Var(this=f":{key}")

    if isinstance(expression, exp.Expression):
        args = {
            key: (
                [replace_literals_with_params(e, param_map, counter) for e in val]
                if isinstance(val, list)
                else (
                    replace_literals_with_params(val, param_map, counter)
                    if isinstance(val, exp.Expression)
                    else val
                )
            )
            for key, val in expression.args.items()
        }
        return expression.__class__(**args)

    return expression


def _handle_literal(
    expression: exp.Expression, counter: Iterator[int]
) -> Tuple[str, Any]:
    """Handle literal values in SQL expression.

    Args:
        expression: Literal expression to process
        counter: Iterator for generating parameter indices

    Returns:
        tuple: (parameter key, parameter value)
    """
    raw = expression.this
    if expression.is_string:
        value = raw
    else:
        if str(raw).upper() == "TRUE":
            value = True
        elif str(raw).upper() == "FALSE":
            value = False
        elif str(raw).upper() == "NULL":
            value = None
        else:
            try:
                value = int(raw)
            except ValueError:
                try:
                    value = float(raw)
                except ValueError:
                    value = raw
    key = generate_param_name(next(counter))

    return key, value


def convert_sql_to_parametric(sql: str) -> Tuple[str, Dict[str, Any]]:
    """
    Replace all literals (numbers, strings, booleans) in SQL statement with
    parameters, and return parameterized SQL and parameter dictionary.
    """
    # Parse SQL syntax tree
    tree = parse_one(sql)
    param_map: Dict[str, Any] = {}
    counter = itertools.count(1)
    # Replace all literals with parameters
    new_tree = replace_literals_with_params(tree, param_map, counter)
    # Generate parameterized SQL
    param_sql = new_tree.sql()
    # Remove colon from parameter names
    params = dict(param_map)
    return param_sql, params
