abstract class InternetAwareActivity : AppCompatActivity() {
    var enableNetworkCapabilitiesCallback = true
    var enableInternetAvailabilityCallback = true

    override fun onResume() {
        super.onResume()
        if (enableNetworkCapabilitiesCallback)
            registerNetworkCapabilitiesCallback()
        if (enableInternetAvailabilityCallback) {
            registerInternetAvailabilityCallback()
            pollInternetAvailability()
        }
    }

    override fun onStop() {
        unregisterNetworkCapabilitiesCallback()
        unregisterInternetAvailabilityCallback()
        internetAvailabilityObserver = null
        clearNetworkCapabilitiesObjects()
        super.onStop()
    }

    private var detectInternetAvailabilityJob: Job? = null
    private var repeatInternetAvailabilityTest = true
    private var testInternetAvailabilityDelay = 1000L

    private fun pollInternetAvailability() {
        if (!isInternetAvailabilityCallbackActive) {
            detectInternetAvailabilityJob = lifecycleScope.launch(Dispatchers.IO) {
                repeatInternetAvailabilityTest()
                while (isActive) {
                    delay(testInternetAvailabilityDelay)
                    repeatInternetAvailabilityTest()
                }
            }
        }
    }

    private fun repeatInternetAvailabilityTest() {
        if (repeatInternetAvailabilityTest)
            detectInternetAvailabilityChanged()
    }

    private var lastInternetAvailabilityTestTime = 0L
        set(value) {
            field = value
            InternetAwareApp.lastInternetAvailabilityTestTime = value
        }

    private fun detectInternetAvailabilityChanged() {
        if (isInternetAvailabilityTimeIntervalExceeded(lastInternetAvailabilityTestTime)) {
            InternetAvailability.postChange(trySafely {
                isConnected && runInternetAvailabilityTest().let { response ->
                    response.isSuccessful.also {
                        if (it) {
                            lastInternetAvailabilityTestTime = now()
                            Log.i(INET_TAG, "Received response for internet availability.")
                        }
                        response.close()
                    }
                }
            })
        }
    }

    fun restartInternetAvailabilityCallback(interval: Long = testInternetAvailabilityDelay) {
        setInternetAvailabilityCallbackDelay(interval)
        resumeInternetAvailabilityCallback()
        pollInternetAvailability()
    }

    fun resumeInternetAvailabilityCallback() {
        repeatInternetAvailabilityTest = true
    }

    fun pauseInternetAvailabilityCallback() {
        repeatInternetAvailabilityTest = false
    }

    fun stopInternetAvailabilityCallback() {
        detectInternetAvailabilityJob?.cancel()
        detectInternetAvailabilityJob = null
    }

    fun setInternetAvailabilityCallbackDelay(interval: Long) {
        testInternetAvailabilityDelay = interval
    }

    val isInternetAvailabilityCallbackActive
        get() = detectInternetAvailabilityJob?.isActive == true

    fun registerInternetAvailabilityCallback() {
        internetAvailabilityObserver?.let { InternetAvailability.observe(this, it) }
    }

    fun unregisterInternetAvailabilityCallback() {
        internetAvailabilityObserver?.let { InternetAvailability.removeObserver(it) }
    }

    open fun runInternetAvailabilityTest() = app.runInternetAvailabilityTest()

    open fun reactToInternetAvailabilityChanged() = app.reactToInternetAvailabilityChanged()

    var internetAvailabilityObserver: Observer<Unit>? = null
        get() = field ?: InternetAvailabilityObserver {
            reactToInternetAvailabilityChanged()
        }.also { field = it }

    protected open inner class InternetAvailabilityObserver(
        block: InternetAwareActivity.() -> Unit
    ) : LifecycleOwnerObserver<InternetAwareActivity>(this, block)
}
