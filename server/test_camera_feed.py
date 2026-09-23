from fastapi.testclient import TestClient
from main import app, get_db

client = TestClient(app)

def test_camera_feed_endpoints():
    test_device_id = "test_cam_device_999"

    # Register/insert device in db
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("""
        INSERT OR REPLACE INTO devices (device_id, device_model, username, user_id, last_sync_timestamp)
        VALUES (?, ?, ?, ?, ?)
    """, (test_device_id, "Test Phone", "test_camera_user", "test_user_id", 123456789))
    conn.commit()
    conn.close()

    # 1. Start camera feed
    start_resp = client.post(f"/api/admin/devices/{test_device_id}/start-camera-feed")
    assert start_resp.status_code == 200, f"Failed: {start_resp.text}"
    start_data = start_resp.json()
    assert start_data["status"] == "success"
    assert "channel_name" in start_data
    assert start_data["channel_name"].startswith("camera_session_")
    assert "token" in start_data
    assert "agora_app_id" in start_data
    assert start_data["agora_app_id"] != ""
    assert start_data["device_id"] == test_device_id

    # 2. Stop camera feed
    stop_resp = client.post(f"/api/admin/devices/{test_device_id}/stop-camera-feed")
    assert stop_resp.status_code == 200, f"Failed: {stop_resp.text}"
    stop_data = stop_resp.json()
    assert stop_data["status"] == "success"
    assert stop_data["device_id"] == test_device_id

    # 3. Non-existent device should return 404
    non_existent_id = "non_existent_device_xyz"
    err_start = client.post(f"/api/admin/devices/{non_existent_id}/start-camera-feed")
    assert err_start.status_code == 404

    err_stop = client.post(f"/api/admin/devices/{non_existent_id}/stop-camera-feed")
    assert err_stop.status_code == 404
