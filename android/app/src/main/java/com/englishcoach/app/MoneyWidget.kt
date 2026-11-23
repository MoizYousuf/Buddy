package com.englishcoach.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Home-screen widget: what home owes + pocket balance. The app saves the numbers each time the Money book loads. */
class MoneyWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = draw(ctx, mgr, ids)

    companion object {
        fun save(ctx: Context, owes: Double, wallet: Double) {
            ctx.getSharedPreferences("money_widget", 0).edit().putFloat("owes", owes.toFloat()).putFloat("wallet", wallet.toFloat()).apply()
            val mgr = AppWidgetManager.getInstance(ctx)
            draw(ctx, mgr, mgr.getAppWidgetIds(ComponentName(ctx, MoneyWidget::class.java)))
        }

        fun draw(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val p = ctx.getSharedPreferences("money_widget", 0)
            val open = PendingIntent.getActivity(ctx, 77, Intent(ctx, MainActivity::class.java).putExtra("screen", "Money")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            ids.forEach { id ->
                val v = RemoteViews(ctx.packageName, R.layout.money_widget)
                v.setTextViewText(R.id.owes, "PKR " + String.format("%,.0f", p.getFloat("owes", 0f)))
                v.setTextViewText(R.id.wallet, "PKR " + String.format("%,.0f", p.getFloat("wallet", 0f)))
                v.setOnClickPendingIntent(R.id.root, open)
                mgr.updateAppWidget(id, v)
            }
        }
    }
}
