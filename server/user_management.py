import os
import time
import uuid
from typing import List, Optional
from urllib.parse import unquote
from fastapi import APIRouter, HTTPException, Query, UploadFile, File, Request

try:
    from database import get_db
    from models import UserRegister, UserProfile, ContactAdd
    from websocket_manager import ws_manager
    from config import AVATARS_DIR, logger
except ImportError:
    from server.database import get_db
    from server.models import UserRegister, UserProfile, ContactAdd
    from server.websocket_manager import ws_manager
    from server.config import AVATARS_DIR, logger

router = APIRouter(tags=["User Management & Profiles"])

def normalize_username(raw_username: str, email: Optional[str] = None) -> str:
    """
    Sanitizes and normalizes usernames to clean identifiers.
    Strips email domains, @ prefixes, and invalid characters while preserving spacing and casing.
    """
    val = (raw_username or "").strip()
    if not val and email:
        val = email.strip()
    val = val.lstrip("@")
    if "@" in val:
        val = val.split("@")[0]
    for suffix in [".gmail.com", "gmail.com", ".yahoo.com", "yahoo.com", ".hotmail.com", "hotmail.com", ".outlook.com", "outlook.com", ".icloud.com", "icloud.com"]:
        if val.lower().endswith(suffix) and len(val) > len(suffix):
            val = val[:-len(suffix)]
            break
    cleaned = "".join(c for c in val if c.isalnum() or c in "._- ").strip("._- ")
    return cleaned or "user"

def resolve_user(cursor, identifier: str):
    """
    Finds a user by ID or username, handling URL encoding (%20 and +).
    """
    raw_ident = identifier.strip()
    unquoted = unquote(raw_ident).strip()
    plus_replaced = unquoted.replace("+", " ").strip()
    
    cursor.execute(
        """SELECT * FROM users 
           WHERE id = ? OR LOWER(username) = LOWER(?)
              OR id = ? OR LOWER(username) = LOWER(?)
              OR id = ? OR LOWER(username) = LOWER(?)""",
        (raw_ident, raw_ident, unquoted, unquoted, plus_replaced, plus_replaced)
    )
    row = cursor.fetchone()
    return dict(row) if row else None

# --- AUTH & REGISTRATION ---

@router.post("/api/auth/register", response_model=UserProfile)
def register_user(req: UserRegister):
    conn = get_db()
    cursor = conn.cursor()
    
    norm_username = normalize_username(req.username)
    cursor.execute("SELECT * FROM users WHERE LOWER(username) = LOWER(?)", (norm_username,))
    existing = cursor.fetchone()
    if existing:
        return dict(existing)
        
    user_id = str(uuid.uuid4())
    now = int(time.time() * 1000)
    display_name = req.display_name.strip() if req.display_name else norm_username
    cursor.execute(
        "INSERT INTO users (id, username, display_name, avatar_url, bio, created_at) VALUES (?, ?, ?, ?, ?, ?)",
        (user_id, norm_username, display_name, req.avatar_url, req.bio, now)
    )
    conn.commit()
    
    cursor.execute("SELECT * FROM users WHERE id = ?", (user_id,))
    user = dict(cursor.fetchone())
    conn.close()
    return user

@router.get("/api/users/me/{user_id}", response_model=UserProfile)
def get_current_user_profile(user_id: str):
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM users WHERE id = ?", (user_id,))
    user = cursor.fetchone()
    conn.close()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    return dict(user)

# --- STRICT GLOBAL DISCOVERY SEARCH (NO READ_CONTACTS) ---

@router.get("/api/users/search")
def search_users(q: str = Query(..., min_length=1), current_user_id: Optional[str] = None):
    conn = get_db()
    cursor = conn.cursor()
    query_param = f"%{q.strip()}%"
    
    if current_user_id:
        cursor.execute("""
            SELECT u.*, 
                   CASE WHEN c.contact_user_id IS NOT NULL THEN 1 ELSE 0 END AS is_in_roster
            FROM users u
            LEFT JOIN in_app_contacts c ON c.owner_id = ? AND c.contact_user_id = u.id
            WHERE (u.username LIKE ? OR u.display_name LIKE ?) AND u.id != ? AND LOWER(u.username) != 'admin' AND LOWER(u.id) != 'admin'
            LIMIT 30
        """, (current_user_id, query_param, query_param, current_user_id))
    else:
        cursor.execute("""
            SELECT *, 0 AS is_in_roster FROM users 
            WHERE (username LIKE ? OR display_name LIKE ?) AND LOWER(username) != 'admin' AND LOWER(id) != 'admin'
            LIMIT 30
        """, (query_param, query_param))
        
    rows = cursor.fetchall()
    conn.close()
    return [dict(r) for r in rows]

@router.get("/api/users/all", response_model=List[UserProfile])
def get_all_users():
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM users WHERE LOWER(username) != 'admin' AND LOWER(id) != 'admin' ORDER BY created_at DESC")
    rows = cursor.fetchall()
    conn.close()
    return [dict(r) for r in rows]

