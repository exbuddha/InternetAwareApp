class InternetAwareApp : Application() {
    val startTime = now()
    var sid = 0L

    override fun onCreate() {
        super.onCreate()
        app = this
        runBlocking {
            sid = runtimeDao.newSession()
            updateNetworkState()
            runtimeDao.truncateSessions()
            networkStateDao.truncateNetworkStates()
            networkCapabilitiesDao.truncateNetworkCapabilities()
        }
        internetAvailabilityTimeInterval = resources.getString(R.string.app_internet_detection_time_interval).toLong()
    }

    fun runInternetAvailabilityTest(): Response {
        return requireHttpClientForInternetAvailabilityTest()
            .newCall(requireHttpRequestForInternetAvailabilityTest())
            .execute()
            .also {
                Log.i(INET_TAG, "Sending out http request for internet availability...")
            }
    }

    private fun requireHttpClientForInternetAvailabilityTest(): OkHttpClient {
        return OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .build()
    }

    private fun requireHttpRequestForInternetAvailabilityTest(): Request {
        return Request.Builder()
            .url("https://httpbin.org/delay/1")
            .build()
    }

    fun reactToNetworkCapabilitiesChanged(
        oldNetwork: Network?, oldNetworkCapabilities: NetworkCapabilities?,
        newNetwork: Network?, newNetworkCapabilities: NetworkCapabilities?) {
        if (newNetworkCapabilities !== null) {
            updateNetworkCapabilities(newNetworkCapabilities)
        }
        if (oldNetworkCapabilities !== null) {
            Log.i(INET_TAG, "Network capabilities have changed.")
        }
    }

    fun reactToInternetAvailabilityChanged() {
        updateNetworkState()
        Log.i(INET_TAG, "Internet availability has changed.")
    }

    private fun updateNetworkState() {
        runBlocking {
            networkStateDao.updateNetworkState()
            Log.i(DB_TAG, "Updated network state.")
        }
    }

    private fun updateNetworkCapabilities(networkCapabilities: NetworkCapabilities) {
        runBlocking {
            networkCapabilitiesDao.updateNetworkCapabilities(networkCapabilities)
            Log.i(DB_TAG, "Updated network capabilities.")
        }
    }

    companion object {
        const val DbVersion = 1

        const val internetAvailabilityTimeIntervalMin = 3000L
        var internetAvailabilityTimeInterval = internetAvailabilityTimeIntervalMin
            set(value) {
                field = if (value < internetAvailabilityTimeIntervalMin)
                    internetAvailabilityTimeIntervalMin
                else value
            }
        var lastInternetAvailabilityTestTime = 0L
            set(value) {
                if (value == 0L || value > field) field = value
            }
        val isInternetAvailabilityTimeIntervalExceeded
            get() = isTimeIntervalExceeded(internetAvailabilityTimeInterval, lastInternetAvailabilityTestTime)
        fun isTimeIntervalExceeded(interval: Long, last: Long) =
            (now() - last).let { it >= interval || it < 0 } || last == 0L

        const val INET_TAG = "INTERNET"
        const val DB_TAG = "DATABASE"
    }
}
