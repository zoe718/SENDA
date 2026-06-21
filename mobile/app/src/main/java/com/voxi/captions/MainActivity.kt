package com.voxi.captions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voxi.captions.ui.components.VoxiBadge
import com.voxi.captions.ui.screens.CameraScreen
import com.voxi.captions.ui.screens.ConversationScreen
import com.voxi.captions.ui.screens.HistoryScreen
import com.voxi.captions.ui.screens.ScanScreen
import com.voxi.captions.ui.screens.VoiceSelectionScreen
import com.voxi.captions.ui.theme.VoxiBackground
import com.voxi.captions.ui.theme.VoxiBg
import com.voxi.captions.ui.theme.VoxiSlate
import com.voxi.captions.ui.theme.VoxiTheme
import com.voxi.captions.viewmodel.ConversationViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoxiTheme {
                VoxiApp()
            }
        }
    }
}

@Composable
private fun VoxiApp() {
    val viewModel: ConversationViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val context = androidx.compose.ui.platform.LocalContext.current

    // Avisos de exportacion (one-shot) -> Toast.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.exportEvents.collect { result ->
            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
        }
    }

    var hasMicPermission by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasMicPermission = granted
        if (granted) viewModel.startListening()
    }

    androidx.compose.runtime.LaunchedEffect(hasMicPermission) {
        if (hasMicPermission) viewModel.startListening()
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val cameraPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
        if (granted) viewModel.setShowCamera(true)
    }

    // Capa 2 (spec 6): permiso de camara dedicado para el escaneo inicial.
    // Si se concede arrancamos el escaneo; si se niega lo omitimos y seguimos.
    val scanCameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
        if (granted) viewModel.startScan() else viewModel.skipScan()
    }

    // Arranque camara-primero: tras conceder el microfono, si aun no se ha
    // escaneado en esta sesion, pedimos camara y abrimos la pantalla de escaneo.
    androidx.compose.runtime.LaunchedEffect(
        hasMicPermission, state.needsScan, state.isScanning, hasCameraPermission,
    ) {
        if (hasMicPermission && state.needsScan && !state.isScanning) {
            if (hasCameraPermission) viewModel.startScan()
            else scanCameraLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val onToggleCamera: () -> Unit = {
        when {
            state.showCamera -> viewModel.setShowCamera(false)
            hasCameraPermission -> viewModel.setShowCamera(true)
            else -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    when {
        state.needsVoiceSelection -> VoiceSelectionScreen(
            onSelect = viewModel::setVoiceType,
            modifier = Modifier.fillMaxSize(),
            onCancel = viewModel::cancelVoiceChange,
        )
        !hasMicPermission -> PermissionRequest(
            onRequest = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        )
        state.isScanning -> ScanScreen(
            people = state.scanPeople,
            liveFaces = state.scanLiveFaces,
            onScanFaces = viewModel::onScanFaces,
            onCaptureFace = viewModel::captureScanFace,
            onRemovePerson = viewModel::removeScanPerson,
            onName = viewModel::nameScanPerson,
            onFinish = viewModel::finishScan,
            onSkip = viewModel::skipScan,
            onReRecordVoice = viewModel::startVoiceEnroll,
            scanRecordingLocalId = state.scanRecordingLocalId,
            modifier = Modifier.fillMaxSize(),
        )
        state.showHistory -> HistoryScreen(
            state = state,
            onClose = viewModel::closeHistory,
            onOpenSession = viewModel::openSession,
            onBackToList = viewModel::backToHistoryList,
            onDeleteSession = viewModel::deleteSession,
            modifier = Modifier.fillMaxSize(),
        )
        state.showCamera && hasCameraPermission -> CameraScreen(
            state = state,
            onFacesDetected = viewModel::onFacesDetected,
            onToggleCamera = { viewModel.setShowCamera(false) },
            onSend = viewModel::speak,
            modifier = Modifier.fillMaxSize(),
        )
        else -> ConversationScreen(
            state = state,
            modifier = Modifier.fillMaxSize(),
            onSelectSpeaker = viewModel::setSpeaker,
            onSend = viewModel::speak,
            onToggleCamera = onToggleCamera,
            onExport = { viewModel.exportConversation(context) },
            onHistory = viewModel::openHistory,
            onNewConversation = viewModel::startNewConversation,
            onChangeVoice = viewModel::requestVoiceChange,
            onToggleMute = viewModel::toggleMute,
            onRescan = {
                // Re-escaneo de personas (spec 6): abre la pantalla de escaneo
                // sembrada con las personas ya enroladas, donde se pueden agregar
                // nuevas o borrar las existentes con la X.
                if (hasCameraPermission) viewModel.startScan()
                else scanCameraLauncher.launch(Manifest.permission.CAMERA)
            },
        )
    }
}

@Composable
private fun PermissionRequest(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoxiBackground)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        VoxiBadge(size = 72.dp)
        Spacer(Modifier.size(20.dp))
        Text(
            text = "SENDA",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Subtitulos en vivo, con el tono y el lugar de cada voz. " +
                "Para empezar, SENDA necesita acceso al microfono.",
            style = MaterialTheme.typography.bodyLarge,
            color = VoxiSlate,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        Button(
            onClick = onRequest,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = VoxiBg,
            ),
        ) {
            Text("Permitir microfono")
        }
    }
}
