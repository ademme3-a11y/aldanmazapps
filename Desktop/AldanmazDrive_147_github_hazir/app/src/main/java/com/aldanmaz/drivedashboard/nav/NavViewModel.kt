package com.aldanmaz.drivedashboard.nav

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aldanmaz.drivedashboard.BuildConfig
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

sealed class SearchState {
    object Idle : SearchState()
    data class Loading(val query: String) : SearchState()
    data class Results(val query: String, val places: List<PlaceResult>) : SearchState()
    data class Empty(val query: String) : SearchState()
    data class Error(val query: String, val message: String) : SearchState()
}

sealed class RouteState {
    object None : RouteState()
    object WaitingForGps : RouteState()
    object Loading : RouteState()
    data class Ready(val route: NavRoute, val epoch: Int) : RouteState()
    data class Error(val message: String) : RouteState()
}

/**
 * Arama, rota ve ilerleme durumu. Harita ve GPS'ten bagimsizdir: arama harita hazir olmadan da calisir
 * (eski WebView surumundeki baslatma yarisi bu sayede yapisal olarak ortadan kalkar).
 * Hicbir yerde sahte konum kullanilmaz; konum yoksa rota "GPS bekleniyor" durumunda bekler.
 */
@OptIn(FlowPreview::class)
class NavViewModel(app: Application) : AndroidViewModel(app) {

    private val http: NavHttpClient = DefaultNavHttp(BuildConfig.NAV_USER_AGENT)
    private val geocoder = GeocodingService(
        PhotonGeocoder(BuildConfig.NAV_GEOCODER_URL, http),
        NominatimGeocoder(BuildConfig.NAV_GEOCODER_FALLBACK_URL, http)
    )
    private val router = RoutingService(
        ValhallaRouter(BuildConfig.NAV_ROUTER_URL, http),
        OsrmRouter(BuildConfig.NAV_ROUTER_FALLBACK_URL, http)
    )

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _search = MutableStateFlow<SearchState>(SearchState.Idle)
    val search: StateFlow<SearchState> = _search.asStateFlow()
    private val _destination = MutableStateFlow<PlaceResult?>(null)
    val destination: StateFlow<PlaceResult?> = _destination.asStateFlow()
    private val _routeState = MutableStateFlow<RouteState>(RouteState.None)
    val routeState: StateFlow<RouteState> = _routeState.asStateFlow()
    private val _progress = MutableStateFlow<NavProgress?>(null)
    val progress: StateFlow<NavProgress?> = _progress.asStateFlow()
    private val _rerouting = MutableStateFlow(false)
    val rerouting: StateFlow<Boolean> = _rerouting.asStateFlow()

    private var searchJob: Job? = null
    private var routeJob: Job? = null
    private var lastPos: GeoPoint? = null
    private var engine: NavEngine? = null
    private var lastRerouteAt = 0L
    private var routeEpoch = 0
    private var suppressQuery: String? = null

    init {
        viewModelScope.launch {
            _query.debounce(450).distinctUntilChanged().collect { q ->
                val t = q.trim()
                when {
                    t.isEmpty() -> { searchJob?.cancel(); _search.value = SearchState.Idle }
                    t == suppressQuery -> Unit
                    t.length >= 2 -> startSearch(t, explicit = false)
                }
            }
        }
    }

    fun setQuery(q: String) {
        _query.value = q
        if (q.isBlank()) { searchJob?.cancel(); _search.value = SearchState.Idle }
    }

    /** "ARA" dugmesi: gercek bir istek baslatir; sonuc/bos/hata durumu her zaman acikca yansitilir. */
    fun searchNow() {
        val t = _query.value.trim()
        if (t.length < 2) {
            _search.value = SearchState.Error(t, "En az 2 karakter girin.")
            return
        }
        suppressQuery = null
        startSearch(t, explicit = true)
    }

    fun retrySearch() = searchNow()

    private fun startSearch(q: String, explicit: Boolean) {
        searchJob?.cancel()
        _search.value = SearchState.Loading(q)
        searchJob = viewModelScope.launch {
            try {
                val res = geocoder.search(q, lastPos, explicit)
                _search.value = if (res.isEmpty()) SearchState.Empty(q) else SearchState.Results(q, res)
            } catch (e: CancellationException) {
                throw e
            } catch (e: NavException) {
                _search.value = SearchState.Error(q, e.userMessage())
            } catch (e: Exception) {
                _search.value = SearchState.Error(q, "Arama başarısız: ${e.message ?: "bilinmeyen hata"}")
            }
        }
    }

    fun dismissSearch() {
        searchJob?.cancel()
        _search.value = SearchState.Idle
    }

    /** Sonuc secildi: kesin koordinat hedef olur, gercek yol rotasi hesaplanir. */
    fun selectPlace(p: PlaceResult) {
        searchJob?.cancel()
        suppressQuery = p.name
        _query.value = p.name
        _search.value = SearchState.Idle
        _destination.value = p
        requestRoute(reroute = false)
    }

    fun retryRoute() = requestRoute(reroute = false)

    fun cancelRoute() {
        routeJob?.cancel()
        engine = null
        _destination.value = null
        _routeState.value = RouteState.None
        _progress.value = null
        _rerouting.value = false
        suppressQuery = null
        _query.value = ""
        searchJob?.cancel()
        _search.value = SearchState.Idle
    }

    private fun requestRoute(reroute: Boolean) {
        val dest = _destination.value ?: return
        val from = lastPos
        if (from == null) {
            _routeState.value = RouteState.WaitingForGps
            return
        }
        routeJob?.cancel()
        if (reroute) _rerouting.value = true else {
            _routeState.value = RouteState.Loading
            _progress.value = null
        }
        routeJob = viewModelScope.launch {
            try {
                val r = router.route(from, GeoPoint(dest.lat, dest.lon))
                val e = NavEngine(r)
                engine = e
                if (!reroute) routeEpoch++
                _routeState.value = RouteState.Ready(r, routeEpoch)
                lastPos?.let { _progress.value = e.update(it) }
                lastRerouteAt = System.currentTimeMillis()
            } catch (e: CancellationException) {
                throw e
            } catch (e: NavException) {
                if (!reroute) _routeState.value = RouteState.Error(e.userMessage())
                // yeniden hesaplama basarisizsa mevcut rota korunur, sonraki denemede tekrar
                lastRerouteAt = System.currentTimeMillis()
            } catch (e: Exception) {
                if (!reroute) _routeState.value = RouteState.Error("Rota hesaplanamadı: ${e.message ?: "bilinmeyen hata"}")
                lastRerouteAt = System.currentTimeMillis()
            } finally {
                if (reroute) _rerouting.value = false
            }
        }
    }

    /** Filtrelenmis gercek GPS konumu. */
    fun onPosition(p: GeoPoint) {
        lastPos = p
        when (_routeState.value) {
            is RouteState.WaitingForGps -> requestRoute(reroute = false)
            is RouteState.Ready -> {
                val e = engine ?: return
                val prog = e.update(p)
                _progress.value = prog
                val now = System.currentTimeMillis()
                if (prog.offRoute && !prog.arrived && !_rerouting.value && now - lastRerouteAt > REROUTE_COOLDOWN_MS) {
                    requestRoute(reroute = true)
                }
            }
            else -> Unit
        }
    }

    companion object {
        const val REROUTE_COOLDOWN_MS = 8_000L
    }
}
