import os
import datetime
from reportlab.lib.pagesizes import A4, A3, A2, A1, A0, landscape, portrait
from reportlab.platypus import SimpleDocTemplate, Paragraph, Table, TableStyle, Image, Spacer
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib import colors
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from ..core.config import logger

def register_cyrillic_font():
    font_path = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
    font_bold_path = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
    
    if os.path.exists(font_path):
        try:
            pdfmetrics.registerFont(TTFont('DejaVuSans', font_path))
            logger.info("Registered font DejaVuSans")
        except Exception as e:
            logger.error("Failed to register DejaVuSans: %s", str(e))
            
    if os.path.exists(font_bold_path):
        try:
            pdfmetrics.registerFont(TTFont('DejaVuSans-Bold', font_bold_path))
            logger.info("Registered font DejaVuSans-Bold")
        except Exception as e:
            logger.error("Failed to register DejaVuSans-Bold: %s", str(e))

# Register fonts on import
register_cyrillic_font()

def build_pdf_report(map_image_path: str, output_pdf_path: str, spec: dict):
    logger.info("Building PDF layout report")
    
    layout_name = spec.get("layout", "A3_LANDSCAPE")
    attributes = spec.get("attributes") or {}
    project_name = attributes.get("projectName", "ГеоИнфоСистема Проект")
    creator = attributes.get("creator", "Воркер генерации отчетов")
    
    # Page size maps
    size_map = {
        "A4": A4,
        "A3": A3,
        "A2": A2,
        "A1": A1,
        "A0": A0
    }
    
    page_format = "A3"
    is_landscape = True
    
    parts = layout_name.split("_")
    if len(parts) >= 1:
        if parts[0] in size_map:
            page_format = parts[0]
    if len(parts) >= 2:
        if parts[1] == "PORTRAIT":
            is_landscape = False
            
    page_size = size_map[page_format]
    if is_landscape:
        page_size = landscape(page_size)
        
    width, height = page_size
    
    # Margin calculation
    margin = 36 # 0.5 inch
    doc_width = width - (2 * margin)
    doc_height = height - (2 * margin)
    
    # Set up doc
    doc = SimpleDocTemplate(
        output_pdf_path,
        pagesize=page_size,
        rightMargin=margin, leftMargin=margin, topMargin=margin, bottomMargin=margin
    )
    
    story = []
    
    # Styles
    styles = getSampleStyleSheet()
    
    has_font = "DejaVuSans" in pdfmetrics.getRegisteredFontNames()
    title_font = "DejaVuSans-Bold" if has_font else "Helvetica-Bold"
    body_font = "DejaVuSans" if has_font else "Helvetica"
    
    title_style = ParagraphStyle(
        'CustomTitle',
        parent=styles['Title'],
        fontName=title_font,
        fontSize=20 if page_format in ("A4", "A3") else 28,
        textColor=colors.HexColor("#0f172a"),
        spaceAfter=15
    )
    
    cell_style = ParagraphStyle(
        'CellBody',
        parent=styles['Normal'],
        fontName=body_font,
        fontSize=10,
        leading=12
    )
    
    cell_bold_style = ParagraphStyle(
        'CellBold',
        parent=styles['Normal'],
        fontName=title_font,
        fontSize=10,
        leading=12
    )

    # 1. Title
    title_text = f"КАРТОГРАФИЧЕСКИЙ ОТЧЕТ: {project_name}"
    story.append(Paragraph(title_text, title_style))
    
    # 2. Map Image
    map_height = doc_height * 0.65
    map_img = Image(map_image_path, width=doc_width, height=map_height)
    story.append(map_img)
    story.append(Spacer(1, 15))
    
    # 3. Document Stamp Table
    date_str = datetime.date.today().strftime("%d.%m.%Y")
    
    stamp_data = [
        [
            Paragraph("<b>Наименование проекта:</b>", cell_bold_style),
            Paragraph(project_name, cell_style),
            Paragraph("<b>Исполнитель:</b>", cell_bold_style),
            Paragraph(creator, cell_style)
        ],
        [
            Paragraph("<b>Дата генерации:</b>", cell_bold_style),
            Paragraph(date_str, cell_style),
            Paragraph("<b>Формат листа:</b>", cell_bold_style),
            Paragraph(f"{page_format} ({'Альбомный' if is_landscape else 'Книжный'})", cell_style)
        ]
    ]
    
    col_w = doc_width / 4.0
    stamp_table = Table(stamp_data, colWidths=[col_w, col_w, col_w, col_w])
    stamp_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (0,1), colors.HexColor("#f1f5f9")),
        ('BACKGROUND', (2,0), (2,1), colors.HexColor("#f1f5f9")),
        ('GRID', (0,0), (-1,-1), 1, colors.HexColor("#94a3b8")),
        ('VALIGN', (0,0), (-1,-1), 'MIDDLE'),
        ('TOPPADDING', (0,0), (-1,-1), 8),
        ('BOTTOMPADDING', (0,0), (-1,-1), 8),
    ]))
    
    story.append(stamp_table)
    
    doc.build(story)
    logger.info("PDF layout report compiled successfully to %s", output_pdf_path)
