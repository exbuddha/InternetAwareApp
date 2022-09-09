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
    }

    override fun onStop() {
        unregisterNetworkCapabilitiesCallback()
        unregisterInternetAvailabilityCallback()
        internetAvailabilityLiveData = null
        internetAvailabilityObserver = null
        clearNetworkCapabilitiesObjects()
        super.onStop()
    }

    private var internetAvailabilityLiveData: MutableLiveData<Boolean?>? = null
        get() = field ?: object : DifferenceLiveData<Boolean?>() {
            override fun postValue(value: Boolean?) {
                hasInternet = value != false
                super.postValue(value)
            }

            override fun observe(owner: LifecycleOwner, observer: Observer<in Boolean?>) {
                restartInternetAvailabilityCallback()
                super.observe(owner, observer)
            }
        }.also { field = it }

    fun registerInternetAvailabilityCallback() {
        internetAvailabilityObserver?.let { internetAvailabilityLiveData?.observe(this, it) }
    }

    fun unregisterInternetAvailabilityCallback() {
        internetAvailabilityObserver?.let { internetAvailabilityLiveData?.removeObserver(it) }
    }


    private var detectInternetAvailabilityJob: Job? = null
    private var repeatInternetAvailabilityTest = true
    private var testInternetAvailabilityDelay = 1000L
    private var lastInternetAvailabilityTestTime = 0L
        set(value) {
            field = value
            InternetAwareApp.lastInternetAvailabilityTestTime = value
        }

    fun restartInternetAvailabilityCallback() {
        if (detectInternetAvailabilityJob?.isActive != true) {
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

    private fun detectInternetAvailabilityChanged() {
        if (isInternetAvailabilityTimeIntervalExceeded) {
            internetAvailabilityLiveData?.postValue(trySafely {
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

    open fun reactToInternetAvailabilityChanged(state: Boolean?) = app.reactToInternetAvailabilityChanged()

    var internetAvailabilityObserver: Observer<Boolean?>? = null
        get() = field ?: InternetAvailabilityObserver {
            reactToInternetAvailabilityChanged(it)
        }.also { field = it }

    protected open inner class InternetAvailabilityObserver(
        block: InternetAwareActivity.(Boolean?) -> Unit
    ) : LifecycleOwnerObserver<InternetAwareActivity, Boolean?>(this, block)
}
