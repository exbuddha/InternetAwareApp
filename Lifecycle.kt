open class DifferenceLiveData<T> : MutableLiveData<T>() {
    fun postValue() = super.postValue(value)
    override fun postValue(value: T) {
        if (value != this.value) super.postValue(value)
    }
}

abstract class DifferenceListener<T> : DifferenceLiveData<T>(), Observer<T> {
    open fun observe(owner: LifecycleOwner) = observe(owner, this)
    open fun removeObserver() = removeObserver(this)
}

interface LiveDataRunner : Observer<Any?> {
    var seq: MutableList<Pair<() -> LiveData<*>?, ((Any?) -> Any?)?>>
    var ln: Int
    var step: LiveData<*>?

    fun attach(step: () -> LiveData<*>?, capture: ((Any?) -> Any?)? = null) {
        seq.add(Pair(step, capture))
    }
    fun attach(step: suspend () -> Unit) {
        fun async() = liveData { emit(step()) }
        attach(::async)
    }
    fun attach(context: CoroutineContext, step: Pair<() -> LiveData<*>?, ((Any?) -> Any?)?>) {
        fun async() = liveData(context) { emit(step.first.invoke()) }
        attach(::async, step.second)
    }

    fun attach(index: Int, step: () -> LiveData<*>?, capture: ((Any?) -> Any?)? = null) {
        seq.add(index, Pair(step, capture))
    }
    fun attach(index: Int, step: suspend () -> Unit) {
        fun async() = liveData { emit(step()) }
        attach(index, ::async)
    }
    fun attach(index: Int, context: CoroutineContext, step: Pair<() -> LiveData<*>?, ((Any?) -> Any?)?>) {
        fun async() = liveData(context) { emit(step.first.invoke()) }
        attach(index, ::async, step.second)
    }

    fun attachImmediately(step: () -> LiveData<*>?, capture: ((Any?) -> Any?)? = null) {
        seq.add(ln + 1, Pair(step, capture))
    }
    fun attachImmediately(step: suspend () -> Unit) {
        fun async() = liveData { emit(step()) }
        attachImmediately(::async)
    }
    fun attachImmediately(context: CoroutineContext, step: Pair<() -> LiveData<*>?, ((Any?) -> Any?)?>) {
        fun async() = liveData(context) { emit(step.first.invoke()) }
        attachImmediately(::async, step.second)
    }

    fun capture(block: (Any?) -> Any?) = attach({ null }, block)
    fun capture(context: CoroutineContext, block: (Any?) -> Any?) = attach(context, { null } to block)

    fun capture(index: Int, block: (Any?) -> Any?) = attach(index, { null }, block)
    fun capture(index: Int, context: CoroutineContext, block: (Any?) -> Any?) = attach(index, context, { null } to block)

    fun captureImmediately(block: (Any?) -> Any?) = attachImmediately({ null }, block)
    fun captureImmediately(context: CoroutineContext, block: (Any?) -> Any?) = attachImmediately(context, { null } to block)

    fun start(): Boolean {
        ln = -1
        return advance()
    }

    fun resume(): Boolean {
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
        return false
    }

    fun capture(t: Any?) {
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

    override fun onChanged(t: Any?) {
        capture(t)
        advance()
    }
}
