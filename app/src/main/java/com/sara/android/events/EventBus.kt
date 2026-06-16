package com.sara.android.events

object EventBus {

    private val subscribers = mutableMapOf<Class<*>, MutableList<(Any) -> Unit>>()

    fun publish(event: Any) {
        val eventClass = event.javaClass
        subscribers[eventClass]?.forEach { it(event) }
        if (eventClass != Event::class.java) {
            subscribers[Event::class.java]?.forEach { it(event) }
        }
    }

    fun <T : Any> subscribe(eventClass: Class<T>, handler: (T) -> Unit) {
        subscribers.getOrPut(eventClass) { mutableListOf() }.add { handler(it as T) }
    }

    inline fun <reified T : Event> subscribe(crossinline handler: (T) -> Unit) {
        subscribe(T::class.java) { handler(it) }
    }

    fun <T : Any> unsubscribe(eventClass: Class<T>, handler: (T) -> Unit) {
        subscribers[eventClass]?.remove(handler)
    }

    fun clear() {
        subscribers.clear()
    }
}
