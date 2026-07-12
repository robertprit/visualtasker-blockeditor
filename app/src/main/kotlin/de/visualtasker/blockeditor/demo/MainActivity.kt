package de.visualtasker.blockeditor.demo

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import de.visualtasker.blockeditor.compose.host.BlockEditorHost
import de.visualtasker.blockeditor.compose.host.BlockEditorHostCallbacks
import de.visualtasker.blockeditor.compose.host.BlockEditorHostUiConfig
import de.visualtasker.blockeditor.compose.host.BlockEditorController
import de.visualtasker.blockeditor.compose.theme.BlockEditorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FileDebugLog.install(this)
        Log.i("BlockEditor", "Debug-Log: ${de.visualtasker.blockeditor.compose.debug.EditorDebugLog.logFilePath}")
        enableEdgeToEdge()
        setContent {
            BlockEditorTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val controller = remember {
                        BlockEditorController.starter(callbacks = BlockEditorHostCallbacks.NoOp)
                    }
                    BlockEditorHost(
                        controller = controller,
                        uiConfig = BlockEditorHostUiConfig(
                            showBottomPanel = true,
                            showBlockFactory = true,
                            allowClearWorkspace = true,
                        ),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
