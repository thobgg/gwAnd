package de.bgghome.gwand

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GwRoot(viewModel) }
    }
}
