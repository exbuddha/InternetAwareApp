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
    fun attachOnce(step: Pair<() -> LiveData<T>?, ((T?) -> Any?)?>) {
        if (isNotAttached(step))
            attach(step)
    }
    fun attach(step: suspend LiveDataScope<T>.() -> Unit, capture: ((T?) -> Any?)? = null): () -> LiveData<T>? {
        fun async() = liveData(block = step)
        attach(Pair(::async, capture))
        return ::async
    }
    fun attach(context: CoroutineContext, step: suspend LiveDataScope<T>.() -> Unit, capture: ((T?) -> Any?)? = null): () -> LiveData<T>? {
        fun async() = liveData(context, block = step)
        attach(Pair(::async, capture))
        return ::async
    }
    fun attach(index: Int, step: Pair<() -> LiveData<T>?, ((T?) -> Any?)?>) {
        seq.add(index, step)
    }
    fun attachOnce(index: Int, step: Pair<() -> LiveData<T>?, ((T?) -> Any?)?>) {
        if (isNotAttached(index, step))
            seq.add(index, step)
    }
    fun attach(index: Int, step: suspend LiveDataScope<T>.() -> Unit, capture: ((T?) -> Any?)? = null): () -> LiveData<T>? {
        fun async() = liveData(block = step)
        attach(index, Pair(::async, capture))
        return ::async
    }
    fun attach(index: Int, context: CoroutineContext, step: suspend LiveDataScope<T>.() -> Unit, capture: ((T?) -> Any?)? = null): () -> LiveData<T>? {
        fun async() = liveData(context, block = step)
        attach(index, Pair(::async, capture))
        return ::async
    }
    fun attachBefore(step: Pair<() -> LiveData<T>?, ((T?) -> Any?)?>) {
        ln = (if (ln > seq.size) ln else ln + 1).also { attach(before, step) }
    }
    fun attachOnceBefore(step: Pair<() -> LiveData<T>?, ((T?) -> Any?)?>) {
        if (ln > 1 && seq[ln - 1].isNotSameStep(step))
            attachBefore(step)
    }
    fun attachBefore(step: suspend LiveDataScope<T>.() -> Unit, capture: ((T?) -> Any?)? = null): () -> LiveData<T>? {
        fun async() = liveData(block = step)
        attachBefore(Pair(::async, capture))
        return ::async
    }
    fun attachBefore(context: CoroutineContext, step: suspend LiveDataScope<T>.() -> Unit, capture: ((T?) -> Any?)? = null): () -> LiveData<T>? {
        fun async() = liveData(context, block = step)
        attachBefore(Pair(::async, capture))
        return ::async
    }
    fun attachAfter(step: Pair<() -> LiveData<T>?, ((T?) -> Any?)?>) =
        attach(after, step)
    fun attachOnceAfter(step: Pair<() -> LiveData<T>?, ((T?) -> Any?)?>) {
        (ln + 1).let {
            if (it < seq.size && seq[it].isNotSameStep(step))
                attach(it, step)
        }
    }
    fun attachAfter(step: suspend LiveDataScope<T>.() -> Unit, capture: ((T?) -> Any?)? = null): () -> LiveData<T>? {
        fun async() = liveData(block = step)
        attachAfter(Pair(::async, capture))
        return ::async
    }
    fun attachAfter(context: CoroutineContext, step: suspend LiveDataScope<T>.() -> Unit, capture: ((T?) -> Any?)? = null): () -> LiveData<T>? {
        fun async() = liveData(context, block = step)
        attachAfter(Pair(::async, capture))
        return ::async
    }

    fun io(step: suspend LiveDataScope<T>.() -> Unit, capture: ((T?) -> Any?)? = null) =
        attach(Dispatchers.IO, step, capture)
    fun io(index: Int, step: suspend LiveDataScope<T>.() -> Unit, capture: ((T?) -> Any?)? = null) =
        attach(index, Dispatchers.IO, step, capture)
    fun ioBefore(step: suspend LiveDataScope<T>.() -> Unit, capture: ((T?) -> Any?)? = null) =
        attachBefore(Dispatchers.IO, step, capture)
    fun ioAfter(step: suspend LiveDataScope<T>.() -> Unit, capture: ((T?) -> Any?)? = null) =
        attachAfter(Dispatchers.IO, step, capture)

    fun unconfined(step: suspend LiveDataScope<T>.() -> Unit, capture: ((T?) -> Any?)? = null) =
        attach(Dispatchers.Unconfined, step, capture)
    fun unconfined(index: Int, step: suspend LiveDataScope<T>.() -> Unit, capture: ((T?) -> Any?)? = null) =
        attach(index, Dispatchers.Unconfined, step, capture)
    fun unconfinedBefore(step: suspend LiveDataScope<T>.() -> Unit, capture: ((T?) -> Any?)? = null) =
        attachBefore(Dispatchers.Unconfined, step, capture)
    fun unconfinedAfter(step: suspend LiveDataScope<T>.() -> Unit, capture: ((T?) -> Any?)? = null) =
        attachAfter(Dispatchers.Unconfined, step, capture)

    fun capture(block: (T?) -> Any?) = attach({ null } to block)
    fun captureOnce(block: (T?) -> Any?) {
        if (isNotAttached(block))
            attach({ null } to block)
    }
    fun capture(index: Int, block: (T?) -> Any?) = attach(index, { null } to block)
    fun captureOnce(index: Int, block: (T?) -> Any?) {
        if (isNotAttached(index, block))
            attach(index, { null } to block)
    }
    fun captureBefore(block: (T?) -> Any?) = attachBefore({ null } to block)
    fun captureOnceBefore(block: (T?) -> Any?) {
        if (ln > 1 && seq[ln - 1].second !== block)
            attachBefore({ null } to block)
    }
    fun captureAfter(block: (T?) -> Any?) = attachAfter({ null } to block)
    fun captureOnceAfter(block: (T?) -> Any?) {
        (ln + 1).let {
            if (it < seq.size && seq[it].second !== block)
                attach(it, { null } to block)
        }
    }

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
        if (ln > seq.size)
            seq.clear()
        else if (ln > 0) {
            seq = MutableList(seq.size - ln) { seq[it + ln] }
            ln = -1
        }
    }

    fun end() {}

    override fun onChanged(t: T) {
        capture(t)
        advance()
    }

    private fun isNotAttached(step: Pair<() -> LiveData<T>?, ((T?) -> Any?)?>) =
        none { it.isSameStep(step) }
    private fun isNotAttached(block: (T?) -> Any?) =
        none { it.second === block }
    private inline fun none(predicate: (Pair<() -> LiveData<T>?, ((T?) -> Any?)?>) -> Boolean): Boolean {
        if (seq.size > 0)
            for (i in (seq.size - 1)..0)
                if (predicate(seq[i])) return false
        return true
    }
    private fun isNotAttached(index: Int, step: Pair<() -> LiveData<T>?, ((T?) -> Any?)?>) =
        none(index) { it.isSameStep(step) }
    private fun isNotAttached(index: Int, block: (T?) -> Any?) =
        none(index) { it.second === block }
    private inline fun none(index: Int, predicate: (Pair<() -> LiveData<T>?, ((T?) -> Any?)?>) -> Boolean): Boolean {
        if (seq.size > 0) when {
            index < seq.size / 2 -> seq.none(predicate)
            else -> for (i in (seq.size - 1)..0)
                if (predicate(seq[i])) return false
        }
        return true
    }
    private val before
        get() = if (ln < 0) 0 else ln
    private val after
        get() = if (ln > seq.size) seq.size else ln + 1
}
private fun <T> Pair<() -> LiveData<T>?, ((T?) -> Any?)?>.isSameStep(step: Pair<() -> LiveData<T>?, ((T?) -> Any?)?>) =
    this === step || (first === step.first && second === step.second)
private fun <T> Pair<() -> LiveData<T>?, ((T?) -> Any?)?>.isNotSameStep(step: Pair<() -> LiveData<T>?, ((T?) -> Any?)?>) =
    this !== step || first !== step.first || second !== step.second


suspend inline fun <T> LiveDataScope<T?>.nullOnError(block: LiveDataScope<T?>.() -> Unit) {
    try { block() }
    catch (ex: Throwable) {
        if (ex !is CancellationException)
            emit(null)
        throw ex
    }
}
suspend inline fun LiveDataScope<Any?>.unitOnError(block: LiveDataScope<Any?>.() -> Unit) {
    try { block() }
    catch (ex: Throwable) {
        if (ex !is CancellationException)
            emit(Unit)
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
    if (t != null)
        block(t as T)
    else
        ln -= 1
}
inline fun <reified T> LiveDataRunner<Any?>.nonUnitOrRepeat(t: Any?, block: (T) -> Any?) {
    if (t != Unit)
        block(t as T)
    else
        ln -= 1
}
inline fun LiveDataRunner<Any?>.unitOrSkip(t: Any?, block: (Any?) -> Any?) {
    if (t == Unit) block(t)
}
