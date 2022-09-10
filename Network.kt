abstract class InternetAvailabilityListener : DifferenceListener<Boolean?>() {
    override fun onChanged(value: Boolean?) {
        hasInternet = value == true
    }
}

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

fun registerNetworkCapabilitiesCallback() {
    requireNetworkCapabilitiesListener().let { connectivityManager?.registerDefaultNetworkCallback(it) }
}
fun unregisterNetworkCapabilitiesCallback() {
    networkCapabilitiesListener?.let { connectivityManager?.unregisterNetworkCallback(it) }
}
fun clearNetworkCapabilitiesObjects() {
    networkCapabilitiesListener = null
    connectivityManager = null
    connectivityRequest = null
}

private var networkCapabilitiesListener: NetworkCallback? = null
private fun requireNetworkCapabilitiesListener() =
    networkCapabilitiesListener ?: object : NetworkCallback() {
        override fun onCapabilitiesChanged(newNetwork: Network, newNetworkCapabilities: NetworkCapabilities) {
            super.onCapabilitiesChanged(newNetwork, newNetworkCapabilities)
            app.reactToNetworkCapabilitiesChanged(
                network, networkCapabilities,
                newNetwork, newNetworkCapabilities)
            network = newNetwork
            networkCapabilities = newNetworkCapabilities
        }
    }.also { networkCapabilitiesListener = it }

private var connectivityManager: ConnectivityManager? = null
    get() = field ?: app.getSystemService(ConnectivityManager::class.java).also { field = it }
private var network: Network? = null
    get() = field ?: connectivityManager?.let { (it.activeNetwork).also { field = it } }
private var networkCapabilities: NetworkCapabilities? = null
    get() = field ?: connectivityManager?.let { (it.getNetworkCapabilities(it.activeNetwork)).also { field = it } }
private var connectivityRequest: NetworkRequest? = null
    get() = field ?: buildNetworkRequest {
        addCapability(NET_CAPABILITY_INTERNET)
    }.also { field = it }

private fun NetworkCapabilities.canSatisfy(request: NetworkRequest) = trySafely(false) {
    request.canBeSatisfiedBy(this)
}

private inline fun buildNetworkRequest(block: NetworkRequest.Builder.() -> Unit) = NetworkRequest.Builder().apply(block).build()
