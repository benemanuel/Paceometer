package com.paceometer.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var paceometerView: PaceometerView
    private lateinit var unitToggle: RadioGroup
    private lateinit var statusText: TextView
    private lateinit var paceText: TextView
    private lateinit var savingsText: TextView
    private lateinit var insightText: TextView

    private var isTracking = false
    private var useMetric = false // false = miles, true = km

    // Conversion factors from m/s
    private val MS_TO_MPH = 2.23694
    private val MS_TO_KPH = 3.6

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        
        if (fineGranted || coarseGranted) {
            startLocationUpdates()
        } else {
            statusText.text = "⚠️ Location permission denied"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views
        paceometerView = findViewById(R.id.paceometerView)
        unitToggle = findViewById(R.id.unitToggle)
        statusText = findViewById(R.id.statusText)
        paceText = findViewById(R.id.paceText)
        savingsText = findViewById(R.id.savingsText)
        insightText = findViewById(R.id.insightText)

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Set up location callback
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    val speedMs = location.speed // meters per second
                    if (location.hasSpeed() && speedMs >= 0) {
                        updateSpeed(speedMs)
                    } else {
                        updateSpeed(0f)
                    }
                }
            }
        }

        // Unit toggle listener
        unitToggle.setOnCheckedChangeListener { _, checkedId ->
            useMetric = checkedId == R.id.radioKm
            paceometerView.setMetric(useMetric)
            // Re-update display with current speed
            paceometerView.invalidate()
            updateInfoPanel(paceometerView.getCurrentSpeed())
        }

        // Start tracking on launch
        checkPermissionsAndStart()
    }

    private fun checkPermissionsAndStart() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                startLocationUpdates()
            }
            else -> {
                requestPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L // Update every 1 second
        ).apply {
            setMinUpdateIntervalMillis(500L)
            setWaitForAccurateLocation(false)
        }.build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            isTracking = true
            statusText.text = "🛰️ GPS Active"
            statusText.setTextColor(ContextCompat.getColor(this, R.color.gps_active))
        } catch (e: SecurityException) {
            statusText.text = "⚠️ Location permission required"
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        isTracking = false
        statusText.text = "GPS Stopped"
    }

    private fun updateSpeed(speedMs: Float) {
        val displaySpeed = if (useMetric) {
            speedMs * MS_TO_KPH
        } else {
            speedMs * MS_TO_MPH
        }

        paceometerView.setSpeed(displaySpeed.toFloat())
        updateInfoPanel(displaySpeed.toFloat())
    }

    private fun updateInfoPanel(speed: Float) {
        val unitLabel = if (useMetric) "km" else "miles"
        val speedLabel = if (useMetric) "km/h" else "mph"
        val maxSpeed = if (useMetric) 200f else 130f

        if (speed > 0.5f) {
            val pace = 600f / speed // minutes per 10 units
            paceText.text = String.format("%.1f min per 10 %s", pace, unitLabel)

            // Calculate savings for +10 speed
            val newSpeed = min(speed + 10f, maxSpeed)
            val newPace = 600f / newSpeed
            val savings = pace - newPace
            savingsText.text = String.format("+10 %s saves: %.1f min", speedLabel, savings)

            // Insight based on speed
            insightText.text = when {
                speed <= 40 -> "⬆️ Big time gains at low speeds"
                speed <= 70 -> "➡️ Moderate time savings"
                else -> "⬇️ Diminishing returns at high speeds"
            }
        } else {
            paceText.text = "— min per 10 $unitLabel"
            savingsText.text = "Start moving to see data"
            insightText.text = "GPS speed reads 0 when stationary"
        }
    }

    override fun onResume() {
        super.onResume()
        if (isTracking) {
            checkPermissionsAndStart()
        }
    }

    override fun onPause() {
        super.onPause()
        // Keep tracking in background for short periods
        // For a production app, you'd use a foreground service
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
    }
}

/**
 * Custom View that draws the Paceometer gauge
 */
