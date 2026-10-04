package com.student.quiz

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.student.quiz.ui.AppViewModel
import com.student.quiz.ui.EndedScreen
import com.student.quiz.ui.JoinScreen
import com.student.quiz.ui.LobbyScreen
import com.student.quiz.ui.QuizScreen
import com.student.quiz.ui.Screen
import com.student.quiz.ui.ScanScreen
import com.student.quiz.ui.theme.QuizStudentTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QuizStudentTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    QuizApp()
                }
            }
        }
    }
}

@Composable
private fun QuizApp(vm: AppViewModel = viewModel()) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The camera permission is requested only when "Scan QR" is tapped.
    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) vm.openScanner() else vm.onCameraDenied()
    }
    val onScanClicked: () -> Unit = {
        val already = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (already) vm.openScanner() else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    when (val screen = state.screen) {
        is Screen.Join -> JoinScreen(state, vm, onScanClicked)
        is Screen.Scan -> ScanScreen(vm)
        is Screen.Lobby -> LobbyScreen(screen.name, state.conn, vm)
        // quiz_start sets both together, so this is only a guard against a blank
        // frame between the quiz ending and the screen switching.
        is Screen.Quiz -> state.quiz?.let { QuizScreen(it, state.conn, vm) }
        // No score and no answers: the server is authoritative and says nothing yet.
        Screen.Ended -> EndedScreen(vm)
    }
}