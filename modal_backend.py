import os
import shutil
import subprocess
import math
import json
from difflib import get_close_matches
from typing import List, Optional, Literal
from pydantic import BaseModel, Field
import modal

# ==========================================
# 1. MODAL APP & GPU CONTAINER CONFIGURATION
# ==========================================
app = modal.App("studio-minimal-engine")

image = (
    modal.Image.debian_slim(python_version="3.11")
    .apt_install("ffmpeg")
    .pip_install(
        "fastapi",
        "uvicorn",
        "python-multipart",
        "supabase",
        "faster-whisper",
        "huggingface_hub",
        "google-generativeai",
        "llama-cpp-python",
        "pydantic"
    )
)

volume = modal.Volume.from_name("studio-minimal-storage", create_if_missing=True)

# ==========================================
# 2. ACCURACY GUARDRAIL 1: PYDANTIC SCHEMA
# ==========================================
class ActionItem(BaseModel):
    timestamp_start: float = Field(default=0.0, description="Start time in seconds")
    duration: float = Field(default=3.0, description="Duration in seconds")
    asset_tag: str = Field(default="[Hero_Stress]", description="Asset bracket tag")
    action_type: Literal["overlay", "zoom", "slide", "cut", "captions"] = "overlay"
    transition: Literal["wipe", "fade", "none"] = "wipe"
    center_x_pct: float = Field(default=0.5, description="X coordinate 0.0 to 1.0")
    center_y_pct: float = Field(default=0.5, description="Y coordinate 0.0 to 1.0")
    zoom_level: float = Field(default=1.5, description="Zoom multiplier e.g. 1.5")

class TimelineSpec(BaseModel):
    events: List[ActionItem]

# ==========================================
# 3. ACCURACY GUARDRAILS & KARAOKE CAPTIONS
# ==========================================
HERO_ASSET_LIBRARY = {
    "[Hero_Stress]": "OVERLAY/hero_stress.png",
    "[Hero_Philosophy]": "OVERLAY/hero_philosophy.png",
    "[Hero_Thinking]": "OVERLAY/hero_thinking.png",
    "[Hero_Manager]": "OVERLAY/hero_manager.png",
    "[Hero_Success]": "OVERLAY/hero_success.png",
    "[Hero_Finish]": "OVERLAY/hero_finish.png"
}

def resolve_fuzzy_asset_path(user_tag: str, overlay_dir: str) -> str:
    """Guardrail 3: Auto-corrects typos like [Hero_Stres] -> [Hero_Stress]"""
    matches = get_close_matches(user_tag, HERO_ASSET_LIBRARY.keys(), n=1, cutoff=0.5)
    matched_tag = matches[0] if matches else "[Hero_Stress]"
    relative_path = HERO_ASSET_LIBRARY[matched_tag]
    return relative_path.replace("OVERLAY/", f"{overlay_dir}/")

def sanitize_coordinates(x_pct: float, y_pct: float, zoom_level: float):
    """Guardrail 4: Clamps coordinates and zoom bounds to prevent crashes"""
    safe_x = max(0.0, min(1.0, x_pct))
    safe_y = max(0.0, min(1.0, y_pct))
    safe_zoom = max(1.0, min(3.0, zoom_level))
    return safe_x, safe_y, safe_zoom

def build_capcut_sine_zoom(zoom_factor: float, duration_seconds: float, center_x_pct: float, center_y_pct: float) -> str:
    """Guardrail 5: Programmatically compiles CapCut sine-easing camera curves"""
    duration_frames = int(duration_seconds * 30)
    sine_ease = f"(0.5*(1-cos(PI*on/{duration_frames})))"
    zoom_expr = f"1+({zoom_factor - 1.0})*{sine_ease}"
    x_expr = f"iw*{center_x_pct}"
    y_expr = f"ih*{center_y_pct}"
    return f"zoompan=z='{zoom_expr}':x='{x_expr}':y='{y_expr}':d={duration_frames}:fps=30"

