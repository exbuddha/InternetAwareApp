class InternetAwareApp : Application(), LiveDataInvoker {
    var startTime = now()

    override fun onCreate() {
        super.onCreate()
        app = this
        startSession()
    }

    var reactToNetworkCapabilitiesChanged = ::reactToNetworkCapabilitiesChangedAsync
    var reactToInternetAvailabilityChanged = ::reactToInternetAvailabilityChangedAsync

    private fun startSession() {
        io(::newSession)
        start()
        io(::initNetworkCapabilities)
    }
    private suspend fun newSession(scope: LiveDataScope<(suspend () -> Any?)?>) { scope.apply {
        runner { resetOnNoEmit { repeatOnError {
            if (session === null) {
                runtimeDao.newSession()
                session = runtimeDao.getSession()
                emit {
                    reactToNetworkCapabilitiesChanged = ::reactToNetworkCapabilitiesChangedSync
                    reactToInternetAvailabilityChanged = ::reactToInternetAvailabilityChangedSync
                    Log.i(SESSION_TAG, "New session created.")
                    Log.i(SESSION_TAG, "Session id = ${session!!.id}")
                }
            }
        } } }
        trySafely { truncateSession() }
    } }
    private suspend fun initNetworkCapabilities(scope: LiveDataScope<(suspend () -> Any?)?>) { scope.apply {
        runner { resetOnFailure {
            if (networkCapabilitiesDao.getNetworkCapabilities()?.sid?.equals(session?.id) == false) {
                networkCapabilitiesDao.updateNetworkCapabilities(networkCapabilities!!)
                emit { Log.i(SESSION_TAG, "Network capabilities initialized.") }
            }
            if (networkStateDao.getNetworkState()?.sid?.equals(session?.id) == false) {
                networkStateDao.updateNetworkState()
                emit { Log.i(SESSION_TAG, "Network state initialized.") }
            }
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
        runBlock(block, { resetOnResume = true }, {
            exception(it)
            interrupt()
            throw it
        }, {
            exception(it)
            error()
        })
    suspend inline fun resetOnError(block: () -> Unit) {
        autoReset(block) {
            throwAutoResetException(it)
        }
    }
    suspend inline fun repeatOnError(block: () -> Unit) {
        autoReset(block) {
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
    suspend inline fun <T> LiveDataScope<T?>.nullOnError(block: LiveDataScope<T?>.() -> Unit) {
        try { block() }
        catch (ex: Throwable) {
            if (ex !is CancellationException)
                emit(null)
            throw ex
        }
    }
    suspend inline fun LiveDataScope<Any?>.unitOnError(block: LiveDataScope<Any?>.() -> Unit) {
        try { block() }
        catch (ex: Throwable) {
            if (ex !is CancellationException)
                emit(Unit)
            throw ex
        }
    }
    suspend inline fun LiveDataScope<Any?>.unitOnSuccess(block: LiveDataScope<Any?>.() -> Unit) {
        postBlock(block) { emit(Unit) }
    }

    inline fun runBlock(block: () -> Unit, onAutoRest: (Throwable) -> Unit = {}, onCancel: (Throwable) -> Unit = {}, onError: (Throwable) -> Unit = {}) {
        try { block() }
        catch (ex: AutoResetException) {
            onAutoRest(ex)
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

    inline fun <reified T> LiveDataRunner<Any?>.nonNullOrRepeat(t: Any?, block: (T) -> Unit) {
        if (t != null)
            block(t as T)
        else
            ln -= 1
    }
    inline fun <reified T> LiveDataRunner<Any?>.nonUnitOrRepeat(t: Any?, block: (T) -> Unit) {
        if (t != Unit)
            block(t as T)
        else
            ln -= 1
    }
    inline fun LiveDataRunner<Any?>.unitOrSkip(t: Any?, block: (Any?) -> Unit) {
        if (t == Unit) block(t)
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
    fun clearError() {
        resolve = null
        ex = null
        hasError = false
    }
    fun clearInterrupt() { isCancelled = false }
    var resetOnResume = false
    var resolve: ((Throwable, Any?) -> Boolean)? = null
    fun inactive() {
        isActive = false
        isObserving = false
    }
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
    override fun end() {
        super.end()
        isActive = false
        isCompleted = true
    }
    override fun reset(step: LiveData<(suspend () -> Any?)?>?) {
        if (!resetOnResume) {
            super.reset(step)
            isObserving = false
        }
    }
    override fun clear() { if (!isActive) super.clear() }
    override fun unload() { if (!isActive) super.unload() }
    override fun onChanged(t: (suspend () -> Any?)?) {
        try { super.onChanged(t) }
        catch (ex: Throwable) {
            if (isUnresolved(ex, t)) {
                exception(ex)
                exit()
            }
        }
    }
    private fun isUnresolved(ex: Throwable, t: (suspend () -> Any?)? = null) = resolve?.invoke(ex, t) == false
    override var seq: MutableList<Pair<() -> LiveData<(suspend () -> Any?)?>?, (((suspend () -> Any?)?) -> Any?)?>> = mutableListOf()
    override var ln = -1
    override var step: LiveData<(suspend () -> Any?)?>? = null
    override var lastCapture: Any? = null

    companion object {
        const val INET_TAG = "INTERNET"
        const val DB_TAG = "DATABASE"
        const val SESSION_TAG = "SESSION"
    }
}
