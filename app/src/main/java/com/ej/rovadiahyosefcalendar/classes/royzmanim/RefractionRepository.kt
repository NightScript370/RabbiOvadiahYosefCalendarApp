package com.ej.rovadiahyosefcalendar.classes.royzmanim

import android.content.Context
import android.util.Log
import com.ej.rovadiahyosefcalendar.classes.royzmanim.StarNightfall.HazeData
import com.kosherjava.zmanim.util.GeoLocation
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Everything the refraction-corrected zmanim need for one place, as the website's refraction-data.js /
 * refraction-snapshot.js / auto-atmosphere.js load it:
 *
 *  - provider:  the air per sunrise / sunset, best first: Open-Meteo path profiles (16 days; else a one-temperature forecast,
 *               NWS in the US or Open-Meteo), the refraction server's profiles (its forecast and climatology), the monthly
 *               normals at the spot (ERA5 via Open-Meteo, else NOAA's station normals), else the calculator's default model;
 *  - horizon:   the terrain horizon set of the official area around the point, for the visible sunrise;
 *  - haze:      CAMS's haze (averages and forecast), light pollution and the AERONET calibration, for Tzet by the stars.
 *
 * Stored per place in the app's files (the last [MAX_LOCATIONS] places): the horizon and normals until the server's terrain
 * changes, the forecast as a snapshot of the provider's answers (used with no network while younger than
 * [FORECAST_TTL_MS], shown while a new one loads up to [FORECAST_MAX_AGE_MS]), the haze. [cached] reads it synchronously, for
 * the first render; [load] fetches what is stale (blocking: call it off the main thread, or use [loadAsync]).
 */
object RefractionRepository {
    private const val TAG = "RefractionRepository"
    const val REFRACTION_SERVER = "https://hanetz.royzmanim.com/selfhost"
    const val FORECAST_TTL_MS = 3 * 60 * 60 * 1000L
    const val FORECAST_MAX_AGE_MS = 48 * 60 * 60 * 1000L
    private const val FORECAST_DAYS = 33
    const val HAZE_TTL_MS = 6 * 60 * 60 * 1000L
    private const val HAZE_CALIBRATION_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
    private const val HAZE_NORMALS_MAX_AGE_MS = 365L * 24 * 60 * 60 * 1000
    private const val MAX_LOCATIONS = 30
    private const val CACHE_VERSION = 1
    /** Bump when stored horizons must be fetched again everywhere (as the website's TERRAIN_VERSION). */
    const val TERRAIN_VERSION = "bare-earth-2"
    private const val TERRAIN_CHECK_MS = 24 * 60 * 60 * 1000L

    /** What a calculator is built with for a place; every field may be missing (offline, first run). */
    class RefractionData(
        val provider: AtmosphereProvider?,
        val horizon: HorizonSet?,
        val horizonAreaName: String?,
        val normals: EventNormals?,
        val haze: HazeData?,
        val forecastFetchedAt: Long?,
        val fromCache: Boolean,
        val notes: List<String>,
        val horizonRetryS: Int? = null,
    )

