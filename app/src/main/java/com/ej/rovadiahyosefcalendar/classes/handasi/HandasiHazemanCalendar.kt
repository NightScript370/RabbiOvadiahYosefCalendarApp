package com.ej.rovadiahyosefcalendar.classes.handasi

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import com.ej.rovadiahyosefcalendar.classes.JewishDateInfo
import com.ej.rovadiahyosefcalendar.classes.ROZmanimCalendar
import com.ej.rovadiahyosefcalendar.classes.SecondTreatment
import com.ej.rovadiahyosefcalendar.classes.ZmanListEntry
import com.ej.rovadiahyosefcalendar.classes.ZmanListEntryType
import com.ej.rovadiahyosefcalendar.classes.ZmanimListProvider
import com.ej.rovadiahyosefcalendar.classes.ZmanimNames
import com.ej.rovadiahyosefcalendar.classes.royzmanim.RefractionRepository
import com.ej.rovadiahyosefcalendar.classes.royzmanim.RefractionRepository.RefractionData
import com.ej.rovadiahyosefcalendar.classes.royzmanim.RefractionSupport
import com.ej.rovadiahyosefcalendar.classes.royzmanim.ROYSPACalculator
import com.ej.rovadiahyosefcalendar.classes.royzmanim.SolarEvent as AirEvent
import com.ej.rovadiahyosefcalendar.classes.royzmanim.StarNightfall
import com.ej.rovadiahyosefcalendar.classes.royzmanim.StarNightfall.HazeData
import com.ej.rovadiahyosefcalendar.classes.royzmanim.StarNightfall.HazeMoment
import com.ej.rovadiahyosefcalendar.classes.royzmanim.SurfaceAir
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.util.GeoLocation
import io.github.cosinekitty.astronomy.Aberration
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.EquatorEpoch
import io.github.cosinekitty.astronomy.Observer
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.equator
import io.github.cosinekitty.astronomy.illumination
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.Calendar
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sign

/**
 * Handasi HaZeman: the rulings of the website's /handasi-hazeman page, as a subclass of ROYZmanim's calendar (the page's
 * HandasiHazemanFunctions extends ZemanFunctions; here HandasiHazemanCalendar extends [ROZmanimCalendar]). Everything here is
 * this class's own: ROZmanimCalendar knows nothing of it.
 *
 * Dawn and nightfall each start from the Gemara's minutes in Jerusalem - 72 before sunrise, 18 after sunset - taken as the
 * standard under Jerusalem's average sky; the air here today makes them longer or shorter. Dawn is light in the sky (the
 * horizon glow); nightfall is stars in the sky. Both: the standard alone without haze data (here or for Jerusalem) or with
 * the haze setting off, and moved at most the haze delay's cap (15 minutes by default) from the standard.
 *
 *  - Dawn: when the glow over the eastern horizon is as bright as in Jerusalem 72 minutes before sunrise at the equinox
 *    (16.04 degrees) under Jerusalem's average sky ([HorizonGlow]), with the morning's haze here (read when the Sun is at
 *    dawn's own depression here) and the air's pressure here against Jerusalem's average haze at 16.04 degrees (as in the
 *    Gemara's time: [GEMARA_SKY_FACTOR]). Standard: 16.04 degrees; midnight where the Sun never gets that low.
 *  - Sunrise: "Any Peak" (the visible sunrise over the terrain; without one, sunrise with elevation when it is on) and
 *    "Full Reveal" (the whole disk up).
 *  - Shema, Tefila, Chatzot, Mincha, Sunset: as ROYZmanim.
 *  - Chametz: as ROYZmanim, over the day from dawn to Rabbenu Tam's nightfall (both by the horizon glow).
 *  - Pelag: Halacha Berura only.
 *  - Nightfall: three medium stars (magnitude 2-3) seen anywhere, by A. Taylor's star-visibility model ([StarNightfall]) with
 *    the Moon and no light pollution, under the haze here at nightfall itself dimming the stars; the standard's haze is
 *    Jerusalem's average 4.65 degrees down (18 minutes after sea-level sunset in Jerusalem at the equinox); the twilight sky's
 *    brightness stays that of the standard's haze. The threshold margin is calibrated so that under Jerusalem's average dusk
 *    haze in the Gemara's time the earliest nightfall of the year in Jerusalem is 18 minutes after sea-level sunset
 *    ([STAR_MARGIN_TABLE]).
 *  - Rabbenu Tam: as dawn, in the evening (the western glow fading to Jerusalem's 72-minute brightness); only the end of the
 *    Chametz day, never shown.
 *  - Candle lighting / Tzet Shabbat / fast ends: sunset / nightfall.
 */
