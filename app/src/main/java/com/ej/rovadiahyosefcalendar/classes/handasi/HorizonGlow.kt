package com.ej.rovadiahyosefcalendar.classes.handasi

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

/**
 * Dawn and Rabbenu Tam's nightfall by the light on the horizon (port of the website's horizon-glow.js, used only by Handasi
 * HaZeman): the moment the glow over the Sun's side of the horizon is as bright as it is in Jerusalem 72 minutes before
 * sunrise (the Sun 16.04 degrees down at the equinox) under Jerusalem's average sky.
 *
 * The glow is sunlight scattered by the air along the line of sight toward the Sun's azimuth, from a single-scattering model
 * whose fall-off with depression is scaled to the measured twilight table (between 12 and 15 degrees down), at its brightest
 * point on the Sun's vertical. The sunlit air that makes it is tens of km up, so the local air only dims it on its way to the
 * eye: Rayleigh by the station pressure, and haze (aerosol optical depth, 550 nm) scaled by 1 - w g^2 = 0.55 for the forward-
 * scattered part that stays in the glow. A fixed luminance is the criterion: a hazier or lower place sees it with the Sun
 * higher. EXPERIMENTAL (see the website file): callers keep the shift within the haze cap.
 */
object HorizonGlow {
    private const val GLOW_FALLOFF = 0.7241
    private val GLOW_ALT = doubleArrayOf(0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 5.0, 6.0, 7.0, 8.0, 10.0, 12.0)
    private val GLOW_DEP = doubleArrayOf(10.0, 10.5, 11.0, 11.5, 12.0, 12.5, 13.0, 13.5, 14.0, 14.5, 15.0, 15.5, 16.0, 16.5, 17.0, 17.5, 18.0, 18.5, 19.0, 19.5, 20.0, 20.5, 21.0, 21.5, 22.0)
    /** log10 glow (relative), [altitude][depression]; sea-level Rayleigh + ozone included */
    private val GLOW_LOG = arrayOf(
        doubleArrayOf(-5.09, -5.201, -5.315, -5.435, -5.56, -5.69, -5.824, -5.964, -6.109, -6.258, -6.413, -6.573, -6.738, -6.908, -7.082, -7.262, -7.447, -7.638, -7.833, -8.035, -8.242, -8.456, -8.679, -8.917, -9.18),
        doubleArrayOf(-5.052, -5.167, -5.287, -5.411, -5.541, -5.675, -5.815, -5.959, -6.109, -6.263, -6.423, -6.588, -6.757, -6.932, -7.112, -7.297, -7.487, -7.683, -7.884, -8.091, -8.305, -8.528, -8.764, -9.025, -9.344),
        doubleArrayOf(-5.053, -5.172, -5.296, -5.425, -5.56, -5.699, -5.843, -5.992, -6.147, -6.306, -6.471, -6.64, -6.815, -6.994, -7.179, -7.369, -7.565, -7.765, -7.972, -8.186, -8.408, -8.643, -8.901, -9.211, -9.655),
        doubleArrayOf(-5.08, -5.203, -5.332, -5.466, -5.605, -5.748, -5.897, -6.051, -6.21, -6.375, -6.544, -6.718, -6.898, -7.082, -7.272, -7.467, -7.668, -7.874, -8.087, -8.309, -8.542, -8.796, -9.096, -9.516, -10.071),
        doubleArrayOf(-5.125, -5.253, -5.386, -5.524, -5.667, -5.816, -5.969, -6.128, -6.292, -6.461, -6.635, -6.814, -6.998, -7.187, -7.382, -7.582, -7.788, -8.001, -8.221, -8.453, -8.703, -8.992, -9.383, -9.921, -10.76),
        doubleArrayOf(-5.182, -5.315, -5.452, -5.595, -5.742, -5.895, -6.053, -6.217, -6.385, -6.559, -6.737, -6.921, -7.11, -7.305, -7.504, -7.71, -7.922, -8.141, -8.37, -8.616, -8.894, -9.255, -9.77, -10.474, -30.0),
        doubleArrayOf(-5.249, -5.385, -5.527, -5.674, -5.826, -5.983, -6.146, -6.314, -6.487, -6.665, -6.848, -7.037, -7.231, -7.43, -7.635, -7.846, -8.064, -8.292, -8.533, -8.801, -9.133, -9.614, -10.228, -11.804, -30.0),
        doubleArrayOf(-5.321, -5.462, -5.607, -5.759, -5.915, -6.077, -6.244, -6.416, -6.594, -6.777, -6.965, -7.158, -7.357, -7.561, -7.771, -7.988, -8.214, -8.451, -8.711, -9.018, -9.452, -10.016, -11.105, -30.0, -30.0),
        doubleArrayOf(-5.476, -5.625, -5.779, -5.939, -6.104, -6.274, -6.45, -6.631, -6.817, -7.009, -7.206, -7.409, -7.618, -7.832, -8.054, -8.285, -8.531, -8.803, -9.141, -9.629, -10.254, -11.922, -30.0, -30.0, -30.0),
        doubleArrayOf(-5.637, -5.794, -5.956, -6.124, -6.297, -6.476, -6.66, -6.85, -7.045, -7.246, -7.452, -7.665, -7.883, -8.109, -8.345, -8.597, -8.88, -9.243, -9.758, -10.457, -30.0, -30.0, -30.0, -30.0, -30.0),
        doubleArrayOf(-5.799, -5.963, -6.133, -6.309, -6.49, -6.677, -6.87, -7.068, -7.272, -7.481, -7.697, -7.919, -8.149, -8.389, -8.645, -8.934, -9.31, -9.835, -10.576, -30.0, -30.0, -30.0, -30.0, -30.0, -30.0),
        doubleArrayOf(-5.958, -6.129, -6.307, -6.49, -6.679, -6.874, -7.075, -7.281, -7.494, -7.712, -7.937, -8.17, -8.413, -8.672, -8.962, -9.336, -9.86, -10.588, -30.0, -30.0, -30.0, -30.0, -30.0, -30.0, -30.0),
        doubleArrayOf(-6.264, -6.449, -6.64, -6.838, -7.042, -7.252, -7.468, -7.691, -7.92, -8.157, -8.403, -8.662, -8.945, -9.286, -9.768, -10.381, -11.875, -30.0, -30.0, -30.0, -30.0, -30.0, -30.0, -30.0, -30.0),
        doubleArrayOf(-6.55, -6.748, -6.953, -7.164, -7.382, -7.607, -7.838, -8.076, -8.323, -8.579, -8.852, -9.156, -9.544, -10.078, -10.837, -30.0, -30.0, -30.0, -30.0, -30.0, -30.0, -30.0, -30.0, -30.0, -30.0)
    )
    /** airmass of the air (H 8 km) and of the haze (H 1.5 km) at each GLOW_ALT */
    private val AIRMASS_AIR = doubleArrayOf(29.382, 24.866, 21.393, 18.666, 16.487, 14.718, 13.261, 12.044, 10.14, 8.729, 7.648, 6.797, 5.551, 4.686)
    private val AIRMASS_HAZE = doubleArrayOf(54.694, 39.836, 30.811, 24.894, 20.777, 17.774, 15.501, 13.727, 11.149, 9.375, 8.083, 7.103, 5.716, 4.785)

