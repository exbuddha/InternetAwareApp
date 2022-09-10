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
    get() = networkCapabilities?.get()?.canSatisfy(connectivityRequest!!) ?: false
var hasInternet = false
    get() = isConnected && field
val hasMobile
    get() = networkCapabilities?.get()?.hasTransport(TRANSPORT_CELLULAR) ?: false
val hasWifi
    get() = networkCapabilities?.get()?.hasTransport(TRANSPORT_WIFI) ?: false

fun registerNetworkCapabilitiesCallback() {
    requireNetworkCapabilitiesListener().let { requireConnectivityManager()?.registerDefaultNetworkCallback(it) }
}
fun unregisterNetworkCapabilitiesCallback() {
    networkCapabilitiesListener?.let { connectivityManager?.get()?.unregisterNetworkCallback(it) }
}
fun clearNetworkCapabilitiesObjects() {
    networkCapabilitiesListener = null
    connectivityManager = null
    connectivityRequest = null
}

private var networkCapabilitiesListener: NetworkCallback? = null
private fun requireNetworkCapabilitiesListener() = networkCapabilitiesListener ?: object : NetworkCallback() {
        override fun onCapabilitiesChanged(newNetwork: Network, newNetworkCapabilities: NetworkCapabilities) {
            super.onCapabilitiesChanged(newNetwork, newNetworkCapabilities)
            app.reactToNetworkCapabilitiesChanged(
                network?.get(), networkCapabilities?.get(),
                newNetwork, newNetworkCapabilities)
            network = WeakReference(newNetwork)
            networkCapabilities = WeakReference(newNetworkCapabilities)
        }
    }.also { networkCapabilitiesListener = it }

private var connectivityManager: WeakReference<ConnectivityManager?>? = null
private fun requireConnectivityManager() =
    connectivityManager?.get() ?: app.getSystemService(ConnectivityManager::class.java)?.also { connectivityManager = WeakReference(it) }
private var network: WeakReference<Network?>? = null
    get() = field ?: connectivityManager?.get()?.let { WeakReference(it.activeNetwork).also { field = it } }
private var networkCapabilities: WeakReference<NetworkCapabilities?>? = null
    get() = field ?: connectivityManager?.get()?.let { WeakReference(it.getNetworkCapabilities(it.activeNetwork)).also { field = it } }
private var connectivityRequest: NetworkRequest? = null
    get() = field ?: buildNetworkRequest {
        addCapability(NET_CAPABILITY_INTERNET)
    }.also { field = it }

private fun NetworkCapabilities.canSatisfy(request: NetworkRequest) = trySafely(false) {
    request.canBeSatisfiedBy(this)
}

private inline fun buildNetworkRequest(block: NetworkRequest.Builder.() -> Unit) = NetworkRequest.Builder().apply(block).build()
