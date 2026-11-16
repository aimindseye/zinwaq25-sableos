package com.android.incallui

import org.sableos.phone.android.PhoneInCallService

/**
 * The name Telecom gives the system dialer's call screen when SableTelecomOverlay does not apply
 * (incall_default_class stays com.android.incallui.InCallServiceImpl): Sable Phone's own call screen under that
 * name, so Telecom can bind it either way.
 */
class InCallServiceImpl : PhoneInCallService()
