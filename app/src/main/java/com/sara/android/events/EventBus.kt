package com.sara.android.events

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

object EventBus {
    
    private val TAG = "EventBus"
    
    private class Subscription<T : Any>(
        val owner: Any,
        val handler: (T) -> Unit
    )
    
    private val subscribers = ConcurrentHashMap<Class<*>, CopyOnWriteArrayList<Subscription<*>>>()
    
    // Shared executor for asynchronous event dispatch to not block the caller thread
    private val executor = Executors.newCachedThreadPool()

    fun publish(event: Any) {
        val eventClass = event.javaClass
        
        executor.execute {
            // Dispatch to specific event class listeners
            subscribers[eventClass]?.forEach { 
                try {
                    @Suppress("UNCHECKED_CAST")
                    (it as Subscription<Any>).handler(event)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in listener for event ${eventClass.simpleName}", e)
                }
            }
            
            // Dispatch to base Event listeners
            if (eventClass != Event::class.java && event is Event) {
                subscribers[Event::class.java]?.forEach { 
                    try {
                        @Suppress("UNCHECKED_CAST")
                        (it as Subscription<Any>).handler(event)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in base listener for event ${eventClass.simpleName}", e)
                    }
                }
            }
        }
    }

    fun <T : Any> subscribe(owner: Any, eventClass: Class<T>, handler: (T) -> Unit) {
        subscribers.getOrPut(eventClass) { CopyOnWriteArrayList() }.add(Subscription(owner, handler))
    }

    inline fun <reified T : Event> subscribe(owner: Any, crossinline handler: (T) -> Unit) {
        subscribe(owner, T::class.java) { handler(it) }
    }

    fun unsubscribeAll(owner: Any) {
        subscribers.values.forEach { list ->
            list.removeIf { it.owner === owner }
        }
    }

    fun clear() {
        subscribers.clear()
    }
}
