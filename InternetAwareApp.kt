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
            nonUnitOrRepeat<AppRuntimeSessionEntity>(it) {
                session = it
                reactToNetworkCapabilitiesChanged = ::reactToNetworkCapabilitiesChangedSync
                reactToInternetAvailabilityChanged = ::reactToInternetAvailabilityChangedSync
                Log.i(SESSION_TAG, "New session created.")
            }
        }
        start()
        io(::initNetworkCapabilities) {
            unitOrSkip(it) {
                Log.i(SESSION_TAG, "Network capabilities initialized.")
            }
        }
        io(::initNetworkState) {
            unitOrSkip(it) {
                Log.i(SESSION_TAG, "Network state initialized.")
            }
        }
    }

    private suspend fun newSession(scope: LiveDataScope<Any?>) { scope.apply {
        runner { resetOnNoEmit { unitOnError<AppRuntimeSessionEntity> {
            if (latestValue === null) {
                runtimeDao.newSession()
                emit(runtimeDao.getSession())
            }
        } } }
        trySafely { truncateSession() }
    } }
    private suspend fun initNetworkCapabilities(scope: LiveDataScope<Any?>) { scope.apply {
        runner { resetOnError { unitOnSuccess {
            if (networkCapabilitiesDao.getNetworkCapabilities()?.sid?.equals(session?.id) == false)
                networkCapabilitiesDao.updateNetworkCapabilities(networkCapabilities!!)
        } } }
    } }
    private suspend fun initNetworkState(scope: LiveDataScope<Any?>) { scope.apply {
        runner { resetOnError { unitOnSuccess {
            if (networkStateDao.getNetworkState()?.sid?.equals(session?.id) == false)
                networkStateDao.updateNetworkState()
        } } }
    } }
    private suspend fun truncateSession() {
        runtimeDao.truncateSessions()
        networkStateDao.truncateNetworkStates()
        networkCapabilitiesDao.truncateNetworkCapabilities()
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

    inline fun runner(block: () -> Unit) {
        try { block() }
        catch (ex: AutoResetException) {
            resetOnResume()
        }
        catch (ex: CancellationException) {
            exception(ex)
            interrupt()
            throw ex
        }
        catch (ex: Throwable) {
            exception(ex)
            error()
        }
    }
    inline fun resetOnError(block: () -> Unit) {
        try { block() }
        catch (ex: AutoResetException) {
            throw ex
        }
        catch (ex: CancellationException) {
            throw ex
        }
        catch (ex: Throwable) {
            exception(ex)
            error()
            throw AutoResetException("Auto-reset: error was emitted.", ex)
        }
    }

    var isActive = false
        private set
        get() = field || isObserving
    var isObserving = false
        private set
    var isCompleted = false
        private set
        get() = field || !(isActive || isCancelled)
    var isCancelled = false
        private set
    var hasError = false
        private set
    var ex: Throwable? = null
        private set
    fun interrupt() { isCancelled = true }
    fun error() { hasError = true }
    fun exception(ex: Throwable) { this.ex = ex }
    fun clear() {
        ex = null
        hasError = false
        isCancelled = false
    }
    private var resetOnResume = false
    fun resetOnResume() { resetOnResume = true }
    fun inactive() { isActive = false }
    private fun exit() {
        isActive = false
        error()
    }
    override fun start() =
        if (isActive) true
        else {
            isActive = true
            isCompleted = false
            isObserving = false
            clear()
            resetOnResume = false
            super.start()
        }
    override fun resume(index: Int) =
        if (isActive) true
        else {
            isActive = true
            if (resetOnResume) {
                reset()
                resetOnResume = false
            }
            super.resume(index)
        }
    override fun retry() =
        if (isActive) true
        else {
            isActive = true
            if (resetOnResume) {
                reset()
                resetOnResume = false
            }
            super.retry()
        }
    override fun advance() =
        try { super.advance() }
        catch (ex: Throwable) {
            exception(ex)
            exit()
            false
        }.also { isObserving = it }
    override fun end() {
        super.end()
        isActive = false
        isCompleted = true
    }
    override fun reset(step: LiveData<Any?>?) {
        super.reset(step)
        isObserving = false
    }
    override fun unload() { if (!isActive) super.unload() }
    override fun onChanged(t: Any?) {
        try { super.onChanged(t) }
        catch (ex: Throwable) {
            exception(ex)
            exit()
        }
    }
    override var seq: MutableList<Pair<() -> LiveData<Any?>?, ((Any?) -> Any?)?>> = mutableListOf()
    override var ln = -1
    override var step: LiveData<Any?>? = null
    override var lastCapture: Any? = null

    companion object {
        const val INET_TAG = "INTERNET"
        const val DB_TAG = "DATABASE"
        const val SESSION_TAG = "SESSION"
    }
}
