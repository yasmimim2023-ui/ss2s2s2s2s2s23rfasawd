package br.com.thorlink.ui

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import br.com.thorlink.MainActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

/** Real Compose pointer events. Does not simulate a Bluetooth link or claim game compatibility. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w960dp-h432dp-land-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class GamepadInputTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun quickButtonTapDeliversBothEdges() {
        val events=mutableListOf<Boolean>()
        compose.activity.setContent {
            ThorTheme { PressControl("A",true,Color.Red,Modifier.size(80.dp)){events.add(it)} }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Botão A").performTouchInput {
            down(center); advanceEventTime(5); up()
        }
        compose.runOnIdle { assertEquals(listOf(true,false),events) }
    }
    @Test fun auxiliaryStartButtonDeliversPressAndRelease() {
        val events=mutableListOf<Boolean>()
        compose.activity.setContent {
            ThorTheme { AuxControl("START",true,Modifier.size(80.dp,30.dp)){events.add(it)} }
        }
        compose.onNodeWithContentDescription("Botão START").performTouchInput {
            down(center);advanceEventTime(20);up()
        }
        compose.runOnIdle { assertEquals(listOf(true,false),events) }
    }
    @Test fun blockedButtonExplainsTheBlockWithoutTransmitting() {
        var blocks=0
        val events=mutableListOf<Boolean>()
        compose.activity.setContent {
            ThorTheme { PressControl("A",false,Color.Red,Modifier.size(80.dp),onBlocked={blocks++}){events.add(it)} }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Botão A").assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(1,blocks); assertTrue(events.isEmpty()) }
    }
    @Test fun stickAndButtonCanBePressedTogetherAndReleasedIndependently() {
        val axes=mutableListOf<Pair<Float,Float>>()
        val presses=mutableListOf<Boolean>()
        compose.activity.setContent {
            ThorTheme {
                Row(Modifier.size(240.dp,120.dp).testTag("controls")) {
                    AnalogStick("L",true,Modifier.size(100.dp)){x,y->axes.add(x to y)}
                    PressControl("A",true,Color.Red,Modifier.size(100.dp)){presses.add(it)}
                }
            }
        }
        compose.waitForIdle()
        val stick=compose.onNodeWithContentDescription("L").fetchSemanticsNode().boundsInRoot
        val key=compose.onNodeWithContentDescription("Botão A").fetchSemanticsNode().boundsInRoot
        val root=compose.onNodeWithTag("controls").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("controls").performTouchInput {
            down(0,Offset(stick.center.x+stick.width*.2f-root.left,stick.center.y-root.top))
            down(1,key.center-Offset(root.left,root.top))
            advanceEventTime(20)
            up(1)
            advanceEventTime(20)
            up(0)
        }
        compose.runOnIdle {
            assertTrue(axes.any { it.first > .5f })
            assertEquals(0f to 0f,axes.last())
            assertEquals(listOf(true,false),presses)
        }
    }
}
