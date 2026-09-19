import json
import logging
from typing import Dict
from fastapi import WebSocket

logger = logging.getLogger("uvicorn")

def clean_user_id(uid: str) -> str:
    if not uid:
        return ""
    return uid.strip().lstrip("@").lower()

def get_id_variants(uid: str) -> list:
    if not uid:
        return []
    raw = uid.strip().lstrip("@").lower()
    variants = [
        raw,
        raw.replace(" ", "_"),
        raw.replace("_", " "),
        raw.replace(" ", ""),
        raw.replace("-", "_"),
        raw.replace("-", " "),
        raw.replace("-", "")
    ]
    return list(dict.fromkeys([v for v in variants if v]))

class WebSocketManager:
    def __init__(self):
        # Map clean_user_id -> WebSocket
        self.active_connections: Dict[str, WebSocket] = {}

    async def connect(self, user_id: str, websocket: WebSocket, aliases: list = None):
        await websocket.accept()
        all_ids = [user_id] + (aliases or [])
        for uid in all_ids:
            if not uid:
                continue
            cid = clean_user_id(uid)
            self.active_connections[cid] = websocket
            for variant in get_id_variants(uid):
                self.active_connections[variant] = websocket
        logger.info(f"[WS] User '{user_id}' connected with aliases {all_ids}. Total active keys: {len(self.active_connections)}")
        # Broadcast online status
        await self.broadcast_user_status(clean_user_id(user_id), online=True)

    def disconnect(self, user_id: str, aliases: list = None):
        all_ids = [user_id] + (aliases or [])
        for uid in all_ids:
            if not uid:
                continue
            cid = clean_user_id(uid)
            for variant in get_id_variants(uid):
                self.active_connections.pop(variant, None)
            self.active_connections.pop(cid, None)
        logger.info(f"[WS] User '{user_id}' disconnected. Remaining active keys: {len(self.active_connections)}")

    def is_online(self, user_id: str) -> bool:
        if not user_id:
            return False
        for variant in get_id_variants(user_id):
            if variant in self.active_connections:
                return True
        return clean_user_id(user_id) in self.active_connections

    async def send_personal_message(self, message: dict, recipient_id: str) -> bool:
        variants = get_id_variants(recipient_id)
        logger.info(f"[WS] Attempting send to recipient '{recipient_id}' (variants: {variants}). Active: {list(self.active_connections.keys())}")
        
        target_ws = None
        target_key = None
        for v in variants:
            if v in self.active_connections:
                target_ws = self.active_connections[v]
                target_key = v
                break

        if target_ws:
            try:
                await target_ws.send_text(json.dumps(message))
                logger.info(f"[WS] Successfully delivered message type '{message.get('type')}' to '{target_key}'")
                return True
            except Exception as e:
                logger.warning(f"[WS] Error sending message to '{target_key}': {e}")
                self.disconnect(target_key)
        else:
            logger.warning(f"[WS] Recipient '{recipient_id}' not found in active connections ({list(self.active_connections.keys())})")
        return False

    async def broadcast_user_status(self, user_id: str, online: bool):
        cid = clean_user_id(user_id)
        event = {
            "type": "USER_STATUS",
            "user_id": cid,
            "online": online
        }
        for uid, ws in list(self.active_connections.items()):
            if uid != cid:
                try:
                    await ws.send_text(json.dumps(event))
                except Exception:
                    self.disconnect(uid)

    async def broadcast_all(self, message: dict):
        payload_str = json.dumps(message)
        for uid, ws in list(self.active_connections.items()):
            try:
                await ws.send_text(payload_str)
            except Exception:
                self.disconnect(uid)

ws_manager = WebSocketManager()

