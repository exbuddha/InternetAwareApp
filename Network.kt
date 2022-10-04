val isInternetAccessPermitted by lazy {
    permissions?.let {
        it.contains(ACCESS_NETWORK_STATE) &&
        it.contains(INTERNET)
    } ?: false
}
val isConnected
    get() = networkCapabilities?.canSatisfy(connectivityRequest!!) ?: false
var hasInternet = false
    get() = isConnected && field
val hasMobile
    get() = networkCapabilities?.hasTransport(TRANSPORT_CELLULAR) ?: false
val hasWifi
    get() = networkCapabilities?.hasTransport(TRANSPORT_WIFI) ?: false

const val internetAvailabilityTimeIntervalMin = 3000L
var internetAvailabilityTimeInterval = internetAvailabilityTimeIntervalMin
    set(value) {
        field = if (value < internetAvailabilityTimeIntervalMin)
            internetAvailabilityTimeIntervalMin
        else value
    }
var lastInternetAvailabilityTestTime = 0L
    set(value) {
        if (value == 0L || value > field) field = value
    }
val isInternetAvailabilityTimeIntervalExceeded
    get() = isTimeIntervalExceeded(internetAvailabilityTimeInterval, lastInternetAvailabilityTestTime)
fun isTimeIntervalExceeded(interval: Long, last: Long) =
    (now() - last).let { it >= interval || it < 0 } || last == 0L

fun registerNetworkCapabilitiesCallback() {
    networkCapabilitiesListener?.let { connectivityManager?.registerDefaultNetworkCallback(it) }
}
fun unregisterNetworkCapabilitiesCallback() {
    networkCapabilitiesListener?.let { connectivityManager?.unregisterNetworkCallback(it) }
}
fun clearNetworkCapabilitiesObjects() {
    networkCapabilitiesListener = null
    connectivityRequest = null
}

private var networkCapabilitiesListener: NetworkCallback? = null
    get() = field ?: object : NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            super.onCapabilitiesChanged(network, networkCapabilities)
            app.reactToNetworkCapabilitiesChanged.invoke(network, networkCapabilities)
        }
    }.also { field = it }

private val connectivityManager
    get() = app.getSystemService(ConnectivityManager::class.java)
private val network
    get() = connectivityManager?.activeNetwork
val networkCapabilities
    get() = connectivityManager?.let { it.getNetworkCapabilities(it.activeNetwork) }
private var connectivityRequest: NetworkRequest? = null
    get() = field ?: buildNetworkRequest {
        addCapability(NET_CAPABILITY_INTERNET)
    }.also { field = it }

private fun NetworkCapabilities.canSatisfy(request: NetworkRequest) =
    try { request.canBeSatisfiedBy(this) }
    catch (_: Throwable) { false }

private inline fun buildNetworkRequest(block: NetworkRequest.Builder.() -> Unit) = NetworkRequest.Builder().apply(block).build()
