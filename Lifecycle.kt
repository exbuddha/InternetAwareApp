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
    val seq: MutableList<Pair<() -> LiveData<*>?, ((Any?) -> Any?)?>>
    var ln: Int
    var step: LiveData<*>?

    fun attach(step: () -> LiveData<*>?, capture: ((Any?) -> Any?)? = null) {
        seq.add(Pair(step, capture))
    }

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

    override fun onChanged(t: Any?) {
        capture(t)
        advance()
    }
}
