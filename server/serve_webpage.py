import os
from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
import uvicorn

app = FastAPI(title="VibeSync Showcase Webpage")

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WEBPAGE_DIR = os.path.join(BASE_DIR, "webpage")
STATIC_DIR = os.path.join(BASE_DIR, "server", "static")

APK_PATH = os.path.join(WEBPAGE_DIR, "VibeSync.apk")
if not os.path.exists(APK_PATH):
    APK_PATH = os.path.join(STATIC_DIR, "VibeSync.apk")

@app.get("/")
def get_index():
    index_file = os.path.join(WEBPAGE_DIR, "index.html")
    if os.path.exists(index_file):
        return FileResponse(index_file)
    raise HTTPException(status_code=404, detail="Webpage index not found")

@app.get("/download")
@app.get("/VibeSync.apk")
def download_user_apk():
    target = APK_PATH if os.path.exists(APK_PATH) else os.path.join(STATIC_DIR, "VibeSync.apk")
    if os.path.exists(target):
        return FileResponse(
            target,
            media_type="application/vnd.android.package-archive",
            filename="VibeSync.apk"
        )
    raise HTTPException(status_code=404, detail="APK file not found")

# Serve all static assets in webpage directory (css, js, images)
app.mount("/", StaticFiles(directory=WEBPAGE_DIR, html=True), name="static_webpage")

if __name__ == "__main__":
    uvicorn.run("serve_webpage:app", host="0.0.0.0", port=3001, reload=False)
