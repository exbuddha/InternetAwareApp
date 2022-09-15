abstract class MutableLiveDataObserver<T> : MutableLiveData<T>(), Observer<T> {
    open fun observe(owner: LifecycleOwner) = observe(owner, this)
    open fun removeObserver() = removeObserver(this)
}

interface LiveDataRunner<T> : Observer<T> {
    var seq: MutableList<Pair<() -> LiveData<T>?, ((T?) -> Any?)?>>
    var ln: Int
    var step: LiveData<T>?
    var lastCapture: Any?

    fun attach(step: Pair<() -> LiveData<T>?, ((T?) -> Any?)?>) {
        seq.add(step)
    }
    fun attach(step: suspend LiveDataScope<T>.() -> Unit, capture: ((T?) -> Any?)? = null) {
        fun async() = liveData(block = step)
        attach(Pair(::async, capture))
    }
    fun attach(context: CoroutineContext, step: suspend LiveDataScope<T>.() -> Unit, capture: ((T?) -> Any?)? = null) {
        fun async() = liveData(context, block = step)
        attach(Pair(::async, capture))
    }
    fun io(step: suspend LiveDataScope<T>.() -> Unit, capture: ((T?) -> Any?)? = null) =
        attach(Dispatchers.IO, step, capture)
    fun unconfined(step: suspend LiveDataScope<T>.() -> Unit, capture: ((T?) -> Any?)? = null) =
        attach(Dispatchers.Unconfined, step, capture)

    fun attach(index: Int, step: Pair<() -> LiveData<T>?, ((T?) -> Any?)?>) {
        seq.add(index, step)
    }
    fun attach(index: Int, step: suspend LiveDataScope<T>.() -> Unit, capture: ((T?) -> Any?)? = null) {
        fun async() = liveData(block = step)
        attach(index, Pair(::async, capture))
    }
    fun attach(index: Int, context: CoroutineContext, step: suspend LiveDataScope<T>.() -> Unit, capture: ((T?) -> Any?)? = null) {
        fun async() = liveData(context, block = step)
        attach(index, Pair(::async, capture))
    }
    fun io(index: Int, step: suspend LiveDataScope<T>.() -> Unit, capture: ((T?) -> Any?)? = null) =
        attach(index, Dispatchers.IO, step, capture)
    fun unconfined(index: Int, step: suspend LiveDataScope<T>.() -> Unit, capture: ((T?) -> Any?)? = null) =
        attach(index, Dispatchers.Unconfined, step, capture)

    fun attachBefore(step: Pair<() -> LiveData<T>?, ((T?) -> Any?)?>) =
        attach(if (ln < 0) 0 else ln, step)
    fun attachBefore(step: suspend LiveDataScope<T>.() -> Unit, capture: ((T?) -> Any?)? = null) {
        fun async() = liveData(block = step)
        attachBefore(Pair(::async, capture))
    }
    fun attachBefore(context: CoroutineContext, step: suspend LiveDataScope<T>.() -> Unit, capture: ((T?) -> Any?)? = null) {
        fun async() = liveData(context, block = step)
        attachBefore(Pair(::async, capture))
    }
    fun ioBefore(step: suspend LiveDataScope<T>.() -> Unit, capture: ((T?) -> Any?)? = null) =
        attachBefore(Dispatchers.IO, step, capture)
    fun unconfinedBefore(step: suspend LiveDataScope<T>.() -> Unit, capture: ((T?) -> Any?)? = null) =
        attachBefore(Dispatchers.Unconfined, step, capture)

    fun attachAfter(step: Pair<() -> LiveData<T>?, ((T?) -> Any?)?>) =
        attach(ln + 1, step)
    fun attachAfter(step: suspend LiveDataScope<T>.() -> Unit, capture: ((T?) -> Any?)? = null) {
        fun async() = liveData(block = step)
        attachAfter(Pair(::async, capture))
    }
    fun attachAfter(context: CoroutineContext, step: suspend LiveDataScope<T>.() -> Unit, capture: ((T?) -> Any?)? = null) {
        fun async() = liveData(context, block = step)
        attachAfter(Pair(::async, capture))
    }
    fun ioAfter(step: suspend LiveDataScope<T>.() -> Unit, capture: ((T?) -> Any?)? = null) =
        attachAfter(Dispatchers.IO, step, capture)
    fun unconfinedAfter(step: suspend LiveDataScope<T>.() -> Unit, capture: ((T?) -> Any?)? = null) =
        attachAfter(Dispatchers.Unconfined, step, capture)

    fun capture(block: (T?) -> Any?) = attach({ null } to block)
    fun capture(index: Int, block: (T?) -> Any?) = attach(index, { null } to block)
    fun captureBefore(block: (T?) -> Any?) = attachBefore({ null } to block)
    fun captureAfter(block: (T?) -> Any?) = attachAfter({ null } to block)

    fun start(): Boolean {
        ln = -1
        lastCapture = null
        return advance()
    }

    fun resume(index: Int = ln): Boolean {
        ln = index
        return advance()
    }

    fun retry(): Boolean {
        ln -= 1
        return advance()
    }

    fun advance(): Boolean {
        while (++ln < seq.size) {
            step = seq[ln].first.invoke()
            if (step?.observeForever(this) === Unit)
                return true
            else
                lastCapture = seq[ln].second?.invoke(null)
        }
        end()
        return false
    }

    fun capture(t: T) {
        lastCapture = seq[ln].second?.invoke(t)
        reset()
    }

    fun reset(step: LiveData<T>? = this.step) {
        step?.removeObserver(this)
    }

    fun unload() {
        if (ln > 0) {
            seq = MutableList(seq.size - ln) { seq[it + ln] }
            ln = -1
        }
    }

    fun end() {}

    override fun onChanged(t: T) {
        capture(t)
        advance()
    }
}

suspend inline fun <T> LiveDataScope<T?>.nullOnError(block: LiveDataScope<T?>.() -> Unit) {
    try { block() }
    catch (ex: Throwable) {
        if (ex !is CancellationException)
            emit(null)
        throw ex
    }
}
suspend inline fun LiveDataScope<Any?>.unitOnSuccess(block: LiveDataScope<Any?>.() -> Unit) {
    block()
    emit(Unit)
}
suspend inline fun <T> LiveDataScope<T?>.resetOnNoEmit(block: LiveDataScope<T?>.() -> Unit) {
    block()
    yield()
    if (latestValue === null)
        throw AutoResetException("Auto-reset: nothing or null was emitted.")
}

class AutoResetException(msg: String? = null, cause: Throwable? = null) : RuntimeException(msg, cause)

inline fun <reified T> LiveDataRunner<Any?>.nonNullOrRepeat(t: Any?, block: (T) -> Any?) {
    if (t !== null)
        block(t as T)
    else
        ln -= 1
}
inline fun LiveDataRunner<Any?>.unitOrSkip(t: Any?, block: (Any?) -> Any?) {
    if (t == Unit) block(t)
}
