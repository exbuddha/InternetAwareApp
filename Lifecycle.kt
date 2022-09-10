open class DifferenceLiveData<T> : MutableLiveData<T>() {
    override fun postValue(value: T) {
        if (value != this.value) super.postValue(value)
    }

    fun postValue() = super.postValue(value)
}

abstract class DifferenceListener<T> : DifferenceLiveData<T>(), Observer<T> {
    open fun observe(owner: LifecycleOwner) = observe(owner, this)
    open fun removeObserver() = removeObserver(this)
}
