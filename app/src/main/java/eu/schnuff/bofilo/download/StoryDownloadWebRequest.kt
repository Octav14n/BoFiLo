package eu.schnuff.bofilo.download

interface StoryDownloadWebRequest {
    fun webRequest(method: String, url: String): String
}