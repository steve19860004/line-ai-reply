package tw.local.lineai

import android.app.Application
import android.os.Handler
import android.os.Looper
import tw.local.lineai.core.Prefs
import java.util.concurrent.Executors

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        app = this
        Prefs.init(this)
    }
    companion object {
        lateinit var app: App; private set
        private val main = Handler(Looper.getMainLooper())
        private val pool = Executors.newFixedThreadPool(2)
        fun bg(block: () -> Unit) { pool.execute { runCatching(block) } }
        fun ui(block: () -> Unit) { main.post(block) }
    }
}
