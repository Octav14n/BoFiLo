package eu.schnuff.bofilo

object Constants {
    // the "key" attribut of the settings (also the key for defaultSharedPreferences)
    const val PREF_PERSONALINI = "pref_dev_personalini"
    const val PREF_DEFAULT_DIRECTORY = "pref_default_directory"
    const val PREF_DEFAULT_SRC_DIRECTORY = "pref_default_src_directory"
    const val PREF_DEFAULT_SRC_DIRECTORY_ENABLED = "pref_default_src_directory_enabled"
    const val PREF_SAVE_CACHE = "pref_save_cache"
    const val PREF_SHOW_CONSOLE = "pref_show_console"
    const val PREF_IS_ADULT = "pref_is_adult"

    // Mime types
    const val MIME_EPUB = "application/epub+zip"
    const val MIME_INI = "*/*" // no idea what mime type .ini is, android tells me it is "text/plain" but I cant select the ini if I have set it to that.
}