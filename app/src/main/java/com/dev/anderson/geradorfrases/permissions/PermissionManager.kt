package com.dev.anderson.geradorfrases.notifications

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class PermissionManager {

    companion object {
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 100

        /**
         * Solicita permissão de notificação (Android 13+)
         */
        fun requestNotificationPermission(activity: Activity) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        activity,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        NOTIFICATION_PERMISSION_REQUEST_CODE
                    )
                    println("DEBUG: Solicitando permissão de notificação")
                } else {
                    println("DEBUG: Permissão de notificação já concedida")
                }
            } else {
                println("DEBUG: Android < 13, permissão não necessária")
            }
        }

        /**
         * Verifica se tem permissão de notificação
         */
        fun hasNotificationPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

                println("DEBUG: Permissão de notificação (Android 13+): $hasPermission")
                hasPermission
            } else {
                val areEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
                println("DEBUG: Notificações habilitadas (Android < 13): $areEnabled")
                areEnabled
            }
        }

        /**
         * Abre as configurações de notificação do app
         */
        fun openNotificationSettings(context: Context) {
            val intent = Intent().apply {
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                        action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    else -> {
                        action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                }
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            try {
                context.startActivity(intent)
                println("DEBUG: Abrindo configurações de notificação")
            } catch (e: Exception) {
                println("ERROR: Erro ao abrir configurações: ${e.message}")
            }
        }

        /**
         * Verifica se as notificações estão habilitadas no canal específico
         */
        fun isChannelEnabled(context: Context, channelId: String): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                        as android.app.NotificationManager

                val channel = notificationManager.getNotificationChannel(channelId)
                val isEnabled = channel?.importance != android.app.NotificationManager.IMPORTANCE_NONE

                println("DEBUG: Canal '$channelId' habilitado: $isEnabled")
                return isEnabled
            }
            return true // Para versões antigas, assume que está habilitado
        }

        /**
         * Verifica se o app tem permissão para alarmes exatos (Android 12+)
         */
        fun hasExactAlarmPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                val canSchedule = alarmManager.canScheduleExactAlarms()
                println("DEBUG: Pode agendar alarmes exatos: $canSchedule")
                canSchedule
            } else {
                true // Para versões antigas, sempre pode
            }
        }

        /**
         * Abre configurações de alarmes exatos
         */
        fun openExactAlarmSettings(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }

                try {
                    context.startActivity(intent)
                    println("DEBUG: Abrindo configurações de alarmes exatos")
                } catch (e: Exception) {
                    println("ERROR: Erro ao abrir configurações de alarmes: ${e.message}")
                }
            }
        }

        /**
         * Verifica todas as permissões necessárias
         */
        fun checkAllPermissions(context: Context): PermissionStatus {
            val hasNotification = hasNotificationPermission(context)
            val hasExactAlarm = hasExactAlarmPermission(context)
            val channelEnabled = isChannelEnabled(context, "daily_phrases_channel")

            return PermissionStatus(
                hasNotificationPermission = hasNotification,
                hasExactAlarmPermission = hasExactAlarm,
                isChannelEnabled = channelEnabled,
                allPermissionsGranted = hasNotification && hasExactAlarm && channelEnabled
            )
        }
    }

    data class PermissionStatus(
        val hasNotificationPermission: Boolean,
        val hasExactAlarmPermission: Boolean,
        val isChannelEnabled: Boolean,
        val allPermissionsGranted: Boolean
    )
}