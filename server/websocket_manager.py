import json
import logging
from typing import Dict
from fastapi import WebSocket

logger = logging.getLogger("uvicorn")

def clean_user_id(uid: str) -> str:
    if not uid:
        return ""
    return uid.strip().lstrip("@").lower()

class WebSocketManager:
    def __init__(self):
        # Map clean_user_id -> WebSocket
        self.active_connections: Dict[str, WebSocket] = {}

    async def connect(self, user_id: str, websocket: WebSocket):
        await websocket.accept()
        cid = clean_user_id(user_id)
        self.active_connections[cid] = websocket
        logger.info(f"[WS] User '{user_id}' (clean: '{cid}') connected. Active clients: {list(self.active_connections.keys())}")
        # Broadcast online status
        await self.broadcast_user_status(cid, online=True)

    def disconnect(self, user_id: str):
        cid = clean_user_id(user_id)
        if cid in self.active_connections:
            del self.active_connections[cid]
            logger.info(f"[WS] User '{cid}' disconnected. Remaining: {list(self.active_connections.keys())}")

    def is_online(self, user_id: str) -> bool:
        cid = clean_user_id(user_id)
        return cid in self.active_connections

    async def send_personal_message(self, message: dict, recipient_id: str) -> bool:
        cid = clean_user_id(recipient_id)
        logger.info(f"[WS] Attempting send to recipient '{recipient_id}' (clean: '{cid}'). Active: {list(self.active_connections.keys())}")
        if cid in self.active_connections:
            try:
                await self.active_connections[cid].send_text(json.dumps(message))
                logger.info(f"[WS] Successfully delivered message type '{message.get('type')}' to '{cid}'")
                return True
            except Exception as e:
                logger.warning(f"[WS] Error sending message to '{cid}': {e}")
                self.disconnect(cid)
        else:
            logger.warning(f"[WS] Recipient '{cid}' not found in active connections ({list(self.active_connections.keys())})")
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

