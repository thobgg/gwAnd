package de.bgghome.gwand

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.bgghome.gwand.daten.Anzeige
import de.bgghome.gwand.daten.Jahrestage
import de.bgghome.gwand.res.Res
import de.bgghome.gwand.res.home_years
import de.bgghome.gwand.res.reminder_channel
import de.bgghome.gwand.res.reminder_more
import de.bgghome.gwand.res.reminder_title
import org.jetbrains.compose.resources.getString
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Taegliche Erinnerung an die Jahrestage des Tages: einmal am Morgen fragt die App den Server und zeigt eine
 * Benachrichtigung, wenn es welche gibt. Ohne gespeicherte Anmeldung oder ohne Netz geschieht nichts.
 */
class Erinnerung(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as GwApp
        val client = app.plattform.client
        if (!client.hatAnmeldung) return Result.success()
        return try {
            client.fortsetzen()
            val heute = LocalDate.now()
            val tage = Jahrestage.aus(client.ereignisseAnTagen(Jahrestage.tage(heute, 1)), heute, 1, Anzeige()::ausProfil)
            if (tage.isNotEmpty()) zeigen(tage.map { j -> "${j.personen.joinToString(" & ") { it.name }} · ${j.art} · ${getString(Res.string.home_years, j.jahre)}" })
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun zeigen(zeilen: List<String>) {
        val ctx = applicationContext
        val verwaltung = ctx.getSystemService(NotificationManager::class.java)
        verwaltung.createNotificationChannel(NotificationChannel(KANAL, getString(Res.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val oeffnen = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stil = NotificationCompat.InboxStyle().also { s -> zeilen.take(6).forEach(s::addLine) }
        if (zeilen.size > 6) stil.setSummaryText(getString(Res.string.reminder_more, zeilen.size - 6))
        val n = NotificationCompat.Builder(ctx, KANAL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(Res.string.reminder_title))
            .setContentText(zeilen.first())
            .setStyle(stil)
            .setContentIntent(oeffnen)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(1, n) }
    }

    companion object {
        private const val KANAL = "jahrestage"
        private const val NAME = "jahrestage"

        /** Einschalten: taeglich gegen 8 Uhr, nur mit Netz. Ausschalten: Auftrag loeschen. */
        fun planen(context: Context, an: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!an) { wm.cancelUniqueWork(NAME); return }
            val jetzt = LocalDateTime.now()
            var naechste = jetzt.toLocalDate().atTime(8, 0)
            if (!naechste.isAfter(jetzt)) naechste = naechste.plusDays(1)
            val auftrag = PeriodicWorkRequestBuilder<Erinnerung>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(jetzt, naechste).toMinutes(), TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, auftrag)
        }
    }
}
