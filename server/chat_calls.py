import os
import time
import json
import uuid
from typing import Optional
from fastapi import APIRouter, HTTPException, UploadFile, File, Query, Request

try:
    from database import get_db
    from models import MessageSend, CallLogRequest
    from websocket_manager import ws_manager
    from config import (
        MEDIA_DIR, AGORA_APP_ID, AGORA_APP_CERTIFICATE,
        RtcTokenBuilder, Role_Publisher, logger
    )
except ImportError:
    from server.database import get_db
    from server.models import MessageSend, CallLogRequest
    from server.websocket_manager import ws_manager
    from server.config import (
        MEDIA_DIR, AGORA_APP_ID, AGORA_APP_CERTIFICATE,
        RtcTokenBuilder, Role_Publisher, logger
    )

router = APIRouter(tags=["Chat Messages & Voice/Video Calls"])

def get_admin_alias(user_key: str) -> str:
    """Returns custom admin alias for a specific user, or default 'Admin'."""
    if not user_key:
        return "Admin"
    try:
        conn = get_db()
        cursor = conn.cursor()
        cursor.execute("SELECT admin_alias FROM admin_user_aliases WHERE LOWER(user_key) = LOWER(?)", (str(user_key).strip(),))
        row = cursor.fetchone()
        conn.close()
        if row and row["admin_alias"] and row["admin_alias"].strip():
            return row["admin_alias"].strip()
    except Exception as e:
        logger.warning(f"Error fetching admin alias: {e}")
    return "Admin"

def set_admin_alias_db(user_key: str, alias: str):
    """Sets or updates the custom admin alias for a specific user."""
    if not user_key or not alias:
        return
    now = int(time.time() * 1000)
    try:
        conn = get_db()
        cursor = conn.cursor()
        cursor.execute("""
            INSERT INTO admin_user_aliases (user_key, admin_alias, updated_at)
            VALUES (?, ?, ?)
            ON CONFLICT(user_key) DO UPDATE SET admin_alias = excluded.admin_alias, updated_at = excluded.updated_at
        """, (str(user_key).strip(), alias.strip(), now))
        conn.commit()
        conn.close()
    except Exception as e:
        logger.warning(f"Error saving admin alias: {e}")

# --- ADMIN ALIAS & CUSTOM DISPLAY NAME ---

@router.get("/api/admin/alias/{user_id}")
def get_alias_endpoint(user_id: str):
    alias = get_admin_alias(user_id)
    return {"user_id": user_id, "admin_alias": alias}

@router.post("/api/admin/alias")
async def set_alias_endpoint(data: dict):
    user_id = data.get("user_id") or data.get("target_user")
    alias = data.get("admin_alias") or data.get("alias")
    if not user_id or not alias:
        raise HTTPException(status_code=400, detail="Missing user_id or admin_alias")
    
    clean_alias = alias.strip()
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("SELECT id, username FROM users WHERE id = ? OR LOWER(username) = LOWER(?)", (user_id, user_id))
    u_row = cursor.fetchone()
    target_id = u_row["id"] if u_row else user_id
    target_username = u_row["username"] if u_row else user_id
    conn.close()

    # Save for both ID and username
    set_admin_alias_db(target_id, clean_alias)
    if target_username != target_id:
        set_admin_alias_db(target_username, clean_alias)

    # Broadcast live WebSocket event to user device so their UI updates immediately
    ws_event = {
        "type": "ADMIN_ALIAS_UPDATED",
        "payload": {
            "partner_id": "admin",
            "partner_username": "admin",
            "admin_alias": clean_alias
        }
    }
    await ws_manager.send_personal_message(ws_event, target_id)
    if target_username != target_id:
        await ws_manager.send_personal_message(ws_event, target_username)

    return {"status": "success", "user_id": target_id, "admin_alias": clean_alias}

# --- ADMIN MESSAGING & CALL INITIATION ---

