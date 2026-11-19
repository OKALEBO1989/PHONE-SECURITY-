package com.okaleb.phoneguard

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.Size
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.util.Date

@SuppressLint("MissingPermission")
class PhotoService : Service() {

    companion object {
        const val CHANNEL = "phoneguard_photo"
        @Volatile var busy = false
    }

    private val main = Handler(Looper.getMainLooper())
    private var thread: HandlerThread? = null
    private var camera: CameraDevice? = null
    private var reader: ImageReader? = null
    private var frames = 0
    private var done = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (busy) return START_NOT_STICKY
        busy = true
        try {
            val nm = getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Security", NotificationManager.IMPORTANCE_LOW)
                )
            }
            val n: Notification = NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentTitle("PHONE SECURITY")
                .setContentText("Security check")
                .build()
            ServiceCompat.startForeground(
                this, 3, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
        } catch (e: Exception) {
            finishWith(null)
            return START_NOT_STICKY
        }

        val ok = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (!ok) { finishWith(null); return START_NOT_STICKY }

        main.postDelayed({ if (!done) finishWith(null) }, 9000)
        try { openCamera() } catch (e: Exception) { finishWith(null) }
        return START_NOT_STICKY
    }

    private fun openCamera() {
        val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        } ?: cm.cameraIdList.firstOrNull()
        if (id == null) { finishWith(null); return }

        val chars = cm.getCameraCharacteristics(id)
        val sensor = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 270
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val size = map?.getOutputSizes(ImageFormat.JPEG)
            ?.filter { it.width <= 1600 }
            ?.maxByOrNull { it.width * it.height } ?: Size(640, 480)

        thread = HandlerThread("cam").also { it.start() }
        val h = Handler(thread!!.looper)

        val r = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 2)
        reader = r
        r.setOnImageAvailableListener({ rd ->
            val img = rd.acquireLatestImage() ?: return@setOnImageAvailableListener
            frames++
            if (frames < 8 || done) { img.close(); return@setOnImageAvailableListener }
            val buf = img.planes[0].buffer
            val bytes = ByteArray(buf.remaining())
            buf.get(bytes)
            img.close()
            val f = File(cacheDir, "intruder_${System.currentTimeMillis()}.jpg")
            f.writeBytes(bytes)
            main.post { finishWith(f) }
        }, h)

        cm.openCamera(id, object : CameraDevice.StateCallback() {
            override fun onOpened(cd: CameraDevice) {
                camera = cd
                try {
                    val req = cd.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(r.surface)
                        set(CaptureRequest.JPEG_ORIENTATION, sensor)
                    }
                    @Suppress("DEPRECATION")
                    cd.createCaptureSession(listOf(r.surface),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(s: CameraCaptureSession) {
                                try { s.setRepeatingRequest(req.build(), null, h) }
                                catch (e: Exception) { main.post { finishWith(null) } }
                            }
                            override fun onConfigureFailed(s: CameraCaptureSession) {
                                main.post { finishWith(null) }
                            }
                        }, h)
                } catch (e: Exception) { main.post { finishWith(null) } }
            }
            override fun onDisconnected(cd: CameraDevice) { cd.close(); main.post { finishWith(null) } }
            override fun onError(cd: CameraDevice, error: Int) { cd.close(); main.post { finishWith(null) } }
        }, h)
    }

    private fun finishWith(photo: File?) {
        if (done) return
        done = true
        try { camera?.close() } catch (_: Exception) {}
        Locator.getLocation(this) { loc ->
            val where = if (loc != null)
                "https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
            else "Location not available"
            val note = if (photo != null) "Photo attached." else "The camera could not take a photo."
            Mailer.send(
                this,
                "PHONE SECURITY: WRONG PIN",
                "Someone entered a wrong PIN.\nTime: ${Date()}\n$note\nLocation: $where",
                photo
            ) {
                photo?.delete()
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        busy = false
        main.removeCallbacksAndMessages(null)
        try { camera?.close() } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        thread?.quitSafely()
        super.onDestroy()
    }
}
