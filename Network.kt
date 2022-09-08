object InternetAvailability : StatefulLiveData<Boolean>(false) {
    init {
        ref = isPermitted && isConnected
    }

    var isExternallyDisconnected = false
        get() = isNotResolved
        set(value) {
            field = value
            if (value) reset()
        }

    fun postExternallyDisconnected() {
        if (isResolved) {
            isExternallyDisconnected = true
            postChange()
        }
    }

    fun postExternallyReconnected() {
        if (ref != true) {
            accept(true)
            postChange()
        }
    }

    fun postInternallyDisconnected() {
        if (ref != false) {
            accept(false)
            postChange()
        }
    }

    override fun postChange(value: Boolean?) {
        when {
            value === null -> postExternallyDisconnected()
            value -> postExternallyReconnected()
            else -> postInternallyDisconnected()
        }
    }

    override fun equals(other: Any?) = ((other === null && isNotResolved) || other == ref) || super.equals(other)
}

val isPermitted by lazy {
    permissions?.let {
        it.contains(ACCESS_NETWORK_STATE) &&
        it.contains(INTERNET)
    } ?: false
}

val isConnected
    get() = networkCapabilities?.get()?.canSatisfy(connectivityRequest!!) ?: false

val hasInternet
    get() = InternetAvailability.state

val hasMobile
    get() = networkCapabilities?.get()?.hasTransport(TRANSPORT_CELLULAR) ?: false

val hasWifi
    get() = networkCapabilities?.get()?.hasTransport(TRANSPORT_WIFI) ?: false

private var listener: NetworkCallback? = null
    get() = field ?: object : NetworkCallback() {
        override fun onCapabilitiesChanged(newNetwork: Network, newNetworkCapabilities: NetworkCapabilities) {
            super.onCapabilitiesChanged(newNetwork, newNetworkCapabilities)
            app.reactToNetworkCapabilitiesChanged(
                network?.get(), networkCapabilities?.get(),
                newNetwork, newNetworkCapabilities)
            network = WeakReference(newNetwork)
            networkCapabilities = WeakReference(newNetworkCapabilities)
        }
    }.also { field = it }

fun registerNetworkCapabilitiesCallback() {
    listener?.let { connectivityManager?.get()?.registerDefaultNetworkCallback(it) }
}
fun unregisterNetworkCapabilitiesCallback() {
    listener?.let { connectivityManager?.get()?.unregisterNetworkCallback(it) }
}
fun clearNetworkCapabilitiesObjects() {
    listener = null
    connectivityManager = null
    connectivityRequest = null
}

private var connectivityManager: WeakReference<ConnectivityManager?>? = null
    get() = field ?: WeakReference(app.getSystemService(ConnectivityManager::class.java)).also { field = it }
private var network: WeakReference<Network?>? = null
    get() = field ?: WeakReference(connectivityManager?.get()?.activeNetwork).also { field = it }
private var networkCapabilities: WeakReference<NetworkCapabilities?>? = null
    get() = field ?: WeakReference(connectivityManager?.get()?.let {
        it.getNetworkCapabilities(it.activeNetwork)
    }).also { field = it }
private var connectivityRequest: NetworkRequest? = null
    get() = field ?: buildNetworkRequest {
        addCapability(NET_CAPABILITY_INTERNET)
    }.also { field = it }

private fun NetworkCapabilities.canSatisfy(request: NetworkRequest) = trySafely(false) {
    request.canBeSatisfiedBy(this)
}

private inline fun buildNetworkRequest(block: NetworkRequest.Builder.() -> Unit) = NetworkRequest.Builder().apply(block).build()
