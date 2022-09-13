lateinit var app: InternetAwareApp
var session: AppRuntimeSessionEntity? = null
val permissions
    get() = app.packageManager.getPackageInfo(app.packageName, GET_PERMISSIONS).requestedPermissions

fun <R> app(block: InternetAwareApp.() -> R) = app.block()

inline fun <reified R> trySafely(block: () -> R?) = try { block() } catch(_: Throwable) { R::class.objectInstance }

fun now() = Calendar.getInstance().timeInMillis
