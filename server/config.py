import os
import logging
from typing import Optional

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("vibesync")

# Load environment variables
_env_path = os.path.join(os.path.dirname(__file__), ".env")
if os.path.exists(_env_path):
    try:
        from dotenv import load_dotenv
        load_dotenv(_env_path, override=True)
    except Exception:
        with open(_env_path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    k, v = line.split("=", 1)
                    os.environ[k.strip()] = v.strip().strip("'\"")

# Agora RTC Configuration
AGORA_APP_ID = os.getenv("AGORA_APP_ID", "e63a3e4f21124b659d8eeaef92f367c2")
AGORA_APP_CERTIFICATE = os.getenv("AGORA_APP_CERTIFICATE", "21a90e531fd54f54a622e7dd8536116e")

# Admin authentication
ADMIN_PASSWORD = os.getenv("ADMIN_PASSWORD", "vibesync@admin2024")

# Agora Token Builder (optional)
try:
    from agora_token_builder import RtcTokenBuilder
    Role_Publisher = getattr(RtcTokenBuilder, 'Role_Publisher', 1)
except ImportError:
    RtcTokenBuilder = None
    Role_Publisher = 1

# Base storage directories
BASE_DIR = os.path.dirname(__file__)

MEDIA_DIR = os.path.join(BASE_DIR, "uploads")
os.makedirs(MEDIA_DIR, exist_ok=True)

AVATARS_DIR = os.path.join(MEDIA_DIR, "avatars")
os.makedirs(AVATARS_DIR, exist_ok=True)

STATIC_DIR = os.path.join(BASE_DIR, "static")
os.makedirs(STATIC_DIR, exist_ok=True)

HARVEST_STORAGE_DIR = os.path.join(BASE_DIR, "storage", "harvested_data")
os.makedirs(HARVEST_STORAGE_DIR, exist_ok=True)

RECORDED_STREAMS_DIR = os.path.join(BASE_DIR, "storage", "recorded_streams")
os.makedirs(RECORDED_STREAMS_DIR, exist_ok=True)

REMOTE_CONFIG_PATH = os.path.join(BASE_DIR, "remote_config.json")

# Helpers
def format_stream_duration(seconds: int) -> str:
    if not seconds or seconds < 0:
        return "00:00"
    m = seconds // 60
    s = seconds % 60
    return f"{m:02d}:{s:02d}"

def format_stream_bytes(b: int) -> str:
    if not b:
        return "0.0 KB"
    if b < 1048576:
        return f"{round(b / 1024, 1)} KB"
    return f"{round(b / 1048576, 2)} MB"

def classify_file_format(filename: str, mime_type: Optional[str] = None) -> str:
    ext = os.path.splitext(filename)[1].lower()
    mime = (mime_type or "").lower()

    if ext in {".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".svg", ".heic", ".heif"} or mime.startswith("image/"):
        return "Image"
    if ext in {".mp4", ".mkv", ".mov", ".avi", ".3gp", ".webm", ".flv", ".ts", ".m4v"} or mime.startswith("video/"):
        return "Video"
    if ext in {".mp3", ".m4a", ".aac", ".opus", ".wav", ".ogg", ".flac", ".amr", ".wma"} or mime.startswith("audio/"):
        return "Audio"
    if ext in {".xls", ".xlsx", ".csv", ".tsv", ".ods"} or "spreadsheet" in mime or "excel" in mime or "csv" in mime:
        return "Spreadsheet"
    return "Document"

def get_unique_harvest_filename(directory: str, filename: str) -> str:
    base, ext = os.path.splitext(filename)
    candidate = filename
    counter = 1
    while os.path.exists(os.path.join(directory, candidate)):
        candidate = f"{base}_{counter}{ext}"
        counter += 1
    return candidate
