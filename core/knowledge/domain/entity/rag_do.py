# pylint: disable=invalid-name
"""
RAG data object module
Defines data model classes related to Retrieval-Augmented Generation (RAG)
"""
from typing import Union


class ChunkInfo:
    """Class representing document chunk information"""

    def __init__(
        self,
        docId: Union[str, int],
        chunkId: Union[int, str],
        content: str,
    ) -> None:

        self.docId = docId
        self.chunkId = chunkId
        self.content = content


class FileInfo:
    """Class representing file information"""

    # pylint: disable=too-few-public-methods

    def __init__(
        self,
        docId: Union[str, int],
        fileName: str,
        fileStatus: str = "",
        fileQuantity: int = 0,
    ) -> None:
        """
        Initialize FileInfo instance

        Args:
            docId: Document identifier
            fileName: File name
            fileStatus: File status
            fileQuantity: File quantity
        """
        self.docId = docId
        self.fileName = fileName
        self.fileStatus = fileStatus
        self.fileQuantity = fileQuantity

    def __repr__(self) -> str:
        """String representation of FileInfo"""
        return f"FileInfo(docId={self.docId}, fileName={self.fileName}, fileStatus={self.fileStatus}, fileQuantity={self.fileQuantity})"