    /** Rayleigh optical depth at 550 nm, 1013.25 hPa (Bucholtz 1995) */
    const val RAYLEIGH_TAU_550 = 0.0973
    /** the part of the haze's extinction that leaves the glow (1 - w g^2) */
    const val HAZE_GLOW_EXTINCTION = 1 - 0.92 * 0.7 * 0.7

    /** Jerusalem (the Old City) and its average air: height, and the mean temperature that gives its average station pressure. */
    const val JERUSALEM_LATITUDE = 31.7780
    const val JERUSALEM_LONGITUDE = 35.2354
    const val JERUSALEM_ELEVATION_M = 754.0
    const val JERUSALEM_MEAN_TEMP_C = 17.5

    private val LOG10E = 1 / ln(10.0)

    /** Station pressure (hPa) at a height from a sea-level pressure and the air's temperature (default the standard atmosphere's). */
    @JvmStatic
    @JvmOverloads
    fun stationPressure(elevationM: Double, tempC: Double = Double.NaN, seaLevelMb: Double = 1013.25): Double {
        val h = max(0.0, elevationM)
        val tStation = (if (tempC.isFinite()) tempC else 15 - 0.0065 * h) + 273.15
        val tMean = tStation + 0.0065 * h / 2
        return seaLevelMb * exp(-9.80665 * h / (287.05 * tMean))
    }