class PaceometerView @JvmOverloads constructor(
    context: android.content.Context,
    attrs: android.util.AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentSpeed = 0f
    private var isMetric = false

    // Paints
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0f172a")
        style = Paint.Style.FILL
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1e293b")
        style = Paint.Style.STROKE
        strokeWidth = 35f
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#e2e8f0")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val speedTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#e2e8f0")
        textSize = 36f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val paceTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#fb923c")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val paceTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#fb923c")
        textSize = 28f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94a3b8")
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    private val currentSpeedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 72f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#ef4444")
        style = Paint.Style.FILL
    }

    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1e293b")
        style = Paint.Style.FILL
    }

    fun setSpeed(speed: Float) {
        currentSpeed = speed
        invalidate()
    }

    fun getCurrentSpeed() = currentSpeed

    fun setMetric(metric: Boolean) {
        isMetric = metric
        invalidate()
    }

    private fun getMaxSpeed() = if (isMetric) 200f else 130f
    private fun getSpeedStep() = if (isMetric) 20 else 10
    private fun getSpeedLabel() = if (isMetric) "km/h" else "mph"
    private fun getUnitLabel() = if (isMetric) "Kilometers per hour" else "Miles per hour"
    private fun getPaceLabel() = if (isMetric) "Minutes per 10 km" else "Minutes per 10 miles"

    private fun getPaceTicks(): List<Pair<Int, Float>> {
        return if (isMetric) {
            listOf(20 to 30f, 40 to 15f, 60 to 10f, 80 to 7.5f, 100 to 6f, 
                   120 to 5f, 150 to 4f, 180 to 3.3f)
        } else {
            listOf(10 to 60f, 20 to 30f, 30 to 20f, 40 to 15f, 50 to 12f,
                   60 to 10f, 70 to 8.6f, 80 to 7.5f, 100 to 6f, 120 to 5f)
        }
    }

    private fun speedToAngle(speed: Float): Float {
        val ratio = speed / getMaxSpeed()
        return -135f + (ratio * 270f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(centerX, centerY) * 0.9f

        // Scale factor for drawing
        val scale = radius / 190f

        // Background circle
        canvas.drawCircle(centerX, centerY, radius, backgroundPaint)

        // Outer ring
        ringPaint.strokeWidth = 35f * scale
        canvas.drawCircle(centerX, centerY, radius * 0.92f, ringPaint)

        // Draw speed ticks
        val maxSpeed = getMaxSpeed().toInt()
        val step = getSpeedStep()
        for (speed in 0..maxSpeed step step) {
            val angle = speedToAngle(speed.toFloat())
            val rad = Math.toRadians((angle - 90).toDouble())

            val innerR = radius * 0.63f
            val outerR = radius * 0.73f
            val labelR = radius * 0.52f

            val x1 = centerX + innerR * cos(rad).toFloat()
            val y1 = centerY + innerR * sin(rad).toFloat()
            val x2 = centerX + outerR * cos(rad).toFloat()
            val y2 = centerY + outerR * sin(rad).toFloat()

            tickPaint.strokeWidth = 3f * scale
            canvas.drawLine(x1, y1, x2, y2, tickPaint)

            val lx = centerX + labelR * cos(rad).toFloat()
            val ly = centerY + labelR * sin(rad).toFloat()
            speedTextPaint.textSize = 36f * scale
            canvas.drawText(speed.toString(), lx, ly + speedTextPaint.textSize / 3, speedTextPaint)
        }

        // Draw pace ticks (orange, outer)
        for ((speed, pace) in getPaceTicks()) {
            val angle = speedToAngle(speed.toFloat())
            val rad = Math.toRadians((angle - 90).toDouble())

            val innerR = radius * 0.83f
            val outerR = radius * 0.94f
            val labelR = radius * 0.76f

            val x1 = centerX + innerR * cos(rad).toFloat()
            val y1 = centerY + innerR * sin(rad).toFloat()
            val x2 = centerX + outerR * cos(rad).toFloat()
            val y2 = centerY + outerR * sin(rad).toFloat()

            paceTickPaint.strokeWidth = 4f * scale
            canvas.drawLine(x1, y1, x2, y2, paceTickPaint)

            val lx = centerX + labelR * cos(rad).toFloat()
            val ly = centerY + labelR * sin(rad).toFloat()
            paceTextPaint.textSize = 28f * scale
            val paceStr = if (pace % 1 == 0f) pace.toInt().toString() else String.format("%.1f", pace)
            canvas.drawText(paceStr, lx, ly + paceTextPaint.textSize / 3, paceTextPaint)
        }

        // Center labels
        labelPaint.textSize = 28f * scale
        canvas.drawText(getUnitLabel(), centerX, centerY - radius * 0.22f, labelPaint)
        
        paceTextPaint.textSize = 26f * scale
        canvas.drawText(getPaceLabel(), centerX, centerY + radius * 0.18f, paceTextPaint)

        // Current speed display
        currentSpeedPaint.textSize = 72f * scale
        canvas.drawText(currentSpeed.toInt().toString(), centerX, centerY + radius * 0.38f, currentSpeedPaint)
        
        labelPaint.textSize = 26f * scale
        canvas.drawText(getSpeedLabel(), centerX, centerY + radius * 0.48f, labelPaint)

        // Draw needle
        val needleAngle = speedToAngle(min(currentSpeed, getMaxSpeed()))
        canvas.save()
        canvas.rotate(needleAngle, centerX, centerY)

        val needlePath = Path().apply {
            moveTo(centerX, centerY - radius * 0.62f)  // tip
            lineTo(centerX - 6f * scale, centerY)       // left base
            lineTo(centerX, centerY + 12f * scale)      // bottom
            lineTo(centerX + 6f * scale, centerY)       // right base
            close()
        }
        canvas.drawPath(needlePath, needlePaint)
        canvas.restore()

        // Center cap
        canvas.drawCircle(centerX, centerY, 12f * scale, centerPaint)
    }
}
