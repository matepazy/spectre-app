package com.matepazy.spectre

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.matepazy.spectre.ui.screens.SpectreAppContainer
import com.matepazy.spectre.ui.theme.SpectreTheme
import com.matepazy.spectre.viewmodel.SpectreViewModel

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        setContent {
            SpectreTheme {
                val viewModel: SpectreViewModel = viewModel()
                SpectreAppContainer(viewModel = viewModel)
            }
        }
    }
}
