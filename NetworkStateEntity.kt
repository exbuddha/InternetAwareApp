@Entity(tableName = "network_states")
data class NetworkStateEntity(
    @ColumnInfo(name = "is_connected")
    val isConnected: Boolean,
    @ColumnInfo(name = "has_internet")
    val hasInternet: Boolean,
    @ColumnInfo(name = "has_wifi")
    val hasWifi: Boolean,
    @ColumnInfo(name = "has_mobile")
    val hasMobile: Boolean,
    @ColumnInfo(name = "db_time", defaultValue = "CURRENT_TIMESTAMP")
    val dbTime: String,
    @PrimaryKey
    val id: Long,
    val sid: Long
)
