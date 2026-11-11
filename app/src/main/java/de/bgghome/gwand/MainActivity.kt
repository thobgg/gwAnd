package de.bgghome.gwand

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.bgghome.gwand.ui.GwRoot
import de.bgghome.gwand.ui.GwViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: GwViewModel by viewModels {
        viewModelFactory { initializer { GwViewModel((application as GwApp).plattform) } }
    }

    private val erlaubnis = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (application as GwApp).berechtigungAnfragen = {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                erlaubnis.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        enableEdgeToEdge()
        if (savedInstanceState == null) geteilterText(intent)
        setContent { GwRoot(viewModel) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        geteilterText(intent)
    }

    /** Text aus dem Teilen-Menue einer anderen App: ein Link auf eine Person des eigenen Servers oeffnet ihr Profil. */
    private fun geteilterText(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        viewModel.linkOeffnen(intent.getStringExtra(Intent.EXTRA_TEXT))
        intent.removeExtra(Intent.EXTRA_TEXT)
        intent.action = null
    }
}
