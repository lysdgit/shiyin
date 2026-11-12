package com.shiyin.player.util

import android.content.Context
import java.io.File
import java.util.Date

/** 崩溃日志落盘:任何未捕获异常追加写入 filesDir/crash.log,供设置页导出排查 */
object CrashLog {

    private var previous: Thread.UncaughtExceptionHandler? = null

    fun install(context: Context) {
        val appContext = context.applicationContext
        previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                File(appContext.filesDir, "crash.log").appendText(
                    "\n==== ${Date()} thread=${t.name} ====\n" +
                        android.util.Log.getStackTraceString(e) + "\n"
                )
            }
            previous?.uncaughtException(t, e)
        }
    }

    fun file(context: Context): File = File(context.applicationContext.filesDir, "crash.log")
}
