package br.com.thorlink.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import br.com.thorlink.domain.*
import org.junit.Assert.*
import br.com.thorlink.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

/** Runs real Compose screens with Android resources in Robolectric; not a physical Bluetooth test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w960dp-h432dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class UiRenderTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun consoleRendersAndNavigationWorks() {
        assertTrue(compose.onAllNodesWithText("THORLINK",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithContentDescription("Analógico esquerdo").assertExists()
        compose.onNodeWithContentDescription("Analógico direito").assertExists()
        compose.onNodeWithContentDescription("Botão A").assertExists()
        compose.waitForIdle()
        val dir=File("build/ui-captures").apply{mkdirs()}
        val role=if(br.com.thorlink.BuildConfig.IS_RECEIVER)"receptor"else"controle"
        saveScreenshot(File(dir,"$role-console.png"))
        compose.onNodeWithText("Conectar",useUnmergedTree=true).performClick()
        compose.onNodeWithText(if(br.com.thorlink.BuildConfig.IS_RECEIVER)"Receber conexão"else"Dispositivos Bluetooth").assertExists()
        compose.onNodeWithContentDescription("Voltar ao menu",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Ajustes",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Controle e segurança").assertExists()
        saveScreenshot(File(dir,"$role-settings.png"))
    }
    @Test fun swipeLeftOpensSecondScreenAndSwipeRightReturnsToMenu() {
        compose.onNodeWithText("Ajustes",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithTag("tela-central").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("SEU SEGUNDO CELULAR",substring=true,useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithTag("tela-central").performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithText("Ajustes",useUnmergedTree=true).assertIsDisplayed()
    }
    @Test fun consentStartsEmptyAndRequiresComparingCode() {
        var approved:Grants?=null
        compose.activity.setContent {
            var grants by remember{mutableStateOf(Grants())}
            var agreed by remember{mutableStateOf(false)}
            ThorTheme { ConsentDialog(UiState(status=LinkStatus.VERIFYING,remoteName="Celular de teste",code="123456",fingerprint="ab".repeat(32),controlMode=ControlMode.GESTURES),true,
                grants,{grants=it},agreed,{agreed=it},{approved=grants},{}) }
        }
        compose.onNodeWithText("Autorizar sessão").assertIsNotEnabled()
        compose.onNodeWithText("Arquivos selecionados").assertIsOff()
        compose.onNodeWithText("Tela • diálogo do Android").assertIsOff()
        compose.onNodeWithText("Joystick / toques e gestos").assertIsOff()
        compose.onNodeWithText("Conferi o código nos dois aparelhos e autorizo esta sessão.").performClick()
        compose.onNodeWithText("Autorizar sessão").assertIsEnabled().performClick()
        assertEquals(Grants(),approved)
    }
    @Test @Config(qualifiers="w640dp-h320dp-land-mdpi") fun compactConsoleKeepsNavigationVisible() {
        compose.onNodeWithContentDescription("Analógico esquerdo").assertExists()
        compose.onNodeWithText("Ajustes",useUnmergedTree=true).assertIsDisplayed().performClick()
        compose.onNodeWithText("Controle e segurança").assertIsDisplayed()
        val dir=File("build/ui-captures").apply{mkdirs()}
        val role=if(br.com.thorlink.BuildConfig.IS_RECEIVER)"receptor"else"controle"
        saveScreenshot(File(dir,"$role-compact.png"))
    }
    private fun saveScreenshot(file:File) {
        compose.runOnIdle {
            val view=compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
    }
    @Test fun controlDiagnosticExplainsConsentAndShowsTheLastReceivedButton() {
        var diagnostic by mutableStateOf(UiState(status=LinkStatus.CONNECTED))
        compose.activity.setContent { ThorTheme { ControlDiagnostic(diagnostic,false) } }
        compose.onNodeWithText("Controles não autorizados",substring=true).assertExists()
        compose.runOnIdle { diagnostic=diagnostic.copy(grants=Grants(controls=true),controlHealth=ControlHealth.ACCESSIBILITY_OFF,
            inputCount=12,lastButtons=Buttons.A) }
        compose.onNodeWithText(ControlHealth.ACCESSIBILITY_OFF.label).assertExists()
        compose.onNodeWithText("Confirmados pelo receptor: 12 • último botão: A").assertExists()
        compose.runOnIdle { diagnostic=diagnostic.copy(controlHealth=ControlHealth.ANDROID_REJECTED) }
        compose.onNodeWithText(ControlHealth.ANDROID_REJECTED.label).assertExists()
    }
    @Test fun hidConsentIsOffAndExplainsForegroundAppScope() {
        compose.activity.setContent {
            var grants by remember{mutableStateOf(Grants())}
            ThorTheme { ConsentDialog(UiState(status=LinkStatus.VERIFYING,code="123456",controlMode=ControlMode.HID),true,
                grants,{grants=it},false,{}, {},{}) }
        }
        compose.onNodeWithText("Gamepad Bluetooth HID").assertIsOff().performClick()
        compose.onNodeWithText("Autorizo entrada nativa de gamepad",substring=true).assertExists()
        compose.onNodeWithText("Autorizar sessão").assertIsNotEnabled()
    }
}
