package com.kopilka.android.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

private const val CHANNEL_SYNC = "kopilka_sync"
private const val CHANNEL_PARTNER = "kopilka_partner"
private const val CHANNEL_BUDGET = "kopilka_budget"
private const val NOTIF_SYNC_ID = 1
private const val NOTIF_PARTNER_ID = 2
private const val NOTIF_BUDGET_ID = 3

class NotificationHelper(private val context: Context) {

    fun createChannels() {
        val mgr = context.getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_SYNC, "Budget Sync", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows while a budget sync is in progress"
                setShowBadge(false)
            }
        )
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_PARTNER, "Partner Activity", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notifies when your partner logs a purchase"
            }
        )
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_BUDGET, "Budget Alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Alerts when a category reaches 80% of its budget"
            }
        )
    }

    fun showSyncing() {
        val notif = NotificationCompat.Builder(context, CHANNEL_SYNC)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Kopilka")
            .setContentText("Syncing budget…")
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setSilent(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIF_SYNC_ID, notif)
        } catch (_: SecurityException) {}
    }

    fun hideSyncing() {
        NotificationManagerCompat.from(context).cancel(NOTIF_SYNC_ID)
    }

    fun showBudgetAlert(categoryName: String, pct: Int) {
        val notif = NotificationCompat.Builder(context, CHANNEL_BUDGET)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Budget alert: $categoryName")
            .setContentText("You've used $pct% of your $categoryName budget")
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIF_BUDGET_ID, notif)
        } catch (_: SecurityException) {}
    }

    fun showPartnerSpent(partnerName: String, count: Int, total: Double, description: String) {
        val text = if (count == 1)
            "$partnerName logged \$${"%.2f".format(total)}: $description"
        else
            "$partnerName logged $count entries (\$${"%.2f".format(total)} total)"
        val notif = NotificationCompat.Builder(context, CHANNEL_PARTNER)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Partner activity")
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIF_PARTNER_ID, notif)
        } catch (_: SecurityException) {}
    }
}
