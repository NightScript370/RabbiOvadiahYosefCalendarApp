package com.ej.rovadiahyosefcalendar.classes.handasi

import com.ej.rovadiahyosefcalendar.classes.ROZmanimCalendar
import com.kosherjava.zmanim.util.GeoLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/** Without stored data (no haze): dawn is the plain 16.04 degrees, nightfall the standard under Jerusalem's default sky. */
class HandasiHazemanCalendarTest {
    private val geo = GeoLocation("Jerusalem", 31.778, 35.2354, 754.0, ZoneId.of("Asia/Jerusalem"))

    @Test
    fun noDataDay() {
        val hh = HandasiHazemanCalendar(geo, null).apply { localDate = LocalDate.of(2026, 3, 20) }
        assertEquals(hh.getSunriseOffsetByDegrees(106.04), hh.alotHashachar)
        val sunset = hh.seaLevelSunset
        val tzeit = hh.tzeit
        assertNotNull(tzeit)
        val min = Duration.between(sunset, tzeit).toMillis() / 60000.0
        println("Handasi nightfall $min min after sea-level sunset; dawn ${hh.alotHashachar}; any peak ${hh.haNetz}; full ${hh.getNetzFullReveal()}")
        assertTrue(min in 12.0..30.0)
        assertEquals(hh.sunriseBasedOnElevationSetting, hh.haNetz)
        assertTrue(hh.getNetzFullReveal()!!.isAfter(hh.haNetz))
        assertEquals(hh.sunsetBasedOnElevationSetting, hh.candleLighting)
        // the copy stays a Handasi calendar (getCopy is used for every other date)
        assertTrue(hh.copy is HandasiHazemanCalendar)
        // Chametz over dawn -> Rabbenu Tam by degrees
        assertTrue(hh.sofZmanBiurChametzMGA!!.isAfter(hh.sofZmanTfilaMGA72MinutesZmanis))
        // ROYZmanim's own calendar is untouched
        val zy = ROZmanimCalendar(geo, null).apply { localDate = LocalDate.of(2026, 3, 20) }
        assertTrue(zy.tzeit != hh.tzeit)
    }
}
