package at.beifahrer.pacenote

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Erkennt Bodenwellen und Schläge über den Beschleunigungssensor.
 * Die Richtung "nach oben" kommt vom Schwerkraft-Sensor, daher ist egal, wie das Handy in der Halterung steckt.
 * Bodenwellen stehen in keiner Karte – so lernt die App sie beim Fahren.
 */
class BumpDetector(context: Context, private val onBump: (Float) -> Unit) : SensorEventListener {

    /** Wird vom Controller laufend gesetzt (m/s). Unter 20 km/h wird nichts erkannt (Parkplatz, Schwellen). */
    @Volatile var speedMs: Float = 0f

    private val sm = context.getSystemService(SensorManager::class.java)
    private val gravity = FloatArray(3)
    private var hasGravity = false
    private var lastBump = 0L

    fun start() {
        sm.getDefaultSensor(Sensor.TYPE_GRAVITY)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() = sm.unregisterListener(this)

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_GRAVITY -> { System.arraycopy(e.values, 0, gravity, 0, 3); hasGravity = true }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                if (!hasGravity || speedMs < 5.5f) return
                val g = sqrt(gravity[0] * gravity[0] + gravity[1] * gravity[1] + gravity[2] * gravity[2])
                if (g < 1f) return
                val vertical = (e.values[0] * gravity[0] + e.values[1] * gravity[1] + e.values[2] * gravity[2]) / g
                val now = SystemClock.elapsedRealtime()
                if (abs(vertical) > THRESHOLD && now - lastBump > 3000) {
                    lastBump = now
                    onBump(vertical)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        /** m/s² senkrecht. Normale Straße: < 2, deutliche Welle: 4–6. Bei Bedarf anpassen. */
        const val THRESHOLD = 4.5f
    }
}
