class InternetAwareApp : Application(), LiveDataRunner<(suspend () -> Unit)?> {
    var startTime = now()

    override fun onCreate() {
        super.onCreate()
        app = this
        io(::newSession)
        start()
    }

    var reactToNetworkCapabilitiesChanged = ::reactToNetworkCapabilitiesChangedAsync
    var reactToInternetAvailabilityChanged = ::reactToInternetAvailabilityChangedAsync

    private suspend fun newSession(scope: LiveDataScope<(suspend () -> Unit)?>) { scope.apply {
        runner { resetOnNoEmit { repeatOnError {
            if (session === null) {
                runtimeDao.newSession()
                session = runtimeDao.getSession()
                emit {
                    reset()
                    reactToNetworkCapabilitiesChanged = ::reactToNetworkCapabilitiesChangedSync
                    reactToInternetAvailabilityChanged = ::reactToInternetAvailabilityChangedSync
                    Log.i(SESSION_TAG, "New session created.")
                    io(::initNetworkCapabilities)
                    inactive()
                    resume()
                }
            }
        } } }
        trySafely { truncateSession() }
    } }
    private suspend fun initNetworkCapabilities(scope: LiveDataScope<(suspend () -> Unit)?>) { scope.apply {
        runner { resetOnError {
            active()
            if (networkCapabilitiesDao.getNetworkCapabilities()?.sid?.equals(session!!.id) == false)
                networkCapabilitiesDao.updateNetworkCapabilities(networkCapabilities!!)
            if (networkStateDao.getNetworkState()?.sid?.equals(session!!.id) == false)
                networkStateDao.updateNetworkState()
            stop()
        } }
    } }
    private suspend fun truncateSession() {
        runtimeDao.truncateSessions()
        networkStateDao.truncateNetworkStates()
        networkCapabilitiesDao.truncateNetworkCapabilities()
    }

    private fun reactToNetworkCapabilitiesChangedAsync(network: Network, networkCapabilities: NetworkCapabilities) {
        attach({ reactToNetworkCapabilitiesChanged(network, networkCapabilities) })
    }
    private fun reactToNetworkCapabilitiesChangedSync(network: Network, networkCapabilities: NetworkCapabilities) {
        runBlocking { reactToNetworkCapabilitiesChanged(network, networkCapabilities) }
    }
    private suspend fun reactToNetworkCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
        updateNetworkCapabilities(networkCapabilities)
        Log.i(INET_TAG, "Network capabilities have changed.")
    }
    private suspend fun updateNetworkCapabilities(networkCapabilities: NetworkCapabilities) {
        networkCapabilitiesDao.updateNetworkCapabilities(networkCapabilities)
        Log.i(DB_TAG, "Updated network capabilities.")
    }

    private fun reactToInternetAvailabilityChangedAsync(state: Boolean?) {
        attach({ reactToInternetAvailabilityChanged() })
    }
    private fun reactToInternetAvailabilityChangedSync(state: Boolean?) {
        runBlocking { reactToInternetAvailabilityChanged() }
    }
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

    suspend inline fun runner(block: () -> Unit) =
        runBlock(block, {
            resetOnResume = true
        }, {
            exception(it)
            interrupt()
            throw it
        }, {
            error(it)
        })
    suspend inline fun resetOnError(block: () -> Unit) {
        autoReset(block) {
            error(it)
            throwAutoResetException(it)
        }
    }
    suspend inline fun repeatOnError(block: () -> Unit) {
        autoReset(block) {
            error(it)
            ln =- 1
            throwAutoResetException(it)
        }
    }
    suspend inline fun <T> LiveDataScope<T?>.resetOnNoEmit(block: LiveDataScope<T?>.() -> Unit) {
        postBlock(block) {
            yield()
            if (latestValue === null)
                throw AutoResetException("Auto-reset: nothing or null was emitted.")
        }
    }
    suspend inline fun <T> LiveDataScope<T?>.resetOnFailure(block: () -> Unit) = resetOnNoEmit { resetOnError(block) }
    inline fun runBlock(block: () -> Unit, onAutoReset: (Throwable) -> Unit = {}, onCancel: (Throwable) -> Unit = {}, onError: (Throwable) -> Unit = {}) {
        try { block() }
        catch (ex: AutoResetException) {
            onAutoReset(ex)
        }
        catch (ex: CancellationException) {
            onCancel(ex)
        }
        catch (ex: Throwable) {
            onError(ex)
        }
    }
    inline fun autoReset(block: () -> Unit, preAutoReset: (Throwable) -> Unit) {
        runBlock(block, { throw it }, { throw it }, {
            preAutoReset(it)
            throw AutoResetException("Auto-reset", it)
        })
    }
    fun throwAutoResetException(ex: Throwable) {
        throw AutoResetException("Auto-reset: error was emitted.", ex)
    }
    inline fun <T> LiveDataScope<T?>.postBlock(block: LiveDataScope<T?>.() -> Unit, patch: () -> Unit) {
        block()
        patch()
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
    fun error(ex: Throwable) {
        exception(ex)
        error()
    }
    fun exception(ex: Throwable) { this.ex = ex }
    fun clearError() {
        resolve = null
        ex = null
        hasError = false
    }
    fun clearInterrupt() { isCancelled = false }
    var resetOnResume = false
    var resolve: ((Throwable, Any?) -> Boolean)? = null
    fun active() { isActive = true }
    fun inactive() {
        isActive = false
        isObserving = false
    }
    fun stop() { resetOnResume = true }
    private fun exit() {
        isActive = false
        hasError = true
    }
    override fun start() =
        if (isActive) true
        else {
            isActive = true
            isCompleted = false
            isObserving = false
            clearError()
            clearInterrupt()
            resetOnResume = false
            super.start()
        }
    override fun resume(index: Int) =
        if (isActive) true
        else {
            isActive = true
            super.resume(index)
        }
    override fun retry() =
        if (isActive) true
        else {
            isActive = true
            super.retry()
        }
    override fun advance() =
        try {
            if (resetOnResume) {
                reset()
                resetOnResume = false
            }
            super.advance()
        } catch (ex: Throwable) {
            exception(ex)
            exit()
            false
        }.also { isObserving = it }
    override fun block(t: (suspend () -> Unit)?) {
        super.block(t)
        if (t !== null) runBlocking { t.invoke() }
    }
    override fun end() {
        super.end()
        isActive = false
        isCompleted = true
    }
    override fun reset(step: LiveData<(suspend () -> Unit)?>?) {
        if (!resetOnResume) {
            super.reset(step)
            isObserving = false
        }
    }
    override fun clear() { if (!isActive) super.clear() }
    override fun unload() { if (!isActive) super.unload() }
    override fun onChanged(t: (suspend () -> Unit)?) {
        try { super.onChanged(t) }
        catch (ex: Throwable) {
            if (isUnresolved(ex, t)) {
                exception(ex)
                exit()
            }
        }
    }
    private fun isUnresolved(ex: Throwable, t: (suspend () -> Unit)? = null) = resolve?.invoke(ex, t) == false
    override var seq: MutableList<Pair<() -> LiveData<(suspend () -> Unit)?>?, (((suspend () -> Unit)?) -> Any?)?>> = mutableListOf()
    override var ln = -1
    override var step: LiveData<(suspend () -> Unit)?>? = null
    override var lastCapture: Any? = null

    companion object {
        const val INET_TAG = "INTERNET"
        const val DB_TAG = "DATABASE"
        const val SESSION_TAG = "SESSION"
    }
}
