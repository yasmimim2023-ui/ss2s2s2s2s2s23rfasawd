package br.com.thorlink.domain

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Standard Android gamepad HID usages, not a spoofed Sony/Xbox device or Windows XInput driver. */
object HidReport {
    const val ID=1
    const val SIZE=13
    // Game pad collection: 16 buttons, 4-bit hat + padding, X/Y/Z/Rz, Brake/Accelerator.
    val descriptor = intArrayOf(
        0x05,0x01,0x09,0x05,0xA1,0x01,0x85,ID,
        0x05,0x09,0x19,0x01,0x29,0x10,0x15,0x00,0x25,0x01,0x75,0x01,0x95,0x10,0x81,0x02,
        0x05,0x01,0x09,0x39,0x15,0x00,0x25,0x07,0x35,0x00,0x46,0x3B,0x01,0x65,0x14,
        0x75,0x04,0x95,0x01,0x81,0x42,0x65,0x00,0x75,0x04,0x95,0x01,0x81,0x03,
        0x09,0x30,0x09,0x31,0x09,0x32,0x09,0x35,0x16,0x01,0x80,0x26,0xFF,0x7F,
        0x36,0x01,0x80,0x46,0xFF,0x7F,0x75,0x10,0x95,0x04,0x81,0x02,
        0x05,0x02,0x09,0xC5,0x09,0xC4,0x15,0x00,0x26,0xFF,0x00,
        0x35,0x00,0x46,0xFF,0x00,0x75,0x08,0x95,0x02,0x81,0x02,0xC0
    ).map { it.toByte() }.toByteArray()
    fun encode(c:ControlState):ByteArray {
        var bits=0
        listOf(Buttons.A to 0,Buttons.B to 1,Buttons.X to 3,Buttons.Y to 4,
            Buttons.L1 to 6,Buttons.R1 to 7,Buttons.L2 to 8,Buttons.R2 to 9,
            Buttons.SELECT to 10,Buttons.START to 11,Buttons.L3 to 13,Buttons.R3 to 14)
            .forEach { (button,bit) -> if(c.buttons and button != 0) bits=bits or (1 shl bit) }
        fun axis(v:Float) = (if(v.isFinite())v.coerceIn(-1f,1f)*32767 else 0f).roundToInt().toShort()
        return ByteBuffer.allocate(SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(bits.toShort());put(hat(c.buttons).toByte())
            putShort(axis(c.lx));putShort(axis(c.ly));putShort(axis(c.rx));putShort(axis(c.ry))
            put(if(c.buttons and Buttons.L2!=0)255.toByte() else 0)
            put(if(c.buttons and Buttons.R2!=0)255.toByte() else 0)
        }.array()
    }
    fun hat(buttons:Int):Int {
        val x=(if(buttons and Buttons.RIGHT!=0)1 else 0)-(if(buttons and Buttons.LEFT!=0)1 else 0)
        val y=(if(buttons and Buttons.DOWN!=0)1 else 0)-(if(buttons and Buttons.UP!=0)1 else 0)
        return when(x to y){0 to -1->0;1 to -1->1;1 to 0->2;1 to 1->3;
            0 to 1->4;-1 to 1->5;-1 to 0->6;-1 to -1->7;else->8}
    }
}

/** Fresh receiver approval is checked independently of the HID link, which can stay connected. */
object HidSendGate {
    fun allowed(connected:Boolean,granted:Boolean,visible:Boolean,receiverAllowed:Boolean,leaseAt:Long,now:Long) =
        connected && granted && visible && receiverAllowed && leaseAt > 0 && now-leaseAt in 0..500
}
