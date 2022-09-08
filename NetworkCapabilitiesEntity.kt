@Entity(tableName = "network_capabilities")
data class NetworkCapabilitiesEntity(
    @ColumnInfo(name = "capabilities")
    val capabilities: String,
    @ColumnInfo(name = "downstream")
    val downstream: Int,
    @ColumnInfo(name = "upstream")
    val upstream: Int,
    @ColumnInfo(name = "strength")
    val strength: Int,
    @ColumnInfo(name = "db_time", defaultValue = "CURRENT_TIMESTAMP")
    val dbTime: String,
    @PrimaryKey
    val id: Long,
    val sid: Long
)

fun IntArray.toJson() = Gson().toJson(this, IntArray::class.java)

fun String.toIntArray() = Gson().fromJson(this, IntArray::class.java)