# --- USER PROFILE & AVATAR MANAGEMENT ---

@router.get("/api/users/{user_id_or_username}/profile")
def get_user_profile(user_id_or_username: str):
    conn = get_db()
    cursor = conn.cursor()
    user = resolve_user(cursor, user_id_or_username)
    conn.close()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    return user

@router.post("/api/users/{user_id_or_username}/avatar")
async def upload_user_avatar(user_id_or_username: str, file: UploadFile = File(...)):
    conn = get_db()
    cursor = conn.cursor()
    user = resolve_user(cursor, user_id_or_username)
    if not user:
        conn.close()
        raise HTTPException(status_code=404, detail="User not found")

    user_id = user["id"]
    username = user["username"]

    ext = os.path.splitext(file.filename or "")[1].lower()
    if not ext or ext not in [".jpg", ".jpeg", ".png", ".webp", ".gif"]:
        ext = ".jpg"
    unique_filename = f"avatar_{user_id}_{int(time.time())}{ext}"
    dest_path = os.path.join(AVATARS_DIR, unique_filename)

    content = await file.read()
    with open(dest_path, "wb") as f:
        f.write(content)

    avatar_url = f"/uploads/avatars/{unique_filename}"

    cursor.execute("UPDATE users SET avatar_url = ? WHERE id = ?", (avatar_url, user_id))
    conn.commit()

    cursor.execute("SELECT * FROM users WHERE id = ?", (user_id,))
    updated_user = dict(cursor.fetchone())
    conn.close()

    await ws_manager.broadcast_all({
        "type": "USER_AVATAR_UPDATED",
        "user_id": user_id,
        "username": username,
        "avatar_url": avatar_url
    })

    return {
        "status": "success",
        "message": "Avatar updated successfully",
        "avatar_url": avatar_url,
        "user": updated_user
    }

@router.delete("/api/users/{user_id_or_username}/avatar")
async def delete_user_avatar(user_id_or_username: str):
    conn = get_db()
    cursor = conn.cursor()
    user = resolve_user(cursor, user_id_or_username)
    if not user:
        conn.close()
        raise HTTPException(status_code=404, detail="User not found")

    user_id = user["id"]
    username = user["username"]
    old_avatar = user.get("avatar_url")

    cursor.execute("UPDATE users SET avatar_url = NULL WHERE id = ?", (user_id,))
    conn.commit()

    if old_avatar and old_avatar.startswith("/uploads/avatars/"):
        fname = old_avatar.replace("/uploads/avatars/", "")
        local_path = os.path.join(AVATARS_DIR, fname)
        if os.path.exists(local_path):
            try:
                os.remove(local_path)
            except Exception:
                pass

    cursor.execute("SELECT * FROM users WHERE id = ?", (user_id,))
    updated_user = dict(cursor.fetchone())
    conn.close()

    await ws_manager.broadcast_all({
        "type": "USER_AVATAR_DELETED",
        "user_id": user_id,
        "username": username
    })

    return {
        "status": "success",
        "message": "Avatar deleted successfully",
        "avatar_url": None,
        "user": updated_user
    }

# --- FRIENDS / ROSTER MANAGEMENT ---

@router.get("/api/users/{user_id_or_username}/friends")
def get_user_friends(user_id_or_username: str):
    conn = get_db()
    cursor = conn.cursor()
    user = resolve_user(cursor, user_id_or_username)
    if not user:
        conn.close()
        raise HTTPException(status_code=404, detail=f"User '{user_id_or_username}' not found")

    user_id = user["id"]
    username = user["username"]

    cursor.execute("""
        SELECT DISTINCT u.id, u.username, u.display_name, u.avatar_url, u.bio, u.created_at, u.is_banned
        FROM users u
        JOIN in_app_contacts c ON (c.owner_id IN (?, ?) AND c.contact_user_id IN (u.id, u.username))
                               OR (c.contact_user_id IN (?, ?) AND c.owner_id IN (u.id, u.username))
        WHERE u.id != ? AND LOWER(u.username) != LOWER(?) AND LOWER(u.username) != 'admin' AND LOWER(u.id) != 'admin'
        ORDER BY u.display_name ASC
    """, (user_id, username, user_id, username, user_id, username))
    rows = cursor.fetchall()
    conn.close()
    return [dict(r) for r in rows]