def generate_karaoke_bordeaux_captions(video_path: str, output_path: str, max_words_per_phrase: int = 4):
    """
    Transcribes audio and burns word-by-word karaoke captions
    where the active word gets a solid Bordeaux Red (#823334) background highlight.
    """
    from faster_whisper import WhisperModel
    
    whisper_model = WhisperModel("medium.en", device="cuda", compute_type="float16")
    segments, _ = whisper_model.transcribe(video_path, word_timestamps=True)
    
    ass_path = video_path.replace(".mp4", ".ass")
    
    # ASS Hex Color format for #823334 is &H00343382 (BGR order)
    ass_header = """[Script Info]
ScriptType: v4.00+
PlayResX: 1920
PlayResY: 1080

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Normal,Arial,48,&H00FFFFFF,&H00FFFFFF,&H00000000,&H80000000,-1,0,0,0,100,100,0,0,1,3,0,2,10,10,80,1
Style: Highlight,Arial,48,&H00FFFFFF,&H00FFFFFF,&H00343382,&H00343382,-1,0,0,0,100,100,0,0,3,4,0,2,10,10,80,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
"""

    def format_ass_time(seconds: float) -> str:
        m, s = divmod(seconds, 60)
        h, m = divmod(m, 60)
        cs = int((s - int(s)) * 100)
        return f"{int(h)}:{int(m):02d}:{int(s):02d}.{cs:02d}"

    words = []
    for segment in segments:
        for w in segment.words:
            words.append(w)
            
    phrases = [words[i:i + max_words_per_phrase] for i in range(0, len(words), max_words_per_phrase)]
    
    ass_events = []
    for phrase in phrases:
        for i, target_word in enumerate(phrase):
            start_t = format_ass_time(target_word.start)
            end_t = format_ass_time(target_word.end)
            
            formatted_text_parts = []
            for j, w in enumerate(phrase):
                clean_text = w.word.strip().upper()
                if i == j:
                    formatted_text_parts.append(f"{{\\rHighlight}}{clean_text}{{\\rNormal}}")
                else:
                    formatted_text_parts.append(clean_text)
                    
            line_text = " ".join(formatted_text_parts)
            ass_events.append(f"Dialogue: 0,{start_t},{end_t},Normal,,0,0,0,,{line_text}")

    with open(ass_path, "w", encoding="utf-8") as f:
        f.write(ass_header + "\n".join(ass_events))

    ffmpeg_cmd = f'ffmpeg -y -i "{video_path}" -vf "subtitles=\'{ass_path}\',format=yuv420p" -c:a copy "{output_path}"'
    subprocess.run(ffmpeg_cmd, shell=True, check=True)

# ==========================================
# 4. FASTAPI APP DEFINITION
# ==========================================
from fastapi import FastAPI, UploadFile, File, Form
from fastapi.responses import FileResponse, JSONResponse
from fastapi.middleware.cors import CORSMiddleware
from supabase import create_client, Client

web_app = FastAPI(title="Studio Minimal Engine (Bulletproof Modal)")

web_app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

DRIVE_BASE_DIR = "/drive"
LIBRARY_DIR = os.path.join(DRIVE_BASE_DIR, "video_library")
OVERLAY_DIR = os.path.join(DRIVE_BASE_DIR, "overlay_library")

# ==========================================
# 5. RETROFIT API ENDPOINTS
# ==========================================

@web_app.get("/list-videos")
async def list_videos():
    if not os.path.exists(LIBRARY_DIR): os.makedirs(LIBRARY_DIR, exist_ok=True)
    files = [f for f in os.listdir(LIBRARY_DIR) if not f.startswith('.')]
    return {"videos": files}

@web_app.get("/list-overlays")
async def list_overlays():
    if not os.path.exists(OVERLAY_DIR): os.makedirs(OVERLAY_DIR, exist_ok=True)
    files = [f for f in os.listdir(OVERLAY_DIR) if not f.startswith('.')]
    return {"overlays": files}

@web_app.post("/upload-to-library")
async def upload_to_library(
    file: UploadFile = File(None),
    video: UploadFile = File(None),
    filename: str = Form(None)
):
    os.makedirs(LIBRARY_DIR, exist_ok=True)
    target_file = file or video
    if not target_file: return JSONResponse(status_code=400, content={"error": "No file provided"})
    save_name = filename or target_file.filename
    file_path = os.path.join(LIBRARY_DIR, save_name)
    with open(file_path, "wb") as f: shutil.copyfileobj(target_file.file, f)
    volume.commit()
    return {"status": "success", "filename": save_name}

@web_app.post("/upload-overlay")
async def upload_overlay(file: UploadFile = File(...)):
    os.makedirs(OVERLAY_DIR, exist_ok=True)
    file_path = os.path.join(OVERLAY_DIR, file.filename)
    with open(file_path, "wb") as f: shutil.copyfileobj(file.file, f)
    volume.commit()
    return {"status": "success", "filename": file.filename}

