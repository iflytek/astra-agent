"""API router module for Xingchen DB service.

This module defines the main API router and includes all version 1 sub-routers.
It sets up the common prefix '/xingchen-db/v1' for all API endpoints.
"""

from fastapi import APIRouter
from memory.database.api.v1 import (clone_db_router, create_db_router,
                                    drop_db_router, exec_ddl_router,
                                    exec_dml_router, export_data_router,
                                    modify_db_description_router,
                                    upload_data_router)

router = APIRouter(
    prefix="/xingchen-db/v1",
)

router.include_router(create_db_router)
router.include_router(exec_ddl_router)
router.include_router(exec_dml_router)
router.include_router(upload_data_router)
router.include_router(export_data_router)
router.include_router(clone_db_router)
router.include_router(drop_db_router)
router.include_router(modify_db_description_router)