    @Volatile private var dir: File? = null
    private val memory = ConcurrentHashMap<String, RefractionData>()
    private val referenceHaze = ConcurrentHashMap<String, HazeData>()
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "refraction-loader").apply { isDaemon = true } }
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<(Double, Double) -> Unit>()

    /** Call once with any context (the files live in the app's private storage). */
    @JvmStatic
    fun init(context: Context) {
        if (dir == null) dir = File(context.applicationContext.filesDir, "refraction-v$CACHE_VERSION").apply { mkdirs() }
    }

    /** Called (on the loader thread) when better data for a place arrives: recompute and redraw there. */
    @JvmStatic
    fun addListener(l: (Double, Double) -> Unit) { listeners.add(l) }

    @JvmStatic
    fun removeListener(l: (Double, Double) -> Unit) { listeners.remove(l) }

    /** Tell the listeners that something for a place (data, a visible sunrise) is now better. */
    @JvmStatic
    fun notifyListeners(lat: Double, lon: Double) = listeners.forEach { try { it(lat, lon) } catch (e: Exception) { Log.e(TAG, "listener failed", e) } }

    private fun placeId(lat: Double, lon: Double) = String.format(Locale.ROOT, "%.5f,%.5f", lat, lon)

    /** Within this distance a location is the same place as one stored: a new GPS fix (every pull to refresh) moves by metres. */
    private const val SAME_PLACE_M = 200.0
    /** Places with data stored or in memory, as (lat, lon). */
    private val known = java.util.concurrent.CopyOnWriteArrayList<DoubleArray>()
    @Volatile private var knownLoaded = false

    private fun loadKnown() {
        if (knownLoaded || dir == null) return
        knownLoaded = true
        dir?.listFiles { f -> f.name.endsWith(".static.json") || f.name.endsWith(".forecast.json") }?.forEach { f ->
            // "<lat>_<lon>.<kind>.json"
            val parts = f.name.removeSuffix(".static.json").removeSuffix(".forecast.json").split('_')
            val lat = parts.getOrNull(0)?.toDoubleOrNull()
            val lon = parts.getOrNull(1)?.toDoubleOrNull()
            if (parts.size == 2 && lat != null && lon != null) remember(lat, lon)
        }
    }

    private fun remember(lat: Double, lon: Double) {
        if (known.none { it[0] == lat && it[1] == lon }) known.add(doubleArrayOf(lat, lon))
    }

    /**
     * The coordinates data are kept under for a location: a stored place within [SAME_PLACE_M] (the nearest), else the
     * location itself. So the 3-hour forecast cache, the daily horizon check and the 6-hour haze cache hold across GPS fixes.
     */
    private fun canonical(lat: Double, lon: Double): DoubleArray {
        loadKnown()
        var best: DoubleArray? = null
        var bestM = SAME_PLACE_M
        for (k in known) {
            val dy = (k[0] - lat) * 111320.0
            val dx = (k[1] - lon) * 111320.0 * kotlin.math.cos(Math.toRadians(lat))
            val m = kotlin.math.hypot(dx, dy)
            if (m <= bestM) { bestM = m; best = k }
        }
        return best ?: doubleArrayOf(lat, lon)
    }
    private fun fileFor(id: String, kind: String) = dir?.let { File(it, "${id.replace(',', '_')}.$kind.json") }

    private fun readJson(f: File?): JSONObject? = try { if (f != null && f.isFile) JSONObject(f.readText()) else null } catch (e: Exception) { null }

    private fun writeJson(id: String?, f: File?, j: JSONObject) {
        if (f == null) return
        try {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(j.toString())
            tmp.renameTo(f)
            if (id != null) prune(id)
        } catch (e: Exception) {
            Log.w(TAG, "could not store ${f.name}", e)
        }
    }

    /** Keep the files of the last MAX_LOCATIONS places (by last write). */
    private fun prune(keepId: String) {
        val d = dir ?: return
        val files = d.listFiles { f -> f.name.endsWith(".json") && !f.name.startsWith("reference_") } ?: return
        val byPlace = files.groupBy { it.name.substringBefore('.') }
        if (byPlace.size <= MAX_LOCATIONS) return
        val keep = keepId.replace(',', '_')
        byPlace.entries.filter { it.key != keep }.sortedBy { e -> e.value.maxOf { it.lastModified() } }
            .take(byPlace.size - MAX_LOCATIONS).forEach { e -> e.value.forEach { it.delete() } }
    }

    // ------------------------------------------------------------------------------------- snapshot (stored forecast)

    private fun specToJson(spec: AtmosphereSpec): JSONObject = when (spec) {
        is SurfaceAir -> JSONObject().apply {
            put("temperatureC", spec.temperatureC)
            spec.pressureMb?.let { put("pressureMb", it) }; spec.heightM?.let { put("heightM", it) }
            spec.dewPointC?.let { put("dewPointC", it) }; spec.relativeHumidity?.let { put("relativeHumidity", it) }
            spec.source?.let { put("source", it) }; spec.key?.let { put("key", it) }
        }
        is PathAir -> JSONObject().apply {
            put("key", spec.identityKey); spec.source?.let { put("source", it) }
            put("path", JSONArray().also { a ->
                for (p in spec.path) a.put(JSONObject().apply {
                    put("distanceKm", p.distanceKm); if (p.water) put("water", true); p.skinC?.let { put("skinC", it) }
                    put("levels", JSONArray().also { la ->
                        for (l in p.levels) la.put(JSONObject().apply {
                            put("h", l.h); put("t", l.t); put("p", l.p); l.td?.let { put("td", it) }; l.rh?.let { put("rh", it) }
                        })
                    })
                })
            })
        }
    }

    private fun specFromJson(j: JSONObject): AtmosphereSpec? {
        val path = j.optJSONArray("path")
        if (path != null) return PathAir(ServerPathAtmosphere.parseProfiles(path), j.optString("key").ifEmpty { null }, j.optString("source").ifEmpty { null })
        if (!j.has("temperatureC")) return null
        return SurfaceAir(j.getDouble("temperatureC"), WeatherHttp.numOrNull(j, "pressureMb"), WeatherHttp.numOrNull(j, "heightM"),
            WeatherHttp.numOrNull(j, "dewPointC"), WeatherHttp.numOrNull(j, "relativeHumidity"), j.optString("source").ifEmpty { null }, j.optString("key").ifEmpty { null })
    }

    /** The provider's answers for every sunrise / sunset in a range (what is stored of a forecast). */
    private fun snapshotProvider(provider: AtmosphereProvider, geo: GeoLocation, from: LocalDate, days: Int): JSONObject {
        val table = JSONObject()
        for (i in 0 until days) {
            val date = from.plusDays(i.toLong())
            for (ev in SolarEvent.entries) {
                val spec = try { provider.airFor(date, ev, geo) } catch (e: Exception) { null } ?: continue
                table.put("$date|${ev.name.lowercase()}", specToJson(spec))
            }
        }
        return table
    }

    private fun providerFromSnapshot(table: JSONObject): AtmosphereProvider {
        val parsed = HashMap<String, AtmosphereSpec?>()
        return AtmosphereProvider { date, event, _ ->
            val k = "$date|${event.name.lowercase()}"
            synchronized(parsed) {
                if (!parsed.containsKey(k)) parsed[k] = table.optJSONObject(k)?.let { specFromJson(it) }
                parsed[k]
            }
        }
    }

    private fun yesterdayAt(lon: Double): LocalDate =
        Instant.ofEpochMilli(System.currentTimeMillis() + (lon / 15 * 3600000).toLong()).atZone(ZoneOffset.UTC).toLocalDate().minusDays(1)

    // ------------------------------------------------------------------------------------- haze

    private fun hazeFromStored(st: JSONObject?): HazeData? {
        if (st == null) return null
        val normals = HazeClient.seriesFromJson(st.optJSONObject("normals")) ?: return null
        if (normals.aod.isEmpty()) return null
        val fetchedAt = if (st.isNull("forecastFetchedAt")) null else st.optLong("forecastFetchedAt", 0).takeIf { it > 0 }
        val fresh = fetchedAt != null && System.currentTimeMillis() - fetchedAt < FORECAST_MAX_AGE_MS
        val range = st.optJSONArray("normalsRange")
        return HazeData(
            normals = normals,
            forecast = if (fresh) HazeClient.seriesFromJson(st.optJSONObject("forecast")) else null,
            blpCdM2 = WeatherHttp.numOrNull(st, "blpCdM2"),
            forecastFetchedAt = if (fresh) fetchedAt else null,
            normalsRange = Pair(range?.optString(0).orEmpty(), range?.optString(1).orEmpty()),
            source = st.optString("source"),
            forecastSource = if (fresh) st.optString("forecastSource").ifEmpty { null } else null,
            aodScale = WeatherHttp.numOrNull(st, "aodScale"),
        )
    }

    /** Haze for a place: stored parts reused while fresh, the rest fetched (reference: normals and calibration only). */
    private fun loadHaze(lat: Double, lon: Double, notes: MutableList<String>, force: Boolean, reference: Boolean): HazeData? {
        val id = placeId(lat, lon)
        val file = if (reference) dir?.let { File(it, "reference_${id.replace(',', '_')}.haze.json") } else fileFor(id, "haze")
        val st = readJson(file)
        val now = System.currentTimeMillis()
        val normalsOk = st != null && st.optJSONObject("normals") != null && now - st.optLong("normalsFetchedAt", 0) < HAZE_NORMALS_MAX_AGE_MS
        val forecastOk = reference || (!force && st != null && !st.isNull("forecastFetchedAt") && st.has("forecastFetchedAt") && now - st.optLong("forecastFetchedAt", 0) < HAZE_TTL_MS)
        val calibrationOk = st != null && st.has("calibratedAt") && now - st.optLong("calibratedAt", 0) < HAZE_CALIBRATION_MAX_AGE_MS
        val normals = if (normalsOk) null else try { HazeClient.fetchHazeNormals(lat, lon) } catch (e: Exception) { notes.add("haze normals: ${e.message}"); null }
        var fcSrc: String? = null
        val fc = if (forecastOk) null else try {
            HazeClient.fetchHazeForecast(lat, lon).also { fcSrc = "cams" }
        } catch (e: Exception) {
            notes.add("haze forecast (Open-Meteo): ${e.message}")
            try { HazeClient.fetchGefsHazeForecast(lat, lon).also { fcSrc = "gefs" } } catch (e2: Exception) { notes.add("haze forecast (NOAA GEFS-Aerosols): ${e2.message}"); null }
        }
        val storedBlp = st?.let { WeatherHttp.numOrNull(it, "blpCdM2") }
        val blp = if (reference || storedBlp != null) null else try { HazeClient.fetchLightPollution(REFRACTION_SERVER, lat, lon) } catch (e: Exception) { notes.add("light pollution: ${e.message}"); null }
        val scale = if (calibrationOk) null else try { HazeClient.fetchHazeCalibration(REFRACTION_SERVER, lat, lon) } catch (e: Exception) { notes.add("haze calibration: ${e.message}"); null }
        val baseNormals = normals?.let { HazeClient.seriesToJson(it.normals) } ?: st?.optJSONObject("normals") ?: return null
        val out = JSONObject().apply {
            put("normals", baseNormals)
            put("normalsRange", normals?.let { JSONArray(listOf(it.normalsRange.first, it.normalsRange.second)) } ?: st?.optJSONArray("normalsRange") ?: JSONArray())
            put("source", normals?.source ?: st?.optString("source").orEmpty())
            put("normalsFetchedAt", if (normals != null) now else st!!.optLong("normalsFetchedAt"))
            put("blpCdM2", blp ?: storedBlp ?: JSONObject.NULL)
            put("forecast", fc?.let { HazeClient.seriesToJson(it) } ?: st?.optJSONObject("forecast") ?: JSONObject.NULL)
            put("forecastFetchedAt", if (fc != null) now else if (st != null && st.has("forecastFetchedAt") && !st.isNull("forecastFetchedAt")) st.getLong("forecastFetchedAt") else JSONObject.NULL)
            put("forecastSource", fcSrc ?: st?.optString("forecastSource").orEmpty())
            put("aodScale", scale ?: st?.let { WeatherHttp.numOrNull(it, "aodScale") } ?: JSONObject.NULL)
            if (scale != null) put("calibratedAt", now) else if (st != null && st.has("calibratedAt")) put("calibratedAt", st.getLong("calibratedAt"))
        }
        if (normals != null || fc != null || blp != null || scale != null) writeJson(if (reference) null else id, file, out)
        return hazeFromStored(out)
    }

    /** The average haze of a reference place (e.g. Jerusalem's): normals and calibration only, stored apart. Blocking. */
    @JvmStatic
    fun loadReferenceHaze(lat: Double, lon: Double): HazeData? {
        val notes = ArrayList<String>()
        val h = loadHaze(lat, lon, notes, force = false, reference = true)
        if (notes.isNotEmpty()) Log.i(TAG, "Reference haze: $notes")
        h?.let { referenceHaze[placeId(lat, lon)] = it }
        return h
    }

    /** The stored average haze of a reference place (null until loadReferenceHaze stored it). */
    @JvmStatic
    fun readCachedReferenceHaze(lat: Double, lon: Double): HazeData? {
        val id = placeId(lat, lon)
        referenceHaze[id]?.let { return it }
        return hazeFromStored(readJson(dir?.let { File(it, "reference_${id.replace(',', '_')}.haze.json") }))?.also { referenceHaze[id] = it }
    }

    // ------------------------------------------------------------------------------------- reading what is stored

    /** What earlier runs stored for this spot (memory first), for the first render; null when nothing is. Synchronous. */
    @JvmStatic
    fun cached(rawLat: Double, rawLon: Double): RefractionData? {
        val place = canonical(rawLat, rawLon)
        val lat = place[0]
        val lon = place[1]
        val id = placeId(lat, lon)
        memory[id]?.let { return it }
        if (dir == null) return null
        val stat = readJson(fileFor(id, "static"))
        val fc = readJson(fileFor(id, "forecast"))?.takeIf { System.currentTimeMillis() - it.optLong("fetchedAt") <= FORECAST_MAX_AGE_MS }
        val haze = hazeFromStored(readJson(fileFor(id, "haze")))
        if (stat == null && fc == null && haze == null) return null
        val normals = EventNormals.fromJson(stat?.optJSONObject("normals"))
        val horizon = stat?.optJSONObject("horizon")?.let { try { HorizonClient.ServerHorizon(it) } catch (e: Exception) { null } }
        val provider = Refraction.chainProviders(fc?.optJSONObject("table")?.let { providerFromSnapshot(it) }, normals?.provider())
        val data = RefractionData(provider, horizon?.set, horizon?.areaName, normals, haze, fc?.optLong("fetchedAt"), true, emptyList())
        memory[id] = data
        remember(lat, lon)
        return data
    }

    // ------------------------------------------------------------------------------------- loading

    /** Load in the background; listeners (and onDone) are told when it lands. */
    @JvmStatic
    @JvmOverloads
    fun loadAsync(lat: Double, lon: Double, force: Boolean = false, onDone: ((RefractionData) -> Unit)? = null) {
        executor.execute {
            try {
                val before = canonical(lat, lon).let { memory[placeId(it[0], it[1])] }
                val data = load(lat, lon, force)
                onDone?.invoke(data)
                if (data !== before) listeners.forEach { try { it(lat, lon) } catch (e: Exception) { Log.e(TAG, "listener failed", e) } }
            } catch (e: Exception) {
                Log.e(TAG, "refraction data failed", e)
            }
        }
    }

    /**
     * Load everything for one spot (blocking). Never throws for network failures: missing pieces come back null and are
     * listed in `notes`. With a fresh stored forecast no forecast request is made at all.
     */
    @JvmStatic
    @JvmOverloads
    fun load(rawLat: Double, rawLon: Double, force: Boolean = false): RefractionData {
        val place = canonical(rawLat, rawLon)
        val lat = place[0]
        val lon = place[1]
        val id = placeId(lat, lon)
        val notes = ArrayList<String>()
        val stat = readJson(fileFor(id, "static"))
        val storedForecast = readJson(fileFor(id, "forecast"))
        val now = System.currentTimeMillis()
        val fresh = !force && storedForecast != null && now - storedForecast.optLong("fetchedAt") < FORECAST_TTL_MS
        val cachedNormals = EventNormals.fromJson(stat?.optJSONObject("normals"))
        val geo = GeoLocation(id, lat, lon, 0.0, java.time.ZoneOffset.UTC)

        val haze = loadHaze(lat, lon, notes, force, reference = false)

        // the stored horizon, checked about once a day against the server's terrain, downloaded again only where that changed
        val storedHorizon = stat?.optJSONObject("horizon")
        var terrainCheckedAt = stat?.optLong("terrainCheckedAt", 0)?.takeIf { it > 0 }
        var horizonRetryS: Int? = null
        val horizonJson: JSONObject? = try {
            when {
                storedHorizon == null -> HorizonClient.fetchHorizonForArea(REFRACTION_SERVER, lat, lon, version = TERRAIN_VERSION).json.also { terrainCheckedAt = now }
                !force && terrainCheckedAt != null && now - terrainCheckedAt!! < TERRAIN_CHECK_MS -> storedHorizon
                else -> {
                    val current = HorizonClient.fetchTerrainVersion(REFRACTION_SERVER, lat, lon)
                    if (HorizonClient.sameTerrain(current, HorizonClient.terrainOf(storedHorizon))) { terrainCheckedAt = now; storedHorizon }
                    else {
                        notes.add("terrain changed here: horizon downloaded again")
                        HorizonClient.fetchHorizonForArea(REFRACTION_SERVER, lat, lon, version = "$TERRAIN_VERSION.${current.layers ?: "-"}.${current.fine ?: "-"}").json.also { terrainCheckedAt = now }
                    }
                }
            }
        } catch (e: Exception) {
            notes.add("horizon: ${e.message}")
            if (e is HorizonClient.HorizonPendingException) horizonRetryS = e.retryAfterS
            storedHorizon
        }
        val horizon = horizonJson?.let { try { HorizonClient.ServerHorizon(it) } catch (e: Exception) { notes.add("horizon: ${e.message}"); null } }

        fun storeStatic(normals: EventNormals?) {
            writeJson(id, fileFor(id, "static"), JSONObject().apply {
                horizonJson?.let { put("horizon", it) }
                normals?.let { put("normals", it.toJson()) }
                terrainCheckedAt?.let { put("terrainCheckedAt", it) }
            })
        }

        val result: RefractionData
        if (fresh) {
            // ── fresh forecast in storage: no Open-Meteo request ──
            if (horizonJson != null && (horizonJson !== storedHorizon || terrainCheckedAt != stat?.optLong("terrainCheckedAt"))) storeStatic(cachedNormals)
            notes.add("forecast from storage (${(now - storedForecast!!.optLong("fetchedAt")) / 60000} min old)")
            result = RefractionData(
                Refraction.chainProviders(providerFromSnapshot(storedForecast.getJSONObject("table")), cachedNormals?.provider()),
                horizon?.set, horizon?.areaName, cachedNormals, haze, storedForecast.optLong("fetchedAt"), true, notes, horizonRetryS,
            )
        } else {
            // ── fetch: path profiles (else one temperature), the server's profiles, the normals ──
            val path = try {
                OpenMeteoPath.create(lat, lon).also { p -> p.lastError?.let { notes.add("Open-Meteo path profiles (partly): ${it.message}") } }
            } catch (e: Exception) { notes.add("Open-Meteo path profiles: ${e.message}"); null }
            val single: FetchedProvider? = if (path != null) null else {
                try { Nws.createAtmosphere(lat, lon) } catch (e: Exception) {
                    if (!(e.message ?: "").let { it.contains("(404)") || it.contains("US only") }) notes.add("NWS: ${e.message}")
                    try { OpenMeteo.createAtmosphere(lat, lon) } catch (e2: Exception) { notes.add("Open-Meteo forecast: ${e2.message}"); null }
                }
            }
            val serverPaths = try {
                ServerPathAtmosphere(REFRACTION_SERVER, lat, lon).also { p ->
                    p.lastError?.let { notes.add("refraction server: ${it.message}") } ?: run { if (p.eventCount == 0) notes.add("refraction server: no data for this place") }
                }
            } catch (e: Exception) { notes.add("refraction server: ${e.message}"); null }
            var normals = cachedNormals
            if (normals == null) {
                normals = try { OpenMeteo.fetchNormals(lat, lon) } catch (e: Exception) { notes.add("Open-Meteo normals: ${e.message}"); null }
                if (normals == null) normals = try { Nws.fetchNearestNormals(lat, lon) } catch (e: Exception) { null }
            }
            storeStatic(normals)
            val forecast: AtmosphereProvider? = path ?: single
            val gotForecast = forecast != null || (serverPaths?.eventCount ?: 0) > 0
            val provider = Refraction.chainProviders(forecast, serverPaths, normals?.provider())
            if (gotForecast) {
                writeJson(id, fileFor(id, "forecast"), JSONObject().apply {
                    put("fetchedAt", now)
                    put("table", snapshotProvider(provider, geo, yesterdayAt(lon), FORECAST_DAYS))
                })
                result = RefractionData(provider, horizon?.set, horizon?.areaName, normals, haze, now, false, notes, horizonRetryS)
            } else if (storedForecast != null && now - storedForecast.optLong("fetchedAt") <= FORECAST_MAX_AGE_MS) {
                // offline / every source failed: a stored forecast (even past its TTL) beats the normals
                notes.add("forecast download failed; using the stored one")
                result = RefractionData(Refraction.chainProviders(providerFromSnapshot(storedForecast.getJSONObject("table")), normals?.provider()),
                    horizon?.set, horizon?.areaName, normals, haze, storedForecast.optLong("fetchedAt"), true, notes, horizonRetryS)
            } else {
                result = RefractionData(normals?.provider(), horizon?.set, horizon?.areaName, normals, haze, null, false, notes, horizonRetryS)
            }
        }
        if (notes.isNotEmpty()) Log.i(TAG, "refraction data for $id: $notes")
        memory[id] = result
        remember(lat, lon)
        return result
    }

    private val lastAttempt = ConcurrentHashMap<String, Long>()
    private const val RETRY_MS = 10 * 60 * 1000L

    /**
     * Load in the background when the place's forecast is stale (or there is none), at most every 10 minutes per place - so a
     * screen that rebuilds its calendar whenever new data arrive never loops while offline. Listeners are told when it lands.
     */
    @JvmStatic
    fun ensureFresh(rawLat: Double, rawLon: Double) {
        val place = canonical(rawLat, rawLon)
        val lat = place[0]
        val lon = place[1]
        if (dir == null || !RefractionSupport.enabled) return
        val id = placeId(lat, lon)
        if (memory[id]?.fromCache == false && forecastStaleIn(lat, lon) > 0) return
        if (cached(lat, lon) != null && forecastStaleIn(lat, lon) > 0 && memory[id]?.horizon != null) return
        val now = System.currentTimeMillis()
        val last = lastAttempt[id]
        if (last != null && now - last < RETRY_MS) return
        lastAttempt[id] = now
        loadAsync(rawLat, rawLon)// listeners are told with the caller's coordinates
    }

    /** Milliseconds until a place's stored forecast should be fetched again (0 = now / none). */
    @JvmStatic
    fun forecastStaleIn(rawLat: Double, rawLon: Double): Long {
        val place = canonical(rawLat, rawLon)
        val lat = place[0]
        val lon = place[1]
        val at = cached(lat, lon)?.forecastFetchedAt ?: return 0
        return maxOf(0, at + FORECAST_TTL_MS - System.currentTimeMillis())
    }
}
