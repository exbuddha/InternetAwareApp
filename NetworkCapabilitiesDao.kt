@Dao
abstract class NetworkCapabilitiesDao {
    @Query("INSERT INTO network_capabilities(capabilities, downstream, upstream, strength, sid) VALUES (:capabilities, :downstream, :upstream, :strength, :sid)")
    abstract suspend fun updateNetworkCapabilities(capabilities: String, downstream: Int, upstream: Int, strength: Int, sid: Long = app.sid)

    @Query("SELECT * FROM network_capabilities ORDER BY id DESC LIMIT 1")
    abstract suspend fun getNetworkCapabilities(): NetworkCapabilitiesEntity?

    @Query("SELECT * FROM network_capabilities ORDER BY id DESC LIMIT 1 OFFSET :n")
    abstract suspend fun getPrevNetworkCapabilities(n: Int = 1): NetworkCapabilitiesEntity?

    @Query("DELETE FROM network_capabilities WHERE id NOT IN (SELECT id FROM network_capabilities ORDER BY id LIMIT 1)")
    abstract suspend fun popNetworkCapabilities()

    @Query("DELETE FROM network_capabilities WHERE id NOT IN (SELECT id FROM network_capabilities ORDER BY id DESC LIMIT :n)")
    abstract suspend fun truncateNetworkCapabilities(n: Int = 30)

    @Query("DELETE FROM network_capabilities WHERE sid <> :sid")
    abstract suspend fun cleanNetworkCapabilities(sid: Long = app.sid)

    @Query("DELETE FROM network_capabilities")
    abstract suspend fun dropNetworkCapabilities()
}
