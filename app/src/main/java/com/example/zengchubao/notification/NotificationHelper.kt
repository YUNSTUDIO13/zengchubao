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
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.zengchubao.model.AppSettings
import com.example.zengchubao.model.DepositStatus
import com.example.zengchubao.storage.LocalFileManager
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

const val CHANNEL_ID = "zengchubao_reminder"
const val ACTION_REMIND = "com.example.zengchubao.REMIND"
private const val TAG = "ZCB_Reminder"

object NotificationHelper {

    fun initChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "存单到期提醒", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "存单到期前发送提醒"; enableVibration(true) }
            )
        }
    }

    /** 返回已调度的存单数 */
    suspend fun scheduleAll(context: Context, settings: AppSettings): Int {
        // 先清掉所有旧闹钟
        cancelAllInternal(context)
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
            // Toast 验证：主线程弹提示
            android.os.Handler(context.mainLooper).post {
                Toast.makeText(context, "已设置 ${count} 个到期提醒", Toast.LENGTH_SHORT).show()
            }
        }
        return count
    }

    private fun scheduleOne(
        context: Context, dep: com.example.zengchubao.model.Deposit,
        targetDate: LocalDate, hour: Int, minute: Int
    ) {
        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val targetDateTime = LocalDateTime.of(targetDate, LocalTime.of(hour, minute))
        val triggerMillis = targetDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        if (triggerMillis <= System.currentTimeMillis() + 5000) {
            Log.d(TAG, "skip: ${dep.productName} trigger too close ($targetDateTime)")
            return
        }

        val body = "${dep.productName}（${dep.bankName}）将于${dep.endDate}到期，本金 ¥${"%.2f".format(dep.principal)}"

        val requestCode = dep.id.hashCode()
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            putExtra("title", "存单到期提醒")
            putExtra("body", body)
            putExtra("notify_id", requestCode)
            data = android.net.Uri.parse("zcb://remind/${dep.id}")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_IMMUTABLE else 0
        val broadcastPi = PendingIntent.getBroadcast(context, requestCode, intent, flags)

        // setAlarmClock: 系统 UI 展示用的 PendingIntent 与 闹钟触发的 PendingIntent 分开
        val showIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val showPi = PendingIntent.getActivity(context, requestCode + 10000, showIntent, flags)
        alarmMgr.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerMillis, showPi),
            broadcastPi
        )

        Log.d(TAG, "alarm SET: ${dep.productName} at $targetDateTime (in ${(triggerMillis - System.currentTimeMillis()) / 1000}s)")
    }

    private fun cancelAllInternal(context: Context) {
        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val storage = LocalFileManager(context)
        val deposits = kotlinx.coroutines.runBlocking { storage.getAllDeposits() }
        deposits.forEach { dep ->
            val requestCode = dep.id.hashCode()
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_REMIND
                data = android.net.Uri.parse("zcb://remind/${dep.id}")
            }
            val flags = PendingIntent.FLAG_NO_CREATE or
                if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_IMMUTABLE else 0
            PendingIntent.getBroadcast(context, requestCode, intent, flags)?.let {
                alarmMgr.cancel(it)
                it.cancel()
            }
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, ">>> RECEIVER FIRED <<<")
        val title = intent.getStringExtra("title") ?: "存单到期提醒"
        val body = intent.getStringExtra("body") ?: return
        val notifyId = intent.getIntExtra("notify_id", 0)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val launchPending = PendingIntent.getActivity(
            context, 0,
            context.packageManager.getLaunchIntentForPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_IMMUTABLE else 0
        )
        nm.notify(notifyId, NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(launchPending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        )
        Log.d(TAG, "notification shown: $title")
    }
}
