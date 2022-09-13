abstract class InternetAwareActivity : AppCompatActivity() {
    var enableNetworkCapabilitiesCallback = true
        get() = field && isInternetAccessPermitted
    var enableInternetAvailabilityCallback = true
        get() = field && isInternetAccessPermitted

    val internetAvailabilityLiveData: DifferenceLiveData<Boolean?>?
        get() = internetAvailabilityListener

    override fun onResume() {
        super.onResume()
        if (enableNetworkCapabilitiesCallback)
            registerNetworkCapabilitiesCallback()
        if (enableInternetAvailabilityCallback)
            registerInternetAvailabilityCallback()
        app {
            unload()
            capture { Log.i(SESSION_TAG, "Session id = ${session!!.id}") }
            resume()
        }
    }

    override fun onStop() {
        unregisterNetworkCapabilitiesCallback()
        unregisterInternetAvailabilityCallback()
        internetAvailabilityListener = null
        clearNetworkCapabilitiesObjects()
        super.onStop()
    }

    fun registerInternetAvailabilityCallback() {
        requireInternetAvailabilityListener().observe(this)
    }

    fun unregisterInternetAvailabilityCallback() {
        internetAvailabilityListener?.removeObserver()
    }

    fun restartInternetAvailabilityCallback() {
        if (detectInternetAvailabilityJob?.isActive != true) {
            detectInternetAvailabilityJob = lifecycleScope.launch(Dispatchers.IO) {
                while (isActive) {
                    if (repeatInternetAvailabilityTest && isInternetAvailabilityTimeIntervalExceeded) {
                        internetAvailabilityListener?.postValue(trySafely {
                            isConnected && runInternetAvailabilityTest().also { isSuccessful ->
                                if (isSuccessful)
                                    lastInternetAvailabilityTestTime = now()
                            }
                        })
                    }
                    delay(testInternetAvailabilityDelay)
                }
            }
        }
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

    open fun runInternetAvailabilityTest() = app.runInternetAvailabilityTest()

    open fun reactToInternetAvailabilityChanged(state: Boolean?) = app.reactToInternetAvailabilityChanged.invoke(state)

    private var internetAvailabilityListener: DifferenceListener<Boolean?>? = null
    private fun requireInternetAvailabilityListener() =
        internetAvailabilityListener ?: object : InternetAvailabilityListener() {
            override fun observe(owner: LifecycleOwner, observer: Observer<in Boolean?>) {
                restartInternetAvailabilityCallback()
                super.observe(owner, observer)
            }
            override fun onChanged(value: Boolean?) {
                super.onChanged(value)
                reactToInternetAvailabilityChanged(value)
            }
        }.also { internetAvailabilityListener = it }

    private var detectInternetAvailabilityJob: Job? = null
    private var repeatInternetAvailabilityTest = true
    private var testInternetAvailabilityDelay = 1000L
    private var lastInternetAvailabilityTestTime = 0L
        set(value) {
            field = value
            my.module.lastInternetAvailabilityTestTime = value
        }
}
