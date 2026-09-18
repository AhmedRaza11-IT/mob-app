import os
import zipfile
import struct
from PIL import Image, ImageDraw

uploads_dir = r'c:\MobileApp\server\uploads'
os.makedirs(uploads_dir, exist_ok=True)

# 1. PNG Image
img_png = Image.new('RGB', (600, 300), color=(30, 41, 59))
draw = ImageDraw.Draw(img_png)
draw.rectangle([20, 20, 580, 280], outline=(45, 212, 191), width=3)
draw.text((40, 50), 'VibeSync Admin - Screenshot (PNG)', fill=(255, 255, 255))
img_png.save(os.path.join(uploads_dir, 'device_screenshot_01.png'))

# 2. JPEG Image
img_jpg = Image.new('RGB', (600, 300), color=(88, 28, 135))
draw_j = ImageDraw.Draw(img_jpg)
draw_j.rectangle([20, 20, 580, 280], outline=(236, 72, 153), width=3)
draw_j.text((40, 50), 'VibeSync Camera Photo Sample (JPEG)', fill=(255, 255, 255))
img_jpg.save(os.path.join(uploads_dir, 'camera_photo_02.jpg'), 'JPEG')

# 3. HTML Document
html_content = """<!DOCTYPE html>
<html>
<head><title>VibeSync Audit Report</title><style>body{font-family:sans-serif;background:#0f172a;color:#fff;padding:2rem;}</style></head>
<body><h1>VibeSync Security Audit</h1><p>System status: ALL GREEN</p></body>
</html>"""
with open(os.path.join(uploads_dir, 'audit_log_view.html'), 'w', encoding='utf-8') as f:
    f.write(html_content)

# 4. CSV File
csv_text = "User ID,Username,Display Name,Role,Status\n1,john_doe,John Doe,Admin,Active\n2,sarah_m,Sarah Miller,User,Active\n"
with open(os.path.join(uploads_dir, 'user_backups_2026.csv'), 'w', encoding='utf-8') as f:
    f.write(csv_text)

# 5. TXT Debug Log
txt_text = "[INFO] 2026-09-16 16:55:00 - VibeSync FastAPI Engine Initialized\n[INFO] Device sync gateway connected\n"
with open(os.path.join(uploads_dir, 'system_debug_log.txt'), 'w', encoding='utf-8') as f:
    f.write(txt_text)

# 6. PDF Document
pdf_content = (
    '%PDF-1.4\n'
    '1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n'
    '2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n'
    '3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> >> >> >>\nendobj\n'
    '4 0 obj\n<< /Length 120 >>\nstream\nBT /F1 20 Tf 50 720 Td (VibeSync System Telemetry Report) Tj /F1 12 Tf 0 -30 Td (Generated on: 2026-09-16) Tj ET\nendstream\nendobj\n'
    'xref\n0 5\n0000000000 65535 f \n0000000009 00000 n \n0000000058 00000 n \n0000000115 00000 n \n0000000282 00000 n \n'
    'trailer\n<< /Size 5 /Root 1 0 R >>\nstartxref\n450\n%%EOF'
)
with open(os.path.join(uploads_dir, 'vibesync_system_report.pdf'), 'wb') as f:
    f.write(pdf_content.encode('latin1'))

# 7. Word Document (.docx - Minimal ZIP container format)
docx_path = os.path.join(uploads_dir, 'contract_draft.docx')
with zipfile.ZipFile(docx_path, 'w') as z:
    z.writestr('[Content_Types].xml', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>')
    z.writestr('_rels/.rels', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>')
    z.writestr('word/document.xml', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>VibeSync Official Contract Draft Document</w:t></w:r></w:p></w:body></w:document>')

# 8. Excel Document (.xlsx - Minimal ZIP container format)
xlsx_path = os.path.join(uploads_dir, 'financial_ledger.xlsx')
with zipfile.ZipFile(xlsx_path, 'w') as z:
    z.writestr('[Content_Types].xml', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/></Types>')
    z.writestr('_rels/.rels', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>')
    z.writestr('xl/workbook.xml', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheets><sheet name="Ledger" sheetId="1" r:id="rId1" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"/></sheets></workbook>')

# 9. MP3 Audio File
mp3_bytes = bytes([0xFF, 0xFB, 0x90, 0x64, 0x00, 0x00, 0x00, 0x00] * 1000)
with open(os.path.join(uploads_dir, 'voice_call_record_001.mp3'), 'wb') as f:
    f.write(mp3_bytes)

# 10. MP4 Video File
mp4_bytes = struct.pack('>I4s4sI', 32, b'ftyp', b'isom', 512) + b'\x00' * 500
with open(os.path.join(uploads_dir, 'screen_recording_demo.mp4'), 'wb') as f:
    f.write(mp4_bytes)

print("All 10 formats (PNG, JPEG, HTML, DOCX, XLSX, CSV, MP3, MP4, PDF, TXT) generated successfully!")