open class HandasiHazemanCalendar @JvmOverloads constructor(
    location: GeoLocation,
    refractionData: RefractionData? = RefractionSupport.dataFor(location),
) : ROZmanimCalendar(location, refractionData), ZmanimListProvider {

    init {
        elevationPreference?.let { isUseElevation = it }
        lastPlace = location
        ensureJerusalemHaze()
    }

    override fun newInstance(location: GeoLocation, refractionData: RefractionData?): ROZmanimCalendar =
        HandasiHazemanCalendar(location, refractionData)

    private fun dataKey() = "d" + System.identityHashCode(refractionData) + "|" + geoLocation.latitude + "|" + geoLocation.longitude + "|" +
        geoLocation.elevation + "|" + isUseElevation + "|" + jerusalemVersion + "|" + RefractionSupport.hazeTzet + "|" + RefractionSupport.hazeMaxDelayMinutes

    // ------------------------------------------------------------------------------------------------ the horizon glow

    /**
     * The Sun's depression (degrees) at which the horizon glow here matches Jerusalem's at 16.04 degrees, or null without the
     * haze of both places (or with the haze setting off). Jerusalem's average haze is read at 16.04 degrees; the haze here at
     * the depression found, so it is found twice. Before sunrise for dawn, after sunset for Rabbenu Tam.
     */
    fun glowDegrees(rising: Boolean): Double? {
        val haze = refractionData?.haze ?: return null
        val jlmHaze = jerusalemHaze ?: return null
        if (!RefractionSupport.hazeTzet) return null
        val date = localDate
        val key = dataKey() + "|glow|" + rising + "|" + date
        val cached = synchronized(numbers) { numbers[key] }
        val deg = cached ?: run {
            var d = Double.NaN
            try {
                val jlm = StarNightfall.hazeOn(jlmHaze, date, HazeMoment(DAWN_DEGREES, rising))
                val pressureMb = pressureHere(date, if (rising) AirEvent.SUNRISE else AirEvent.SUNSET)
                fun solve(readAt: Double): Double {
                    val here = StarNightfall.hazeOn(haze, date, HazeMoment(readAt, rising))
                    return if (here != null && jlm != null) HorizonGlow.glowDepression(HorizonGlow.GlowAir(here.aod, pressureMb),
                        HorizonGlow.jerusalemAir(gemaraAod(jlm.aodRef)), DAWN_DEGREES) else Double.NaN
                }
                d = solve(DAWN_DEGREES)
                if (d.isFinite()) {
                    val again = solve(d)
                    if (again.isFinite()) d = again
                }
            } catch (e: Exception) {
                Log.e(TAG, "Horizon glow failed", e)
                d = Double.NaN
            }
            synchronized(numbers) { numbers[key] = d }
            d
        }
        return if (deg.isFinite()) deg else null
    }

    /**
     * Station pressure (hPa) here for a sunrise / sunset: the atmosphere provider's air (forecast or normals) brought to the
     * observer's height, else the standard atmosphere's.
     */
    private fun pressureHere(date: LocalDate, event: AirEvent): Double {
        val h = max(0.0, geoLocation.elevation)
        val spec = try { refractionData?.provider?.airFor(date, event, geoLocation) } catch (e: Exception) { null }
        if (spec is SurfaceAir) {
            val tempC = spec.temperatureC.takeIf { it.isFinite() }
            val specH = spec.heightM?.takeIf { it.isFinite() } ?: h
            val p = spec.pressureMb
            if (p != null && p.isFinite()) return HorizonGlow.pressureAt(p, specH, h, tempC ?: 15.0)
            if (tempC != null) return HorizonGlow.pressureAt(HorizonGlow.stationPressure(specH, tempC), specH, h, tempC)
        }
        return HorizonGlow.stationPressure(h)
    }

    /** Dawn (rising) or Rabbenu Tam's nightfall by the horizon glow, kept within the haze cap of the 16.04-degree time; null to use that time. */
    private fun glowTime(rising: Boolean, plain: Instant?): Instant? {
        // the Sun never 16.04 degrees down: midnight stands in, as without haze (there is no time to cap against)
        if (plain == null) return null
        val deg = glowDegrees(rising) ?: return null
        var time = if (rising) getSunriseOffsetByDegrees(GEOMETRIC_ZENITH + deg) else getSunsetOffsetByDegrees(GEOMETRIC_ZENITH + deg)
        val capMin = RefractionSupport.hazeMaxDelayMinutes
        if (time == null) {
            // a clear sky needs the Sun lower than it gets tonight: midnight, or as far toward it as the cap allows
            val midnight = if (rising) previousSolarMidnight else solarMidnight
            if (capMin == null) return midnight
            val capped = plain.plus(minutes((if (rising) -1 else 1) * capMin))
            if (midnight == null) return capped
            return if (rising) (if (capped.isBefore(midnight)) midnight else capped) else (if (capped.isAfter(midnight)) midnight else capped)
        }
        if (capMin != null) {
            val shift = Duration.between(plain, time).toMillis() / 60000.0
            if (abs(shift) > capMin) time = plain.plus(minutes(sign(shift) * capMin))
        }
        return time
    }

    private fun minutes(m: Double) = Duration.ofMillis(round(m * 60000).toLong())

    /** Dawn by the horizon glow (16.04 degrees without haze data; the previous solar midnight where the Sun never gets that low). */
    override fun getAlotHashachar(): Instant? {
        val plain = getSunriseOffsetByDegrees(GEOMETRIC_ZENITH + DAWN_DEGREES)
        return glowTime(true, plain) ?: plain ?: previousSolarMidnight
    }

    /** Rabbenu Tam by the evening glow: the end of the MG"A day, for Chametz. */
    fun getTzetRabbenuTam(): Instant? {
        val plain = getSunsetOffsetByDegrees(GEOMETRIC_ZENITH + DAWN_DEGREES)
        return glowTime(false, plain) ?: plain ?: solarMidnight
    }

    override fun getTzais72Zmanis(): Instant? = getTzetRabbenuTam()
    override fun getTzais72ForceRegZmanis(): Instant? = getTzetRabbenuTam()
    override fun getTzais72ZmanisAmudeiHoraahLkulah(): Instant? = getTzetRabbenuTam()

    override fun getMisheyakir60ZmaniyotMinutes(): Instant? = getMisheyakir(5.0 / 6.0)
    override fun getMisheyakir66ZmaniyotMinutes(): Instant? = getMisheyakir(11.0 / 12.0)

    // ------------------------------------------------------------------------------------------------ sunrise

    /** Any Peak: the visible sunrise over the terrain; without one, sunrise (with elevation when it is on). */
    override fun getHaNetz(): Instant? = getTerrainHaNetz() ?: sunriseBasedOnElevationSetting

    override fun isHaNetzFromTerrain() = getTerrainHaNetz() != null

    /** Full Reveal: the whole disk above the terrain; without terrain, Any Peak moved by the solar diameter. */
    fun getNetzFullReveal(): Instant? = sunriseFullDisk

    /** Whether Full Reveal is the whole disk over the terrain horizon (not Any Peak moved by the solar diameter). */
    fun isNetzFullRevealFromTerrain() =
        getTerrainSunrise(com.ej.rovadiahyosefcalendar.classes.royzmanim.VisibleOptions(limb = com.ej.rovadiahyosefcalendar.classes.royzmanim.Limb.FULL), "") != null

    // ------------------------------------------------------------------------------------------------ nightfall by the stars

    /**
     * Nightfall by the stars on this date: the standard (Jerusalem's average dusk haze in the Gemara's time) and today's (the
     * haze here at dusk), epoch ms, the latter within the cap of the former. NaN where the Sun never gets 18 degrees down and
     * the stars aren't seen before. seen: whether the model saw the stars before the Sun was 18 degrees down under each haze;
     * where it didn't, the 18-degree moment (or the cap) stands in - a fallback.
     */
    class NightfallTimes(@JvmField val standard: Double, @JvmField val today: Double, @JvmField val seenStandard: Boolean, @JvmField val seenToday: Boolean)

    fun nightfallTimes(): NightfallTimes {
        val date = localDate
        val key = dataKey() + "|nightfall|" + date
        synchronized(nightfalls) { nightfalls[key] }?.let { return it }
        val lat = geoLocation.latitude
        val lon = geoLocation.longitude
        val elevationM = max(0.0, geoLocation.elevation)
        var standard = Double.NaN
        var today = Double.NaN
        var seenStandard = true
        var seenToday = true
        try {
            // the Gemara's sky: calibrates the margin (18 minutes in Jerusalem) and is the standard the cap is kept from
            val jlm = jerusalemHaze?.let { StarNightfall.hazeOn(it, date, HazeMoment(NIGHTFALL_DEGREES, false)) }
            val refAod = jlm?.let { gemaraAod(it.aodRef) } ?: DEFAULT_JERUSALEM_AOD
            val marginMag = starMarginFor(refAod)
            val haze = refractionData?.haze
            val useLocal = haze != null && jlm != null && RefractionSupport.hazeTzet
            fun localAt(depressionDeg: Double) = (if (useLocal) StarNightfall.hazeOn(haze, date, HazeMoment(depressionDeg, false))?.aod else null) ?: refAod
            var localAod = localAt(NIGHTFALL_DEGREES)
            val moon = moonFor(geoLocation)
            // the twilight sky's brightness stays the reference sky's; the air here dims the stars
            fun at(aod: Double, isStandard: Boolean): Double {
                val r = StarNightfall.starNightfall(date, lat, lon, StarNightfall.Options(aod = aod, skyAod = refAod, blpCdM2 = 0.0,
                    stars = StarNightfall.StarClass.MEDIUM, elevationM = elevationM, marginMag = marginMag, moon = moon))
                if (isStandard) seenStandard = r.reached else seenToday = r.reached
                // stars not seen before the Sun is 18 degrees down: that moment; never that low: midnight
                return if (!r.reached && !StarNightfall.eveningSunAt(date, lat, lon, -18.0).isFinite()) Double.NaN else r.ms
            }
            standard = at(refAod, true)
            if (localAod == refAod) {
                today = standard
                seenToday = seenStandard
            } else {
                today = at(localAod, false)
                // read again at the nightfall found: the haze of its own moment
                if (today.isFinite()) {
                    val again = localAt(-StarNightfall.sunAltAz(today, lat, lon)[0])
                    if (again != localAod) { localAod = again; today = at(localAod, false) }
                }
            }
            val capMin = RefractionSupport.hazeMaxDelayMinutes
            if (!standard.isFinite()) today = Double.NaN
            else if (capMin != null && today.isFinite()) today = min(standard + capMin * 60000, max(standard - capMin * 60000, today))
            else if (!today.isFinite()) today = if (capMin == null) Double.NaN else standard + capMin * 60000
        } catch (e: Exception) {
            Log.e(TAG, "Star nightfall failed", e)
        }
        val result = NightfallTimes(standard, today, seenStandard, seenToday)
        synchronized(nightfalls) { nightfalls[key] = result }
        return result
    }

    /** Nightfall by the stars (solar midnight where there is none). */
    override fun getTzeit(): Instant? {
        val today = nightfallTimes().today
        return if (!today.isFinite()) solarMidnight else Instant.ofEpochMilli(round(today).toLong())
    }

    /** How far today's air moves nightfall from the standard; null when it doesn't (no haze data, the setting off, no nightfall). */
    override fun getHazeDelay(degree: Double): Duration? {
        if (refractionData?.haze == null || !RefractionSupport.hazeTzet) return null
        val n = nightfallTimes()
        if (!n.standard.isFinite() || !n.today.isFinite() || n.today == n.standard) return null
        return Duration.ofMillis(round(n.today - n.standard).toLong())
    }

    override fun getTzeitLChumra(): Instant? = getTzeit()
    override fun getTzaisAteretTorah(): Instant? = getTzeit()
    override fun getTzeitShabbatAmudeiHoraah(): Instant? = getTzeit()
    override fun getTzeitShabbatAmudeiHoraahLesserThan40(): Instant? = getTzeit()

    /** Candle lighting at sunset. */
    override fun getCandleLighting(): Instant? = sunsetBasedOnElevationSetting

    // ------------------------------------------------------------------------------------------------ the twilight notice

    enum class ShiftKind { DAWN, NIGHTFALL, RT }

    /** How the air here moves a twilight time from its time under Jerusalem's sky in the Gemara's time. */
    class TwilightShift(
        @JvmField val kind: ShiftKind, @JvmField val minutes: Double, @JvmField val standard: Instant,
        @JvmField val aod: Double, @JvmField val refAod: Double, @JvmField val forecast: Boolean,
        @JvmField val capped: Boolean, @JvmField val fallback: Boolean,
    )

    /**
     * The shifts the website's notice above the zemanim lists (dawn, nightfall, and Rabbenu Tam on Erev Pesach); empty without
     * haze data (here or for Jerusalem) or with the haze setting off.
     */
    fun twilightShifts(): List<TwilightShift> {
        val haze = refractionData?.haze ?: return emptyList()
        val jlmHaze = jerusalemHaze ?: return emptyList()
        if (!RefractionSupport.hazeTzet) return emptyList()
        val date = localDate
        val capMin = RefractionSupport.hazeMaxDelayMinutes
        val out = ArrayList<TwilightShift>()
        fun add(kind: ShiftKind, refDeg: Double, standard: Instant?, actual: Instant?, fallback: Boolean = false) {
            if (standard == null || actual == null) return
            val rising = kind == ShiftKind.DAWN
            // the haze here as the time itself read it: at its own moment
            val here = StarNightfall.hazeOn(haze, date, HazeMoment(-StarNightfall.sunAltAz(actual.toEpochMilli().toDouble(), geoLocation.latitude, geoLocation.longitude)[0], rising))
            val jlm = StarNightfall.hazeOn(jlmHaze, date, HazeMoment(refDeg, rising))
            if (here == null || jlm == null) return
            val minutes = Duration.between(standard, actual).toMillis() / 60000.0
            out.add(TwilightShift(kind, minutes, standard, here.aod, gemaraAod(jlm.aodRef), here.forecast,
                capMin != null && abs(minutes) >= capMin - 0.01, fallback))
        }
        try {
            add(ShiftKind.DAWN, DAWN_DEGREES, getSunriseOffsetByDegrees(GEOMETRIC_ZENITH + DAWN_DEGREES), alotHashachar)
            val n = nightfallTimes()
            if (n.standard.isFinite() && n.today.isFinite()) add(ShiftKind.NIGHTFALL, NIGHTFALL_DEGREES,
                Instant.ofEpochMilli(round(n.standard).toLong()), Instant.ofEpochMilli(round(n.today).toLong()), !n.seenStandard || !n.seenToday)
            add(ShiftKind.RT, DAWN_DEGREES, getSunsetOffsetByDegrees(GEOMETRIC_ZENITH + DAWN_DEGREES), getTzetRabbenuTam())
        } catch (e: Exception) {
            Log.e(TAG, "Twilight shifts failed", e)
        }
        return out
    }

    // ------------------------------------------------------------------------------------------------ the list of zmanim

    /** The page's rows (its zmanim.yml): no Misheyakir, MG"A, Tzet L'Chumra, Yalkut Yosef Pelag or Rabbenu Tam rows. */
    override fun addZmanim(zmanim: MutableList<ZmanListEntry>, isForWeeklyZmanim: Boolean, settingsPreferences: SharedPreferences,
                           sharedPreferences: SharedPreferences, jewishDateInfo: JewishDateInfo) {
        val names = ZmanimNames(
            sharedPreferences.getBoolean("isZmanimInHebrew", false),
            sharedPreferences.getBoolean("isZmanimEnglishTranslated", false),
            sharedPreferences.getBoolean("isZmanimAmericanized", false))
        val hebrew = names.isZmanimInHebrew
        val jCal = jewishDateInfo.jewishCalendar
        if (jCal.isTaanis && jCal.yomTovIndex != JewishCalendar.TISHA_BEAV && jCal.yomTovIndex != JewishCalendar.YOM_KIPPUR) {
            zmanim.add(ZmanListEntry(names.taanitString + names.startsString, alotHashachar, SecondTreatment.ROUND_EARLIER, ZmanListEntryType.FAST_STARTS_NON_TISHA_BAV_ZMAN, ""))
        }
        zmanim.add(ZmanListEntry(names.alotString, alotHashachar, SecondTreatment.ROUND_EARLIER, ZmanListEntryType.ALOT_HASHACHAR_ZMAN, "Alot"))
        // the visible sunrise over the terrain, traced through the day's weather, is shown to the second
        val weather = isSunriseWeatherBased
        zmanim.add(ZmanListEntry(names.haNetzString + " (" + (if (hebrew) "ראשית הנץ" else "Any Peak") + ")", haNetz,
            if (weather && isHaNetzFromTerrain()) SecondTreatment.ALWAYS_DISPLAY else SecondTreatment.ROUND_LATER, ZmanListEntryType.HANETZ_ZMAN, "HaNetz"))
        zmanim.add(ZmanListEntry(names.haNetzString + " (" + (if (hebrew) "גילוי מלא" else "Full Reveal") + ")", getNetzFullReveal(),
            if (weather && isNetzFullRevealFromTerrain()) SecondTreatment.ALWAYS_DISPLAY else SecondTreatment.ROUND_LATER, ZmanListEntryType.HANETZ_ZMAN, ""))
        zmanim.add(ZmanListEntry(names.shmaGraString, sofZmanShmaGRA, SecondTreatment.ROUND_EARLIER, ZmanListEntryType.SOF_ZMAN_SHMA_GRA_ZMAN, "SofZmanShmaGRA"))
        if (jCal.yomTovIndex == JewishCalendar.EREV_PESACH) {
            zmanim.add(ZmanListEntry(names.achilatChametzString, sofZmanTfilaMGA72MinutesZmanis, SecondTreatment.ROUND_EARLIER,
                ZmanListEntryType.SOF_ZMAN_ACHILAT_CHAMETZ_ZMAN, "SofZmanAchilatChametz").apply { isNoteworthyZman = true })
            zmanim.add(ZmanListEntry(names.brachotShmaString, sofZmanTfilaGRA, SecondTreatment.ROUND_EARLIER, ZmanListEntryType.SOF_ZMAN_BERACHOT_SHMA_ZMAN, "SofZmanTefila"))
            zmanim.add(ZmanListEntry(names.biurChametzString, sofZmanBiurChametzMGA, SecondTreatment.ROUND_EARLIER,
                ZmanListEntryType.SOF_ZMAN_BIUR_CHAMETZ_ZMAN, "SofZmanBiurChametz").apply { isNoteworthyZman = true })
        } else {
            zmanim.add(ZmanListEntry(names.brachotShmaString, sofZmanTfilaGRA, SecondTreatment.ROUND_EARLIER, ZmanListEntryType.SOF_ZMAN_BERACHOT_SHMA_ZMAN, "SofZmanTefila"))
        }
        zmanim.add(ZmanListEntry(names.chatzotString, chatzot, SecondTreatment.ROUND_EARLIER, ZmanListEntryType.CHATZOT_ZMAN, "Chatzot"))
        zmanim.add(ZmanListEntry(names.minchaGedolaString, minchaGedolaGreaterThan30, SecondTreatment.ROUND_LATER, ZmanListEntryType.MINCHA_GEDOLAH_ZMAN, "MinchaGedola"))
        zmanim.add(ZmanListEntry(names.minchaKetanaString, minchaKetanaGRA, SecondTreatment.ROUND_LATER, ZmanListEntryType.MINCHA_KETANA_ZMAN, "MinchaKetana"))
        zmanim.add(ZmanListEntry(names.plagHaminchaString, plagHaminchaGRA, SecondTreatment.ROUND_LATER, ZmanListEntryType.PLAG_HAMINCHA_HB_ZMAN, "PlagHaMinchaHB"))
        if ((jCal.hasCandleLighting() && !jCal.isAssurBemelacha) || jCal.dayOfWeek == Calendar.FRIDAY) {
            zmanim.add(ZmanListEntry(names.candleLightingString, candleLighting, SecondTreatment.ROUND_EARLIER,
                ZmanListEntryType.CANDLELIGHTING_ZMAN, "CandleLighting").apply { isNoteworthyZman = true })
        }
        if (jewishDateInfo.tomorrow().jewishCalendar.isTishaBav) {
            zmanim.add(ZmanListEntry(names.taanitString + names.startsString, sunsetBasedOnElevationSetting, SecondTreatment.ROUND_EARLIER,
                ZmanListEntryType.FAST_STARTS_TISHA_BAV_ZMAN, "Shkia"))
        }
        zmanim.add(ZmanListEntry(names.sunsetString, sunsetBasedOnElevationSetting, SecondTreatment.ROUND_EARLIER, ZmanListEntryType.SUNSET_ZMAN, "Shkia"))
        zmanim.add(ZmanListEntry(names.tzaitHacochavimString, tzeit, SecondTreatment.ROUND_LATER, ZmanListEntryType.TZET_HAKOKHAVIM_ZMAN, "TzeitHacochavim").apply {
            if (jCal.isAssurBemelacha && !jCal.hasCandleLighting()) setShouldBeDimmed(true)
        })
        if (jCal.hasCandleLighting() && jCal.isAssurBemelacha && jCal.dayOfWeek != Calendar.FRIDAY) {
            // into Yom Tov from Shabbat or Yom Tov: candles at nightfall
            zmanim.add(ZmanListEntry(names.candleLightingString, tzeit, SecondTreatment.ROUND_LATER, ZmanListEntryType.CANDLELIGHTING_ZMAN, "TzeitHacochavimLChumra"))
        }
        if (jCal.isTaanis && !jCal.isYomKippur) {
            zmanim.add(ZmanListEntry(names.tzaitString + names.taanitString + names.endsString, tzeit, SecondTreatment.ROUND_LATER,
                ZmanListEntryType.FAST_ENDS_ZMAN, "FastEnd").apply { isNoteworthyZman = true })
        }
        if (jCal.isAssurBemelacha && !jCal.isTomorrowShabbosOrYomTov) {
            val what = when {
                jCal.isYomTovAssurBemelacha && jCal.dayOfWeek == Calendar.SATURDAY -> if (hebrew) "שבת/חג" else "Shabbat/Ḥag"
                jCal.dayOfWeek == Calendar.SATURDAY -> if (hebrew) "שבת" else "Shabbat"
                else -> if (hebrew) "חג" else if (names.isZmanimAmericanized) "Chag" else "Ḥag"
            }
            zmanim.add(ZmanListEntry(names.tzaitString + what + names.endsString, tzeit, SecondTreatment.ROUND_LATER,
                ZmanListEntryType.SHABBAT_CHAG_ENDS_ZMAN, "ShabbatEnd").apply { isNoteworthyZman = true })
        }
        zmanim.add(ZmanListEntry(names.chatzotLaylaString, solarMidnight, SecondTreatment.ROUND_LATER, ZmanListEntryType.CHATZOT_LAYLA_ZMAN, "NightChatzot"))
    }

    companion object {
        private const val TAG = "HandasiHazeman"

        /**
         * Dawn, and the end of the MG"A day (Rabbenu Tam) for Chametz, in degrees below the horizon under Jerusalem's average
         * sky in the Gemara's time: 72 minutes before sea-level sunrise in Jerusalem at the equinox.
         */
        const val DAWN_DEGREES = 16.04

        /** The Sun's depression 18 minutes after sea-level sunset in Jerusalem at the equinox (geometric): nightfall's standard. */
        const val NIGHTFALL_DEGREES = 4.65

        /**
         * The Gemara's sky: the part of Jerusalem's average haze that is not man-made today. Over the Eastern Mediterranean's
         * land, 2000-2012, anthropogenic aerosol is ~51% of the optical depth (Georgoulias et al. 2016, ACP 16:13853), so 0.49
         * of today's average. A declared reconstruction (a halachic modelling choice), not a measured ancient optical depth.
         */
        const val GEMARA_SKY_FACTOR = 0.49

        /** Jerusalem's average haze as it was in the Gemara's time. */
        @JvmStatic
        fun gemaraAod(aodRef: Double) = aodRef * GEMARA_SKY_FACTOR

        /**
         * Threshold margin of the star model (magnitudes) by Jerusalem's average dusk haze: the margin that puts the earliest
         * nightfall of the year in Jerusalem (754 m, no light pollution, moonless) at 18.00 minutes after sea-level sunset in
         * every year 2024-2030. Recalibrated October 2026 for the gases' ozone and pressure.
         */
        private val STAR_MARGIN_TABLE = arrayOf(
            doubleArrayOf(0.03, 0.5818), doubleArrayOf(0.05, 0.4933), doubleArrayOf(0.07, 0.411), doubleArrayOf(0.1, 0.2992),
            doubleArrayOf(0.13, 0.1971), doubleArrayOf(0.16, 0.1038), doubleArrayOf(0.2, -0.0107), doubleArrayOf(0.25, -0.1425),
            doubleArrayOf(0.3, -0.2626), doubleArrayOf(0.35, -0.3752), doubleArrayOf(0.4, -0.4811), doubleArrayOf(0.5, -0.6762),
            doubleArrayOf(0.6, -0.8575),
        )

        /** halakhic_calc's default sky at Jerusalem's height: Jerusalem's average until its haze is loaded */
        private val DEFAULT_JERUSALEM_AOD = StarNightfall.defaultAod(HorizonGlow.JERUSALEM_ELEVATION_M)

        /** STAR_MARGIN_TABLE at an average haze (linear, held at the ends). */
        @JvmStatic
        fun starMarginFor(aod: Double): Double {
            val t = STAR_MARGIN_TABLE
            if (!(aod > t[0][0])) return t[0][1]
            for (i in 1 until t.size) if (aod <= t[i][0]) return t[i - 1][1] + (t[i][1] - t[i - 1][1]) * (aod - t[i - 1][0]) / (t[i][0] - t[i - 1][0])
            return t[t.size - 1][1]
        }

        /** Settings key: "true" / "false" = elevation in sunrise and sunset on / off; absent = as ROYZmanim. */
        const val ELEVATION_KEY = "handasiHazeman.elevation"

        /** Elevation in sunrise and sunset: null = as ROYZmanim. */
        @JvmStatic @Volatile var elevationPreference: Boolean? = null

        /** Reads this class's own settings (call once, e.g. when the app starts). */
        @JvmStatic
        fun init(context: Context) {
            val p = PreferenceManager.getDefaultSharedPreferences(context)
            elevationPreference = if (p.contains(ELEVATION_KEY)) p.getBoolean(ELEVATION_KEY, true) else null
            ensureJerusalemHaze()
        }

        /** Jerusalem's average haze (CAMS's archive): the sky the Gemara's 72 and 18 minutes are measured in. */
        @Volatile private var jerusalemHaze: HazeData? = null
        /** bumped when Jerusalem's haze arrives, so cached dawns are worked out again */
        @Volatile private var jerusalemVersion = 0
        @Volatile private var jerusalemRequested = false
        @Volatile private var lastPlace: GeoLocation? = null
        private val loader = Executors.newSingleThreadExecutor { r -> Thread(r, "jerusalem-haze").apply { isDaemon = true } }

        private val numbers = ROYSPACalculator.lru<String, Double>(256)
        private val nightfalls = ROYSPACalculator.lru<String, NightfallTimes>(128)

        /**
         * Jerusalem's average haze: what an earlier run stored first (a quick read), then (re)load it - from storage when fresh,
         * else Open-Meteo - in the background, and tell the listeners if it changed. Until it arrives dawn is plain 16.04 degrees.
         */
        @JvmStatic
        fun ensureJerusalemHaze() {
            if (jerusalemHaze == null) {
                RefractionRepository.readCachedReferenceHaze(HorizonGlow.JERUSALEM_LATITUDE, HorizonGlow.JERUSALEM_LONGITUDE)?.let { apply(it) }
            }
            if (jerusalemRequested || !RefractionSupport.enabled) return
            jerusalemRequested = true
            loader.execute {
                try {
                    RefractionRepository.loadReferenceHaze(HorizonGlow.JERUSALEM_LATITUDE, HorizonGlow.JERUSALEM_LONGITUDE)?.let { h ->
                        if (apply(h)) lastPlace?.let { RefractionRepository.notifyListeners(it.latitude, it.longitude) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Jerusalem's haze failed", e)
                    jerusalemRequested = false
                }
            }
        }

        /** Use Jerusalem's haze if it differs from what is in use. */
        private fun apply(h: HazeData): Boolean {
            val old = jerusalemHaze
            if (old != null && old.normalsRange == h.normalsRange && old.aodScale == h.aodScale && old.normals.aod.contentEquals(h.normals.aod)) return false
            jerusalemHaze = h
            jerusalemVersion++
            return true
        }

        private val moonSources = HashMap<String, StarNightfall.MoonSource>()

        /** The Moon's topocentric place of date and phase angle (Astronomy Engine, with the IERS Delta-T). */
        @JvmStatic
        fun moonFor(geo: GeoLocation): StarNightfall.MoonSource = synchronized(moonSources) {
            moonSources.getOrPut("${geo.latitude},${geo.longitude},${geo.elevation}") {
                val observer = Observer(geo.latitude, geo.longitude, max(0.0, geo.elevation))
                StarNightfall.MoonSource { ms ->
                    val time = Time.fromMillisecondsSince1970(floor(ms).toLong())
                    val eq = equator(Body.Moon, time, observer, EquatorEpoch.OfDate, Aberration.Corrected)
                    StarNightfall.MoonPlace(eq.ra * 15, eq.dec, illumination(Body.Moon, time).phaseAngle)
                }
            }
        }

    }
}