    /** Pressure (hPa) at heightTo from pressureMb at heightFrom (hypsometric, temperature tempC at heightFrom). */
    @JvmStatic
    @JvmOverloads
    fun pressureAt(pressureMb: Double, heightFrom: Double, heightTo: Double, tempC: Double = 15.0): Double {
        val dh = heightTo - heightFrom
        val tMean = tempC + 273.15 - 0.0065 * dh / 2
        return pressureMb * exp(-9.80665 * dh / (287.05 * tMean))
    }

    /** aod: aerosol optical depth at 550 nm; pressureMb: station pressure, hPa. */
    data class GlowAir(val aod: Double, val pressureMb: Double)

    private fun pos(ax: DoubleArray, x: Double): Pair<Int, Double> {
        if (x <= ax[0]) return Pair(0, 0.0)
        if (x >= ax[ax.size - 1]) return Pair(ax.size - 2, 1.0)
        var i = 0
        while (x > ax[i + 1]) i++
        return Pair(i, (x - ax[i]) / (ax[i + 1] - ax[i]))
    }

    /** The glow at its brightest point on the Sun's vertical, log10 (relative), as it reaches the eye; and that altitude. */
    @JvmStatic
    fun glowAt(depDeg: Double, air: GlowAir): Pair<Double, Double> {
        val (j, fj) = pos(GLOW_DEP, depDeg)
        val dTauAir = RAYLEIGH_TAU_550 * (air.pressureMb / 1013.25 - 1)
        val tauHaze = HAZE_GLOW_EXTINCTION * max(0.0, air.aod)
        var best = Double.NEGATIVE_INFINITY
        var bestAlt = Double.NaN
        for (i in GLOW_ALT.indices) {
            val lg = (1 - fj) * GLOW_LOG[i][j] + fj * GLOW_LOG[i][j + 1]
            val v = lg - LOG10E * (dTauAir * AIRMASS_AIR[i] + tauHaze * AIRMASS_HAZE[i])
            if (v > best) { best = v; bestAlt = GLOW_ALT[i] }
        }
        return Pair(best, bestAlt)
    }

    /** Jerusalem's average air: its average haze at its average station pressure. */
    @JvmStatic
    fun jerusalemAir(aodAvg: Double) = GlowAir(aodAvg, stationPressure(JERUSALEM_ELEVATION_M, JERUSALEM_MEAN_TEMP_C))

    /** The Sun's depression (degrees) at which the glow under air is as bright as under refAir with the Sun refDepDeg down (10-22). */
    @JvmStatic
    fun glowDepression(air: GlowAir, refAir: GlowAir, refDepDeg: Double): Double {
        val target = glowAt(refDepDeg, refAir).first
        var lo = GLOW_DEP[0]
        var hi = GLOW_DEP[GLOW_DEP.size - 1]
        if (glowAt(lo, air).first <= target) return lo
        if (glowAt(hi, air).first >= target) return hi
        for (k in 0 until 40) {
            val mid = (lo + hi) / 2
            if (glowAt(mid, air).first > target) lo = mid else hi = mid
        }
        return (lo + hi) / 2
    }

    @Suppress("unused") private const val FALLOFF_NOTE = GLOW_FALLOFF
}
