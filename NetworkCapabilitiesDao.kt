@Dao
abstract class NetworkCapabilitiesDao {
    @Update(onConflict = REPLACE)
    suspend fun updateNetworkCapabilities(networkCapabilities: NetworkCapabilities) = updateNetworkCapabilities(
        networkCapabilities.capabilities.toJson(),
        networkCapabilities.linkDownstreamBandwidthKbps,
        networkCapabilities.linkUpstreamBandwidthKbps,
        networkCapabilities.signalStrength)

    @Query("INSERT INTO network_capabilities(capabilities, downstream, upstream, strength, sid) VALUES (:capabilities, :downstream, :upstream, :strength, :sid)")
    abstract suspend fun updateNetworkCapabilities(capabilities: String, downstream: Int, upstream: Int, strength: Int, sid: Long = session!!.id)

    @Update(onConflict = REPLACE)
    abstract suspend fun updateNetworkCapabilities(networkCapabilitiesEntity: NetworkCapabilitiesEntity)

    @Query("SELECT * FROM network_capabilities ORDER BY id DESC LIMIT 1")
    abstract suspend fun getNetworkCapabilities(): NetworkCapabilitiesEntity?

    @Query("SELECT * FROM network_capabilities ORDER BY id DESC LIMIT 1 OFFSET :n")
    abstract suspend fun getPrevNetworkCapabilities(n: Int = 1): NetworkCapabilitiesEntity?

    @Query("SELECT * FROM network_capabilities ORDER BY id DESC LIMIT :n")
    abstract suspend fun getLastNetworkCapabilities(n: Int): List<NetworkCapabilitiesEntity>

    @Query("DELETE FROM network_capabilities WHERE id NOT IN (SELECT id FROM network_capabilities ORDER BY id LIMIT 1)")
    abstract suspend fun dequeueNetworkCapabilities()

    @Query("DELETE FROM network_capabilities WHERE id NOT IN (SELECT id FROM network_capabilities ORDER BY id DESC LIMIT :n)")
    abstract suspend fun truncateNetworkCapabilities(n: Int = 30)

    @Query("DELETE FROM network_capabilities WHERE sid <> :sid")
    abstract suspend fun cleanNetworkCapabilities(sid: Long = session!!.id)

    @Query("DELETE FROM network_capabilities")
    abstract suspend fun dropNetworkCapabilities()
}
