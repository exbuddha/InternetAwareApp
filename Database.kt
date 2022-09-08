val db by lazy {
    Room.databaseBuilder(app.applicationContext,
        AppDatabase::class.java, "app.db"
    ).build()
}
val networkDb by lazy {
    Room.databaseBuilder(app.applicationContext,
        NetworkDatabase::class.java, "network.db"
    ).build()
}

val runtimeDao
    get() = db.appRuntimeDao()
val networkStateDao
    get() = networkDb.networkStateDao()
val networkCapabilitiesDao
    get() = networkDb.networkCapabilitiesDao()
