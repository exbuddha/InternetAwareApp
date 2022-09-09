open class VolatileLiveData<T>(private var ref: T) : MutableLiveData<T>() {
    override fun postValue(value: T) {
        setValue(value)
        super.postValue(value)
    }

    override fun setValue(value: T) {
        ref = value
    }

    fun postChange() = postValue(ref)
}

open class DifferenceLiveData<T>(ref: T) : VolatileLiveData<T>(ref) {
    override fun postValue(value: T) {
        if (this.value != value) super.postValue(value)
    }
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
