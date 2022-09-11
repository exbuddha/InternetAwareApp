class InternetAwareApp : Application(), LiveDataRunner {
    val startTime = now()
    var sid = 0L

    override fun onCreate() {
        super.onCreate()
        app = this
        attach(::startSession) {
            session = it as AppRuntimeSessionEntity
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

    private fun startSession() = liveData(Dispatchers.IO) {
        if (latestValue === null) {
            resetOnErrorSuspended {
                runtimeDao.newSession()
                emit(runtimeDao.getSession())
            }
        }
        resetSuspended {
            truncateSession()
        }
    }

    private suspend fun truncateSession() {
        runtimeDao.truncateSessions()
        networkStateDao.truncateNetworkStates()
        networkCapabilitiesDao.truncateNetworkCapabilities()
    }

    private fun initNetworkState() = liveData(Dispatchers.IO) {
        resetSuspended {
            if (networkStateDao.getNetworkState()?.sid?.equals(session?.id) == false)
                networkStateDao.updateNetworkState()
            emit(Unit)
        }
    }

    private fun initNetworkCapabilities() = liveData(Dispatchers.IO) {
        resetSuspended {
            if (networkCapabilitiesDao.getNetworkCapabilities()?.sid?.equals(session?.id) == false)
                networkCapabilitiesDao.updateNetworkCapabilities(networkCapabilities!!)
            emit(Unit)
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

    fun reactToNetworkCapabilitiesChanged(
        oldNetwork: Network?, oldNetworkCapabilities: NetworkCapabilities?,
        newNetwork: Network?, newNetworkCapabilities: NetworkCapabilities?) {
        attachResettingOnNullSession {
            if (newNetworkCapabilities !== null) {
                runBlockingUnlessAttached(it) { updateNetworkCapabilities(newNetworkCapabilities) }
            }
            if (oldNetworkCapabilities !== null) {
                Log.i(INET_TAG, "Network capabilities have changed.")
            }
        }
    }

    private suspend fun updateNetworkCapabilities(networkCapabilities: NetworkCapabilities) {
        networkCapabilitiesDao.updateNetworkCapabilities(networkCapabilities)
        Log.i(DB_TAG, "Updated network capabilities.")
    }

    fun reactToInternetAvailabilityChanged() {
        attachResettingOnNullSession {
            runBlockingUnlessAttached(it) { updateNetworkState() }
            Log.i(INET_TAG, "Internet availability has changed.")
        }
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
    override fun reset() = super.reset().also { isObserving = false }

    private suspend fun resetSuspended(block: suspend () -> Unit) {
        try {
            block()
        } catch (ex: Throwable) {
            reset()
            this.ex = ex
            throw ex
        }
    }
    private suspend fun resetOnErrorSuspended(block: suspend () -> Unit) {
        try {
            block()
        } catch (ex: Throwable) {
            if (ex !is CancellationException) reset()
            this.ex = ex
            throw ex
        }
    }
    private fun attachResettingOnNullSession(block: suspend (Boolean) -> Unit) {
        if (session === null) {
            fun async() = liveData { resetSuspended { emit(block(true)) } }
            attach(::async)
        }
        else runBlocking { block(false) }
    }
    private suspend fun runBlockingUnlessAttached(isAttached: Boolean, block: suspend () -> Unit) {
        if (isAttached) block() else runBlocking { block() }
    }

    companion object {
        const val INET_TAG = "INTERNET"
        const val DB_TAG = "DATABASE"
        const val SESSION_TAG = "SESSION"
    }
}
