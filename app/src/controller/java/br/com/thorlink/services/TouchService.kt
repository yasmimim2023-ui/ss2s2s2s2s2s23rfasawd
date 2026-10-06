package br.com.thorlink.services

/** The controller has no AccessibilityService; shared session code can call this absent endpoint safely. */
object TouchService {
    val instance: AbsentTouch? = null
    class AbsentTouch { fun release() = Unit; fun configureSession(active:Boolean) = Unit }
}
