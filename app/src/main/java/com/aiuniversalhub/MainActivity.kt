package com.aiuniversalhub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.aiuniversalhub.integrations.ClaudeCodeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HubApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubApp() {
    var isArabic by remember { mutableStateOf(true) }
    val shellColors = darkColorScheme(
        primary = Color(0xFFD6B56D),
        onPrimary = Color(0xFF100D07),
        secondary = Color(0xFFE7C982),
        background = Color(0xFF080808),
        onBackground = Color(0xFFF5F0E3),
        surface = Color(0xFF15120D),
        onSurface = Color(0xFFF5F0E3)
    )

    MaterialTheme(colorScheme = shellColors) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color(0xFF080808),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (isArabic) "AI Universal Hub • Claude Code" else "AI Universal Hub • Claude Code",
                            color = shellColors.primary
                        )
                    },
                    actions = {
                        TextButton(onClick = { isArabic = !isArabic }) {
                            Text(if (isArabic) "English" else "العربية", color = shellColors.primary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF050505),
                        titleContentColor = shellColors.primary,
                        actionIconContentColor = shellColors.primary
                    )
                )
            }
        ) { padding ->
            ClaudeCodeScreen(
                isArabic = isArabic,
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        }
    }
}
