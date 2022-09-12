class InternetAwareApp : Application(), LiveDataRunner {
    val startTime = now()

    override fun onCreate() {
        super.onCreate()
        app = this
        attach(::newSession) {
            session = it as AppRuntimeSessionEntity
            reactToNetworkCapabilitiesChanged = ::reactToNetworkCapabilitiesChangedSync
            reactToInternetAvailabilityChanged = ::reactToInternetAvailabilityChangedSync
            Log.i(SESSION_TAG, "New session created.")
        }
        attach(::initNetworkState) {
            Log.i(SESSION_TAG, "Network state initialized.")
        }
        attach(::initNetworkCapabilities) {
            Log.i(SESSION_TAG, "Network capabilities initialized.")
        }
        isObserving = start()
    }

    private fun newSession() = liveData(Dispatchers.IO) {
        try {
            if (latestValue === null) {
                runtimeDao.newSession()
                emit(runtimeDao.getSession())
            }
            truncateSession()
        } catch (ex: Throwable) {
            this@InternetAwareApp.ex = ex
        }
    }

    private suspend fun truncateSession() {
        runtimeDao.truncateSessions()
        networkStateDao.truncateNetworkStates()
        networkCapabilitiesDao.truncateNetworkCapabilities()
    }

    private fun initNetworkState() = liveData(Dispatchers.IO) {
        try {
            if (networkStateDao.getNetworkState()?.sid?.equals(session?.id) == false)
                networkStateDao.updateNetworkState()
            emit(Unit)
        } catch (ex: Throwable) {
            this@InternetAwareApp.ex = ex
        }
    }

    private fun initNetworkCapabilities() = liveData(Dispatchers.IO) {
        try {
            if (networkCapabilitiesDao.getNetworkCapabilities()?.sid?.equals(session?.id) == false)
                networkCapabilitiesDao.updateNetworkCapabilities(networkCapabilities!!)
            emit(Unit)
        } catch (ex: Throwable) {
            this@InternetAwareApp.ex = ex
        }
    }

    fun runInternetAvailabilityTest(): Boolean {
        Log.i(INET_TAG, "Trying to send out http request for internet availability...")
        return requireHttpClientForInternetAvailabilityTest()
            .newCall(requireHttpRequestForInternetAvailabilityTest())
            .execute().let { response ->
                response.isSuccessful.also {
                    response.close()
                    Log.i(INET_TAG, "Received response for internet availability.")
                }
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

    var reactToNetworkCapabilitiesChanged = ::reactToNetworkCapabilitiesChangedAsync
    private fun reactToNetworkCapabilitiesChangedAsync(network: Network, networkCapabilities: NetworkCapabilities) =
        attach { reactToNetworkCapabilitiesChanged(network, networkCapabilities) }
    private fun reactToNetworkCapabilitiesChangedSync(network: Network, networkCapabilities: NetworkCapabilities) =
        runBlocking { reactToNetworkCapabilitiesChanged(network, networkCapabilities) }
    private suspend fun reactToNetworkCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
        updateNetworkCapabilities(networkCapabilities)
        Log.i(INET_TAG, "Network capabilities have changed.")
    }

    private suspend fun updateNetworkCapabilities(networkCapabilities: NetworkCapabilities) {
        networkCapabilitiesDao.updateNetworkCapabilities(networkCapabilities)
        Log.i(DB_TAG, "Updated network capabilities.")
    }

    var reactToInternetAvailabilityChanged = ::reactToInternetAvailabilityChangedAsync
    private fun reactToInternetAvailabilityChangedAsync() =
        attach { reactToInternetAvailabilityChanged() }
    private fun reactToInternetAvailabilityChangedSync() =
        runBlocking { reactToInternetAvailabilityChanged() }
    private suspend fun reactToInternetAvailabilityChanged() {
        updateNetworkState()
        Log.i(INET_TAG, "Internet availability has changed.")
    }

    private suspend fun updateNetworkState() {
        networkStateDao.updateNetworkState()
        Log.i(DB_TAG, "Updated network state.")
    }

    override val seq: MutableList<Pair<() -> LiveData<*>?, ((Any?) -> Any?)?>> = mutableListOf()
    override var ln = -1
    override var step: LiveData<*>? = null
    var isObserving = false
    var ex: Throwable? = null
    override fun advance() = super.advance().also { isObserving = it }
    override fun reset() {
        super.reset()
        isObserving = false
    }
    private fun attach(block: suspend () -> Unit) {
        fun async() = liveData { emit(block()) }
        attach(::async)
    }

    companion object {
        const val INET_TAG = "INTERNET"
        const val DB_TAG = "DATABASE"
        const val SESSION_TAG = "SESSION"
    }
}
