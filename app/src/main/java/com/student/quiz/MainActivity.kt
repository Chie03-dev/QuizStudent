package com.student.quiz

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.student.quiz.data.LockCommand
import com.student.quiz.net.ConnState
import com.student.quiz.ui.AppViewModel
import com.student.quiz.ui.EndedScreen
import com.student.quiz.ui.FinishedScreen
import com.student.quiz.ui.JoinScreen
import com.student.quiz.ui.LobbyScreen
import com.student.quiz.ui.PausedScreen
import com.student.quiz.ui.QuizScreen
import com.student.quiz.ui.Screen
import com.student.quiz.ui.ScanScreen
import com.student.quiz.ui.theme.QuizStudentTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    /** Pinned with startLockTask() while the quiz runs with lock on. */
    private var pinned = false

    /**
     * Polls getLockTaskModeState() while the quiz runs. The student can leave
     * screen pinning with the phone's button combination, and there is no
     * callback for that, so the state has to be looked at.
     */
    private val pinPoll = object : Runnable {
        override fun run() {
            reportPinState()
            if (viewModel.quizRunning()) handler.postDelayed(this, PIN_POLL_MS)
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The pin poll is the only source of truth for real lock task mode, and
        // there is no callback for the student leaving screen pinning, so it has
        // to run the whole time the quiz runs. onResume alone is not enough:
        // students sit in the lobby (already resumed) when the instructor starts,
        // and quiz_start arrives over the socket without another onResume. So
        // drive the poll off the running flag, not off the lifecycle.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.ui
                    .map { it.lock.running }
                    .distinctUntilChanged()
                    .collect { running ->
                        handler.removeCallbacks(pinPoll)
                        if (running) {
                            // Read once now: a pin just requested (grace window
                            // still open) must not be read as an unpin yet, which
                            // reportPinState already guards for via the grace flag.
                            reportPinState()
                            handler.postDelayed(pinPoll, PIN_POLL_MS)
                        }
                    }
            }
        }
        // FLAG_SECURE itself is applied and removed by QuizApp, which knows
        // when the quiz starts and ends.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // The only Android part of pinning: everything else about lock
                // is decided by LockController and sent by the ViewModel.
                viewModel.pinCommands.collect { command ->
                    // Only pin commands reach this flow; focus messages are
                    // sent by the ViewModel itself.
                    when (command) {
                        // The collector runs on the main dispatcher
                        // (lifecycleScope), so both calls happen on the main
                        // thread. The real lock task state is only polled
                        // about once a second, so check it right here before
                        // calling: the controller's commands may race the
                        // poll, and stopLockTask() throws when the activity
                        // is not in lock task mode.
                        LockCommand.StartPin -> runCatching {
                            if (lockTaskModeState() == ActivityManager.LOCK_TASK_MODE_NONE) {
                                startLockTask()
                            }
                            pinned = true
                        }.onFailure {
                            Log.w(TAG, "startLockTask failed: ${it.message}")
                        }
                        LockCommand.StopPin -> runCatching {
                            if (lockTaskModeState() != ActivityManager.LOCK_TASK_MODE_NONE) {
                                stopLockTask()
                            }
                            pinned = false
                        }.onFailure {
                            Log.w(TAG, "stopLockTask failed: ${it.message}")
                        }
                        else -> Unit
                    }
                }
            }
        }
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The notification shade and the recents switcher both take the window
        // focus without stopping the activity, so this is its own event.
        viewModel.onWindowFocusChanged(hasFocus)
        // Coming back with lock on but no lock task means unpinning.
        if (hasFocus) reportPinState()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onWindowFocusChanged(true)
        viewModel.onStopped(false)
        reportPinState()
        handler.removeCallbacks(pinPoll)
        if (viewModel.quizRunning()) handler.postDelayed(pinPoll, PIN_POLL_MS)
    }

    override fun onPause() {
        super.onPause()
        viewModel.onStopped(true)
    }

    override fun onStop() {
        super.onStop()
        viewModel.onStopped(true)
        handler.removeCallbacks(pinPoll)
    }

    /**
     * Reads the real lock task mode and hands it to the controller.
     */
    private fun reportPinState() {
        if (!viewModel.quizRunning()) return
        val state = lockTaskModeState()
        val nowPinned = state != ActivityManager.LOCK_TASK_MODE_NONE
        if (nowPinned != pinned && !nowPinned) Log.i(TAG, "Screen pinning ended")
        pinned = nowPinned
        viewModel.onPinState(nowPinned)
    }

    private fun lockTaskModeState(): Int =
        getSystemService(ActivityManager::class.java)?.lockTaskModeState
            ?: ActivityManager.LOCK_TASK_MODE_NONE

    private companion object {
        const val TAG = "MainActivity"
        const val PIN_POLL_MS = 1_000L
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
        // A permission dialog takes the window focus, so no focus event is
        // reported while it is up.
        vm.onPermissionDialog(false)
        if (granted) vm.openScanner() else vm.onCameraDenied()
    }
    val onScanClicked: () -> Unit = {
        val already = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (already) {
            vm.openScanner()
        } else {
            vm.onPermissionDialog(true)
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    // FLAG_SECURE blocks screenshots and screen recording and blanks the app in
    // the recents view. It is set for as long as the quiz runs, so quiz_end
    // takes it off again.
    val activity = context as? Activity
    val quizRunning = state.lock.running
    DisposableEffect(activity, quizRunning) {
        val window = activity?.window
        if (quizRunning && window != null) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        }
        onDispose {
            if (window != null) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // Back is ignored while the quiz is pinned: there is nowhere to go.
    BackHandler(enabled = state.lock.running && state.lock.lockMode && state.lock.pinned) {}

    // A rejected connection means a kick or a closed session; leaving releases
    // the pin. This covers screens the quiz screen no longer renders.
    LaunchedEffect(state.conn) {
        if (state.conn is ConnState.Rejected) vm.leaveQuiz()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val screen = state.screen) {
            is Screen.Join -> JoinScreen(state, vm, onScanClicked)
            is Screen.Scan -> ScanScreen(vm)
            is Screen.Lobby -> LobbyScreen(screen.name, state.conn, vm)
            // quiz_start sets both together, so this is only a guard against a blank
            // frame between the quiz ending and the screen switching.
            // A paused student sees no question at all, not a dimmed quiz.
            // A finished student never sees the quiz again, even when paused.
            is Screen.Quiz -> when {
                state.lock.finished -> FinishedScreen()
                state.lock.paused -> PausedScreen(state.lock, vm)
                else -> state.quiz?.let { QuizScreen(it, state.conn, vm) }
            }
            // Step 5: submitted. Nothing to change and nowhere to go back to.
            is Screen.Finished -> FinishedScreen()
            // No score and no answers: the server is authoritative and says nothing yet.
            Screen.Ended -> EndedScreen(vm)
        }

        state.meme?.let { meme ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = meme.bitmap.asImageBitmap(),
                    contentDescription = "Meme",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}