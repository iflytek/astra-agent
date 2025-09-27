from enum import Enum


class CodeEnum(Enum):
    Successes = (0, "success")
    HttpError = (25500, "Server error")

    ParamError = (25000, "Parameter validation error")

    DatabaseExecutionError = (25010, "Database operation failed")
    CreatDBError = (25011, "Failed to create database")
    DeleteDBError = (25012, "Failed to delete database")
    DatabaseNotExistError = (25013, "Database does not exist")
    ModifyDBDescriptionError = (25014, "Failed to modify database description")
    SpaceIDNotExistError = (25015, "Team space does not exist")

    NoAuthorityError = (25020, "Permission error")
    NoSchemaError = (25021, "User schema does not exist")

    SQLParseError = (25030, "SQL syntax parsing failed")

    DDLNotAllowed = (25040, "DDL syntax not allowed")
    DDLExecutionError = (25041, "DDL statement execution failed")

    DMLNotAllowed = (25050, "DML syntax not allowed")
    DMLExecutionError = (25051, "DML statement execution failed")

    # ---------------- Interface deprecated ----------------------
    UploadFileTypeError = (25100, "Incorrect upload file type")
    ParseFileError = (25200, "File parsing failed")
    FileEmptyError = (25300, "File is empty")
    # ----------------------------------------------

    @property
    def code(self) -> int:
        return self.value[0]

    @property
    def msg(self) -> str:
        return self.value[1]
