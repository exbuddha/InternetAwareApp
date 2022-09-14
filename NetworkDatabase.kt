@Database(version = DbVersion, exportSchema = false, entities = [
    NetworkCapabilitiesEntity::class,
    NetworkStateEntity::class, ])
abstract class NetworkDatabase : RoomDatabase() {
    abstract fun networkCapabilitiesDao(): NetworkCapabilitiesDao
    abstract fun networkStateDao(): NetworkStateDao
}
