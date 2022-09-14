abstract class MutableLiveDataObserver<T> : MutableLiveData<T>(), Observer<T> {
    open fun observe(owner: LifecycleOwner) = observe(owner, this)
    open fun removeObserver() = removeObserver(this)
}

interface LiveDataRunner<T> : Observer<T> {
    var seq: MutableList<Pair<() -> LiveData<T>?, ((T?) -> Any?)?>>
    var ln: Int
    var step: LiveData<T>?

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

    fun attachImmediately(step: Pair<() -> LiveData<T>?, ((T?) -> Any?)?>) =
        attach(ln + 1, step)
    fun attachImmediately(step: suspend LiveDataScope<T>.() -> Unit, capture: ((T?) -> Any?)? = null) {
        fun async() = liveData(block = step)
        attachImmediately(Pair(::async, capture))
    }
    fun attachImmediately(context: CoroutineContext, step: suspend LiveDataScope<T>.() -> Unit, capture: ((T?) -> Any?)? = null) {
        fun async() = liveData(context, block = step)
        attachImmediately(Pair(::async, capture))
    }
    fun ioImmediately(step: suspend LiveDataScope<T>.() -> Unit, capture: ((T?) -> Any?)? = null) =
        attachImmediately(Dispatchers.IO, step, capture)
    fun unconfinedImmediately(step: suspend LiveDataScope<T>.() -> Unit, capture: ((T?) -> Any?)? = null) =
        attachImmediately(Dispatchers.Unconfined, step, capture)

    fun capture(block: (T?) -> Any?) = attach({ null } to block)
    fun capture(index: Int, block: (T?) -> Any?) = attach(index, { null } to block)
    fun captureImmediately(block: (T?) -> Any?) = attachImmediately({ null } to block)

    fun start(): Boolean {
        ln = -1
        return advance()
    }

    fun resume() = advance()

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
                seq[ln].second?.invoke(null)
        }
        end()
        return false
    }

    fun end() {}
    fun exit() = end()

    fun capture(t: T) {
        seq[ln].second?.invoke(t)
        reset()
    }

    fun reset() {
        step?.removeObserver(this)
    }

    fun unload() {
        if (ln > 0) {
            seq = MutableList(seq.size - ln) { seq[it + ln] }
            ln = 0
        }
    }

    override fun onChanged(t: T) {
        capture(t)
        advance()
    }
}

suspend inline fun <T> LiveDataScope<T?>.nullOnError(block: LiveDataScope<T?>.() -> Any?) {
    try { block() }
    catch (ex: Throwable) {
        app.ex = ex
        emit(null)
    }
}
suspend inline fun LiveDataScope<Any?>.unitOnSuccess(block: LiveDataScope<Any?>.() -> Any?) {
    try {
        block()
        emit(Unit)
    } catch (ex: Throwable) {
        app.ex = ex
        emit(null)
    }
}

inline fun <reified T> LiveDataRunner<Any?>.nonNullOrRepeat(t: Any?, block: (T) -> Any?) {
    if (t !== null)
        block(t as T)
    else
        ln =- 1
}
inline fun LiveDataRunner<Any?>.unitOrSkip(t: Any?, block: () -> Any?) {
    if (t === Unit) block()
}
