package com.englishcoach.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

/** Daily "keep your streak" reminder (only if you haven't practised yet today) and the Sunday weekly report. */
object Reminders {
    private const val CH = "practice"

    fun prefs(c: Context) = c.getSharedPreferences("coach", 0)
    fun on(c: Context) = prefs(c).getBoolean("remindOn", true)
    fun time(c: Context) = prefs(c).getInt("remindHour", 20) to prefs(c).getInt("remindMin", 0)

    fun set(c: Context, on: Boolean, hour: Int, min: Int) {
        prefs(c).edit().putBoolean("remindOn", on).putInt("remindHour", hour).putInt("remindMin", min).apply()
        schedule(c)
    }

    /** (Re)schedules both alarms; inexact so the phone can batch them and save battery. */
    fun schedule(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java)
        val daily = pi(c, "daily"); val weekly = pi(c, "weekly")
        am.cancel(daily); am.cancel(weekly)
        if (!on(c)) return
        val (h, m) = time(c)
        val d = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1) }
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, d.timeInMillis, AlarmManager.INTERVAL_DAY, daily)
        val w = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY); set(Calendar.HOUR_OF_DAY, 10); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); if (timeInMillis <= System.currentTimeMillis()) add(Calendar.WEEK_OF_YEAR, 1) }
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, w.timeInMillis, AlarmManager.INTERVAL_DAY * 7, weekly)
    }

    private fun pi(c: Context, kind: String) = PendingIntent.getBroadcast(c, kind.hashCode(),
        Intent(c, ReminderReceiver::class.java).setAction(kind), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun notify(c: Context, id: Int, title: String, text: String, screen: String) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Practice reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(c, id, Intent(c, MainActivity::class.java).putExtra("screen", screen)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(id, Notification.Builder(c, CH).setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle(title)
            .setContentText(text).setStyle(Notification.BigTextStyle().bigText(text)).setContentIntent(open).setAutoCancel(true).build())
    }

    /** Text of the reminder for today, or null when there is nothing to remind. */
    fun dailyText(store: Store): Pair<String, String>? {
        if (store.practicedToday) return null
        val st = store.streak
        val due = store.dueForReview.size
        return if (st > 0) "🔥 Keep your $st-day streak!" to "5 minutes of English today keeps it going." + if (due > 0) " $due mistakes are ready to review." else ""
        else "🗣 Time for English" to "Just 5 minutes today. Start a new streak!" + if (due > 0) " $due mistakes are ready to review." else ""
    }

    fun weeklyText(store: Store): Pair<String, String> {
        val w = store.week(0); val p = store.week(1)
        val top = w.cats.maxByOrNull { it.value }?.key?.let { CATEGORY_NAMES[it] ?: it }
        return "📈 Your English week" to "${w.days}/7 days · ${w.answers} answers · +${w.xp} XP" +
            (if (p.answers > 0) " (${if (w.answers >= p.answers) "up" else "down"} from ${p.answers})" else "") +
            (top?.let { ". Most repeated mistake: $it." } ?: ". No mistakes this week!")
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val store = Store(c)
        when (i.action) {
            "daily" -> Reminders.dailyText(store)?.let { (t, x) -> Reminders.notify(c, 9001, t, x, "Practice") }
            "weekly" -> Reminders.weeklyText(store).let { (t, x) -> Reminders.notify(c, 9002, t, x, "Weekly") }
        }
    }
}
