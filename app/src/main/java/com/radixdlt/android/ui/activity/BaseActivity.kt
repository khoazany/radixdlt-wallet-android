package com.radixdlt.android.ui.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.radixdlt.android.R
import com.radixdlt.android.RadixWalletApplication
import com.radixdlt.android.identity.AndroidRadixIdentity
import com.radixdlt.android.identity.Identity
import com.radixdlt.android.util.PREF_SECRET
import com.radixdlt.android.util.QueryPreferences
import com.radixdlt.android.util.Vault
import com.radixdlt.client.core.crypto.ECKeyPair
import okio.ByteString
import org.jetbrains.anko.startActivity

@SuppressLint("Registered")
open class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        if (this !is NewWalletActivity) setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)

        // If launching from uri intent, for now make sure to finish all activities if the flow
        // is starting from NewWalletActivity.
        // This avoids going into onResume if app was in recents/background state which
        // would trigger app to start a new activity if auto lock was activated and end up
        // with a duplicate password screen causing problems.
        (this as? NewWalletActivity)?.let {
            if (QueryPreferences.getPrefPasswordEnabled(this) &&
                intent.action != null && intent.action == Intent.ACTION_VIEW
            ) {
                QueryPreferences.setPrefAddress(this, "")
                finishAffinity()
            }
            return
        }

        if (!QueryPreferences.getPrefPasswordEnabled(this) && Identity.myIdentity == null) {
            val privateKeyDecoded = ByteString.decodeHex(
                Vault.getVault().getString(PREF_SECRET, null)!!
            ).toByteArray()
            Identity.myIdentity = AndroidRadixIdentity(ECKeyPair(privateKeyDecoded))
        } else if (Identity.myIdentity == null) {
            QueryPreferences.setPrefAddress(this, "")

            startActivity<NewWalletActivity>()
            finishAffinity()
        }
    }

    override fun onResume() {
        super.onResume()

        // If not logged in
        if (QueryPreferences.getPrefAddress(this).isBlank()) return

        if ((RadixWalletApplication.wasInBackground && !openedShareDialog &&
                !openedPermissionDialog && QueryPreferences.getPrefPasswordEnabled(this)) ||
            lockActive) {
            QueryPreferences.setPrefAddress(this, "")
            lockActive = true
            startActivity<NewWalletActivity>() // With persistence we could probably finish activities and restart from first screen
        }

        (this.application as RadixWalletApplication).stopActivityTransitionTimer()
    }

    override fun onPause() {
        super.onPause()
        if (this is NewWalletActivity || QueryPreferences.getPrefAddress(this).isBlank()) {
            return
        }
        (this.application as RadixWalletApplication).startActivityTransitionTimer()
    }

    override fun onDestroy() {
        (this.application as RadixWalletApplication).stopActivityTransitionTimer()
        super.onDestroy()
    }

    companion object {
        internal var lockActive: Boolean = false
        internal var openedShareDialog = false
        @JvmField
        internal var openedPermissionDialog = false
    }
}