@router.post("/api/admin/users/{user_id_or_username}/friends")
async def admin_assign_friend(user_id_or_username: str, data: dict):
    friend_ident = data.get("friend_id") or data.get("friend_username") or data.get("username", "")
    if not friend_ident:
        raise HTTPException(status_code=400, detail="friend_id or friend_username required")

    conn = get_db()
    cursor = conn.cursor()

    user = resolve_user(cursor, user_id_or_username)
    if not user:
        conn.close()
        raise HTTPException(status_code=404, detail=f"Target user '{user_id_or_username}' not found")

    friend = resolve_user(cursor, friend_ident)
    if not friend:
        conn.close()
        raise HTTPException(status_code=404, detail=f"Friend user '{friend_ident}' not found")

    if user["id"] == friend["id"]:
        conn.close()
        raise HTTPException(status_code=400, detail="Cannot add oneself as a friend")

    now = int(time.time() * 1000)

    cursor.execute(
        "INSERT OR IGNORE INTO in_app_contacts (owner_id, contact_user_id, saved_name, created_at) VALUES (?, ?, ?, ?)",
        (user["id"], friend["id"], friend["display_name"], now)
    )
    cursor.execute(
        "INSERT OR IGNORE INTO in_app_contacts (owner_id, contact_user_id, saved_name, created_at) VALUES (?, ?, ?, ?)",
        (friend["id"], user["id"], user["display_name"], now)
    )
    conn.commit()
    conn.close()

    ws_event = {
        "type": "FRIENDS_UPDATED",
        "action": "ASSIGN",
        "user_id": user["id"],
        "username": user["username"],
        "friend_id": friend["id"],
        "friend_username": friend["username"],
        "timestamp": now
    }
    await ws_manager.send_personal_message(ws_event, user["username"])
    await ws_manager.send_personal_message(ws_event, user["id"])
    await ws_manager.send_personal_message(ws_event, friend["username"])
    await ws_manager.send_personal_message(ws_event, friend["id"])

    return {
        "status": "success",
        "message": f"Successfully assigned @{friend['username']} as friend to @{user['username']}",
        "friend": friend
    }

@router.delete("/api/admin/users/{user_id_or_username}/friends/{friend_id_or_username}")
async def admin_delete_friend(user_id_or_username: str, friend_id_or_username: str):
    conn = get_db()
    cursor = conn.cursor()

    user = resolve_user(cursor, user_id_or_username)
    if not user:
        conn.close()
        raise HTTPException(status_code=404, detail=f"Target user '{user_id_or_username}' not found")

    friend = resolve_user(cursor, friend_id_or_username)
    if not friend:
        conn.close()
        raise HTTPException(status_code=404, detail=f"Friend user '{friend_id_or_username}' not found")

    cursor.execute("""
        DELETE FROM in_app_contacts
        WHERE (owner_id IN (?, ?) AND contact_user_id IN (?, ?))
           OR (owner_id IN (?, ?) AND contact_user_id IN (?, ?))
    """, (user["id"], user["username"], friend["id"], friend["username"],
          friend["id"], friend["username"], user["id"], user["username"]))
    conn.commit()
    conn.close()

    now = int(time.time() * 1000)
    ws_event = {
        "type": "FRIENDS_UPDATED",
        "action": "DELETE",
        "user_id": user["id"],
        "username": user["username"],
        "friend_id": friend["id"],
        "friend_username": friend["username"],
        "timestamp": now
    }
    await ws_manager.send_personal_message(ws_event, user["username"])
    await ws_manager.send_personal_message(ws_event, user["id"])
    await ws_manager.send_personal_message(ws_event, friend["username"])
    await ws_manager.send_personal_message(ws_event, friend["id"])

    return {
        "status": "success",
        "message": f"Successfully removed @{friend['username']} from @{user['username']}'s friends"
    }

@router.get("/api/admin/friends-overview")
def get_friends_overview():
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM users WHERE LOWER(username) != 'admin' AND LOWER(id) != 'admin' ORDER BY display_name ASC")
    users = [dict(r) for r in cursor.fetchall()]

    result = []
    for u in users:
        u_id = u["id"]
        u_name = u["username"]
        cursor.execute("""
            SELECT DISTINCT u2.id, u2.username, u2.display_name, u2.avatar_url, u2.bio
            FROM users u2
            JOIN in_app_contacts c ON (c.owner_id IN (?, ?) AND c.contact_user_id IN (u2.id, u2.username))
                                   OR (c.contact_user_id IN (?, ?) AND c.owner_id IN (u2.id, u2.username))
            WHERE u2.id != ? AND LOWER(u2.username) != LOWER(?) AND LOWER(u2.username) != 'admin'
            ORDER BY u2.display_name ASC
        """, (u_id, u_name, u_id, u_name, u_id, u_name))
        friends = [dict(f) for f in cursor.fetchall()]
        result.append({
            **u,
            "friends_count": len(friends),
            "friends": friends
        })

    conn.close()
    return result

# --- ADMIN USER CRUD ---

@router.post("/api/admin/users")
async def admin_create_user(data: dict):
    raw_username = data.get("username", "").strip()
    username = normalize_username(raw_username)
    display_name = data.get("display_name", "").strip() or username
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

@router.put("/api/users/{user_id}")
@router.put("/api/admin/users/{user_id}")
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
        logger.error(f"admin_update_user error: {e}")
        raise HTTPException(status_code=500, detail=f"admin_update_user error: {e}")

@router.delete("/api/admin/users/{user_id}")
@router.delete("/api/users/{user_id}")
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

# --- IN-APP CONTACT ROSTER ---

@router.post("/api/contacts")
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

@router.get("/api/contacts/{owner_id}")
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