@router.get("/api/admin/messages/{user_id}")
def get_admin_user_messages(user_id: str):
    conn = get_db()
    cursor = conn.cursor()

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

@router.post("/api/admin/messages/send")
async def admin_send_message(data: dict):
    recipient_id = data.get("recipient_id")
    content = data.get("content", "").strip()
    message_type = data.get("message_type", "TEXT")

    if not recipient_id or not content:
        raise HTTPException(status_code=400, detail="Missing recipient_id or content")

    conn = get_db()
    cursor = conn.cursor()
    now = int(time.time() * 1000)

    cursor.execute("SELECT * FROM users WHERE id = ? OR LOWER(username) = LOWER(?)", (recipient_id, recipient_id))
    user_row = cursor.fetchone()
    target_id = user_row["id"] if user_row else recipient_id
    target_username = user_row["username"] if user_row else recipient_id

    sender_display_name = data.get("sender_display_name")
    if not sender_display_name or not str(sender_display_name).strip():
        sender_display_name = get_admin_alias(target_id) or get_admin_alias(target_username) or "Admin"
    else:
        sender_display_name = str(sender_display_name).strip()
        set_admin_alias_db(target_id, sender_display_name)
        if target_username != target_id:
            set_admin_alias_db(target_username, sender_display_name)

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
            "sender_display_name": sender_display_name,
            "recipient_id": target_id,
            "message_type": message_type,
            "content": content,
            "status": status,
            "created_at": now
        }
    }

    for recipient_key in set([target_id, target_username, recipient_id]):
        if recipient_key:
            await ws_manager.send_personal_message(event, recipient_key)

    return {
        "status": "success",
        "message": {
            "id": msg_id,
            "conversation_id": conv_id,
            "sender_id": "admin",
            "sender_display_name": sender_display_name,
            "recipient_id": target_id,
            "message_type": message_type,
            "content": content,
            "status": status,
            "created_at": now
        }
    }

@router.post("/api/admin/calls/initiate")
async def admin_initiate_call(data: dict):
    recipient_id = data.get("recipient_id") or data.get("target_user")
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

    caller_name = data.get("caller_name")
    if not caller_name or not str(caller_name).strip():
        caller_name = get_admin_alias(target_id) or get_admin_alias(target_username) or "Admin"
    else:
        caller_name = str(caller_name).strip()
        set_admin_alias_db(target_id, caller_name)
        if target_username != target_id:
            set_admin_alias_db(target_username, caller_name)

    call_id = str(uuid.uuid4())
    channel_name = f"admin_call_{target_username}_{int(time.time())}"

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
        "caller_name": caller_name,
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

@router.post("/api/admin/calls/end")
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

# --- CONVERSATIONS & MESSAGES ---

@router.get("/api/conversations/{user_id}")
def get_conversations(user_id: str):
    conn = get_db()
    cursor = conn.cursor()

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
        p_id = str(item.get("partner_id", "")).lower()
        p_user = str(item.get("partner_username", "")).lower()
        if p_id == "admin" or p_user == "admin":
            admin_alias = get_admin_alias(u_id) or get_admin_alias(u_name) or "Admin"
            item["partner_display_name"] = admin_alias
            item["partner_username"] = "admin"
            item["partner_id"] = "admin"
        item["is_online"] = ws_manager.is_online(r["partner_id"])
        conversations.append(item)
    return conversations

@router.get("/api/messages/{conversation_id}")
def get_messages(conversation_id: str, current_user: Optional[str] = None):
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("""
        SELECT * FROM messages
        WHERE conversation_id = ?
        ORDER BY created_at ASC
    """, (conversation_id,))
    rows = cursor.fetchall()
    
    if not rows:
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
        if m.get("sender_id") == "admin":
            user_key = current_user or m.get("recipient_id")
            m["sender_display_name"] = get_admin_alias(user_key)
            m["sender_username"] = "admin"
        if m.get("waveform_data"):
            try:
                m["waveform_data"] = json.loads(m["waveform_data"])
            except Exception:
                m["waveform_data"] = []
        messages.append(m)
    return messages

