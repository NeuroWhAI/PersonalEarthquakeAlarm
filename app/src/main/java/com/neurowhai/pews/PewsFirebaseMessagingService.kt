package com.neurowhai.pews

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.location.Location
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater
import kotlin.math.round


class PewsFirebaseMessagingService : FirebaseMessagingService() {

    private val topicName = "eqk"
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate() {
        super.onCreate()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        FirebaseMessaging.getInstance().subscribeToTopic(topicName)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "구독 성공")
                }
                else {
                    Log.d(TAG, "구독 실패")
                }
            }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")

        if (remoteMessage.from == "/topics/$topicName" && remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: " + remoteMessage.data)

            val data = remoteMessage.data
            val msg = data["msg"] ?: ""
            val scale = data["scale"] ?: ""
            val mmi = data["mmi"] ?: ""

            val defaultMsg = "규모 $scale 최대진도 ${mmi}의 지진 발생."

            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { loc : Location? ->
                        if (loc != null) {
                            var intensity = getIntensity(data["grid"] ?: "",
                                loc.latitude, loc.longitude)
                            val intensityStr = if (intensity < 0) {
                                intensity = 0
                                "불명"
                            }
                            else {
                                intensity.toString()
                            }
                            val locationMsg = "$defaultMsg 내 위치 예상진도 ${intensityStr}."
                            sendNotification(msg, locationMsg, intensity)
                        }
                        else {
                            sendNotification(msg, defaultMsg, 0)
                        }
                    }
                    .addOnFailureListener {
                        sendNotification(msg, defaultMsg, 0)
                    }
                    .addOnCanceledListener {
                        sendNotification(msg, defaultMsg, 0)
                    }
            }
            else {
                sendNotification(msg, defaultMsg, 0)
            }
        }
    }

    private fun sendNotification(title: String, messageBody: String, mmi: Int) {
        var mmiRanged = mmi
        if (mmiRanged < 0) {
            mmiRanged = 0
        }
        else if (mmiRanged >= MmiColors.size) {
            mmiRanged = MmiColors.size - 1
        }

        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xff000000.toInt() or MmiColors[mmiRanged])
        val pnt = Paint()
        pnt.color = if (mmiRanged <= 5) {
            Color.BLACK
        }
        else {
            Color.WHITE
        }
        pnt.textSize = 64.0f
        pnt.textAlign = Paint.Align.CENTER
        canvas.drawText(MmiChar[mmiRanged], 64.0f, 64.0f - (pnt.descent() + pnt.ascent()) / 2, pnt)

        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT)

        val channelId = "pews_eqk"
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setLargeIcon(bitmap)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("${messageBody}\n내 위치에서 ${MmiDescription[mmiRanged]}"))
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId,
                "지진 정보",
                NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(0, notificationBuilder.build())
    }

    private fun getIntensity(gridData: String, latitude: Double, longitude: Double): Int {
        if (gridData.isEmpty()) {
            return -1
        }

        val compressedBytes = ByteArray(gridData.length / 2) {
            gridData.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }

        val outputStream = ByteArrayOutputStream(gridData.length)
        val decompresser = Inflater()
        decompresser.setInput(compressedBytes, 0, compressedBytes.size)
        val buffer = ByteArray(1024)
        while (!decompresser.finished()) {
            val count = decompresser.inflate(buffer)
            outputStream.write(buffer, 0, count)
        }
        decompresser.end()
        val gridBytes = outputStream.toByteArray()

        val sizeX = ((132.05 - 124.5) / 0.05).toInt()
        val y = round((38.85 - latitude) / 0.05).toInt()
        val x = round((longitude - 124.5) / 0.05).toInt()
        val index: Int = y * sizeX + x

        return if (y >= 0 && x >= 0 && x < sizeX
            && index >= 0 && index / 2 < gridBytes.size
        ) {
            if (index % 2 == 0) {
                gridBytes[index / 2] / 16
            }
            else {
                gridBytes[index / 2] % 16
            }
        }
        else {
            -1
        }
    }

    companion object {
        private const val TAG = "PewsFCMService"
        private val MmiColors: Array<Int> = arrayOf(0xffffff, 0xffffff, 0xa0e6ff, 0x92d050, 0xffff00, 0xffc000,
            0xff0000, 0xa32777, 0x632523, 0x4c2600, 0x000000)
        private val MmiChar: Array<String> = arrayOf("?", "Ⅰ", "Ⅱ", "Ⅲ", "Ⅳ", "Ⅴ", "Ⅵ", "Ⅶ", "Ⅷ", "Ⅸ", "Ⅹ")
        private val MmiDescription: Array<String> = arrayOf(
            "진도 Ⅰ 미만 혹은 불명.",
            "대부분 사람들은 느낄 수 없으나, 지진계에는 기록된다.",
            "조용한 상태나 건물 위층에 있는 소수의 사람만 느낀다.",
            "실내, 특히 건물 위층에 있는 사람이 현저하게 느끼며, 정지하고 있는 차가 약간 흔들린다.",
            "실내에서 많은 사람이 느끼고, 밤에는 잠에서 깨기도 하며, 그릇과 창문 등이 흔들린다.",
            "거의 모든 사람이 진동을 느끼고, 그릇, 창문 등이 깨지기도 하며, 불안정한 물체는 넘어진다.",
            "모든 사람이 느끼고, 일부 무거운 가구가 움직이며, 벽의 석회가 떨어지기도 한다.",
            "일반 건물에 약간의 피해가 발생하며, 부실한 건물에는 상당한 피해가 발생한다.",
            "일반 건물에 부분적 붕괴 등 상당한 피해가 발생하며, 부실한 건물에는 심각한 피해가 발생한다.",
            "잘 설계된 건물에도 상당한 피해가 발생하며, 일반 건축물에는 붕괴 등 큰 피해가 발생한다.",
            "대부분의 석조 및 골조 건물이 파괴되고, 기차선로가 휘어진다."
        )
    }
}
