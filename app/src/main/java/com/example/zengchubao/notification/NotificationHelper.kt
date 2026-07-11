package com.example.zengchubao.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.zengchubao.model.AppSettings
import com.example.zengchubao.model.DepositStatus
import com.example.zengchubao.storage.LocalFileManager
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

const val CHANNEL_ID = "zengchubao_reminder"
private const val TAG = "ZCB_Reminder"

object NotificationHelper {

    fun initChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "存单到期提醒", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "存单到期前发送提醒" }
            )
        }
    }

    suspend fun scheduleAll(context: Context, settings: AppSettings): Int {
        cancelAll(context)
        if (settings.reminderDays <= 0) return 0

        val storage = LocalFileManager(context)
        val deposits = storage.getAllDeposits()
        val now = LocalDate.now()
        var count = 0

        deposits.filter { it.status == DepositStatus.HOLDING }.forEach { dep ->
            try {
                val endDate = LocalDate.parse(dep.endDate)
                val targetDate = endDate.minusDays(settings.reminderDays.toLong())
                if (!targetDate.isBefore(now)) {
                    scheduleOne(context, dep, targetDate,
                        settings.reminderHour, settings.reminderMinute)
                    count++
                }
            } catch (e: Exception) {
                Log.e(TAG, "schedule failed: ${dep.id}", e)
            }
        }

        if (count > 0) {
            Log.d(TAG, "scheduled $count alarms")
        }
        return count
    }

    private fun scheduleOne(
        context: Context,
        dep: com.example.zengchubao.model.Deposit,
        targetDate: LocalDate,
        hour: Int, minute: Int
    ) {
        val targetDateTime = LocalDateTime.of(targetDate, LocalTime.of(hour, minute))
        val triggerMillis = targetDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        if (triggerMillis <= System.currentTimeMillis() + 3000) {
            Log.d(TAG, "skip past: ${dep.productName}")
            return
        }

        val body = "${dep.productName}（${dep.bankName}）将于${dep.endDate}到期，本金 ¥${"%.2f".format(dep.principal)}"
        val requestCode = dep.id.hashCode()

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.example.zengchubao.REMIND"
            putExtra("title", "存单到期提醒")
            putExtra("body", body)
            putExtra("notify_id", requestCode)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_IMMUTABLE else 0

        val pending = PendingIntent.getBroadcast(context, requestCode, intent, flags)

        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pending)
            } else if (Build.VERSION.SDK_INT >= 19) {
                alarmMgr.setExact(AlarmManager.RTC_WAKEUP, triggerMillis, pending)
            } else {
                alarmMgr.set(AlarmManager.RTC_WAKEUP, triggerMillis, pending)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "exact alarm denied, fallback to inexact", e)
            alarmMgr.set(AlarmManager.RTC_WAKEUP, triggerMillis, pending)
        }

        Log.d(TAG, "alarm SET: ${dep.productName} @ $targetDateTime (in ${(triggerMillis - System.currentTimeMillis()) / 1000}s)")
    }

    private fun cancelAll(context: Context) {
        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val storage = LocalFileManager(context)
        val deposits = kotlinx.coroutines.runBlocking { storage.getAllDeposits() }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_IMMUTABLE else 0
        deposits.forEach { dep ->
            val requestCode = dep.id.hashCode()
            // 用与 scheduleOne 完全一致的 Intent 结构（含 extras）才能匹配到同一个 PendingIntent
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = "com.example.zengchubao.REMIND"
                putExtra("title", "存单到期提醒")
                putExtra("body", "")
                putExtra("notify_id", requestCode)
            }
            alarmMgr.cancel(PendingIntent.getBroadcast(context, requestCode, intent, flags))
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, ">>> RECEIVER FIRED <<< action=${intent.action}")
        val title = intent.getStringExtra("title") ?: return
        val body = intent.getStringExtra("body") ?: return
        val notifyId = intent.getIntExtra("notify_id", 0)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notifyId, NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        )
        Log.d(TAG, "notification posted: $title")
    }
}