@router.post("/api/messages/send")
def send_message_api(req: MessageSend):
    conn = get_db()
    cursor = conn.cursor()
    
    sender_id = req.sender_id or "me"
    recipient_id = req.recipient_id
    message_type = req.message_type
    content = req.content
    now = int(time.time() * 1000)

    cursor.execute("SELECT id FROM users WHERE id = ? OR LOWER(username) = LOWER(?)", (sender_id, sender_id))
    s_row = cursor.fetchone()
    if s_row:
        sender_id = s_row["id"]

    cursor.execute("SELECT id FROM users WHERE id = ? OR LOWER(username) = LOWER(?)", (recipient_id, recipient_id))
    r_row = cursor.fetchone()
    if r_row:
        recipient_id = r_row["id"]
    
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

# --- CALL LOGGING (Consolidated) ---

@router.post("/api/calls/log")
async def log_call_endpoint(request: Request):
    """
    Consolidated endpoint to log voice/video calls, supporting both
    direct JSON dictionary and Pydantic CallLogRequest payloads.
    """
    data = await request.json()
    call_id = str(uuid.uuid4())
    caller_id = data.get("caller_id") or "caller_user"
    recipient_id = data.get("recipient_id")
    is_video = bool(data.get("is_video", False))
    now = int(time.time() * 1000)

    if not recipient_id:
        raise HTTPException(status_code=400, detail="recipient_id is required")

    conn = get_db()
    cursor = conn.cursor()

    call_type_str = "Video Call" if is_video else "Voice Call"
    content = f"Outgoing {call_type_str}"

    p1, p2 = sorted([caller_id, recipient_id])
    cursor.execute("SELECT id FROM conversations WHERE participant_one = ? AND participant_two = ?", (p1, p2))
    c_row = cursor.fetchone()
    if c_row:
        conv_id = c_row["id"]
        cursor.execute("UPDATE conversations SET last_message_preview = ?, last_message_time = ? WHERE id = ?", (f"📞 {call_type_str}", now, conv_id))
    else:
        conv_id = str(uuid.uuid4())
        cursor.execute("INSERT INTO conversations (id, participant_one, participant_two, last_message_preview, last_message_time) VALUES (?, ?, ?, ?, ?)", (conv_id, p1, p2, f"📞 {call_type_str}", now))

    cursor.execute("""
        INSERT INTO call_logs (id, caller_id, recipient_id, is_video, status, timestamp)
        VALUES (?, ?, ?, ?, 'INITIATED', ?)
    """, (call_id, caller_id, recipient_id, 1 if is_video else 0, now))

    msg_id = str(uuid.uuid4())
    cursor.execute("""
        INSERT INTO messages (id, conversation_id, sender_id, recipient_id, message_type, content, status, created_at)
        VALUES (?, ?, ?, ?, 'CALL_LOG', ?, 'DELIVERED', ?)
    """, (msg_id, conv_id, caller_id, recipient_id, content, now))

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

    return {
        "status": "success",
        "call_id": call_id,
        "call_log_id": msg_id,
        "conversation_id": conv_id,
        "call_type": call_type_str,
        "created_at": now
    }

# --- MEDIA / VOICE NOTE UPLOADS ---

@router.post("/api/media/upload")
async def upload_voice_note(file: UploadFile = File(...)):
    ext = os.path.splitext(file.filename or "")[1] or ".m4a"
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

@router.get("/api/calls/token")
def get_agora_token(channel_name: str = Query(...), uid: int = Query(0)):
    app_id = AGORA_APP_ID
    app_cert = AGORA_APP_CERTIFICATE
    
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
            logger.warning(f"Error generating Agora RTC Token: {e}")
            token = ""
    
    return {
        "app_id": app_id,
        "token": token,
        "channel_name": channel_name,
        "uid": uid
    }
