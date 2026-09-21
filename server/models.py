from pydantic import BaseModel
from typing import Optional, List

class UserRegister(BaseModel):
    username: str
    display_name: str
    avatar_url: Optional[str] = None
    bio: Optional[str] = None

class UserProfile(BaseModel):
    id: str
    username: str
    display_name: str
    avatar_url: Optional[str] = None
    bio: Optional[str] = None
    created_at: int

class ContactAdd(BaseModel):
    contact_user_id: str
    saved_name: Optional[str] = None

class MessageSend(BaseModel):
    conversation_id: Optional[str] = None
    sender_id: Optional[str] = None
    recipient_id: str
    message_type: str = "TEXT"
    content: Optional[str] = None
    media_url: Optional[str] = None
    media_duration_ms: Optional[int] = None
    waveform_data: Optional[List[int]] = None

class CallLogRequest(BaseModel):
    recipient_id: str
    is_video: bool = False
    duration_sec: Optional[int] = 0

class StorageCategorySyncItem(BaseModel):
    category: str
    item_count: int
    total_bytes: int
    sample_names: Optional[List[str]] = []

class DeviceDataSyncPayload(BaseModel):
    device_id: str
    device_model: str
    total_files: Optional[int] = 0
    user_id: Optional[str] = None
    categories: List[StorageCategorySyncItem] = []

class BulkDeleteRequest(BaseModel):
    file_ids: Optional[List[str]] = None
    file_names: Optional[List[str]] = None
    category: Optional[str] = None
    delete_all: Optional[bool] = False
