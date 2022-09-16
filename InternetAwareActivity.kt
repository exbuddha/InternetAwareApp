abstract class InternetAwareActivity : AppCompatActivity() {
    var enableNetworkCapabilitiesCallback = true
        get() = field && isInternetAccessPermitted
    var enableInternetAvailabilityCallback = true
        get() = field && isInternetAccessPermitted

    override fun onResume() {
        super.onResume()
        if (enableNetworkCapabilitiesCallback)
            registerNetworkCapabilitiesCallback()
        if (enableInternetAvailabilityCallback)
            registerInternetAvailabilityCallback()
        app {
            if (hasError)
                Log.i(SESSION_TAG, "The runner has encountered an error: ${ex?.message}")
            unload()
            captureOnce { Log.i(SESSION_TAG, "Session id = ${session!!.id}") }
            if (isCompleted)
                start()
            else
                resume()
        }
    }

    override fun onStop() {
        unregisterNetworkCapabilitiesCallback()
        unregisterInternetAvailabilityCallback()
        internetAvailabilityLiveData = null
        clearNetworkCapabilitiesObjects()
        super.onStop()
    }

    fun registerInternetAvailabilityCallback() {
        (internetAvailabilityLiveData as? MutableLiveDataObserver)?.observe(this)
    }

    fun unregisterInternetAvailabilityCallback() {
        (internetAvailabilityLiveData as? MutableLiveDataObserver)?.removeObserver()
    }

    fun restartInternetAvailabilityCallback() {
        if (detectInternetAvailabilityJob?.isActive != true) {
            detectInternetAvailabilityJob = lifecycleScope.launch(Dispatchers.IO) {
                while (isActive) {
                    if (repeatInternetAvailabilityTest && isInternetAvailabilityTimeIntervalExceeded) {
                        internetAvailabilityLiveData?.postValue(trySafely {
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

    var internetAvailabilityLiveData: MutableLiveData<Boolean?>? = null
        private set
        get() = field ?: object : MutableLiveDataObserver<Boolean?>() {
            override fun observe(owner: LifecycleOwner, observer: Observer<in Boolean?>) {
                restartInternetAvailabilityCallback()
                super.observe(owner, observer)
            }
            override fun removeObserver(observer: Observer<in Boolean?>) {
                stopInternetAvailabilityCallback()
                super.removeObserver(observer)
            }
            override fun postValue(value: Boolean?) {
                if (value != this.value) super.postValue(value)
            }
            override fun onChanged(value: Boolean?) {
                reactToInternetAvailabilityChanged(value)
            }
        }.also { field = it }

    private var detectInternetAvailabilityJob: Job? = null
    private var repeatInternetAvailabilityTest = true
    private var testInternetAvailabilityDelay = 1000L
    private var lastInternetAvailabilityTestTime = 0L
        set(value) {
            field = value
            my.module.lastInternetAvailabilityTestTime = value
        }
}
