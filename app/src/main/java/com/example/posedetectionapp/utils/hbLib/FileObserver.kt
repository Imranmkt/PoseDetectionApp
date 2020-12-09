package com.example.posedetectionapp.utils.hbLib

import android.app.Activity
import android.os.FileObserver
import com.example.posedetectionapp.utils.MyListener
import java.io.File
import java.util.*

internal class FileObserver(private val mPath: String, val activity: Activity, private val myListener: MyListener) : FileObserver(mPath, ALL_EVENTS) {

    private var mObservers: MutableList<SingleFileObserver>? = null
    private val mMask: Int = ALL_EVENTS

    override fun startWatching() {
        if (mObservers != null) return
        mObservers = ArrayList()
        val stack = Stack<String>()
        stack.push(mPath)
        while (!stack.isEmpty()) {
            val parent = stack.pop()
            (mObservers as ArrayList<SingleFileObserver>).add(SingleFileObserver(parent, mMask))
            val path = File(parent)
            val files = path.listFiles() ?: continue
            for (f in files) {
                if (f.isDirectory && f.name != "." && f.name != "..") {
                    stack.push(f.path)
                }
            }
        }
        for (sfo in mObservers as ArrayList<SingleFileObserver>) {
            sfo.startWatching()
        }
    }

    override fun stopWatching() {
        if (mObservers == null) return
        for (sfo in mObservers!!) sfo.stopWatching()
        mObservers!!.clear()
        mObservers = null
    }

    override fun onEvent(event: Int, path: String?) {
        if (event == CLOSE_WRITE)
            activity.runOnUiThread { myListener.callback() }
    }

    internal inner class SingleFileObserver(private val mPath: String, mask: Int) : FileObserver(mPath, mask) {
        override fun onEvent(event: Int, path: String?) {
            val newPath = "$mPath/$path"
            this@FileObserver.onEvent(event, newPath)
        }
    }

}