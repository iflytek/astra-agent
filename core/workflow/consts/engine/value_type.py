from enum import Enum


class ValueType(Enum):
    """
    Value type enumeration.

    Tracks the type of a value.
    """

    REF = "ref"
    LITERAL = "literal"
