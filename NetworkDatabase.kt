@Database(version = DbVersion, exportSchema = false, entities = [
    NetworkStateEntity::class,
    NetworkCapabilitiesEntity::class, ])
abstract class NetworkDatabase : RoomDatabase() {
    abstract fun networkStateDao(): NetworkStateDao
    abstract fun networkCapabilitiesDao(): NetworkCapabilitiesDao
}
