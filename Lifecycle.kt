abstract class MutableLiveDataObserver<T> : MutableLiveData<T>(), Observer<T> {
    open fun observe(owner: LifecycleOwner) = observe(owner, this)
    open fun removeObserver() = removeObserver(this)
}

interface LiveDataRunner<T> : Observer<T> {
    var seq: MutableList<Pair<() -> LiveData<T>?, ((T?) -> Any?)?>>
    var ln: Int
    var step: LiveData<T>?
    var lastCapture: Any?

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
        if (ln < -1) ln = -1
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

    fun block(t: T) {
        lastCapture = seq[ln].second?.invoke(t)
        reset()
    }

    fun reset(step: LiveData<T>? = this.step) {
        step?.removeObserver(this)
    }

    fun clear() {
        if (ln < 0)
            seq.clear()
        else
            seq = MutableList(ln + 1) { seq[it] }
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
        block(t)
        advance()
    }

    fun attach(step: Pair<() -> LiveData<T>?, ((T?) -> Any?)?>) {
        seq.add(step)
    }
    fun attachOnce(step: Pair<() -> LiveData<T>?, ((T?) -> Any?)?>) {
        if (isNotAttached(step))
            attach(step)
    }
    fun attachOnce(range: IntRange, step: Pair<() -> LiveData<T>?, ((T?) -> Any?)?>) {
        if (isNotAttached(range, step))
            attach(step)
    }
    fun attachOnce(first: Int, last: Int, step: Pair<() -> LiveData<T>?, ((T?) -> Any?)?>) {
        if (isNotAttached(first, last, step))
            attach(step)
    }
    fun attach(index: Int, step: Pair<() -> LiveData<T>?, ((T?) -> Any?)?>) {
        if (index <= ln)
            ln = if (ln > seq.size) ln else ln + 1
        seq.add(index, step)
    }
    fun attachOnce(index: Int, step: Pair<() -> LiveData<T>?, ((T?) -> Any?)?>) {
        if (isNotAttached(index, step))
            seq.add(index, step)
    }
    fun attachOnce(range: IntRange, index: Int, step: Pair<() -> LiveData<T>?, ((T?) -> Any?)?>) {
        if (isNotAttached(range, index, step))
            attach(index, step)
    }
    fun attachOnce(first: Int, last: Int, index: Int, step: Pair<() -> LiveData<T>?, ((T?) -> Any?)?>) {
        if (isNotAttached(first, last, index, step))
            attach(index, step)
    }
    fun attachBefore(step: Pair<() -> LiveData<T>?, ((T?) -> Any?)?>) {
        attach(before, step)
    }
    fun attachOnceBefore(step: Pair<() -> LiveData<T>?, ((T?) -> Any?)?>) {
        attachOnce(before, step)
    }
    fun attachAfter(step: Pair<() -> LiveData<T>?, ((T?) -> Any?)?>) {
        attach(after, step)
    }
    fun attachOnceAfter(step: Pair<() -> LiveData<T>?, ((T?) -> Any?)?>) {
        attachOnce(after, step)
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

    fun capture(block: (T?) -> Any?) {
        attach(nullStep to block)
    }
    fun captureOnce(block: (T?) -> Any?) {
        if (isNotAttached(block))
            capture(block)
    }
    fun captureOnce(range: IntRange, block: (T?) -> Any?) {
        if (isNotAttached(range, block))
            capture(block)
    }
    fun captureOnce(first: Int, last: Int, block: (T?) -> Any?) {
        if (isNotAttached(first, last, block))
            capture(block)
    }
    fun capture(index: Int, block: (T?) -> Any?) {
        attach(index, nullStep to block)
    }
    fun captureOnce(index: Int, block: (T?) -> Any?) {
        if (isNotAttached(index, block))
            capture(index, block)
    }
    fun captureOnce(range: IntRange, index: Int, block: (T?) -> Any?) {
        if (isNotAttached(range, index, block))
            capture(block)
    }
    fun captureOnce(first: Int, last: Int, index: Int, block: (T?) -> Any?) {
        if (isNotAttached(first, last, index, block))
            capture(block)
    }
    fun captureBefore(block: (T?) -> Any?) {
        attachBefore(nullStep to block)
    }
    fun captureOnceBefore(block: (T?) -> Any?) {
        captureOnce(before, block)
    }
    fun captureAfter(block: (T?) -> Any?) {
        attachAfter(nullStep to block)
    }
    fun captureOnceAfter(block: (T?) -> Any?) {
        captureOnce(after, block)
    }
    val nullStep: () -> LiveData<T>?

    fun <T> Pair<() -> LiveData<T>?, ((T?) -> Any?)?>.isSameStep(step: Pair<() -> LiveData<T>?, ((T?) -> Any?)?>) =
        this === step || (first === step.first && second === step.second)
    fun <T> Pair<() -> LiveData<T>?, ((T?) -> Any?)?>.isNotSameStep(step: Pair<() -> LiveData<T>?, ((T?) -> Any?)?>) =
        this !== step || first !== step.first || second != step.second

    private fun isNotAttached(step: Pair<() -> LiveData<T>?, ((T?) -> Any?)?>) =
        seq.fails { it.isSameStep(step) }
    private fun isNotAttached(range: IntRange, step: Pair<() -> LiveData<T>?, ((T?) -> Any?)?>): Boolean {
        range.forEach { if (seq[it].isSameStep(step)) return false }
        return true
    }
    private fun isNotAttached(first: Int, last: Int, step: Pair<() -> LiveData<T>?, ((T?) -> Any?)?>): Boolean {
        for (i in first..last) if (seq[i].isSameStep(step)) return false
        return true
    }
    private fun isNotAttached(index: Int, step: Pair<() -> LiveData<T>?, ((T?) -> Any?)?>) =
        none(index) { it.isSameStep(step) }
    private fun isNotAttached(range: IntRange, index: Int, step: Pair<() -> LiveData<T>?, ((T?) -> Any?)?>) =
        if (range.isEmpty()) true
        else when {
            index - range.first <= range.last - index -> range.none { seq[it].isSameStep(step) }
            else -> range.fails { seq[it].isSameStep(step) }
        }
    private fun isNotAttached(first: Int, last: Int, index: Int, step: Pair<() -> LiveData<T>?, ((T?) -> Any?)?>) =
        if (first < last) true
        else when {
            index - first <= last - index -> seq.none { it.isSameStep(step) }
            else -> seq.fails { it.isSameStep(step) }
        }
    private fun isNotAttached(block: (T?) -> Any?) =
        seq.fails { it.second === block }
    private fun isNotAttached(range: IntRange, block: (T?) -> Any?): Boolean {
        range.forEach { if (seq[it].second === block) return false }
        return true
    }
    private fun isNotAttached(first: Int, last: Int, block: (T?) -> Any?): Boolean {
        for (i in first..last) if (seq[i].second === block) return false
        return true
    }
    private fun isNotAttached(index: Int, block: (T?) -> Any?) =
        none(index) { it.second === block }
    private fun isNotAttached(range: IntRange, index: Int, block: (T?) -> Any?) =
        if (range.isEmpty()) true
        else when {
            index - range.first <= range.last - index -> range.none { seq[it].second === block }
            else -> range.fails { seq[it].second === block }
        }
    private fun isNotAttached(first: Int, last: Int, index: Int, block: (T?) -> Any?) =
        if (first < last) true
        else when {
            index - first <= last - index -> seq.none { it.second === block }
            else -> seq.fails { it.second === block }
        }
    private inline fun none(index: Int, predicate: (Pair<() -> LiveData<T>?, ((T?) -> Any?)?>) -> Boolean) = when {
        index < seq.size / 2 -> seq.none(predicate)
        else -> seq.fails(predicate)
    }
    private inline fun MutableList<Pair<() -> LiveData<T>?, ((T?) -> Any?)?>>.fails(predicate: (Pair<() -> LiveData<T>?, ((T?) -> Any?)?>) -> Boolean): Boolean {
        if (size == 0) return true
        for (i in (size - 1) downTo 0) if (predicate(this[i])) return false
        return true
    }
    private inline fun IntRange.fails(predicate: (Int) -> Boolean): Boolean {
        reversed().forEach { if (predicate(it)) return false }
        return true
    }

    val leading
        get() = 0 until if (ln < seq.size) ln else seq.size
    val trailing
        get() = (if (ln < 0) 0 else ln + 1) until seq.size

    private val before
        get() = when {
            ln <= 0 -> 0
            ln < seq.size -> ln - 1
            else -> seq.size
        }
    private val after
        get() = when {
            ln < 0 -> 0
            ln < seq.size -> ln + 1
            else -> seq.size
        }
}

class AutoResetException(msg: String? = null, cause: Throwable? = null) : RuntimeException(msg, cause)
