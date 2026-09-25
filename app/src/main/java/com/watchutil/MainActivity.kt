package com.watchutil

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.watchutil.ui.WatchUtilApp

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            WatchUtilApp(viewModel)
        }
    }

    override fun onResume() {
        super.onResume()
        // The bridge may have been started or killed while we were away.
        viewModel.refreshBackend()
    }
}
