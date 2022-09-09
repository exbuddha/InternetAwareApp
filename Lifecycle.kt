open class DifferenceLiveData<T>() : MutableLiveData<T>() {
    constructor(value: T) : this() {
        super.postValue(value)
    }

    override fun postValue(value: T) {
        if (value != this.value) super.postValue(value)
    }

    fun postValue() = super.postValue(value)
}

/** WARNING: memory leak! */
abstract class LifecycleOwnerObserver<out T : LifecycleOwner, R>(
    private val receiver: T,
    private val block: T.(R) -> Unit
) : Observer<R> {
    override fun onChanged(t: R) {
        receiver.block(t)
    }
}
