package br.com.thorlink.domain

import org.junit.Assert.*
import org.junit.Test

class ControlInputBufferTest {
    @Test fun quickTapSurvivesUntilTheSamplerCanSendIt() {
        val buffer = ControlInputBuffer()
        buffer.offer(ControlState(buttons=Buttons.A), 100)
        buffer.offer(ControlState(), 105)
        assertEquals(Buttons.A, buffer.next(120).buttons)
        assertEquals(0, buffer.next(140).buttons)
        assertEquals(ControlState(), buffer.next(160))
    }
    @Test fun oldAxisMovementsAreReplacedByTheNewestPosition() {
        val buffer = ControlInputBuffer()
        repeat(100) { buffer.offer(ControlState(lx=it/100f), it.toLong()) }
        assertEquals(.99f, buffer.next(100).lx)
        assertEquals(.99f, buffer.next(120).lx)
    }
    @Test fun overlappingButtonsKeepBothPressAndReleaseEdges() {
        val buffer = ControlInputBuffer()
        listOf(Buttons.A, Buttons.A or Buttons.B, Buttons.B, 0).forEachIndexed { i,b ->
            buffer.offer(ControlState(lx=.5f,buttons=b),100+i.toLong())
        }
        assertEquals(listOf(Buttons.A,Buttons.A or Buttons.B,Buttons.B,0),List(4){buffer.next(120+it.toLong()).buttons})
    }
    @Test fun resetDropsHeldButtonsAndPendingGestures() {
        val buffer = ControlInputBuffer()
        buffer.offer(ControlState(lx=1f,buttons=Buttons.X),0)
        buffer.reset()
        assertEquals(ControlState(),buffer.next(1))
    }
    @Test fun expiredCommandsAreNeverReplayedAfterLag() {
        val buffer = ControlInputBuffer()
        buffer.offer(ControlState(buttons=Buttons.A),10)
        buffer.offer(ControlState(),15)
        assertEquals(ControlState(),buffer.next(261))
        assertEquals(ControlState(),buffer.next(262))
    }
    @Test fun overflowFailsClosedAndLeavesNoHeldButton() {
        val buffer = ControlInputBuffer(capacity=2)
        assertTrue(buffer.offer(ControlState(buttons=Buttons.A),0))
        assertTrue(buffer.offer(ControlState(buttons=Buttons.B),1))
        assertFalse(buffer.offer(ControlState(buttons=Buttons.X),2))
        assertEquals(ControlState(),buffer.next(3))
    }
}
