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

    fun attach(step: Pair<() -> LiveData<*>?, ((Any?) -> Any?)?>) {
        seq.add(step)
    }
    fun attach(step: () -> LiveData<*>?, capture: ((Any?) -> Any?)? = null) {
        attach(Pair(step, capture))
    }
    fun <T> attach(step: suspend LiveDataScope<T>.() -> Unit, capture: ((Any?) -> Any?)? = null) {
        fun async() = liveData(block = step)
        attach(::async, capture)
    }
    fun <T> attach(context: CoroutineContext, step: suspend LiveDataScope<T>.() -> Unit, capture: ((Any?) -> Any?)? = null) {
        fun async() = liveData(context, block = step)
        attach(::async, capture)
    }

    fun attach(index: Int, step: Pair<() -> LiveData<*>?, ((Any?) -> Any?)?>) {
        seq.add(index, step)
    }
    fun attach(index: Int, step: () -> LiveData<*>?, capture: ((Any?) -> Any?)? = null) {
        attach(index, Pair(step, capture))
    }
    fun <T> attach(index: Int, step: suspend LiveDataScope<T>.() -> Unit, capture: ((Any?) -> Any?)? = null) {
        fun async() = liveData(block = step)
        attach(index, ::async, capture)
    }
    fun <T> attach(index: Int, context: CoroutineContext, step: suspend LiveDataScope<T>.() -> Unit, capture: ((Any?) -> Any?)? = null) {
        fun async() = liveData(context, block = step)
        attach(index, ::async, capture)
    }

    fun attachImmediately(step: Pair<() -> LiveData<*>?, ((Any?) -> Any?)?>) {
        attach(ln + 1, step)
    }
    fun attachImmediately(step: () -> LiveData<*>?, capture: ((Any?) -> Any?)? = null) {
        attachImmediately(Pair(step, capture))
    }
    fun <T> attachImmediately(step: suspend LiveDataScope<T>.() -> Unit, capture: ((Any?) -> Any?)? = null) {
        fun async() = liveData(block = step)
        attachImmediately(::async, capture)
    }
    fun <T> attachImmediately(context: CoroutineContext, step: suspend LiveDataScope<T>.() -> Unit, capture: ((Any?) -> Any?)? = null) {
        fun async() = liveData(context, block = step)
        attachImmediately(::async, capture)
    }

    fun capture(block: (Any?) -> Any?) = attach({ null } to block)
    fun capture(index: Int, block: (Any?) -> Any?) = attach(index, { null } to block)
    fun captureImmediately(block: (Any?) -> Any?) = attachImmediately({ null } to block)

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
