package com.englishcoach.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.json.JSONObject

/** "Aaj ka plan" home-screen widget: today's video, Etsy idea, home owes. Saved each time the Home card loads. */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = draw(ctx, mgr, ids)

    companion object {
        fun save(ctx: Context, b: JSONObject) {
            val mo = b.optJSONObject("money")
            val idea = b.optJSONArray("etsy")?.optJSONObject(0)
            ctx.getSharedPreferences("today_widget", 0).edit()
                .putString("video", b.optJSONObject("video")?.optString("title")?.substringBefore("|")?.trim() ?: "Koi video nahi")
                .putString("idea", idea?.let { "🛍 " + it.optString("title") } ?: "🛍 Koi Etsy idea nahi")
                .putString("money", mo?.let { "🏠 PKR %,.0f lene · 👛 PKR %,.0f".format(it.optDouble("home_owes"), it.optDouble("wallet")) } ?: "")
                .putString("waiting", "✅ ${b.optJSONObject("youtube")?.optInt("waiting") ?: 0} videos OK ke liye").apply()
            val mgr = AppWidgetManager.getInstance(ctx)
            draw(ctx, mgr, mgr.getAppWidgetIds(ComponentName(ctx, TodayWidget::class.java)))
        }

        fun draw(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val p = ctx.getSharedPreferences("today_widget", 0)
            val open = PendingIntent.getActivity(ctx, 78, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            ids.forEach { id ->
                val v = RemoteViews(ctx.packageName, R.layout.today_widget)
                v.setTextViewText(R.id.t_video, "🎬 " + p.getString("video", "Your day"))
                v.setTextViewText(R.id.t_idea, p.getString("idea", ""))
                v.setTextViewText(R.id.t_money, p.getString("money", ""))
                v.setTextViewText(R.id.t_wait, p.getString("waiting", ""))
                v.setOnClickPendingIntent(R.id.t_root, open)
                mgr.updateAppWidget(id, v)
            }
        }
    }
}
