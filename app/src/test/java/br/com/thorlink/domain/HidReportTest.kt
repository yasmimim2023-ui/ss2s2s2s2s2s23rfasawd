package br.com.thorlink.domain

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class HidReportTest {
    private fun word(data:ByteArray,offset:Int)=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getShort(offset).toInt()
    @Test fun neutralReportHasNoHeldButtonsOrAxesAndUsesNullHat() {
        val report=HidReport.encode(ControlState())
        assertEquals(13,report.size);assertEquals(0,word(report,0));assertEquals(8,report[2].toInt())
        assertTrue(report.drop(3).all{it.toInt()==0})
    }
    @Test fun faceButtonsUseAndroidHidUsagesOneTwoFourFive() {
        assertEquals(1,word(HidReport.encode(ControlState(buttons=Buttons.A)),0))
        assertEquals(2,word(HidReport.encode(ControlState(buttons=Buttons.B)),0))
        assertEquals(8,word(HidReport.encode(ControlState(buttons=Buttons.X)),0))
        assertEquals(16,word(HidReport.encode(ControlState(buttons=Buttons.Y)),0))
    }
    @Test fun shouldersStartSelectAndStickClicksRemainIndependent() {
        val report=HidReport.encode(ControlState(buttons=Buttons.L1 or Buttons.R1 or Buttons.SELECT or Buttons.START or Buttons.L3 or Buttons.R3))
        assertEquals(0x6cc0,word(report,0))
    }
    @Test fun bothSticksUseSignedSixteenBitAxesInCorrectOrder() {
        val report=HidReport.encode(ControlState(lx=-1f,ly=1f,rx=.5f,ry=-.5f))
        assertEquals(-32767,word(report,3));assertEquals(32767,word(report,5))
        assertEquals(16384,word(report,7));assertEquals(-16383,word(report,9))
    }
    @Test fun invalidAndOutOfRangeAxesDoNotCreateInvalidReports() {
        val report=HidReport.encode(ControlState(lx=Float.NaN,ly=Float.POSITIVE_INFINITY,rx=3f,ry=-3f))
        assertEquals(0,word(report,3));assertEquals(0,word(report,5))
        assertEquals(32767,word(report,7));assertEquals(-32767,word(report,9))
    }
    @Test fun dpadHasAllEightDirectionsAndOppositeDirectionsCancel() {
        val directions=listOf(Buttons.UP,Buttons.UP or Buttons.RIGHT,Buttons.RIGHT,Buttons.DOWN or Buttons.RIGHT,
            Buttons.DOWN,Buttons.DOWN or Buttons.LEFT,Buttons.LEFT,Buttons.UP or Buttons.LEFT)
        assertEquals((0..7).toList(),directions.map{HidReport.hat(it)})
        assertEquals(8,HidReport.hat(Buttons.UP or Buttons.DOWN or Buttons.LEFT or Buttons.RIGHT))
    }
    @Test fun triggersEmitAnalogEndpointsAndDigitalButtons() {
        val report=HidReport.encode(ControlState(buttons=Buttons.L2 or Buttons.R2))
        assertEquals(0x300,word(report,0));assertEquals(255,report[11].toInt() and 255);assertEquals(255,report[12].toInt() and 255)
    }
    @Test fun descriptorDeclaresExactlyOneGamepadInputReportWithThirteenBytes() {
        var at=0;var size=0;var count=0;var bits=0;var depth=0;var id=0
        val d=HidReport.descriptor
        // Independent short-item parser: count declared report bits instead of trusting encoder SIZE.
        while(at<d.size) {
            val prefix=d[at++].toInt() and 255
            val bytes=when(prefix and 3){3->4;else->prefix and 3}
            var value=0;repeat(bytes){i->value=value or ((d[at++].toInt() and 255) shl (i*8))}
            when(prefix and 0xfc){0x74->size=value;0x94->count=value;0x84->id=value;
                0x80->bits+=size*count;0xa0->depth++;0xc0->depth--}
            assertTrue(depth>=0)
        }
        assertEquals(0,depth);assertEquals(1,id);assertEquals(104,bits)
        assertArrayEquals(byteArrayOf(5,1,9,5,0xa1.toByte(),1),d.copyOfRange(0,6))
    }
    @Test fun nativeSendingRequiresConsentVisibilityAndFreshUnlockedReceiverLease() {
        fun gate(connected:Boolean=true,granted:Boolean=true,visible:Boolean=true,receiverAllowed:Boolean=true,leaseAt:Long=1000,now:Long=1200)=
            HidSendGate.allowed(connected,granted,visible,receiverAllowed,leaseAt,now)
        assertTrue(gate());assertFalse(gate(connected=false));assertFalse(gate(granted=false))
        assertFalse(gate(visible=false));assertFalse(gate(receiverAllowed=false))
        assertFalse(gate(leaseAt=0));assertFalse(gate(now=1501));assertFalse(gate(now=999))
    }
}
