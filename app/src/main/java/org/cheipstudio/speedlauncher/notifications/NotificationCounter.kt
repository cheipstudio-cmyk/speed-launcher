package org.cheipstudio.speedlauncher.notifications

import androidx.lifecycle.MutableLiveData

class NotificationCounter {
    val counts = MutableLiveData<Map<String, Int>>(emptyMap())

    fun update(newCounts: Map<String, Int>) {
        counts.postValue(newCounts)
    }

    fun countFor(packageName: String): Int {
        return counts.value?.get(packageName) ?: 0
    }
}
