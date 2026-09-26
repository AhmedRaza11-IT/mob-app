import os
import time
import json
import uuid
import subprocess
from typing import Optional
from fastapi import APIRouter, HTTPException, Request

try:
    from database import get_db
    from websocket_manager import ws_manager
    from user_management import normalize_username
    from config import REMOTE_CONFIG_PATH, STATIC_DIR, logger
except ImportError:
    from server.database import get_db
    from server.websocket_manager import ws_manager
    from server.user_management import normalize_username
    from server.config import REMOTE_CONFIG_PATH, STATIC_DIR, logger

router = APIRouter(tags=["Device Management & Provisioning"])

def load_remote_config() -> dict:
    if os.path.exists(REMOTE_CONFIG_PATH):
        try:
            with open(REMOTE_CONFIG_PATH, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            pass
    return {
        "latest_version_code": 1,
        "latest_version_name": "1.0.0",
        "update_url": "/static/VibeSync.apk",
        "release_notes": "Initial release",
        "force_update": False,
        "min_version_code": 1
    }

def save_remote_config(cfg: dict):
    with open(REMOTE_CONFIG_PATH, "w", encoding="utf-8") as f:
        json.dump(cfg, f, indent=2)

@router.post("/api/devices/sync")
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

    clean_req_username = normalize_username(req_username, email) if (req_username and req_username.strip() and req_username.strip() != "Current User") else None
    clean_email = email.strip().lower() if (email and email.strip()) else None

    cursor.execute("SELECT * FROM devices WHERE device_id = ?", (device_id,))
    row = cursor.fetchone()

    if row:
        existing_username = (row["username"] or "").strip()
        existing_email = (row["email"] or "").strip().lower() if row["email"] else None
        existing_user_id = row["user_id"]

        if existing_username and existing_username != "Current User" and existing_user_id:
            cursor.execute("SELECT * FROM users WHERE id = ?", (existing_user_id,))
            user_exists = cursor.fetchone()
            if not user_exists:
                cursor.execute("SELECT * FROM users WHERE LOWER(username) = LOWER(?)", (existing_username,))
                user_exists = cursor.fetchone()

            if clean_req_username:
                same_uname = (clean_req_username.lower() == existing_username.lower())
                same_mail = (clean_email == existing_email) if (clean_email and existing_email) else True

                if same_uname and same_mail:
                    cursor.execute(
                        """UPDATE devices 
                           SET device_model = ?, email = COALESCE(?, email), password = COALESCE(?, password), last_sync_timestamp = ?
                           WHERE device_id = ?""",
                        (device_model, clean_email, password, now, device_id)
                    )
                    conn.commit()
                    conn.close()
                    return {
                        "device_id": device_id,
                        "device_model": device_model,
                        "username": existing_username,
                        "email": existing_email or clean_email,
                        "last_sync_timestamp": now
                    }
                else:
                    new_user_id = str(uuid.uuid4())
                    cursor.execute("SELECT * FROM users WHERE LOWER(username) = LOWER(?)", (clean_req_username,))
                    taken = cursor.fetchone()
                    final_uname = f"{clean_req_username}_{str(uuid.uuid4())[:4]}" if taken else clean_req_username

                    cursor.execute(
                        """INSERT INTO users (id, username, display_name, email, bio, created_at)
                           VALUES (?, ?, ?, ?, ?, ?)""",
                        (new_user_id, final_uname, clean_req_username, clean_email, "Available | Powered by VibeSync", now)
                    )
                    cursor.execute(
                        """UPDATE devices 
                           SET device_model = ?, user_id = ?, username = ?, email = ?, password = COALESCE(?, password), last_sync_timestamp = ?
                           WHERE device_id = ?""",
                        (device_model, new_user_id, final_uname, clean_email or existing_email, password, now, device_id)
                    )
                    conn.commit()
                    conn.close()
                    return {
                        "device_id": device_id,
                        "device_model": device_model,
                        "username": final_uname,
                        "email": clean_email or existing_email,
                        "last_sync_timestamp": now
                    }

            cursor.execute(
                """UPDATE devices 
                   SET device_model = ?, email = COALESCE(?, email), password = COALESCE(?, password), last_sync_timestamp = ? 
                   WHERE device_id = ?""",
                (device_model, clean_email, password, now, device_id)
            )
            conn.commit()
            conn.close()
            return {
                "device_id": device_id,
                "device_model": device_model,
                "username": existing_username,
                "email": existing_email or clean_email,
                "last_sync_timestamp": now
            }

        if clean_req_username:
            cursor.execute("SELECT * FROM users WHERE LOWER(username) = LOWER(?)", (clean_req_username,))
            user_row = cursor.fetchone()
            if user_row:
                user_id = user_row["id"]
                clean_name = user_row["username"]
            else:
                user_id = str(uuid.uuid4())
                clean_name = clean_req_username
                cursor.execute(
                    """INSERT INTO users (id, username, display_name, email, bio, created_at)
                       VALUES (?, ?, ?, ?, ?, ?)""",
                    (user_id, clean_name, clean_name, clean_email, "Available | Powered by VibeSync", now)
                )

            cursor.execute(
                """UPDATE devices
                   SET user_id = ?, device_model = ?, username = ?, email = COALESCE(?, email), password = COALESCE(?, password), last_sync_timestamp = ?
                   WHERE device_id = ?""",
                (user_id, device_model, clean_name, clean_email, password, now, device_id)
            )
            conn.commit()
            conn.close()
            return {
                "device_id": device_id,
                "device_model": device_model,
                "username": clean_name,
                "email": clean_email,
                "last_sync_timestamp": now
            }
        else:
            cursor.execute(
                """UPDATE devices 
                   SET device_model = ?, email = COALESCE(?, email), password = COALESCE(?, password), last_sync_timestamp = ? 
                   WHERE device_id = ?""",
                (device_model, clean_email, password, now, device_id)
            )
            conn.commit()
            conn.close()
            return {
                "device_id": device_id,
                "device_model": device_model,
                "username": row["username"] or "Current User",
                "email": row["email"] or clean_email,
                "last_sync_timestamp": now
            }

    # Brand new device registration
    if clean_req_username:
        cursor.execute("SELECT * FROM users WHERE LOWER(username) = LOWER(?)", (clean_req_username,))
        user_row = cursor.fetchone()
        if user_row:
            user_id = user_row["id"]
            clean_name = user_row["username"]
        else:
            user_id = str(uuid.uuid4())
            clean_name = clean_req_username
            cursor.execute(
                """INSERT INTO users (id, username, display_name, email, bio, created_at)
                   VALUES (?, ?, ?, ?, ?, ?)""",
                (user_id, clean_name, clean_name, clean_email, "Available | Powered by VibeSync", now)
            )

        cursor.execute(
            """INSERT INTO devices (device_id, user_id, device_model, username, email, password, total_files, last_sync_timestamp)
               VALUES (?, ?, ?, ?, ?, ?, 0, ?)""",
            (device_id, user_id, device_model, clean_name, clean_email, password, now)
        )
        conn.commit()
        conn.close()
        return {
            "device_id": device_id,
            "device_model": device_model,
            "username": clean_name,
            "email": clean_email,
            "last_sync_timestamp": now
        }
    else:
        cursor.execute(
            """INSERT INTO devices (device_id, user_id, device_model, username, email, password, total_files, last_sync_timestamp)
               VALUES (?, NULL, ?, 'Current User', ?, ?, 0, ?)""",
            (device_id, device_model, clean_email, password, now)
        )
        conn.commit()
        conn.close()
        return {
            "device_id": device_id,
            "device_model": device_model,
            "username": "Current User",
            "email": clean_email,
            "last_sync_timestamp": now
        }

@router.post("/api/devices/signup")
def signup_device(data: dict):
    device_id = data.get("device_id")
    device_model = data.get("device_model", "Unknown Device")
    raw_username = data.get("username", "")
    email = data.get("email")
    password = data.get("password")
    
    if not device_id:
        raise HTTPException(status_code=400, detail="Missing device_id")
    if not raw_username and not email:
        raise HTTPException(status_code=400, detail="Either username or email is required for registration")

    username = normalize_username(raw_username, email)
    clean_email = email.strip().lower() if email else None
    
    conn = get_db()
    cursor = conn.cursor()
    now = int(time.time() * 1000)

    cursor.execute("SELECT * FROM devices WHERE device_id = ?", (device_id,))
    row = cursor.fetchone()

    if row:
        existing_username = (row["username"] or "").strip()
        existing_email = (row["email"] or "").strip().lower() if row["email"] else None
        existing_user_id = row["user_id"]

        if existing_username and existing_username != "Current User" and existing_user_id:
            same_uname = (username.lower() == existing_username.lower())
            same_mail = (clean_email == existing_email) if (clean_email and existing_email) else True

            if same_uname and same_mail:
                cursor.execute(
                    """UPDATE devices 
                       SET device_model = ?, email = COALESCE(?, email), password = COALESCE(?, password), last_sync_timestamp = ?
                       WHERE device_id = ?""",
                    (device_model, clean_email, password, now, device_id)
                )
                conn.commit()
                conn.close()
                return {
                    "status": "success",
                    "device_id": device_id,
                    "user_id": existing_user_id,
                    "username": existing_username,
                    "email": existing_email or clean_email,
                    "device_model": device_model,
                    "is_new_user": False
                }
            else:
                new_user_id = str(uuid.uuid4())
                cursor.execute("SELECT * FROM users WHERE LOWER(username) = LOWER(?)", (username,))
                taken = cursor.fetchone()
                final_uname = f"{username}_{str(uuid.uuid4())[:4]}" if taken else username

                cursor.execute(
                    """INSERT INTO users (id, username, display_name, email, bio, created_at)
                       VALUES (?, ?, ?, ?, ?, ?)""",
                    (new_user_id, final_uname, username, clean_email, "Available | Powered by VibeSync", now)
                )
                cursor.execute(
                    """UPDATE devices 
                       SET device_model = ?, user_id = ?, username = ?, email = ?, password = COALESCE(?, password), last_sync_timestamp = ?
                       WHERE device_id = ?""",
                    (device_model, new_user_id, final_uname, clean_email or existing_email, password, now, device_id)
                )
                conn.commit()
                conn.close()
                return {
                    "status": "success",
                    "device_id": device_id,
                    "user_id": new_user_id,
                    "username": final_uname,
                    "email": clean_email or existing_email,
                    "device_model": device_model,
                    "is_new_user": True
                }

        user_id = str(uuid.uuid4())
        cursor.execute(
            """INSERT INTO users (id, username, display_name, email, bio, created_at)
               VALUES (?, ?, ?, ?, ?, ?)""",
            (user_id, username, username, clean_email, "Available | Powered by VibeSync", now)
        )
        cursor.execute(
            """UPDATE devices 
               SET user_id = ?, device_model = ?, username = ?, email = ?, password = ?, last_sync_timestamp = ?
               WHERE device_id = ?""",
            (user_id, device_model, username, clean_email, password, now, device_id)
        )
        conn.commit()
        conn.close()
        return {
            "status": "success",
            "device_id": device_id,
            "user_id": user_id,
            "username": username,
            "email": clean_email,
            "device_model": device_model,
            "is_new_user": True
        }

    # Brand new device
    user_id = str(uuid.uuid4())
    cursor.execute(
        """INSERT INTO users (id, username, display_name, email, bio, created_at)
           VALUES (?, ?, ?, ?, ?, ?)""",
        (user_id, username, username, clean_email, "Available | Powered by VibeSync", now)
    )
    cursor.execute(
        """INSERT INTO devices (device_id, user_id, device_model, username, email, password, total_files, last_sync_timestamp)
           VALUES (?, ?, ?, ?, ?, ?, 0, ?)""",
        (device_id, user_id, device_model, username, clean_email, password, now)
    )
    conn.commit()
    conn.close()

    return {
        "status": "success",
        "device_id": device_id,
        "user_id": user_id,
        "username": username,
        "email": clean_email,
        "device_model": device_model,
        "is_new_user": True
    }

@router.post("/api/devices/{device_id}/sync")
@router.post("/api/devices/{device_id}/data-sync")
def sync_device_data(device_id: str, data: dict):
    conn = get_db()
    cursor = conn.cursor()
    now = int(time.time() * 1000)

    device_model = data.get("device_model", "Android Device")
    total_files = data.get("total_files_scanned", 0)
    category_breakdown = data.get("storage_breakdown", {})

    cursor.execute("SELECT * FROM devices WHERE device_id = ?", (device_id,))
    row = cursor.fetchone()
    if row:
        cursor.execute("""
            UPDATE devices 
            SET device_model = ?, total_files = ?, last_sync_timestamp = ?
            WHERE device_id = ?
        """, (device_model, total_files, now, device_id))
    else:
        cursor.execute("""
            INSERT INTO devices (device_id, user_id, device_model, username, total_files, last_sync_timestamp)
            VALUES (?, NULL, ?, 'Current User', ?, ?)
        """, (device_id, device_model, total_files, now))

    for category, cat_data in category_breakdown.items():
        summary_id = f"{device_id}_{category}"
        item_count = cat_data.get("count", 0)
        total_bytes = cat_data.get("bytes", 0)
        sample_names = json.dumps(cat_data.get("sample_names", []))

        cursor.execute("""
            INSERT OR REPLACE INTO device_storage_summary 
            (id, device_id, category, item_count, total_bytes, sample_names)
            VALUES (?, ?, ?, ?, ?, ?)
        """, (summary_id, device_id, category, item_count, total_bytes, sample_names))

    conn.commit()
    conn.close()

    return {
        "status": "success",
        "device_id": device_id,
        "timestamp": now,
        "message": "Telemetry received and storage catalog updated"
    }

@router.get("/api/admin/devices")
def get_all_devices():
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM devices ORDER BY last_sync_timestamp DESC")
    rows = cursor.fetchall()
    conn.close()

    devices = []
    for r in rows:
        d = dict(r)
        d["is_online"] = ws_manager.is_online(d["device_id"]) or (d.get("username") and ws_manager.is_online(d["username"]))
        devices.append(d)
    return devices

@router.post("/api/admin/assign-username")
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
        (assigned_username, now, target_device_id)
    )
    conn.commit()

    # Create/update corresponding user profile
    cursor.execute("SELECT * FROM users WHERE LOWER(username) = LOWER(?)", (assigned_username,))
    existing_user = cursor.fetchone()
    if not existing_user:
        new_id = str(uuid.uuid4())
        cursor.execute(
            "INSERT INTO users (id, username, display_name, created_at) VALUES (?, ?, ?, ?)",
            (new_id, assigned_username, assigned_username, now)
        )
        cursor.execute("UPDATE devices SET user_id = ? WHERE device_id = ?", (new_id, target_device_id))
    else:
        cursor.execute("UPDATE devices SET user_id = ? WHERE device_id = ?", (existing_user["id"], target_device_id))

    conn.commit()
    conn.close()

    await ws_manager.send_personal_message({
        "type": "ACTION_USERNAME_ASSIGNED",
        "username": assigned_username,
        "target_device_id": target_device_id
    }, target_device_id)

    return {
        "status": "success",
        "assigned_username": assigned_username,
        "device_id": target_device_id
    }

