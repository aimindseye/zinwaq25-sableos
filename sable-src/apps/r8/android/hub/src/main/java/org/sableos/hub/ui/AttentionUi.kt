package org.sableos.hub.ui

import org.sableos.hub.ConnectedAppKey
import org.sableos.hub.policy.AttentionOutput

/** Sable Attention state for the connected-apps screen: only profile-supported Sable outputs. */
internal data class AttentionUi(
    val selectableOutputs: List<AttentionOutput>,
    val selections: Map<ConnectedAppKey, Set<AttentionOutput>>,
)
