package app.docubuds

import android.app.Application
import app.docubuds.android.ReminderWorker

/** Application class: schedules the daily local reminder check. */
class DocuBudsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ReminderWorker.schedule(this)
    }
}