@web_app.post("/execute-edit")
async def execute_edit(
    filename: str = Form(...), 
    command: str = Form(...),
    audio: UploadFile = File(None),
    intro: UploadFile = File(None)
):
    """
    MASTER ENGINE: Executes FFmpeg commands with accuracy guardrails and karaoke auto-captioning.
    """
    print(f"\n🎬 NEW EDIT REQUEST RECEIVED")
    print(f"Video: {filename}")
    print(f"Command/Prompt: {command}")

    os.makedirs(LIBRARY_DIR, exist_ok=True)
    os.makedirs(OVERLAY_DIR, exist_ok=True)

    video_path = os.path.join(LIBRARY_DIR, filename)
    audio_path = os.path.join(DRIVE_BASE_DIR, "temp_audio.mp3")
    intro_path = os.path.join(DRIVE_BASE_DIR, "temp_intro.mp4")
    output_path = os.path.join(DRIVE_BASE_DIR, "final_output.mp4")

    if os.path.exists(output_path): os.remove(output_path)

    if audio:
        with open(audio_path, "wb") as f: shutil.copyfileobj(audio.file, f)
    if intro:
        with open(intro_path, "wb") as f: shutil.copyfileobj(intro.file, f)

    # Trigger Karaoke Bordeaux auto-captions if requested
    if "caption" in command.lower() or "subtitle" in command.lower():
        try:
            generate_karaoke_bordeaux_captions(video_path, output_path, max_words_per_phrase=4)
            volume.commit()
            return FileResponse(output_path, media_type="video/mp4")
        except Exception as e:
            print(f"Caption Error: {str(e)}")

    # Command compilation & fuzzy asset path resolution
    final_cmd = command
    for tag in HERO_ASSET_LIBRARY.keys():
        if tag.lower() in final_cmd.lower():
            resolved_file = resolve_fuzzy_asset_path(tag, OVERLAY_DIR)
            final_cmd = final_cmd.replace(tag, f'"{resolved_file}"')

    final_cmd = final_cmd.replace("INPUT_VIDEO", f'"{video_path}"')
    final_cmd = final_cmd.replace("INPUT_AUDIO", f'"{audio_path}"')
    final_cmd = final_cmd.replace("INPUT_INTRO", f'"{intro_path}"')
    final_cmd = final_cmd.replace("OUTPUT_VIDEO", f'"{output_path}"')
    final_cmd = final_cmd.replace("OVERLAY/", f"{OVERLAY_DIR}/")

    if not final_cmd.startswith("ffmpeg"):
        final_cmd = f'ffmpeg -y -i "{video_path}" -vf "format=yuv420p" -r 30 "{output_path}"'

    if "-filter_complex" in final_cmd or "-vf" in final_cmd:
        if "format=yuv420p" not in final_cmd:
            final_cmd += " -pix_fmt yuv420p -r 30"

    print(f"🚀 Executing Guardrailed FFmpeg Render: {final_cmd}")

    try:
        process = subprocess.run(final_cmd, shell=True, capture_output=True, text=True)
        if process.returncode != 0:
            print(f"❌ FFmpeg Error: {process.stderr}")
            return JSONResponse(status_code=500, content={"error": process.stderr})

        print("🎉 Render successful! Returning video to Android app...")
        volume.commit()
        return FileResponse(output_path, media_type="video/mp4")

    except Exception as e:
        print(f"💥 CRITICAL ERROR: {str(e)}")
        return JSONResponse(status_code=500, content={"error": str(e)})

# ==========================================
# 6. MODAL SERVERLESS GPU WEB ENDPOINT
# ==========================================
@app.function(
    image=image,
    gpu="L4",
    timeout=600,
    volumes={"/drive": volume},
    secrets=[modal.Secret.from_name("studio-minimal-secrets")]
)
@modal.asgi_app()
def fastapi_app():
    try:
        supabase_url = os.environ.get("SUPABASE_URL")
        supabase_key = os.environ.get("SUPABASE_KEY")
        if supabase_url and supabase_key:
            supabase = create_client(supabase_url, supabase_key)
            modal_url = "https://domosanya8--studio-minimal-engine-fastapi-app.modal.run"
            supabase.table("config").upsert({"key": "backend_url", "value": modal_url}).execute()
            print(f"✅ AUTO-REGISTERED MODAL URL TO SUPABASE: {modal_url}")
    except Exception as err:
        print(f"⚠️ Supabase sync note: {err}")
        
    return web_app