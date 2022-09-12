lateinit var app: InternetAwareApp
var session: AppRuntimeSessionEntity? = null
val permissions
    get() = app.packageManager.getPackageInfo(app.packageName, GET_PERMISSIONS).requestedPermissions

fun <R> app(block: MediaPlayerApp.() -> R) = app.block()

inline fun <R> trySafely(block: () -> R): R? = try { block() } catch(_: Throwable) { null }

fun now() = Calendar.getInstance().timeInMillis
