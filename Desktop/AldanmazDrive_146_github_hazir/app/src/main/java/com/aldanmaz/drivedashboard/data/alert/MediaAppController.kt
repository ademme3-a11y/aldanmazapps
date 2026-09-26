package com.aldanmaz.drivedashboard.data.alert

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.audiofx.Visualizer
import kotlin.math.sqrt
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.KeyEvent

data class MediaAppOption(val packageName: String, val label: String)

object MediaAppController {
    private const val PREFS = "media_app_settings"
    private const val KEY_MUSIC_PACKAGE = "music_package"
    private const val KEY_RADIO_PACKAGE = "radio_package"
    private const val KEY_MUSIC_ACTIVE = "music_active"
    private const val KEY_RADIO_ACTIVE = "radio_active"
    private const val KEY_MUSIC_PAUSED_BY_USER = "music_paused_by_user"
    private val radioHints = listOf("radio", "radyo", "fm", "tuner")

    private var heldRadioFocusRequest: AudioFocusRequest? = null
    private var legacyRadioFocusHeld: Boolean = false
    private var visualizer: Visualizer? = null
    @Volatile private var audioLevel: Float = 0f

    private fun pauseMusicForSourceSwitch(context: Context) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val musicPackage = selectedMusicPackage(context)
        dispatch(audio, KeyEvent.KEYCODE_MEDIA_PAUSE)
        if (!musicPackage.isNullOrBlank()) {
            sendMediaButtonToPackage(context, musicPackage, KeyEvent.KEYCODE_MEDIA_PAUSE)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_MUSIC_PAUSED_BY_USER, false).apply()
        setMusicPlaying(context, false)
    }

    fun openRadio(context: Context): Boolean {
        // Radyo açılırken müzik yalnızca PAUSE edilir; kullanıcı tekrar müzik seçerse
        // aynı parça/kondumdan devam eder. Bu duraklatma kullanıcı isteği değildir.
        pauseMusicForSourceSwitch(context)
        releaseRadioStopFocus(context)

        val radioPackage = findBestMatchingPackage(context, radioHints)
            ?: findAudioCapablePackage(context, radioHints)
            ?: return false

        val opened = launchPackage(context, radioPackage)
        if (opened) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_RADIO_PACKAGE, radioPackage)
                .putBoolean(KEY_RADIO_ACTIVE, true)
                .apply()
        }
        return opened
    }

    /** Kurulu ve ses dosyası açabilen uygulamaları kullanıcıya seçim için döndürür. */
    fun musicApps(context: Context): List<MediaAppOption> {
        val pm = context.packageManager
        val audioIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("content://media/external/audio/media"), "audio/*")
        }
        val audioApps = runCatching { pm.queryIntentActivities(audioIntent, 0) }.getOrDefault(emptyList())
        val musicIntent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
        val musicApps = runCatching { pm.queryIntentActivities(musicIntent, 0) }.getOrDefault(emptyList())
        return (audioApps + musicApps)
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg)
                MediaAppOption(pkg, label)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun selectedMusicPackage(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MUSIC_PACKAGE, null)

    /** Multimedya kartındaki müzik ikonunu ekolayzere çeviren ortak oynatma durumu. */
    fun isMusicPlaying(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val radioActive = prefs.getBoolean(KEY_RADIO_ACTIVE, false)
        val audioActive = runCatching {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audio?.isMusicActive == true
        }.getOrDefault(false)
        // Gerçek sistem oynatma durumu esas alınır. Radyo açıkken aynı STREAM_MUSIC
        // akışı kullanıldığı için radyo bayrağı müziği yanlışlıkla aktif göstermesin.
        return audioActive && !radioActive
    }

    fun isRadioPlaying(context: Context): Boolean {
        val prefsActive = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_RADIO_ACTIVE, false)
        if (!prefsActive) return false
        return runCatching {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audio?.isMusicActive == true
        }.getOrDefault(false)
    }

    fun currentAudioLevel(context: Context): Float {
        val active = runCatching {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audio?.isMusicActive == true
        }.getOrDefault(false)
        if (!active) return 0f
        ensureVisualizer()
        return audioLevel.coerceIn(0f, 1f)
    }

    private fun ensureVisualizer() {
        if (visualizer != null) return
        runCatching {
            val v = Visualizer(0)
            v.captureSize = Visualizer.getCaptureSizeRange()[0].coerceAtLeast(128)
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                    if (waveform == null || waveform.isEmpty()) return
                    var sum = 0.0
                    for (b in waveform) {
                        val centered = (b.toInt() - 128).toDouble()
                        sum += centered * centered
                    }
                    val rms = sqrt(sum / waveform.size) / 128.0
                    audioLevel = (rms * 2.2).toFloat().coerceIn(0f, 1f)
                }
                override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) = Unit
            }, Visualizer.getMaxCaptureRate(), true, false)
            v.enabled = true
            visualizer = v
        }
    }

    private fun setMusicPlaying(context: Context, playing: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_MUSIC_ACTIVE, playing)
            .apply()
    }

    fun selectMusicApp(context: Context, packageName: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MUSIC_PACKAGE, packageName).apply()
    }

    fun openMusic(context: Context): Boolean {
        stopRadio(context, holdFocus = false)
        val selected = selectedMusicPackage(context)
        val opened = !selected.isNullOrBlank() && launchPackage(context, selected)
        if (opened) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_MUSIC_PAUSED_BY_USER, false).apply()
            setMusicPlaying(context, true)
        }
        return opened
    }

    /**
     * 94: Gemini "müzik çaları aç" dediğinde daha önce seçim yapılmamış olsa bile
     * cihazdaki uygun ilk müzik uygulamasını seçip açar. Seçim kalıcı olur.
     */
    fun openMusicOrBest(context: Context): Boolean {
        stopRadio(context, holdFocus = false)
        val selected = selectedMusicPackage(context)
        val packageName = if (!selected.isNullOrBlank()) {
            selected
        } else {
            val best = musicApps(context).firstOrNull() ?: return false
            selectMusicApp(context, best.packageName)
            best.packageName
        }
        if (!launchPackage(context, packageName)) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_MUSIC_PAUSED_BY_USER, false).apply()
        setMusicPlaying(context, true)
        scheduleBackgroundPlayback(context, packageName)
        return true
    }

    /**
     * 94: Müzik uygulamasına Android'in standart MEDIA_PLAY_FROM_SEARCH intent'ini
     * gönderir. Uygulama bu intent'i desteklemiyorsa en azından müzik çaları açar;
     * ardından global PLAY komutu gönderilir. Böylece Spotify/YouTube Music/OEM
     * çalarların desteklediği ölçüde "X parçasını çal" çalışır.
     */
    fun playMusicQuery(context: Context, query: String): Boolean {
        stopRadio(context, holdFocus = false)
        val clean = query.trim()
        if (clean.isBlank()) return false

        var packageName = selectedMusicPackage(context)
        if (packageName.isNullOrBlank()) {
            packageName = musicApps(context).firstOrNull()?.packageName
            if (!packageName.isNullOrBlank()) selectMusicApp(context, packageName)
        }
        val resolvedPackage = packageName?.takeIf { it.isNotBlank() } ?: return false

        val playFromSearch = Intent("android.media.action.MEDIA_PLAY_FROM_SEARCH").apply {
            setPackage(resolvedPackage)
            putExtra(SearchManager.QUERY, clean)
            putExtra("query", clean)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val direct = runCatching {
            context.startActivity(playFromSearch)
            true
        }.getOrDefault(false)

        if (!direct && !launchPackage(context, resolvedPackage)) return false
        // 136: Gemini ile başlatılan müzik ALD Drive öne döndüğünde de çalmaya devam etsin.
        // OEM/Spotify/YouTube Music farkları için hedefli + global PLAY komutunu iki kez deneriz.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_MUSIC_PAUSED_BY_USER, false).apply()
        setMusicPlaying(context, true)
        scheduleBackgroundPlayback(context, resolvedPackage)
        return true
    }

    fun openMusicPackage(context: Context, packageName: String): Boolean {
        stopRadio(context, holdFocus = false)
        selectMusicApp(context, packageName)
        val opened = launchPackage(context, packageName)
        if (opened) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_MUSIC_PAUSED_BY_USER, false).apply()
            setMusicPlaying(context, true)
        }
        return opened
    }

    /**
     * 136: Multimedya kartındaki müzik ikonuna çift dokunulduğunda çağrılır. stopRadio() ile
     * aynı nedenle (bazı OEM/üçüncü parti oynatıcılar global medya tuşlarını dinlemeyebiliyor)
     * global PAUSE/STOP komutlarının yanında, seçili müzik uygulamasına hedefli
     * ACTION_MEDIA_BUTTON broadcast'i de gönderilir. Bu sayede Gemini tarafından
     * başlatılmış olsa bile müzik güvenilir şekilde durur ve ikon anında normal görünüme döner.
     */
    fun stopMedia(context: Context) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val musicPackage = selectedMusicPackage(context)

        // PAUSE kullanıyoruz: parça ve konum korunur; sonraki PLAY kaldığı yerden devam eder.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_MUSIC_PAUSED_BY_USER, true).apply()

        // Yalnız PAUSE gönderiyoruz. STOP + PAUSE aynı anda bazı çalarlarda iki kez
        // durum değiştirerek müziğin ikinci dokunuşta yeniden başlamasına yol açıyordu.
        dispatch(audio, KeyEvent.KEYCODE_MEDIA_PAUSE)
        if (!musicPackage.isNullOrBlank()) {
            sendMediaButtonToPackage(context, musicPackage, KeyEvent.KEYCODE_MEDIA_PAUSE)
        }
        setMusicPlaying(context, false)
    }

    fun resumeMedia(context: Context) {
        // Müzik yeniden başlarken radyo kesin olarak kesilir.
        stopRadio(context, holdFocus = false)
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val musicPackage = selectedMusicPackage(context)

        // Önce hedef uygulamaya, sonra global medya oturumuna PLAY gönder.
        if (!musicPackage.isNullOrBlank()) {
            sendMediaButtonToPackage(context, musicPackage, KeyEvent.KEYCODE_MEDIA_PLAY)
        }
        dispatch(audio, KeyEvent.KEYCODE_MEDIA_PLAY)

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_MUSIC_PAUSED_BY_USER, false).apply()
        setMusicPlaying(context, true)
    }

    /** Gemini/eğim gibi harici sesler müziği geçici olarak durdurursa geri döndürmek için. */
    fun rememberMusicBeforeExternalSpeech(context: Context): Boolean {
        val wasPlaying = isMusicPlaying(context)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("music_before_external_speech", wasPlaying).apply()
        return wasPlaying
    }

    fun resumeMusicAfterExternalSpeech(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val wasPlaying = prefs.getBoolean("music_before_external_speech", false)
        if (!wasPlaying) return
        if (!isMusicPlaying(context) && !prefs.getBoolean(KEY_MUSIC_PAUSED_BY_USER, false)) {
            resumeMedia(context)
        }
        prefs.edit().remove("music_before_external_speech").apply()
    }

    fun nextMedia(context: Context) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        dispatch(audio, KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun previousMedia(context: Context) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        dispatch(audio, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    fun resumeRadio(context: Context) {
        // 141: Müzik ve radyo birbirinden tamamen bağımsız olmalı. Önceden burada müzik
        // durdurulmuyordu; global PLAY komutu bazen radyo yerine son aktif müzik oturumuna
        // gidiyor, müzik gerçekten çalmaya başlıyordu. Şimdi openRadio() ile aynı şekilde
        // müzik önce kesin olarak duraklatılıyor.
        pauseMusicForSourceSwitch(context)
        releaseRadioStopFocus(context)
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val radioPackage = prefs.getString(KEY_RADIO_PACKAGE, null)

        if (!radioPackage.isNullOrBlank()) {
            // Radyo uygulaması arka planda tamamen durmuş olabilir; yalnızca PLAY tuşu
            // göndermek yetmeyebilir, bu yüzden uygulamayı da tekrar öne getiriyoruz.
            launchPackage(context, radioPackage)
            sendMediaButtonToPackage(context, radioPackage, KeyEvent.KEYCODE_MEDIA_PLAY)
        }
        dispatch(audio, KeyEvent.KEYCODE_MEDIA_PLAY)

        // 141: isMusicPlaying()/isRadioPlaying() bu bayrağa göre ayrım yapıyor. Önceden
        // burada set edilmediği için radyo sesi "müzik çalıyor" olarak algılanıp müzik
        // ikonundaki ekolayzer radyo sesiyle yanlışlıkla hareketleniyordu.
        prefs.edit().putBoolean(KEY_RADIO_ACTIVE, true).apply()
    }

    /**
     * Araç ünitelerindeki fabrika FM uygulamalarının bir kısmı global MEDIA_STOP komutunu
     * dinlemiyor. Bu nedenle _21'de üç yöntem birlikte kullanılıyor:
     * 1) global PAUSE/STOP medya tuşları,
     * 2) son açılan radyo paketine hedefli ACTION_MEDIA_BUTTON,
     * 3) ALD'nin ses odağını alması.
     * Sistem uygulaması bunların hiçbirini dinlemiyorsa Android üçüncü parti uygulamaya
     * zorla-kapat yetkisi vermediği için güvenli şekilde daha ileri gidilmez.
     */
    fun stopRadio(context: Context, holdFocus: Boolean = true) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val radioPackage = prefs.getString(KEY_RADIO_PACKAGE, null)

        dispatch(audio, KeyEvent.KEYCODE_MEDIA_PAUSE)
        dispatch(audio, KeyEvent.KEYCODE_MEDIA_STOP)

        if (!radioPackage.isNullOrBlank()) {
            sendMediaButtonToPackage(context, radioPackage, KeyEvent.KEYCODE_MEDIA_PAUSE)
            sendMediaButtonToPackage(context, radioPackage, KeyEvent.KEYCODE_MEDIA_STOP)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_RADIO_ACTIVE, false)
            .apply()

        if (!holdFocus) {
            releaseRadioStopFocus(context)
            return
        }

        // Radyo uygulaması medya tuşlarını görmezden gelirse ses odağını ALD'ye geçir.
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                heldRadioFocusRequest?.let { audio.abandonAudioFocusRequest(it) }
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setOnAudioFocusChangeListener { }
                    .build()
                if (audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    heldRadioFocusRequest = request
                }
            } else {
                @Suppress("DEPRECATION")
                val result = audio.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
                legacyRadioFocusHeld = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        }
    }

    private fun sendMediaButtonToPackage(context: Context, packageName: String, keyCode: Int) {
        fun send(action: Int) {
            val keyEvent = KeyEvent(action, keyCode)
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                setPackage(packageName)
                putExtra(Intent.EXTRA_KEY_EVENT, keyEvent)
            }
            runCatching { context.sendBroadcast(intent) }
        }
        send(KeyEvent.ACTION_DOWN)
        send(KeyEvent.ACTION_UP)
    }

    private fun releaseRadioStopFocus(context: Context) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                heldRadioFocusRequest?.let { audio.abandonAudioFocusRequest(it) }
                heldRadioFocusRequest = null
            } else if (legacyRadioFocusHeld) {
                @Suppress("DEPRECATION")
                audio.abandonAudioFocus(null)
                legacyRadioFocusHeld = false
            }
        }
    }

    /**
     * Gemini üzerinden açılan müzik uygulamasını kısa süre öne getirip PLAY komutunu yollar,
     * ardından ALD Drive'ı tekrar öne alır. Müzik uygulaması standart Android medya
     * oturumunu destekliyorsa oynatma arka planda devam eder. İkinci PLAY denemesi,
     * Gemini'nin kısa sesli yanıtından sonra odağı geri alan OEM oynatıcılara yardımcı olur.
     */
    private fun scheduleBackgroundPlayback(context: Context, packageName: String) {
        val appContext = context.applicationContext
        val handler = Handler(Looper.getMainLooper())

        fun sendPlay() {
            val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            sendMediaButtonToPackage(appContext, packageName, KeyEvent.KEYCODE_MEDIA_PLAY)
            if (audio != null) dispatch(audio, KeyEvent.KEYCODE_MEDIA_PLAY)
        }

        handler.postDelayed({ sendPlay() }, 650L)
        handler.postDelayed({
            val backToAld = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            if (backToAld != null) runCatching { appContext.startActivity(backToAld) }
        }, 1_250L)
        handler.postDelayed({ sendPlay() }, 4_000L)
    }

    private fun launchPackage(context: Context, packageName: String): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return false
        return runCatching { context.startActivity(launch); true }.getOrDefault(false)
    }

    private fun dispatch(audio: AudioManager, keyCode: Int) {
        val down = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val up = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        runCatching {
            audio.dispatchMediaKeyEvent(down)
            audio.dispatchMediaKeyEvent(up)
        }
    }

    private fun findBestMatchingPackage(context: Context, hints: List<String>): String? {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val candidates = runCatching { pm.queryIntentActivities(launcher, 0) }.getOrDefault(emptyList())
        return candidates.firstOrNull { info ->
            val label = runCatching { info.loadLabel(pm).toString().lowercase() }.getOrDefault("")
            val pkg = info.activityInfo?.packageName?.lowercase().orEmpty()
            hints.any { label.contains(it) || pkg.contains(it.replace(" ", "")) }
        }?.activityInfo?.packageName
    }

    private fun findAudioCapablePackage(context: Context, hints: List<String>): String? {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "audio/*")
        }
        val candidates = runCatching { pm.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
        val match = candidates.firstOrNull { info ->
            val label = runCatching { info.loadLabel(pm).toString().lowercase() }.getOrDefault("")
            val pkg = info.activityInfo?.packageName?.lowercase().orEmpty()
            hints.any { label.contains(it) || pkg.contains(it.replace(" ", "")) }
        } ?: candidates.firstOrNull()
        return match?.activityInfo?.packageName
    }
}