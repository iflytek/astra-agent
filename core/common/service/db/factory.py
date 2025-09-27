import os

from common.service.base import ServiceFactory
from common.service.db.db_service import DatabaseService


class DatabaseServiceFactory(ServiceFactory):
    def __init__(self) -> None:
        super().__init__(DatabaseService)  # type: ignore[arg-type]

    def create(self) -> DatabaseService:  # type: ignore[override, no-untyped-def]
        """
        Create a new DatabaseService instance.
        :param database_url:
        :return:
        """
        host = os.getenv("MYSQL_HOST")
        port = os.getenv("MYSQL_PORT")
        user = os.getenv("MYSQL_USER")
        password = os.getenv("MYSQL_PASSWORD")
        db = os.getenv("MYSQL_DB")
        database_url = f"mysql+pymysql://{user}:{password}@{host}:{port}/{db}"
        return DatabaseService(database_url=database_url)
