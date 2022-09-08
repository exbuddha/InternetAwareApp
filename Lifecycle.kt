open class ReferenceLiveData<T>(protected open var ref: T?) : LiveData<Unit>() {
    open fun postChange(value: T?) {
        ref = value
        postChange()
    }

    open fun postChange() {
        super.postValue(Unit)
    }

    open fun reset() {
        ref = null
    }
}

open class DifferenceLiveData<T>(ref: T?) : ReferenceLiveData<T>(ref) {
    constructor() : this(null)

    open val isResolved
        get() = ref !== null

    open val isNotResolved
        get() = ref === null

    override fun postChange(value: T?) {
        if (ref != value) super.postChange(value)
    }
}

abstract class StatefulLiveData<T>(protected open var fallback: T) : DifferenceLiveData<T>() {
    open val state
        get() = ref ?: fallback

    open fun accept(state: T) {
        ref = state
    }
}

/** WARNING: memory leak! */
abstract class LifecycleOwnerObserver<out T : LifecycleOwner>(
    private val receiver: T,
    private val block: T.() -> Unit
) : Observer<Unit> {
    override fun onChanged(t: Unit?) {
        receiver.block()
    }
}
