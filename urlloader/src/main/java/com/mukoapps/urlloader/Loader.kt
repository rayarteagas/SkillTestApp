package com.mukoapps.urlloader

import android.util.LruCache
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.net.URL
import java.util.concurrent.Semaphore

@Suppress("EXPERIMENTAL_FEATURE_WARNING")
class Loader {
    private val semaphore = Semaphore(5)
    var cacheSize = 20 * 1024 * 1024 // 20MiB
    private val itemsFlow = Channel<String>()
    private val mainJob = Job()

    private val cache = object : LruCache<String, DownloadableContent>(cacheSize) {
        override fun sizeOf(key: String, value: DownloadableContent): Int {
            return value.size
        }
    }

    data class DownloadTask(val downloadables: MutableList<Downloadable<*>>) {
        lateinit var task: Deferred<Unit>
    }

    private val downloadTasks = mutableMapOf<String, DownloadTask>()

    /**
     * Check if a downloadable is cached, if so, inmedatelly call loaded callback, if not cached,
     * will check if there is some task already downloading the same resource, if so, attach this
     * downloadable to that task, so it gets notified when such task finish loading the needed resource.
     *
     * If not cached and there are no tasks downloading the needed resource, push the url to the
     * "downloader" {@link #itemsFlow} channel, it will download the resouce on its turn
     */
    private fun load(downloadable: Downloadable<*>) = CoroutineScope(Dispatchers.Main).async {
        val fromCache = cache.get(downloadable.url)
        if (fromCache != null) {
            downloadable.callOnLoad(fromCache)
            return@async
        }
        if (downloadTasks.containsKey(downloadable.url)) {
            downloadTasks[downloadable.url]?.downloadables!!.add(downloadable)
        } else {
            downloadTasks[downloadable.url] = DownloadTask(mutableListOf(downloadable))
            itemsFlow.send(downloadable.url)
        }
    }

    /**
     * cancels a dowloadable (unfinished, don't review this)
     */
    fun cancel(downloadable: Downloadable<*>) {
        val urlAssociatedTask = downloadTasks[downloadable.url]
        if (urlAssociatedTask != null) {
            urlAssociatedTask.downloadables.remove(downloadable)
            if (urlAssociatedTask.downloadables.size < 1)
                urlAssociatedTask.task.cancel()
        }
    }

    /**
     * This will processs all the downloads, calling the downloadables onLoad callbacks when finished
     *.
     *
     * After downloading a url, it will call the onLoad callback for each downloadable associated
     * with the downloaded url.
     */
    private fun mainProcess() = CoroutineScope(mainJob).async {
        //I know using while(true) is a bad practice, I used it only for illustrative purposes
        while (true) {
            semaphore.acquire()
            val url = itemsFlow.receive()
            val def = async {
                val result = try {
                    URL(url).readBytes()
                } catch (e: Exception) {
                    null
                }
                if (result == null) {
                    //download failed, remove task and don't call onLoad callbacks for downloadables
                    downloadTasks.remove(url)
                    return@async
                }
                //add to cache
                cache.put(url, result)

                //call all downloadables onLoad callbacks associated with this url
                downloadTasks[url]!!.downloadables.forEach {
                    it.callOnLoad(result)
                }
                semaphore.release()
            }
            downloadTasks[url]?.task = def
        }
    }

    companion object {
        private var instance: Loader = Loader()

        init {
            instance.mainProcess()
        }

        fun load(downloadable: Downloadable<*>) {
            instance.load(downloadable)
        }

        fun cancel(downloadable: Downloadable<*>) {
            instance.cancel(downloadable)
        }

    }
}