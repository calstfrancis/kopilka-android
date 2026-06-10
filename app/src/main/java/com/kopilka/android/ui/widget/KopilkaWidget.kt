package com.kopilka.android.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.kopilka.android.MainActivity
import com.kopilka.android.R

const val EXTRA_DIRECT_ADD = "direct_add"

class KopilkaWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        widgetIds: IntArray,
    ) {
        widgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_kopilka)

            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_DIRECT_ADD, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_add_btn, pi)
            manager.updateAppWidget(id, views)
        }
    }
}
