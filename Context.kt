lateinit var app: InternetAwareApp

val permissions
    get() = app.packageManager.getPackageInfo(app.packageName, GET_PERMISSIONS).requestedPermissions

fun <R> trySafely(block: () -> R): R? = try { block() } catch(_: Throwable) { null }
fun <R> trySafely(fallback: R, block: () -> R) = try { block() } catch(_: Throwable) { fallback }
