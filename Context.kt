lateinit var app: InternetAwareApp
var session: AppRuntimeSessionEntity? = null
val permissions
    get() = app.packageManager.getPackageInfo(app.packageName, GET_PERMISSIONS).requestedPermissions

fun <R> app(block: MediaPlayerApp.() -> R) = app.block()

fun <R> trySafely(block: () -> R): R? = try { block() } catch(_: Throwable) { null }
fun <R> trySafely(fallback: R, block: () -> R) = try { block() } catch(_: Throwable) { fallback }

fun now() = Calendar.getInstance().timeInMillis
