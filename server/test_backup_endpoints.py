import io
from fastapi.testclient import TestClient
from main import app, get_db

client = TestClient(app)

def test_data_summary_endpoint():
    resp = client.get("/api/admin/data-summary")
    assert resp.status_code == 200
    data = resp.json()
    assert "total_storage_mb" in data
    assert "total_items" in data
    assert "device_count" in data
    assert "category_breakdown" in data
    assert "device_storage" in data

def test_device_file_upload_and_download_flow():
    test_device_id = "test_device_abc123"
    fake_file_content = b"This is test device backup content for documents and images."
    fake_file = io.BytesIO(fake_file_content)

    # 1. Upload a file
    upload_resp = client.post(
        f"/api/devices/{test_device_id}/upload-file",
        files={"file": ("test_backup_doc.txt", fake_file, "text/plain")},
        data={"category": "Document", "username": "test_owner"}
    )
    assert upload_resp.status_code == 200
    upload_data = upload_resp.json()
    assert upload_data["status"] == "success"
    assert upload_data["filename"] == "test_backup_doc.txt"
    file_id = upload_data["file_id"]

    # 2. Check that the file appears in /api/admin/files
    files_resp = client.get("/api/admin/files")
    assert files_resp.status_code == 200
    files = files_resp.json()
    uploaded_item = next((f for f in files if f["id"] == file_id or f["name"] == "test_backup_doc.txt"), None)
    assert uploaded_item is not None
    assert uploaded_item["category"] == "Document"
    assert uploaded_item["username"] == "test_owner"

    # 3. Download the file
    download_resp = client.get(f"/api/admin/files/download/{file_id}")
    assert download_resp.status_code == 200
    assert download_resp.content == fake_file_content

    # 4. Trigger backup signal
    trigger_resp = client.post(f"/api/admin/devices/{test_device_id}/trigger-backup")
    assert trigger_resp.status_code == 200
    trigger_data = trigger_resp.json()
    assert trigger_data["status"] == "success"
    assert trigger_data["device_id"] == test_device_id

    # 5. Delete the file
    del_resp = client.delete(f"/api/admin/files/{file_id}")
    assert del_resp.status_code == 200
    assert del_resp.json()["status"] == "success"

def test_bulk_delete_and_exact_item_count_reduction():
    test_device_id = "test_device_bulk"
    
    # Get initial item count
    initial_summary = client.get("/api/admin/data-summary").json()
    initial_count = initial_summary["total_items"]
    
    # Upload 5 files
    uploaded_ids = []
    for i in range(5):
        f = io.BytesIO(f"Sample test content {i}".encode("utf-8"))
        res = client.post(
            f"/api/devices/{test_device_id}/upload-file",
            files={"file": (f"test_doc_{i}.txt", f, "text/plain")},
            data={"category": "Document", "username": "test_owner"}
        )
        assert res.status_code == 200
        uploaded_ids.append(res.json()["file_id"])
        
    after_upload_summary = client.get("/api/admin/data-summary").json()
    assert after_upload_summary["total_items"] == initial_count + 5
    
    # Delete 2 files via bulk-delete
    bulk_del_res = client.post("/api/admin/files/bulk-delete", json={"file_ids": uploaded_ids[:2]})
    assert bulk_del_res.status_code == 200
    assert bulk_del_res.json()["deleted_count"] == 2
    
    after_partial_del = client.get("/api/admin/data-summary").json()
    assert after_partial_del["total_items"] == initial_count + 3
    
    # Delete remaining 3 files
    bulk_del_res2 = client.post("/api/admin/files/bulk-delete", json={"file_ids": uploaded_ids[2:]})
    assert bulk_del_res2.status_code == 200
    assert bulk_del_res2.json()["deleted_count"] == 3
    
    after_final_del = client.get("/api/admin/data-summary").json()
    assert after_final_del["total_items"] == initial_count

if __name__ == "__main__":
    test_data_summary_endpoint()
    test_device_file_upload_and_download_flow()
    test_bulk_delete_and_exact_item_count_reduction()
    print("All backup endpoint tests passed successfully!")
