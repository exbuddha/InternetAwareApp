@Entity(tableName = "sessions")
data class AppRuntimeSessionEntity(
    @ColumnInfo(name = "app_time")
    val startTime: Long,
    @ColumnInfo(name = "db_time", defaultValue = "CURRENT_TIMESTAMP")
    val dbTime: String,
    @PrimaryKey
    val id: Long
)
