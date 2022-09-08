@Dao
abstract class NetworkStateDao {
    @Query("INSERT INTO network_states(is_connected, has_internet, is_externally_disconnected, has_wifi, has_mobile, sid) VALUES (:isConnected, :hasInternet, :isExternallyDisconnected, :hasWifi, :hasMobile, :sid)")
    abstract suspend fun updateNetworkState(isConnected: Boolean, hasInternet: Boolean, isExternallyDisconnected: Boolean, hasWifi: Boolean, hasMobile: Boolean, sid: Long = app.sid)

    @Query("SELECT * FROM network_states ORDER BY id DESC LIMIT 1")
    abstract suspend fun getNetworkState(): NetworkStateEntity?

    @Query("SELECT * FROM network_states ORDER BY id DESC LIMIT 1 OFFSET :n")
    abstract suspend fun getPrevNetworkState(n: Int = 1): NetworkStateEntity?

    @Query("DELETE FROM network_states WHERE id NOT IN (SELECT id FROM network_states ORDER BY id LIMIT 1)")
    abstract suspend fun popNetworkState()

    @Query("DELETE FROM network_states WHERE id NOT IN (SELECT id FROM network_states ORDER BY id DESC LIMIT :n)")
    abstract suspend fun truncateNetworkStates(n: Int = 30)

    @Query("DELETE FROM network_states WHERE sid <> :sid")
    abstract suspend fun cleanNetworkStates(sid: Long = app.sid)

    @Query("DELETE FROM network_states")
    abstract suspend fun dropNetworkStates()
}
