package com.kylecorry.andromeda.sense.compass

import android.content.Context
import com.kylecorry.andromeda.core.math.MovingAverageFilter
import com.kylecorry.andromeda.core.math.deltaAngle
import com.kylecorry.andromeda.core.sensors.AbstractSensor
import com.kylecorry.andromeda.core.sensors.Quality
import com.kylecorry.andromeda.core.units.Bearing
import com.kylecorry.andromeda.sense.SensorChecker
import com.kylecorry.andromeda.sense.accelerometer.GravitySensor
import com.kylecorry.andromeda.sense.accelerometer.IAccelerometer
import com.kylecorry.andromeda.sense.accelerometer.LowPassAccelerometer
import com.kylecorry.andromeda.sense.magnetometer.LowPassMagnetometer
import kotlin.math.max
import kotlin.math.min

class GravityCompensatedCompass(context: Context, smoothingFactor: Int, private val useTrueNorth: Boolean) :
    AbstractSensor(), ICompass {

    override val hasValidReading: Boolean
        get() = gotReading
    private var gotReading = false

    override val quality: Quality
        get() = _quality
    private var _quality = Quality.Unknown

    private val sensorChecker = SensorChecker(context)
    private val accelerometer: IAccelerometer =
        if (sensorChecker.hasGravity()) GravitySensor(context) else LowPassAccelerometer(context)
    private val magnetometer = LowPassMagnetometer(context)

    private var filterSize = smoothingFactor * 2 * 2
    private val filter = MovingAverageFilter(max(1, filterSize))

    override var declination = 0f

    override val bearing: Bearing
        get() {
            return if (useTrueNorth) {
                Bearing(_filteredBearing).withDeclination(declination)
            } else {
                Bearing(_filteredBearing)
            }
        }
    override val rawBearing: Float
        get(){
            return if (useTrueNorth) {
               Bearing.getBearing(Bearing.getBearing(_filteredBearing) + declination)
            } else {
                Bearing.getBearing(_filteredBearing)
            }
        }

    private var _bearing = 0f
    private var _filteredBearing = 0f

    private var gotMag = false
    private var gotAccel = false

    private fun updateBearing(newBearing: Float) {
        _bearing += deltaAngle(_bearing, newBearing)
        _filteredBearing = filter.filter(_bearing.toDouble()).toFloat()
    }

    private fun updateSensor(): Boolean {

        if (!gotAccel || !gotMag) {
            return true
        }

        val newBearing =
            AzimuthCalculator.calculate(accelerometer.rawAcceleration, magnetometer.rawMagneticField)
                ?: return true

        val accelAccuracy = accelerometer.quality
        val magAccuracy = magnetometer.quality
        _quality = Quality.values()[min(accelAccuracy.ordinal, magAccuracy.ordinal)]

        updateBearing(newBearing.value)
        gotReading = true
        notifyListeners()
        return true
    }

    private fun updateAccel(): Boolean {
        gotAccel = true
        return updateSensor()
    }

    private fun updateMag(): Boolean {
        gotMag = true
        return updateSensor()
    }

    override fun startImpl() {
        accelerometer.start(this::updateAccel)
        magnetometer.start(this::updateMag)
    }

    override fun stopImpl() {
        accelerometer.stop(this::updateAccel)
        magnetometer.stop(this::updateMag)
    }

}