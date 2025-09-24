from typing import List

import fitz

DOCUMENT_PAGE_UNLIMITED = -1


def pdf_convert_png(
    pdf_content: bytes,
    page_start: int = DOCUMENT_PAGE_UNLIMITED,
    page_end: int = DOCUMENT_PAGE_UNLIMITED,
) -> List[bytes]:
    """
    pdf转图片，png格式
    Args:
        pdf_content:
        page_start: 页码开始范围，-1表示全部页码，从0开始
        page_end:   页码结束范围，-1表示全部页码，从0开始

    Returns:

    """
    if (
        page_start > page_end != DOCUMENT_PAGE_UNLIMITED
        and page_start != DOCUMENT_PAGE_UNLIMITED
    ):
        raise ValueError("page_start should be less than page_end")

    if not pdf_content.startswith(b"%PDF-"):
        raise ValueError("pdf content is invalid")

    pngs = []
    with fitz.Document(stream=pdf_content, filetype="pdf") as pdf:
        for i, page in enumerate(pdf):
            if page_start != DOCUMENT_PAGE_UNLIMITED and i < page_start:
                continue
            if page_end != DOCUMENT_PAGE_UNLIMITED and i > page_end:
                break
            # rotate = int(0)
            # 每个尺寸的缩放系数为2，这将为我们生成分辨率提高4的图像。
            # 此处若是不做设置，默认图片大小为：792X612, dpi=96
            zoom_x = 2  # (2-->1584x1224)
            zoom_y = 2
            mat = fitz.Matrix(zoom_x, zoom_y)
            pixmap = page.get_pixmap(matrix=mat, alpha=False)
            image_bytes = pixmap.pil_tobytes(format="PNG")
            pngs.append(image_bytes)
    return pngs
