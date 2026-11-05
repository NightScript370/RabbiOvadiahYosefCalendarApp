package com.ej.rovadiahyosefcalendar

import android.app.Application
import androidx.preference.PreferenceManager
import com.ej.rovadiahyosefcalendar.classes.handasi.HandasiHazemanCalendar
import com.ej.rovadiahyosefcalendar.classes.royzmanim.RefractionRepository
import com.ej.rovadiahyosefcalendar.classes.royzmanim.RefractionSupport

/**
 * Sets up the refraction data store before any calendar is built (activities, widgets, notifications and boot receivers all
 * build ROZmanimCalendar objects, which read the stored weather / terrain / haze data for their place).
 */
class ZmanimApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        RefractionRepository.init(this)
        val settings = PreferenceManager.getDefaultSharedPreferences(this)
        RefractionSupport.enabled = settings.getBoolean(PREF_REFRACTION, true)
        RefractionSupport.hazeTzet = settings.getBoolean(PREF_HAZE_TZET, true)
        // the calendar class in use (switch back to ROZmanimCalendar at its call sites for ROYZmanim alone)
        HandasiHazemanCalendar.init(this)
        settings.registerOnSharedPreferenceChangeListener(settingsListener)
    }

    /** Kept as a field: SharedPreferences holds its listeners weakly. */
    private val settingsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        when (key) {
            PREF_REFRACTION -> RefractionSupport.enabled = prefs.getBoolean(PREF_REFRACTION, true)
            PREF_HAZE_TZET -> RefractionSupport.hazeTzet = prefs.getBoolean(PREF_HAZE_TZET, true)
            HandasiHazemanCalendar.ELEVATION_KEY -> HandasiHazemanCalendar.init(this)
        }
    }

    companion object {
        /** Settings: use weather, terrain and haze data for the zmanim (the website's refraction data). */
        const val PREF_REFRACTION = "useRefractionData"
        /** Settings: move Tzet by degrees by the evening's haze. */
        const val PREF_HAZE_TZET = "useHazeTzet"
    }
}
