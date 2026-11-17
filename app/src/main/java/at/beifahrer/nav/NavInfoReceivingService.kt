package at.beifahrer.nav

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import at.beifahrer.BeifahrerApp
import com.google.android.libraries.mapsplatform.turnbyturn.TurnByTurnManager

/**
 * Das Navigation SDK schickt hierher laufend den nächsten Abbiege-Schritt.
 * Wir nutzen ihn für das Banner am Handy und die Navigationsvorlage in Android Auto.
 */
class NavInfoReceivingService : Service() {

    private val messenger by lazy { Messenger(IncomingHandler(Looper.getMainLooper(), application as BeifahrerApp)) }

    override fun onBind(intent: Intent): IBinder = messenger.binder

    private class IncomingHandler(looper: Looper, private val app: BeifahrerApp) : Handler(looper) {
        private val manager = TurnByTurnManager.createInstance()
        override fun handleMessage(msg: Message) {
            if (msg.what == TurnByTurnManager.MSG_NAV_INFO) {
                manager.readNavInfoFromBundle(msg.data)?.let { app.controller.onNavInfo(it) }
            } else super.handleMessage(msg)
        }
    }
}
