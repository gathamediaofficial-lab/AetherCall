package com.aether.call.telecom

import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager

/**
 * Custom Android Telecom ConnectionService for self-managed VoIP calling.
 */
class AetherConnectionService : ConnectionService() {

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ): Connection {
        val connection = AetherCallConnection()
        request?.let {
            connection.setAddress(it.address, TelecomManager.PRESENTATION_ALLOWED)
            connection.extras = it.extras
        }
        connection.setInitializing()
        connection.setActive()
        return connection
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ): Connection {
        val connection = AetherCallConnection()
        request?.let {
            connection.setAddress(it.address, TelecomManager.PRESENTATION_ALLOWED)
            connection.extras = it.extras
        }
        connection.setRinging()
        return connection
    }

    /**
     * Connection class representing an active self-managed VoIP call session.
     */
    inner class AetherCallConnection : Connection() {

        init {
            connectionProperties = PROPERTY_SELF_MANAGED
            connectionCapabilities = CAPABILITY_SUPPORT_HOLD or CAPABILITY_MUTE
            setAudioModeIsVoip(true)
        }

        override fun onDisconnect() {
            setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
            destroy()
        }

        override fun onHold() {
            setOnHold()
        }

        override fun onUnhold() {
            setActive()
        }

        override fun onAnswer() {
            setActive()
        }

        override fun onReject() {
            setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
            destroy()
        }

        override fun onStateChanged(state: Int) {
            super.onStateChanged(state)
        }
    }
}