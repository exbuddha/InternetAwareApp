class InternetAwareApp : Application(), LiveDataRunner<Any?> {
    var startTime = now()

    lateinit var reactToNetworkCapabilitiesChanged: (Network, NetworkCapabilities) -> Unit
    lateinit var reactToInternetAvailabilityChanged: (Boolean?) -> Unit

    override fun onCreate() {
        super.onCreate()
        app = this

        reactToNetworkCapabilitiesChanged = ::reactToNetworkCapabilitiesChangedAsync
        reactToInternetAvailabilityChanged = ::reactToInternetAvailabilityChangedAsync
        io(::newSession) {
            nonNullOrRetry<AppRuntimeSessionEntity>(it) {
                session = it
                reactToNetworkCapabilitiesChanged = ::reactToNetworkCapabilitiesChangedSync
                reactToInternetAvailabilityChanged = ::reactToInternetAvailabilityChangedSync
                Log.i(SESSION_TAG, "New session created.")
            }
        }
        io(::initNetworkState) {
            unitOrReset(it) {
                Log.i(SESSION_TAG, "Network state initialized.")
            }
        }
        io(::initNetworkCapabilities) {
            unitOrReset(it) {
                Log.i(SESSION_TAG, "Network capabilities initialized.")
            }
        }
        start()
    }

    private suspend fun newSession(scope: LiveDataScope<Any?>) { scope.apply {
        nullOnError {
            if (latestValue === null) {
                runtimeDao.newSession()
                emit(runtimeDao.getSession())
            }
        }
        trySafely { truncateSession() }
    } }

    private suspend fun truncateSession() {
        runtimeDao.truncateSessions()
        networkStateDao.truncateNetworkStates()
        networkCapabilitiesDao.truncateNetworkCapabilities()
    }

    private suspend fun initNetworkState(scope: LiveDataScope<Any?>) { scope.apply {
        unitOnSuccess {
            if (networkStateDao.getNetworkState()?.sid?.equals(session?.id) == false)
                networkStateDao.updateNetworkState()
        }
    } }

    private suspend fun initNetworkCapabilities(scope: LiveDataScope<Any?>) { scope.apply {
        unitOnSuccess {
            if (networkCapabilitiesDao.getNetworkCapabilities()?.sid?.equals(session?.id) == false)
                networkCapabilitiesDao.updateNetworkCapabilities(networkCapabilities!!)
        }
    } }

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

    private fun reactToNetworkCapabilitiesChangedAsync(network: Network, networkCapabilities: NetworkCapabilities) =
        attach({ reactToNetworkCapabilitiesChanged(network, networkCapabilities) })
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

    private fun reactToInternetAvailabilityChangedAsync(state: Boolean?) =
        attach({ reactToInternetAvailabilityChanged() })
    private fun reactToInternetAvailabilityChangedSync(state: Boolean?) =
        runBlocking { reactToInternetAvailabilityChanged() }
    private suspend fun reactToInternetAvailabilityChanged() {
        updateNetworkState()
        Log.i(INET_TAG, "Internet availability has changed.")
    }

    private suspend fun updateNetworkState() {
        networkStateDao.updateNetworkState()
        Log.i(DB_TAG, "Updated network state.")
    }

    override var seq: MutableList<Pair<() -> LiveData<Any?>?, ((Any?) -> Any?)?>> = mutableListOf()
    override var ln = -1
    override var step: LiveData<Any?>? = null
    var isObserving = false
    var ex: Throwable? = null
    override fun start() = super.start().also { isObserving = it }
    override fun advance() = super.advance().also { isObserving = it }
    override fun resume() = isObserving || super.resume()
    override fun reset() {
        super.reset()
        isObserving = false
    }
    override fun unload() {
        if (!isObserving) super.unload()
    }

    companion object {
        const val INET_TAG = "INTERNET"
        const val DB_TAG = "DATABASE"
        const val SESSION_TAG = "SESSION"
    }
}
