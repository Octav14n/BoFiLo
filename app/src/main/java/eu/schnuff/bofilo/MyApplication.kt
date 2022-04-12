package eu.schnuff.bofilo

import android.content.Context
import org.acra.config.dialog
import org.acra.config.mailSender
import org.acra.data.StringFormat
import org.acra.ktx.initAcra

class MyApplication : com.chaquo.python.android.PyApplication() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        initAcra {
            reportFormat = StringFormat.JSON

            dialog {
                resTheme = R.style.Theme_AppCompat_Dialog_Alert
                title = getString(R.string.acra_dialog_title)
                text = getString(R.string.acra_dialog_text)
                positiveButtonText = getString(R.string.acra_dialog_button_positive)
                negativeButtonText = getString(R.string.acra_dialog_button_negative)
            }
            mailSender {
                mailTo = "oct4v14n+acra.bofilo@gmail.com"
                subject = getString(R.string.acra_mail_subject)
                reportFileName = "crashreport.json"
            }
        }
    }
}