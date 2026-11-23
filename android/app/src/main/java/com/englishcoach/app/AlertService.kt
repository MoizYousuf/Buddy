package com.englishcoach.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Keeps one long-poll open to the Mac (GET /events) and turns each event into an Android notification:
 * video finished, agent finished, permission needed, storage low. Runs while the phone is paired.
 */
class AlertService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        channels(this)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, QUIET)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle("Connected to your Mac")
            .setContentText("You'll get alerts for videos, the agent and storage")
            .setContentIntent(open).setOngoing(true).build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        scope.launch { loop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private suspend fun loop() {
        val prefs = getSharedPreferences("coach", 0)
        var after = prefs.getInt("alertAfter", -1)
        var useLan = true
        while (scope.isActive) {
            val token = prefs.getString("macToken", null) ?: run { stopSelf(); return }
            val lan = prefs.getString("lanUrl", null)
            val server = prefs.getString("server", CoachViewModel.DEFAULT_SERVER)!!
            val url = if (useLan) (lan ?: CoachViewModel.HOME_URL ?: server) else server.ifBlank { lan ?: "" }
            val r = runCatching { CoachApi({ url }, { token }).events(maxOf(after, 0)) }.getOrNull()
            if (r == null) { useLan = !useLan; delay(if (useLan) 15_000 else 2_000); continue }
            val events = r.optJSONArray("events")
            if (after < 0) after = r.optInt("last")  // first run: don't replay old alerts
            else if (events != null) for (i in 0 until events.length()) show(events.getJSONObject(i))
            after = maxOf(after, r.optInt("last"))
            prefs.edit().putInt("alertAfter", after).apply()
        }
    }

    private fun show(e: JSONObject) {
        val id = e.optInt("id")
        val open = Intent(this, MainActivity::class.java).putExtra("screen", e.optString("screen"))
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val b = Notification.Builder(this, ALERTS)
            .setSmallIcon(if (e.optString("kind") == "permission") android.R.drawable.ic_dialog_alert else android.R.drawable.ic_dialog_info)
            .setContentTitle(e.optString("title"))
            .setContentText(e.optString("body"))
            .setStyle(Notification.BigTextStyle().bigText(e.optString("body")))
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(this, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        e.optJSONObject("action")?.let { a ->
            val tap = Intent(this, AlertAction::class.java).putExtra("id", id).putExtra("path", a.optString("path"))
                .putExtra("body", a.optJSONObject("body")?.toString() ?: "{}")
            b.addAction(Notification.Action.Builder(null, a.optString("label"),
                PendingIntent.getBroadcast(this, id, tap, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)).build())
        }
        getSystemService(NotificationManager::class.java).notify(100 + id % 10_000, b.build())
    }

    companion object {
        const val ALERTS = "mac_alerts"
        const val QUIET = "mac_link"

        fun channels(c: Context) {
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(ALERTS, "Mac alerts", NotificationManager.IMPORTANCE_HIGH))
            nm.createNotificationChannel(NotificationChannel(QUIET, "Mac connection", NotificationManager.IMPORTANCE_MIN))
        }

        fun start(c: Context) = runCatching { c.startForegroundService(Intent(c, AlertService::class.java)) }
        fun stop(c: Context) = c.stopService(Intent(c, AlertService::class.java))
    }
}

/** A notification button (e.g. "Allow & continue"): sends the signed request to the Mac, then updates the alert. */
class AlertAction : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val pending = goAsync()
        val prefs = c.getSharedPreferences("coach", 0)
        CoroutineScope(Dispatchers.IO).launch {
            val token = prefs.getString("macToken", null)
            val urls = listOfNotNull(prefs.getString("lanUrl", null), prefs.getString("server", CoachViewModel.DEFAULT_SERVER))
            var ok = false
            for (u in urls) {
                val r = runCatching { CoachApi({ u }, { token }).send(i.getStringExtra("path")!!, JSONObject(i.getStringExtra("body")!!)) }.getOrNull()
                if (r != null) { ok = !r.has("error"); break }
            }
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.notify(100 + i.getIntExtra("id", 0) % 10_000, Notification.Builder(c, AlertService.ALERTS)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(if (ok) "Allowed. Claude is continuing" else "Couldn't reach the Mac").setAutoCancel(true).build())
            pending.finish()
        }
    }
}

/** Starts the alert link again after the phone restarts (only when paired). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (c.getSharedPreferences("coach", 0).getString("macToken", null) != null) AlertService.start(c)
        Reminders.schedule(c)
    }
}
