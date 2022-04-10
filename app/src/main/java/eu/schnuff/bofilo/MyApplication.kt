package eu.schnuff.bofilo

import android.content.Context
import org.acra.config.mailSender
import org.acra.data.StringFormat
import org.acra.ktx.initAcra

class MyApplication : com.chaquo.python.android.PyApplication() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        initAcra {
            reportFormat = StringFormat.JSON

            mailSender {
                mailTo = "oct4v14n+acra.bofilo@gmail.com"
                withResSubject(R.string.acra_mail_subject)
                reportFileName = "crashreport.json"
            }
        }
    }
}