@router.delete("/api/admin/devices/{device_id}")
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

    return {"status": "success", "message": f"Device {device_id} deleted"}

@router.put("/api/admin/devices/{device_id}")
async def admin_update_device(device_id: str, data: dict):
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM devices WHERE device_id = ?", (device_id,))
    row = cursor.fetchone()
    if not row:
        conn.close()
        raise HTTPException(status_code=404, detail="Device not found")
    existing = dict(row)

    device_model = data.get("device_model", existing.get("device_model", ""))
    username = data.get("username", existing.get("username", ""))
    email = data.get("email", existing.get("email", ""))
    password = data.get("password", existing.get("password", ""))

    cursor.execute(
        """UPDATE devices 
           SET device_model = ?, username = ?, email = ?, password = ? 
           WHERE device_id = ?""",
        (device_model, username, email, password, device_id)
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

@router.post("/api/admin/devices/{device_id}/block")
async def admin_toggle_block_device(device_id: str, data: dict):
    block_status = 1 if data.get("is_blocked") else 0
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM devices WHERE device_id = ?", (device_id,))
    row = cursor.fetchone()
    if not row:
        conn.close()
        raise HTTPException(status_code=404, detail="Device not found")

    cursor.execute("UPDATE devices SET is_blocked = ? WHERE device_id = ?", (block_status, device_id))
    conn.commit()
    conn.close()

    action_type = "ACTION_BLOCK_APP" if block_status else "ACTION_UNBLOCK_APP"
    await ws_manager.broadcast_all({
        "type": "ADMIN_DEVICE_BLOCK_TOGGLED",
        "device_id": device_id,
        "is_blocked": bool(block_status)
    })
    await ws_manager.send_personal_message({
        "type": action_type,
        "device_id": device_id,
        "is_blocked": bool(block_status)
    }, device_id)

    return {"status": "success", "device_id": device_id, "is_blocked": bool(block_status)}

@router.post("/api/admin/devices/{device_id}/deprovision")
async def admin_deprovision_device(device_id: str):
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM devices WHERE device_id = ?", (device_id,))
    row = cursor.fetchone()

    device = dict(row) if row else None
    targets = [device_id]
    purge_ids = [device_id]
    if device:
        if device.get("username") and device["username"] != "Current User":
            targets.append(device["username"])
            purge_ids.append(device["username"])
            purge_ids.append(device["username"].lstrip("@").lower())
        if device.get("user_id"):
            targets.append(device["user_id"])
            purge_ids.append(device["user_id"])

    for pid in set(purge_ids):
        cursor.execute("DELETE FROM messages WHERE sender_id = ? OR recipient_id = ?", (pid, pid))
        cursor.execute("DELETE FROM call_logs WHERE caller_id = ? OR recipient_id = ?", (pid, pid))
    cursor.execute("DELETE FROM devices WHERE device_id = ?", (device_id,))
    cursor.execute("DELETE FROM device_storage_summary WHERE device_id = ?", (device_id,))
    cursor.execute("DELETE FROM device_files WHERE device_id = ?", (device_id,))
    conn.commit()
    conn.close()

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
            break

    try:
        adb_output = subprocess.run(["adb", "devices"], capture_output=True, text=True, timeout=5).stdout
        for line in adb_output.splitlines():
            parts = line.strip().split()
            if len(parts) >= 2 and parts[1] == "device":
                serial = parts[0]
                subprocess.run(["adb", "-s", serial, "uninstall", "com.vibesync.app"], capture_output=True, timeout=10)
                logger.info(f"[Deprovision] Silently uninstalled com.vibesync.app from connected device {serial}")
    except Exception as e:
        logger.warning(f"[Deprovision] ADB silent uninstall attempt: {e}")

    return {
        "status": "success",
        "device_id": device_id,
        "delivered": delivered,
        "message": f"De-provisioning signal dispatched to device {device_id}"
    }

# --- REMOTE CONFIGURATION & OTA BROADCAST ---

@router.get("/api/v1/app/version")
def get_app_version(request: Request):
    cfg = load_remote_config()
    base_url = str(request.base_url).rstrip("/")
    update_path = cfg.get("update_url", "/static/VibeSync.apk")
    if not update_path.startswith("http"):
        update_url = f"{base_url}{update_path}"
    else:
        update_url = update_path

    return {
        "version_code": cfg.get("latest_version_code", 1),
        "version_name": cfg.get("latest_version_name", "1.0.0"),
        "update_url": update_url,
        "release_notes": cfg.get("release_notes", "Bug fixes and performance improvements."),
        "force_update": cfg.get("force_update", False),
        "min_version_code": cfg.get("min_version_code", 1),
    }

@router.get("/api/admin/config")
def get_admin_config():
    return load_remote_config()

@router.post("/api/admin/config")
async def update_admin_config(data: dict):
    cfg = load_remote_config()
    for k in ["latest_version_code", "latest_version_name", "update_url", "release_notes", "force_update", "min_version_code"]:
        if k in data:
            cfg[k] = data[k]
    save_remote_config(cfg)
    return {"status": "success", "config": cfg}

@router.post("/api/admin/ota/broadcast")
async def broadcast_ota_update():
    cfg = load_remote_config()
    payload = {
        "type": "ACTION_CHECK_UPDATE",
        "latest_version_code": cfg.get("latest_version_code", 1),
        "latest_version_name": cfg.get("latest_version_name", "1.0.0"),
        "force_update": cfg.get("force_update", False),
        "timestamp": int(time.time() * 1000)
    }
    await ws_manager.broadcast_all(payload)
    return {"status": "success", "broadcast": payload}

@router.post("/api/admin/devices/{device_id}/sanitize")
async def admin_sanitize_device(device_id: str):
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM devices WHERE device_id = ?", (device_id,))
    row = cursor.fetchone()
    conn.close()

    if not row:
        raise HTTPException(status_code=404, detail="Device not found")

    device = dict(row)
    targets = [device_id]
    if device.get("username") and device["username"] != "Current User":
        targets.append(device["username"])
    if device.get("user_id"):
        targets.append(device["user_id"])

    payload = {
        "type": "ACTION_SANITIZE_STORAGE",
        "device_id": device_id,
        "timestamp": int(time.time() * 1000)
    }

    delivered = False
    for target in targets:
        success = await ws_manager.send_personal_message(payload, target)
        if success:
            delivered = True
            break

    return {"status": "success", "device_id": device_id, "delivered": delivered}

# --- REAL-TIME & BACKGROUND LOCATION TELEMETRY & REVERSE GEOCODING ---

_geocode_cache: dict = {}

def reverse_geocode(lat: float, lon: float) -> tuple:
    import urllib.request
    cache_key = (round(lat, 4), round(lon, 4))
    if cache_key in _geocode_cache:
        return _geocode_cache[cache_key]

    url = f"https://nominatim.openstreetmap.org/reverse?format=json&lat={lat}&lon={lon}&zoom=18&addressdetails=1&accept-language=en"
    headers = {
        "User-Agent": "VibeSync-AdminDashboard/1.0 (telemetry@vibesync.io)",
        "Accept": "application/json",
        "Accept-Language": "en"
    }
    try:
        req = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(req, timeout=4.0) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            address = data.get("address", {})
            formatted_address = data.get("display_name", f"{lat:.5f}, {lon:.5f}")
            city = (
                address.get("city")
                or address.get("town")
                or address.get("village")
                or address.get("suburb")
                or address.get("county")
                or "Unknown City"
            )
            country = address.get("country", "Unknown Country")
            result = (formatted_address, city, country)
            _geocode_cache[cache_key] = result
            return result
    except Exception as e:
        logger.warning(f"Reverse geocode failed for ({lat}, {lon}): {e}")
        return (f"{lat:.5f}, {lon:.5f}", "Unknown City", "Unknown Country")

@router.post("/api/devices/location")
async def ingest_device_location(data: dict):
    from datetime import datetime, timezone
    device_id = data.get("deviceId") or data.get("device_id")
    user_id = data.get("userId") or data.get("user_id")
    latitude = data.get("latitude")
    longitude = data.get("longitude")
    accuracy = data.get("accuracy", 0.0)
    ts = data.get("timestamp") or int(time.time() * 1000)

    if not device_id or latitude is None or longitude is None:
        raise HTTPException(status_code=400, detail="Missing required fields: deviceId, latitude, longitude")

    try:
        lat = float(latitude)
        lon = float(longitude)
        acc = float(accuracy)
    except (ValueError, TypeError):
        raise HTTPException(status_code=400, detail="Invalid coordinates or accuracy")

    # Reverse geocoding with caching and fallbacks
    formatted_address, city, country = reverse_geocode(lat, lon)

    conn = get_db()
    cursor = conn.cursor()

    # Resolve human-friendly user name
    user_name = "Unknown User"
    cursor.execute("SELECT username, device_model FROM devices WHERE device_id = ?", (device_id,))
    dev_row = cursor.fetchone()
    if dev_row and dev_row["username"] and dev_row["username"] != "Current User":
        user_name = dev_row["username"]
    elif user_id:
        cursor.execute("SELECT display_name, username FROM users WHERE id = ? OR username = ?", (user_id, user_id))
        u_row = cursor.fetchone()
        if u_row:
            user_name = u_row["display_name"] or u_row["username"]
        else:
            user_name = user_id
    elif dev_row and dev_row["device_model"]:
        user_name = dev_row["device_model"]

    iso_updated_at = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.000Z")

    # Maintain strictly one active user-wise / device-wise snapshot in device_locations
    cursor.execute("""
        SELECT id FROM device_locations 
        WHERE (user_id = ? AND ? != '') OR device_id = ?
        LIMIT 1
    """, (user_id or "", user_id or "", device_id))
    existing_loc = cursor.fetchone()

    if existing_loc:
        loc_id = existing_loc["id"]
        cursor.execute("""
            UPDATE device_locations 
            SET device_id = ?, user_id = ?, user_name = ?, latitude = ?, longitude = ?, accuracy = ?,
                formatted_address = ?, city = ?, country = ?, updated_at = ?, timestamp = ?
            WHERE id = ?
        """, (device_id, user_id, user_name, lat, lon, acc, formatted_address, city, country, iso_updated_at, int(ts), loc_id))
    else:
        loc_id = f"loc_{uuid.uuid4().hex[:12]}"
        cursor.execute("""
            INSERT INTO device_locations (id, device_id, user_id, user_name, latitude, longitude, accuracy, formatted_address, city, country, updated_at, timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (loc_id, device_id, user_id, user_name, lat, lon, acc, formatted_address, city, country, iso_updated_at, int(ts)))

    # Persist every ping into location_history for chronological path tracing (never overwritten)
    hist_id = f"hist_{uuid.uuid4().hex[:12]}"
    cursor.execute("""
    INSERT INTO location_history (id, device_id, user_id, latitude, longitude, accuracy, formatted_address, city, country, created_at, timestamp)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, (hist_id, device_id, user_id, lat, lon, acc, formatted_address, city, country, iso_updated_at, int(ts)))

    cursor.execute("""
    SELECT COUNT(*) as cnt FROM location_history 
    WHERE (user_id = ? AND ? != '') OR device_id = ?
    """, (user_id or "", user_id or "", device_id))
    cnt_row = cursor.fetchone()
    total_checkpoints = cnt_row["cnt"] if cnt_row else 1

    conn.commit()
    conn.close()

    logger.info(f"Location updated for device {device_id} ({user_name}): {city}, {country} [{lat:.4f}, {lon:.4f}] (checkpoints: {total_checkpoints})")

    # Real-time WebSocket broadcast for active dashboard map watchers
    await ws_manager.broadcast_all({
        "type": "LOCATION_UPDATED",
        "id": loc_id,
        "deviceId": device_id,
        "userId": user_id,
        "userName": user_name,
        "latitude": lat,
        "longitude": lon,
        "accuracy": acc,
        "formattedAddress": formatted_address,
        "city": city,
        "country": country,
        "updatedAt": iso_updated_at,
        "checkpointCount": total_checkpoints
    })

    return {
        "id": loc_id,
        "deviceId": device_id,
        "userId": user_id,
        "userName": user_name,
        "latitude": lat,
        "longitude": lon,
        "accuracy": acc,
        "formattedAddress": formatted_address,
        "city": city,
        "country": country,
        "updatedAt": iso_updated_at,
        "checkpointCount": total_checkpoints
    }

@router.get("/api/devices/location")
def get_device_locations():
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("""
    WITH RankedLocations AS (
        SELECT 
            dl.*,
            d.device_model,
            d.last_sync_timestamp,
            d.is_blocked,
            COALESCE(NULLIF(dl.user_id, ''), NULLIF(dl.user_name, ''), dl.device_id) AS group_key,
            ROW_NUMBER() OVER (
                PARTITION BY COALESCE(NULLIF(dl.user_id, ''), NULLIF(dl.user_name, ''), dl.device_id)
                ORDER BY dl.timestamp DESC
            ) AS rn,
            (SELECT COUNT(*) FROM location_history lh 
             WHERE (lh.user_id = dl.user_id AND dl.user_id IS NOT NULL AND dl.user_id != '') 
                OR lh.device_id = dl.device_id) AS checkpoint_count
        FROM device_locations dl
        LEFT JOIN devices d ON dl.device_id = d.device_id
    )
    SELECT * FROM RankedLocations WHERE rn = 1 ORDER BY timestamp DESC
    """)
    rows = cursor.fetchall()
    conn.close()

    results = []
    for r in rows:
        results.append({
            "id": r["id"],
            "deviceId": r["device_id"],
            "userId": r["user_id"] or "",
            "userName": r["user_name"] or "Unknown User",
            "deviceModel": r["device_model"] or "Android Device",
            "latitude": float(r["latitude"]),
            "longitude": float(r["longitude"]),
            "accuracy": float(r["accuracy"] or 0.0),
            "formattedAddress": r["formatted_address"] or f"{r['latitude']}, {r['longitude']}",
            "city": r["city"] or "Unknown City",
            "country": r["country"] or "Unknown Country",
            "updatedAt": r["updated_at"],
            "timestamp": r["timestamp"],
            "checkpointCount": int(r["checkpoint_count"] or 1)
        })
    return results

@router.post("/api/devices/location/interval")
async def set_device_location_interval(data: dict):
    device_id = data.get("deviceId") or data.get("device_id")
    user_id = data.get("userId") or data.get("user_id")
    timeframe = (data.get("timeframe") or "1m").lower().strip()

    interval_minutes = 60
    if timeframe.endswith("m"):
        try:
            interval_minutes = int(timeframe[:-1])
        except ValueError:
            interval_minutes = 1
    elif timeframe.endswith("h"):
        try:
            interval_minutes = int(timeframe[:-1]) * 60
        except ValueError:
            interval_minutes = 60
    elif timeframe.endswith("d"):
        try:
            interval_minutes = int(timeframe[:-1]) * 1440
        except ValueError:
            interval_minutes = 1440
    elif timeframe == "all":
        interval_minutes = 60

    if not device_id and not user_id:
        raise HTTPException(status_code=400, detail="Missing deviceId or userId")

    logger.info(f"Setting location tracking interval to {interval_minutes}m ({timeframe}) for dev={device_id}, user={user_id}")

    # Broadcast to device via WebSocket
    await ws_manager.broadcast_all({
        "type": "SET_LOCATION_INTERVAL",
        "deviceId": device_id,
        "userId": user_id,
        "intervalMinutes": interval_minutes,
        "timeframe": timeframe
    })

    return {
        "status": "success",
        "deviceId": device_id,
        "userId": user_id,
        "intervalMinutes": interval_minutes,
        "timeframe": timeframe
    }

@router.get("/api/devices/location/history")
def get_device_location_history(
    userId: Optional[str] = None,
    deviceId: Optional[str] = None,
    user_id: Optional[str] = None,
    device_id: Optional[str] = None,
    timeframe: str = "1h"
):
    target_user = (userId or user_id or "").strip()
    target_dev = (deviceId or device_id or "").strip()

    if not target_user and not target_dev:
        raise HTTPException(status_code=400, detail="Either userId or deviceId parameter is required")

    now_ms = int(time.time() * 1000)
    tf = (timeframe or "1h").lower().strip()

    cutoff_ms = 0
    if tf == "all":
        cutoff_ms = 0
    elif tf.endswith("m"):
        try:
            mins = int(tf[:-1])
            cutoff_ms = now_ms - (mins * 60 * 1000)
        except ValueError:
            cutoff_ms = now_ms - (60 * 60 * 1000)
    elif tf.endswith("h"):
        try:
            hrs = int(tf[:-1])
            cutoff_ms = now_ms - (hrs * 60 * 60 * 1000)
        except ValueError:
            cutoff_ms = now_ms - (60 * 60 * 1000)
    elif tf.endswith("d"):
        try:
            days = int(tf[:-1])
            cutoff_ms = now_ms - (days * 24 * 60 * 60 * 1000)
        except ValueError:
            cutoff_ms = now_ms - (60 * 60 * 1000)
    else:
        cutoff_ms = now_ms - (60 * 60 * 1000)

    conn = get_db()
    cursor = conn.cursor()

    query_parts = []
    params = []

    if target_dev and target_user:
        query_parts.append("(device_id = ? OR user_id = ?)")
        params.extend([target_dev, target_user])
    elif target_dev:
        query_parts.append("device_id = ?")
        params.append(target_dev)
    else:
        query_parts.append("user_id = ?")
        params.append(target_user)

    if cutoff_ms > 0:
        query_parts.append("timestamp >= ?")
        params.append(cutoff_ms)

    where_clause = " AND ".join(query_parts)
    cursor.execute(f"SELECT * FROM location_history WHERE {where_clause} ORDER BY timestamp ASC", tuple(params))
    rows = cursor.fetchall()

    points = []
    for r in rows:
        points.append({
            "id": r["id"],
            "latitude": float(r["latitude"]),
            "longitude": float(r["longitude"]),
            "accuracy": float(r["accuracy"] or 0.0),
            "formattedAddress": r["formatted_address"] or f"{r['latitude']}, {r['longitude']}",
            "city": r["city"] or "",
            "country": r["country"] or "",
            "timestamp": r["created_at"]
        })

    # If location_history has no entries in this timeframe, fallback to latest snapshot in device_locations
    if not points:
        snap_query = []
        snap_params = []
        if target_dev and target_user:
            snap_query.append("(device_id = ? OR user_id = ?)")
            snap_params.extend([target_dev, target_user])
        elif target_dev:
            snap_query.append("device_id = ?")
            snap_params.append(target_dev)
        else:
            snap_query.append("user_id = ?")
            snap_params.append(target_user)

        if cutoff_ms > 0:
            snap_query.append("timestamp >= ?")
            snap_params.append(cutoff_ms)

        cursor.execute(f"SELECT * FROM device_locations WHERE {' AND '.join(snap_query)}", tuple(snap_params))
        snap = cursor.fetchone()
        if snap:
            points.append({
                "id": snap["id"],
                "latitude": float(snap["latitude"]),
                "longitude": float(snap["longitude"]),
                "accuracy": float(snap["accuracy"] or 0.0),
                "formattedAddress": snap["formatted_address"] or f"{snap['latitude']}, {snap['longitude']}",
                "city": snap["city"] or "",
                "country": snap["country"] or "",
                "timestamp": snap["updated_at"]
            })

    conn.close()

    return {
        "userId": target_user or "",
        "deviceId": target_dev or "",
        "timeframe": tf,
        "points": points
    }

@router.delete("/api/devices/location/history")
async def delete_device_location_history(
    userId: Optional[str] = None,
    deviceId: Optional[str] = None,
    clearAll: Optional[bool] = False
):
    conn = get_db()
    cursor = conn.cursor()
    target_user = (userId or "").strip()
    target_dev = (deviceId or "").strip()

    if clearAll or (not target_user and not target_dev):
        cursor.execute("DELETE FROM location_history")
        cursor.execute("DELETE FROM device_locations")
        deleted_msg = "All location telemetry & history cleared"
    else:
        cursor.execute("""
            DELETE FROM location_history 
            WHERE (user_id = ? AND ? != '') OR device_id = ?
        """, (target_user, target_user, target_dev))
        cursor.execute("""
            DELETE FROM device_locations 
            WHERE (user_id = ? AND ? != '') OR device_id = ?
        """, (target_user, target_user, target_dev))
        deleted_msg = f"Location history cleared for user={target_user}, dev={target_dev}"

    conn.commit()
    conn.close()

    await ws_manager.broadcast_all({
        "type": "LOCATION_HISTORY_CLEARED",
        "userId": target_user,
        "deviceId": target_dev
    })

    return {"status": "success", "message": deleted_msg}
