import pytest
import httpx
import websockets
import json
import asyncio

BASE_URL = "http://127.0.0.1:8000"
WS_URL = "ws://127.0.0.1:8000"

@pytest.mark.asyncio
async def test_user_registration_and_profile_retrieval():
    async with httpx.AsyncClient(base_url=BASE_URL) as client:
        unique_username = f"user_{int(asyncio.get_event_loop().time())}"
        
        # 1. Register new user successfully
        resp = await client.post("/api/auth/register", json={
            "username": unique_username,
            "display_name": "Test User",
            "bio": "Automated E2E Test Profile"
        })
        assert resp.status_code == 200
        data = resp.json()
        assert data["username"] == unique_username
        user_id = data["id"]

        # 2. Retrieve user profile
        me_resp = await client.get(f"/api/users/me/{user_id}")
        assert me_resp.status_code == 200
        assert me_resp.json()["id"] == user_id

@pytest.mark.asyncio
async def test_global_search_query_accuracy():
    async with httpx.AsyncClient(base_url=BASE_URL) as client:
        # Search for registered user
        resp = await client.get("/api/users/search", params={"q": "user"})
        assert resp.status_code == 200
        results = resp.json()
        assert isinstance(results, list)
        assert len(results) > 0

@pytest.mark.asyncio
async def test_websocket_two_way_messaging_and_persistence():
    async with httpx.AsyncClient(base_url=BASE_URL) as client:
        # Connect two clients via WebSocket gateway
        ws1_url = f"{WS_URL}/ws?user_id=sender_e2e"
        ws2_url = f"{WS_URL}/ws?user_id=receiver_e2e"

        async with websockets.connect(ws1_url) as ws1, websockets.connect(ws2_url) as ws2:
            payload = {
                "type": "CHAT_MESSAGE",
                "payload": {
                    "recipient_id": "receiver_e2e",
                    "content": "Hello via WebSocket E2E Test!",
                    "message_type": "TEXT"
                }
            }
            await ws1.send(json.dumps(payload))

            # Receive message on receiver_e2e WebSocket
            incoming_raw = await asyncio.wait_for(ws2.recv(), timeout=5.0)
            incoming = json.loads(incoming_raw)
            assert incoming["type"] == "NEW_MESSAGE"
            assert incoming["payload"]["content"] == "Hello via WebSocket E2E Test!"

@pytest.mark.asyncio
async def test_agora_token_generation_endpoint():
    async with httpx.AsyncClient(base_url=BASE_URL) as client:
        resp = await client.get("/api/calls/token", params={"channel_name": "vibe_sync_test_room", "uid": 12345})
        assert resp.status_code == 200
        data = resp.json()
        assert "token" in data
        assert data["channel_name"] == "vibe_sync_test_room"
        assert data["uid"] == 12345

@pytest.mark.asyncio
async def test_admin_moderation_update_and_delete():
    async with httpx.AsyncClient(base_url=BASE_URL) as client:
        mod_user = f"mod_user_{int(asyncio.get_event_loop().time())}"
        
        # 1. Register test user to manage
        reg_resp = await client.post("/api/auth/register", json={
            "username": mod_user,
            "display_name": "Mod Target",
            "bio": "To be managed"
        })
        user_id = reg_resp.json()["id"]

        # 2. Admin updates user display name via REST
        update_resp = await client.put(f"/api/users/{user_id}", data={
            "display_name": "Updated Mod Target",
            "bio": "Updated Bio"
        })
        assert update_resp.status_code == 200
        assert update_resp.json()["display_name"] == "Updated Mod Target"

        # 3. Admin deletes user
        del_resp = await client.delete(f"/api/users/{user_id}")
        assert del_resp.status_code == 200
        assert del_resp.json()["status"] == "success"
