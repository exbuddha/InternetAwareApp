@Database(version = DbVersion, exportSchema = false, entities = [
    AppRuntimeSessionEntity::class ])
abstract class AppDatabase : RoomDatabase() {
    abstract fun appRuntimeDao(): AppRuntimeDao
}
