package com.sara.android.events

object EventBus {

    private val subscribers = mutableMapOf<Class<*>, MutableList<(Any) -> Unit>>()

    fun publish(event: Any) {
        val eventClass = event.javaClass
        subscribers[eventClass]?.forEach { handler ->
            handler(event)
        }

        subscribers[Event::class.java]?.forEach { handler ->
            handler(event)
        }
    }

    fun <T : Any> subscribe(eventClass: Class<T>, handler: (T) -> Unit) {
        subscribers.getOrPut(eventClass) { mutableListOf() }.add { handler(it as T) }
    }

    fun <T : Any> unsubscribe(eventClass: Class<T>, handler: (T) -> Unit) {
        subscribers[eventClass]?.remove(handler)
    }

    fun clear() {
        subscribers.clear()
    }
}
