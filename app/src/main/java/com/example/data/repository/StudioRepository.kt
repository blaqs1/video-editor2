package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.api.ColabApiClient
import com.example.data.api.GeminiApiClient
import com.example.data.api.GeminiContent
import com.example.data.api.GeminiGenerateRequest
import com.example.data.api.GeminiPart
import com.example.data.db.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import com.example.data.api.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StudioRepository(context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val videoDao = db.videoDao()
    private val scriptDao = db.scriptDao()
    private val configDao = db.configDao()
    private val overlayDao = db.overlayDao()
    private val slideDao = db.slideDao()
    private val timelineEventDao = db.timelineEventDao()
    private val projectSessionDao = db.projectSessionDao()
    private val chatMessageDao = db.chatMessageDao()

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ==========================================
    // SUPABASE BACKGROUND SYNC HELPERS
    // ==========================================
    private fun syncConfigToSupabaseAsync(key: String, value: String) {
        repositoryScope.launch {
            try {
                val json = JSONArray().put(JSONObject().apply {
                    put("key", key)
                    put("value", value)
                }).toString()
                SupabaseClient.upsert("config", json)
            } catch (e: Exception) {
                Log.e("StudioRepository", "Supabase config sync error: ${e.message}")
            }
        }
    }

    private fun syncVideoToSupabaseAsync(video: VideoEntity) {
        repositoryScope.launch {
            try {
                val json = JSONArray().put(JSONObject().apply {
                    if (video.id > 0) put("id", video.id)
                    put("filename", video.filename)
                    put("display_name", video.displayName)
                    put("language", video.language)
                    put("status", video.status)
                    put("local_uri", video.localUri)
                    put("timestamp", video.timestamp)
                }).toString()
                SupabaseClient.upsert("videos", json)
            } catch (e: Exception) {
                Log.e("StudioRepository", "Supabase video sync error: ${e.message}")
            }
        }
    }

    private fun syncOverlayToSupabaseAsync(overlay: OverlayEntity) {
        repositoryScope.launch {
            try {
                val json = JSONArray().put(JSONObject().apply {
                    if (overlay.id > 0) put("id", overlay.id)
                    put("filename", overlay.filename)
                    put("display_name", overlay.displayName)
                    put("local_path", overlay.localPath)
                    put("timestamp", overlay.timestamp)
                }).toString()
                SupabaseClient.upsert("overlay_assets", json)
            } catch (e: Exception) {
                Log.e("StudioRepository", "Supabase overlay sync error: ${e.message}")
            }
        }
    }

    private fun syncSlideToSupabaseAsync(slide: SlideEntity) {
        repositoryScope.launch {
            try {
                val json = JSONArray().put(JSONObject().apply {
                    if (slide.id > 0) put("id", slide.id)
                    put("slide_text", slide.slideText)
                    put("bordeaux_color", slide.bordeauxColor)
                    put("duration", slide.duration)
                    put("timestamp", slide.timestamp)
                }).toString()
                SupabaseClient.upsert("slide_assets", json)
            } catch (e: Exception) {
                Log.e("StudioRepository", "Supabase slide sync error: ${e.message}")
            }
        }
    }

    private fun syncTimelineEventToSupabaseAsync(event: TimelineEventEntity) {
        repositoryScope.launch {
            try {
                val json = JSONArray().put(JSONObject().apply {
                    if (event.id > 0) put("id", event.id)
                    put("video_id", event.videoId)
                    put("asset_type", event.assetType)
                    put("asset_id_or_name", event.assetIdOrName)
                    put("start_time", event.startTime)
                    put("end_time", event.endTime)
                    put("timestamp", event.timestamp)
                    put("transition_type", event.transitionType)
                    put("transition_duration", event.transitionDuration)
                }).toString()
                SupabaseClient.upsert("timeline_events", json)
            } catch (e: Exception) {
                Log.e("StudioRepository", "Supabase timeline event sync error: ${e.message}")
            }
        }
    }

    private fun syncProjectSessionToSupabaseAsync(session: ProjectSessionEntity) {
        repositoryScope.launch {
            try {
                val json = JSONArray().put(JSONObject().apply {
                    if (session.id > 0) put("id", session.id)
                    put("session_name", session.sessionName)
                    put("video_filename", session.videoFilename)
                    put("audio_filename", session.audioFilename)
                    put("sync_language", session.syncLanguage)
                    put("sync_action_cue", session.syncActionCue)
                    put("ai_engine", session.aiEngine)
                    put("sync_status_label", session.syncStatusLabel)
                    put("category_tag", session.categoryTag)
                    put("notes", session.notes)
                    put("timeline_events_count", session.timelineEventsCount)
                    put("timestamp", session.timestamp)
                    put("last_modified", session.lastModified)
                }).toString()
                SupabaseClient.upsert("project_sessions", json)
            } catch (e: Exception) {
                Log.e("StudioRepository", "Supabase project session sync error: ${e.message}")
            }
        }
    }

    val allVideos: Flow<List<VideoEntity>> = videoDao.getAllVideos()
    val allScripts: Flow<List<ScriptEntity>> = scriptDao.getAllScripts()
    val allOverlays: Flow<List<OverlayEntity>> = overlayDao.getAllOverlays()
    val allSlides: Flow<List<SlideEntity>> = slideDao.getAllSlides()
    val allProjectSessions: Flow<List<ProjectSessionEntity>> = projectSessionDao.getAllSessions()

    fun getSessionsByTag(tag: String): Flow<List<ProjectSessionEntity>> = projectSessionDao.getSessionsByTag(tag)

    suspend fun saveProjectSession(session: ProjectSessionEntity): Long = withContext(Dispatchers.IO) {
        val id = projectSessionDao.insertSession(session)
        val saved = session.copy(id = id)
        syncProjectSessionToSupabaseAsync(saved)
        id
    }

    suspend fun deleteProjectSession(id: Long) = withContext(Dispatchers.IO) {
        projectSessionDao.deleteSession(id)
    }

    suspend fun getProjectSessionById(id: Long): ProjectSessionEntity? = withContext(Dispatchers.IO) {
        projectSessionDao.getSessionById(id)
    }

    fun getScriptsForVideo(videoId: Long): Flow<List<ScriptEntity>> = scriptDao.getScriptsForVideo(videoId)

    fun getEventsForVideo(videoId: Long): Flow<List<TimelineEventEntity>> = timelineEventDao.getEventsForVideo(videoId)

    suspend fun insertOverlay(filename: String, localPath: String = "") = withContext(Dispatchers.IO) {
        val entity = OverlayEntity(filename = filename, localPath = localPath)
        val id = overlayDao.insertOverlay(entity)
        syncOverlayToSupabaseAsync(entity.copy(id = id))
        id
    }

    suspend fun deleteOverlay(id: Long) = withContext(Dispatchers.IO) {
        overlayDao.deleteOverlay(id)
    }

    suspend fun clearOverlays() = withContext(Dispatchers.IO) {
        overlayDao.clearOverlays()
    }

    suspend fun insertSlide(slideText: String, bordeauxColor: String = "#823334", duration: Int = 5) = withContext(Dispatchers.IO) {
        val entity = SlideEntity(slideText = slideText, bordeauxColor = bordeauxColor, duration = duration)
        val id = slideDao.insertSlide(entity)
        syncSlideToSupabaseAsync(entity.copy(id = id))
        id
    }

    suspend fun deleteSlide(id: Long) = withContext(Dispatchers.IO) {
        slideDao.deleteSlide(id)
    }

    suspend fun insertTimelineEvent(videoId: Long, assetType: String, assetIdOrName: String, startTime: Float, endTime: Float, transitionType: String = "none", transitionDuration: Float = 0.5f) = withContext(Dispatchers.IO) {
        val entity = TimelineEventEntity(videoId = videoId, assetType = assetType, assetIdOrName = assetIdOrName, startTime = startTime, endTime = endTime, transitionType = transitionType, transitionDuration = transitionDuration)
        val id = timelineEventDao.insertEvent(entity)
        syncTimelineEventToSupabaseAsync(entity.copy(id = id))
        id
    }

    suspend fun updateTimelineEventTransition(id: Long, transitionType: String, duration: Float) = withContext(Dispatchers.IO) {
        timelineEventDao.updateEventTransition(id, transitionType, duration)
    }

    suspend fun deleteTimelineEvent(id: Long) = withContext(Dispatchers.IO) {
        timelineEventDao.deleteEvent(id)
    }

    suspend fun clearTimelineEventsForVideo(videoId: Long) = withContext(Dispatchers.IO) {
        timelineEventDao.clearEventsForVideo(videoId)
    }

    fun getBackendUrlFlow(): Flow<ConfigEntity?> = configDao.getConfigFlow("backend_url")

    suspend fun restoreConfigFromSupabase(): String = withContext(Dispatchers.IO) {
        try {
            val remoteUrlResult = SupabaseClient.fetchConfig("backend_url")
            val remoteUrl = remoteUrlResult.getOrNull()
            if (!remoteUrl.isNullOrEmpty() && remoteUrl.isNotBlank()) {
                configDao.setConfig(ConfigEntity("backend_url", remoteUrl))
                Log.d("StudioRepository", "Restored backend_url from Supabase: $remoteUrl")
                return@withContext remoteUrl
            }
        } catch (e: Exception) {
            Log.e("StudioRepository", "Error restoring config from Supabase: ${e.message}")
        }
        return@withContext configDao.getConfig("backend_url")?.value ?: ""
    }

    suspend fun getBackendUrl(): String = withContext(Dispatchers.IO) {
        val local = configDao.getConfig("backend_url")?.value ?: ""
        if (local.isBlank() && SupabaseClient.isConfigured) {
            return@withContext restoreConfigFromSupabase()
        }
        local
    }

    suspend fun saveBackendUrl(url: String) = withContext(Dispatchers.IO) {
        configDao.setConfig(ConfigEntity("backend_url", url))
        syncConfigToSupabaseAsync("backend_url", url)
    }

    suspend fun loadSupabaseCredentials() = withContext(Dispatchers.IO) {
        val savedUrl = configDao.getConfig("supabase_url")?.value ?: ""
        val savedKey = configDao.getConfig("supabase_anon_key")?.value ?: ""
        if (savedUrl.isNotBlank() || savedKey.isNotBlank()) {
            SupabaseClient.updateCredentials(savedUrl, savedKey)
        }
    }

    suspend fun saveSupabaseCredentials(url: String, key: String) = withContext(Dispatchers.IO) {
        configDao.setConfig(ConfigEntity("supabase_url", url))
        configDao.setConfig(ConfigEntity("supabase_anon_key", key))
        SupabaseClient.updateCredentials(url, key)
    }

    suspend fun saveConfig(key: String, value: String) = withContext(Dispatchers.IO) {
        configDao.setConfig(ConfigEntity(key, value))
        syncConfigToSupabaseAsync(key, value)
    }

    suspend fun translateScript(enText: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Error: Gemini API Key is not configured. Please set it in AI Studio Secrets."
        }

        val request = GeminiGenerateRequest(
            contents = listOf(
                GeminiContent(parts = listOf(GeminiPart(text = "Translate this English script to fluent German. Return ONLY the translated text, with no extra explanations or intro. Text:\n\n$enText")))
            ),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = "You are a professional script translator. Translate English scripts to German. Maintain tone and format. Return ONLY translation.")))
        )

        try {
            val response = GeminiApiClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                ?: "Translation empty."
        } catch (e: Exception) {
            "Error translating script: ${e.localizedMessage}"
        }
    }

    suspend fun translateDirectorPrompt(
        prompt: String,
        events: List<TimelineEventEntity>,
        xPct: Float,
        yPct: Float,
        targetResolution: String = "Original (Fastest)",
        targetFps: String = "Original"
    ): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Error: Gemini API Key is not configured."
        }

        // Fetch asset library from DB for validation and context mapping
        val overlaysList = overlayDao.getAllOverlays().firstOrNull() ?: emptyList()
        val videosList = videoDao.getAllVideos().firstOrNull() ?: emptyList()
        val slidesList = slideDao.getAllSlides().firstOrNull() ?: emptyList()

        // Bracket Mention Validation Logic
        val bracketRegex = Regex("\\[([^\\]]+)\\]")
        val mentionedNames = bracketRegex.findAll(prompt).map { it.groupValues[1].trim() }.toList()

        val validNamesMap = mutableMapOf<String, String>() // lowercase name -> canonical identifier
        overlaysList.forEach {
            if (it.displayName.isNotBlank()) validNamesMap[it.displayName.lowercase()] = "OVERLAY/${it.filename}"
            validNamesMap[it.filename.lowercase()] = "OVERLAY/${it.filename}"
        }
        videosList.forEach {
            if (it.displayName.isNotBlank()) validNamesMap[it.displayName.lowercase()] = it.filename
            validNamesMap[it.filename.lowercase()] = it.filename
        }
        slidesList.forEach {
            validNamesMap[it.slideText.lowercase()] = "SLIDE: '${it.slideText}'"
        }

        for (mentioned in mentionedNames) {
            if (!validNamesMap.containsKey(mentioned.lowercase())) {
                return@withContext "Error: Asset '$mentioned' not found in your library. Please upload it first."
            }
        }

        // Build Asset Map Context for Gemini
        val assetMapSb = java.lang.StringBuilder()
        assetMapSb.append("NAMED ASSET LIBRARY MAPPING:\n")
        overlaysList.forEach { o ->
            val dName = if (o.displayName.isNotBlank()) o.displayName else o.filename
            assetMapSb.append("- Display Name: '$dName' -> Overlay File Path: 'OVERLAY/${o.filename}' (filename '${o.filename}')\n")
        }
        videosList.forEach { v ->
            val dName = if (v.displayName.isNotBlank()) v.displayName else v.filename
            assetMapSb.append("- Display Name: '$dName' -> Video File Path: '${v.filename}'\n")
        }
        slidesList.forEach { s ->
            assetMapSb.append("- Slide Asset: '${s.slideText}' (duration ${s.duration}s)\n")
        }
        assetMapSb.append("If the user prompt mentions an asset name in brackets like [Morning Stress] or [Hero Success], substitute it with its corresponding exact file path/filename in the FFmpeg filter graph.")

        val tapCoordsInfo = if (xPct >= 0 && yPct >= 0) {
            "Note: The user tapped at screen coordinate X=${xPct}% and Y=${yPct}%. Use this coordinate mapping for filters (like crop or zoompan). For instance, crop coordinates can be built as x=iw*${xPct/100f} and y=ih*${yPct/100f}."
        } else {
            "No tap coordinates captured."
        }

        val eventsInfo = if (events.isNotEmpty()) {
            val sb = java.lang.StringBuilder()
            sb.append("The following timeline events must be incorporated as FFmpeg filter layers or transitions:\n")
            events.forEachIndexed { index, event ->
                sb.append("- Event #${index + 1}: Type: ${event.assetType}, Asset/Text Name: ${event.assetIdOrName}, Start Time: ${event.startTime} seconds, End Time: ${event.endTime} seconds")
                if (event.transitionType != "none") {
                    sb.append(", Transition Type: ${event.transitionType}, Transition Duration: ${event.transitionDuration} seconds")
                }
                sb.append("\n")
            }
            sb.toString()
        } else {
            "No timeline events to apply."
        }

        val exportConstraintsInfo = """
            PRE-RENDER EXPORT CONFIGURATION & VARIABLE CONSTRAINTS:
            - Target Resolution: $targetResolution
            - Target Frame Rate: $targetFps
            
            EXPORT NORMALIZATION & ANTI-STRETCHING RULES:
            - If Target Resolution or Target FPS differ from the source video, perform Normalization at the start of the filter graph on [0:v] (the main video) BEFORE B-roll overlays or Bordeaux slides are added.
            - Mathematical Anti-Stretching Rule for Resolution Scaling:
              Always maintain original aspect ratio without stretching or distortion using force_original_aspect_ratio=increase,crop=W:H:
              * For '1080p Full HD' (1920x1080): apply `scale=1920:1080:force_original_aspect_ratio=increase,crop=1920:1080:flags=lanczos`
              * For '720p HD' (1280x720): apply `scale=1280:720:force_original_aspect_ratio=increase,crop=1280:720:flags=lanczos`
              * For '4K Ultra HD' (3840x2160): apply `scale=3840:2160:force_original_aspect_ratio=increase,crop=3840:2160:flags=lanczos`
              * For 'Original (Fastest)': do not add resolution scaling unless requested by other filters.
            - Frame Rate Boost Rule:
              * For '60 fps (Ultra Smooth)' or '60 fps': apply `fps=60` to normalize stream framerate.
              * For '30 fps': apply `fps=30`.
              * For 'Original': preserve original framerate unless xfade requires uniform fps (e.g., `fps=30,format=yuv420p`).
        """.trimIndent()

        val request = GeminiGenerateRequest(
            contents = listOf(
                GeminiContent(parts = listOf(GeminiPart(text = """
                    Translate this user editing request and timeline events into a valid, single FFmpeg command.
                    
                    User Request: "$prompt"
                    $tapCoordsInfo
                    
                    ${assetMapSb.toString()}
                    
                    $eventsInfo
                    
                    $exportConstraintsInfo
                    
                    CRITICAL TRANSITION, KEYFRAME & ANIMATION REQUIREMENTS:
                    1. Use EXACT placeholders: INPUT_VIDEO for the main video input, INPUT_AUDIO (if audio is used), INPUT_INTRO (if intro is used), and OUTPUT_VIDEO.
                    2. Normalization on Primary Video Stream [0:v]:
                       When target resolution or fps are specified, normalize [0:v] at the top of the filter graph before overlays, keyframes, or xfade transitions (e.g. `[0:v]scale=1920:1080:force_original_aspect_ratio=increase,crop=1920:1080:flags=lanczos,fps=60[v0_norm]`).
                    3. Precision Trimming & Fades:
                       - If user asks to trim an asset/video (e.g., "Trim [Hero 1] to start at 2s and end at 10s"), generate trim expressions like trim=start=2:end=10,setpts=PTS-STARTPTS or use -ss 00:00:02.0 -to 00:00:10.0.
                       - If user asks for fade-in or fade-out (e.g., "1s fade-in at start, 1s fade-out at end"), apply fade filters: fade=t=in:st=0:d=1,fade=t=out:st=ST_OUT:d=1 (or afade for audio).
                    4. Keyframe Engine (Zoom & Motion Expressions):
                       - Zoom Keyframes: generate dynamic expressions with time t or frame n, e.g. zoompan=z='min(zoom+0.001,1.5)':d=1 or scale/crop expressions based on t.
                       - Motion Keyframes: generate dynamic overlay coordinate functions of t, e.g. overlay=x='if(lte(t,2), -w+(w+10)*t/2, 10)':y=10.
                    5. AI "Animator" Logic (FFmpeg xfade) math & Framerate Normalization:
                       - To prevent xfade errors ("Inputs must have the same frame rate and pixel format"), ALWAYS normalize video inputs with `fps=30,format=yuv420p` or requested fps before applying `xfade`.
                       - When a transition between two video streams or clips is requested or defined in timeline events, calculate Offset = (End time of Clip A / Preceding Event) - (Transition Duration).
                    6. "Bordeaux Fade" (Slide Transitions):
                       When transitioning from a video clip or overlay to a Bordeaux Slide (solid #823334 slide), use a 'Fade to Color' transition or cross-fade to the generated solid color frame.
                    7. Smooth Audio Cross-fades:
                       If original audio from B-roll overlay clips or assets exists, ensure it fades out smoothly using afade=t=out:st=START_time:d=DURATION while main audio continues smoothly.
                    8. Anti-Stretching Rule:
                       Always maintain aspect ratio when scaling or upscaling using force_original_aspect_ratio=increase,crop=W:H:flags=lanczos so video frames remain pixel-perfect and not stretched.
                    9. For standard B-Roll Overlay events, treat them as overlay layers using:
                       [0:v][1:v]overlay=enable='between(t,start_time,end_time)'[v_out]
                    10. For Bordeaux Slide events, draw solid background color #823334 with white text:
                       drawbox=y=0:color=0x823334:t=fill,drawtext=text='SLIDE TEXT':fontcolor=white:fontsize=48:x=(w-text_w)/2:y=(h-text_h)/2:enable='between(t,start,end)'
                    11. Chain multiple filters sequentially in the filter_complex graph.
                    12. Return ONLY the valid, runnable FFmpeg filter or command. Do not include markdown code blocks.
                """.trimIndent())))
            ),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = "You are Studio Minimal's cinematic animation, keyframe expression, pre-render normalization, and FFmpeg command generator. Convert natural language requests, named asset mentions, keyframe motion requests, precision trimming, timeline overlay/slide transition events, and pre-render resolution/fps constraints into a single, valid, optimized FFmpeg filter/command string. When scaling, always use force_original_aspect_ratio=increase,crop=W:H to maintain aspect ratio and prevent stretching. Return only the raw command without any formatting, quotes, or markdown code blocks.")))
        )

        try {
            val response = GeminiApiClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                ?: "ffmpeg -i INPUT_VIDEO -c copy OUTPUT_VIDEO"
        } catch (e: Exception) {
            "Error generating command: ${e.localizedMessage}"
        }
    }

    suspend fun uploadOverlay(file: File, displayName: String = ""): Result<String> = withContext(Dispatchers.IO) {
        val baseUrl = getBackendUrl()
        if (baseUrl.isBlank()) {
            return@withContext Result.failure(Exception("Backend URL is not configured. Please enter your Ngrok backend link."))
        }
        try {
            val reqFile = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", file.name, reqFile)
            val responseBody = ColabApiClient.getService(baseUrl).uploadOverlay(filePart)
            val result = responseBody.string()
            // insert into local db as well with displayName
            val entity = OverlayEntity(filename = file.name, displayName = displayName, localPath = file.absolutePath)
            val id = overlayDao.insertOverlay(entity)
            syncOverlayToSupabaseAsync(entity.copy(id = id))
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadVideoToLibrary(file: File, customFilename: String = ""): Result<String> = withContext(Dispatchers.IO) {
        val baseUrl = getBackendUrl()
        if (baseUrl.isBlank()) {
            return@withContext Result.failure(Exception("Backend URL is not configured."))
        }
        try {
            val reqFile = file.asRequestBody("video/*".toMediaTypeOrNull())
            val saveName = if (customFilename.isNotBlank()) customFilename else file.name
            val filePart = MultipartBody.Part.createFormData("file", saveName, reqFile)
            val filenameBody = saveName.toRequestBody("text/plain".toMediaTypeOrNull())
            val responseBody = ColabApiClient.getService(baseUrl).uploadToLibrary(filePart, filenameBody)
            val result = responseBody.string()
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun insertOverlay(filename: String, displayName: String = "", localPath: String = "") = withContext(Dispatchers.IO) {
        val entity = OverlayEntity(filename = filename, displayName = displayName, localPath = localPath)
        val id = overlayDao.insertOverlay(entity)
        syncOverlayToSupabaseAsync(entity.copy(id = id))
        id
    }

    suspend fun updateOverlayDisplayName(id: Long, displayName: String) = withContext(Dispatchers.IO) {
        overlayDao.updateOverlayDisplayName(id, displayName)
        repositoryScope.launch {
            val item = overlayDao.getAllOverlays().firstOrNull()?.find { it.id == id }
            if (item != null) {
                syncOverlayToSupabaseAsync(item.copy(displayName = displayName))
            }
        }
    }

    suspend fun updateVideoDisplayName(id: Long, displayName: String) = withContext(Dispatchers.IO) {
        videoDao.updateVideoDisplayName(id, displayName)
        repositoryScope.launch {
            val item = videoDao.getAllVideos().firstOrNull()?.find { it.id == id }
            if (item != null) {
                syncVideoToSupabaseAsync(item.copy(displayName = displayName))
            }
        }
    }

    suspend fun syncVideos(): Result<List<VideoEntity>> = withContext(Dispatchers.IO) {
        val baseUrl = getBackendUrl()
        if (baseUrl.isBlank()) {
            return@withContext Result.failure(Exception("Backend URL is not configured. Please enter your Ngrok backend link."))
        }

        try {
            val responseBody = ColabApiClient.getService(baseUrl).listVideos()
            val rawJson = responseBody.string()
            
            // Parse robustly
            val videoList = mutableListOf<VideoEntity>()
            try {
                if (rawJson.trim().startsWith("[")) {
                    val array = JSONArray(rawJson)
                    for (i in 0 until array.length()) {
                        val item = array.get(i)
                        if (item is JSONObject) {
                            val filename = item.optString("filename", "")
                            val language = item.optString("language", "en")
                            val status = item.optString("status", "idle")
                            if (filename.isNotEmpty()) {
                                videoList.add(VideoEntity(filename = filename, language = language, status = status))
                            }
                        } else if (item is String) {
                            videoList.add(VideoEntity(filename = item, language = "en", status = "idle"))
                        }
                    }
                } else if (rawJson.trim().startsWith("{")) {
                    val obj = JSONObject(rawJson)
                    val keys = listOf("videos", "files", "data")
                    var found = false
                    for (key in keys) {
                        val array = obj.optJSONArray(key)
                        if (array != null) {
                            for (i in 0 until array.length()) {
                                val item = array.get(i)
                                if (item is JSONObject) {
                                    val filename = item.optString("filename", "")
                                    val language = item.optString("language", "en")
                                    val status = item.optString("status", "idle")
                                    if (filename.isNotEmpty()) {
                                        videoList.add(VideoEntity(filename = filename, language = language, status = status))
                                    }
                                } else if (item is String) {
                                    videoList.add(VideoEntity(filename = item, language = "en", status = "idle"))
                                }
                            }
                            found = true
                            break
                        }
                    }
                    if (!found) {
                        // Just parse the keys or some filename keys
                        val filename = obj.optString("filename", "")
                        if (filename.isNotEmpty()) {
                            videoList.add(VideoEntity(filename = filename, language = "en", status = "idle"))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("StudioRepository", "Error parsing json: ${e.message}")
            }

            if (videoList.isEmpty()) {
                // Try treating raw text as comma/newline separated
                rawJson.split(Regex("[,\n]")).forEach {
                    val cleaned = it.trim().removeSurrounding("\"").removeSurrounding("'")
                    if (cleaned.isNotEmpty() && (cleaned.endsWith(".mp4") || cleaned.contains("."))) {
                        videoList.add(VideoEntity(filename = cleaned, language = "en", status = "idle"))
                    }
                }
            }

            if (videoList.isNotEmpty()) {
                videoDao.clearVideos()
                videoDao.insertVideos(videoList)
                videoList.forEach { syncVideoToSupabaseAsync(it) }
                Result.success(videoList)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun insertLocalVideo(
        filename: String,
        displayName: String = "",
        language: String = "en",
        status: String = "idle",
        localUri: String = ""
    ) = withContext(Dispatchers.IO) {
        val entity = VideoEntity(
            filename = filename,
            displayName = displayName,
            language = language,
            status = status,
            localUri = localUri
        )
        val id = videoDao.insertVideo(entity)
        syncVideoToSupabaseAsync(entity.copy(id = id))
        id
    }

    suspend fun updateVideoStatusByFilename(filename: String, status: String) = withContext(Dispatchers.IO) {
        videoDao.updateVideoStatusByFilename(filename, status)
        val video = videoDao.getAllVideos().firstOrNull()?.find { it.filename == filename }
        if (video != null) {
            syncVideoToSupabaseAsync(video.copy(status = status))
        }
    }

    suspend fun saveScript(videoId: Long, enText: String, deText: String) = withContext(Dispatchers.IO) {
        scriptDao.insertScript(ScriptEntity(videoId = videoId, enText = enText, deText = deText))
    }

data class ExecuteEditResult(
    val message: String,
    val videoBytes: ByteArray? = null
)

    suspend fun deleteScript(id: Long) = withContext(Dispatchers.IO) {
        scriptDao.deleteScript(id)
    }

    suspend fun deleteVideo(id: Long) = withContext(Dispatchers.IO) {
        videoDao.deleteVideo(id)
    }

    suspend fun executeEdit(
        filename: String,
        command: String,
        audioFile: File?,
        introFile: File?,
        geminiApiKey: String? = null,
        aiEngine: String? = null
    ): Result<ExecuteEditResult> = withContext(Dispatchers.IO) {
        val baseUrl = getBackendUrl()
        if (baseUrl.isBlank()) {
            return@withContext Result.failure(Exception("Backend URL is not configured."))
        }

        try {
            val filenameBody = filename.toRequestBody("text/plain".toMediaTypeOrNull())
            val commandBody = command.toRequestBody("text/plain".toMediaTypeOrNull())

            val keyToUse = if (!geminiApiKey.isNullOrBlank()) geminiApiKey else com.example.BuildConfig.GEMINI_API_KEY
            val geminiKeyPart = keyToUse.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaTypeOrNull())
            val aiEnginePart = aiEngine?.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaTypeOrNull())

            val audioPart = audioFile?.let {
                val reqFile = it.asRequestBody("audio/*".toMediaTypeOrNull())
                MultipartBody.Part.createFormData("audio", it.name, reqFile)
            }

            val introPart = introFile?.let {
                val reqFile = it.asRequestBody("video/*".toMediaTypeOrNull())
                MultipartBody.Part.createFormData("intro", it.name, reqFile)
            }

            val responseBody = ColabApiClient.getService(baseUrl).executeEdit(
                filename = filenameBody,
                command = commandBody,
                audio = audioPart,
                intro = introPart,
                geminiApiKeyHeader = keyToUse.ifBlank { null },
                geminiApiKeyPart = geminiKeyPart,
                aiEnginePart = aiEnginePart
            )

            val contentType = responseBody.contentType()?.toString()?.lowercase() ?: ""
            val bytes = responseBody.bytes()

            val isVideoResponse = contentType.contains("video") ||
                    contentType.contains("octet-stream") ||
                    (bytes.size >= 8 && bytes[4] == 'f'.code.toByte() && bytes[5] == 't'.code.toByte() && bytes[6] == 'y'.code.toByte() && bytes[7] == 'p'.code.toByte())

            if (isVideoResponse) {
                Result.success(
                    ExecuteEditResult(
                        message = "Rendered video received directly from backend response (${bytes.size} bytes).",
                        videoBytes = bytes
                    )
                )
            } else {
                val textResponse = String(bytes, Charsets.UTF_8)
                Result.success(
                    ExecuteEditResult(
                        message = textResponse,
                        videoBytes = null
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // CHAT MESSAGE PERSISTENCE & PROMPT EDIT API
    // ==========================================
    fun getChatMessagesForVideo(videoId: Long): Flow<List<ChatMessageEntity>> = chatMessageDao.getMessagesForVideo(videoId)

    suspend fun insertChatMessage(message: ChatMessageEntity): Long = withContext(Dispatchers.IO) {
        chatMessageDao.insertMessage(message)
    }

    suspend fun updateChatMessageStatus(id: Long, status: String, msg: String = "") = withContext(Dispatchers.IO) {
        chatMessageDao.updateMessageStatus(id, status, msg)
    }

    suspend fun updateChatMessageCommand(id: Long, cmd: String, status: String) = withContext(Dispatchers.IO) {
        chatMessageDao.updateMessageCommand(id, cmd, status)
    }

    suspend fun deleteChatMessage(id: Long) = withContext(Dispatchers.IO) {
        chatMessageDao.deleteMessage(id)
    }

    suspend fun clearMessagesForVideo(videoId: Long) = withContext(Dispatchers.IO) {
        chatMessageDao.clearMessagesForVideo(videoId)
    }

    suspend fun submitPromptEdit(
        filename: String,
        prompt: String,
        audioFile: File? = null,
        introFile: File? = null,
        geminiApiKey: String? = null
    ): Result<ExecuteEditResult> = withContext(Dispatchers.IO) {
        val baseUrl = getBackendUrl()
        if (baseUrl.isBlank()) {
            return@withContext Result.failure(Exception("Backend URL is not configured."))
        }

        try {
            val filenameBody = filename.toRequestBody("text/plain".toMediaTypeOrNull())
            val promptBody = prompt.toRequestBody("text/plain".toMediaTypeOrNull())

            val keyToUse = if (!geminiApiKey.isNullOrBlank()) geminiApiKey else BuildConfig.GEMINI_API_KEY
            val geminiKeyPart = keyToUse.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaTypeOrNull())

            val audioPart = audioFile?.let {
                val reqFile = it.asRequestBody("audio/*".toMediaTypeOrNull())
                MultipartBody.Part.createFormData("audio", it.name, reqFile)
            }

            val introPart = introFile?.let {
                val reqFile = it.asRequestBody("video/*".toMediaTypeOrNull())
                MultipartBody.Part.createFormData("intro", it.name, reqFile)
            }

            val responseBody = ColabApiClient.getService(baseUrl).promptEdit(
                filename = filenameBody,
                prompt = promptBody,
                audio = audioPart,
                intro = introPart,
                geminiApiKeyHeader = keyToUse.ifBlank { null },
                geminiApiKeyPart = geminiKeyPart
            )

            val contentType = responseBody.contentType()?.toString()?.lowercase() ?: ""
            val bytes = responseBody.bytes()

            val isVideoResponse = contentType.contains("video") ||
                    contentType.contains("octet-stream") ||
                    (bytes.size >= 8 && bytes[4] == 'f'.code.toByte() && bytes[5] == 't'.code.toByte() && bytes[6] == 'y'.code.toByte() && bytes[7] == 'p'.code.toByte())

            if (isVideoResponse) {
                Result.success(
                    ExecuteEditResult(
                        message = "Rendered video received directly from backend response (${bytes.size} bytes).",
                        videoBytes = bytes
                    )
                )
            } else {
                val textResponse = String(bytes, Charsets.UTF_8)
                Result.success(
                    ExecuteEditResult(
                        message = textResponse,
                        videoBytes = null
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
