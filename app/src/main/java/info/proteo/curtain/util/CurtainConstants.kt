package info.proteo.curtain.util

object CurtainConstants {

    object PredefinedHosts {
        const val CELSUS_BACKEND = "https://celsus.muttsu.xyz"
        const val QUEST_BACKEND = "https://curtain-backend.omics.quest"
        const val PROTEO_FRONTEND = "https://curtain.proteo.info"
    }

    object ExampleData {
        const val UNIQUE_ID = "f4b009f3-ac3c-470a-a68b-55fcadf68d0f"
        const val API_URL = "https://celsus.muttsu.xyz/"
        const val FRONTEND_URL = "https://curtain.proteo.info/"
        const val DESCRIPTION = "Example Proteomics Dataset"
        const val CURTAIN_TYPE = "TP"
    }

    val COMMON_HOSTNAMES = listOf(
        PredefinedHosts.CELSUS_BACKEND,
        PredefinedHosts.QUEST_BACKEND,
        "localhost",
        "https://your-curtain-server.com"
    )

    object URLPatterns {
        private const val PROTEO_HOST = "curtain.proteo.info"

        fun isProteoURL(urlString: String): Boolean {
            return try {
                val url = java.net.URL(urlString)
                url.host == PROTEO_HOST && url.ref != null
            } catch (e: Exception) {
                false
            }
        }

        fun extractLinkIdFromProteoURL(urlString: String): String? {
            return try {
                val url = java.net.URL(urlString)
                url.ref?.removePrefix("/")
            } catch (e: Exception) {
                null
            }
        }
    }
}
