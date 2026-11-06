@file:Suppress("FunctionNaming") // Compose functions are named like types.

package org.sableos.assistant.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import org.sableos.assistant.core.AssistantProvider
import org.sableos.assistant.core.AssistantSettings
import org.sableos.assistant.core.AssistantText.t
import org.sableos.assistant.core.Persona
import org.sableos.assistant.core.Speech
import org.sableos.design.SableActionButton
import org.sableos.design.SableListRow
import org.sableos.design.SablePageHeader
import org.sableos.design.SableScrollableScreen
import org.sableos.design.SableSectionHeader

/** Name, where answers come from, and the model. Saved on Done and on Back. */
@Suppress("LongMethod") // One form, read top to bottom.
@Composable
internal fun AssistantSettingsScreen(
    state: AssistantState,
    actions: AssistantActions,
) {
    var edited by remember { mutableStateOf(state.settings) }
    val close = {
        actions.save(edited.copy(name = Persona.cleanName(edited.name)))
        state.showSettings = false
    }
    BackHandler(onBack = close)
    SableScrollableScreen {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SablePageHeader(title = t("settings"), modifier = Modifier.weight(1f))
            SableActionButton(text = t("done"), onClick = close)
        }
        OutlinedTextField(
            value = edited.name,
            onValueChange = { edited = edited.copy(name = it.take(Persona.MAX_NAME)) },
            label = { Text(t("name")) },
            supportingText = { Text(t("name_detail", Persona.commandWord(Persona.cleanName(edited.name)))) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SableSectionHeader(t("provider"))
        AssistantProvider.entries.forEach { provider ->
            SableListRow(
                title = providerLabel(provider),
                subtitle = providerDetail(provider),
                selected = edited.provider == provider,
                onClick = {
                    edited = edited.copy(provider = provider, model = "")
                    state.models = emptyList()
                    state.testStatus = null
                },
            )
        }
        ConnectionFields(edited) { edited = it }
        SableSectionHeader(t("model"))
        OutlinedTextField(
            value = edited.model,
            onValueChange = { edited = edited.copy(model = it.trim()) },
            label = { Text(t("model")) },
            supportingText = if (edited.model.isBlank()) ({ Text(t("model_none")) }) else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SableActionButton(
            text = if (state.testing) t("testing") else t("test_connection"),
            onClick = { actions.test(edited) },
        )
        state.testStatus?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        state.models.forEach { model ->
            SableListRow(title = model, selected = edited.model == model, onClick = {
                edited =
                    edited.copy(model = model)
            })
        }
        VoiceSection(state, actions)
        Text(
            t("privacy_note"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VoiceSection(
    state: AssistantState,
    actions: AssistantActions,
) {
    SableSectionHeader(t("voice"))
    Text(t("voice_detail"), style = MaterialTheme.typography.bodyMedium)
    when {
        state.voiceDownloading -> {
            Unit
        }

        state.voiceReady -> {
            SableActionButton(text = t("voice_remove"), primary = false, onClick = actions::removeVoice)
        }

        else -> {
            SableActionButton(
                text = t("voice_download", Speech.MODEL_MB.toString()),
                onClick = actions::downloadVoice,
            )
        }
    }
    val status = state.voiceStatus ?: if (state.voiceReady) t("voice_ready") else null
    status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun ConnectionFields(
    edited: AssistantSettings,
    onChange: (AssistantSettings) -> Unit,
) {
    val provider = edited.provider ?: return
    if (provider.onOwnNetwork) {
        OutlinedTextField(
            value = edited.address,
            onValueChange = { onChange(edited.copy(address = it.trim())) },
            label = { Text(t("address")) },
            placeholder = { Text(t("address_hint")) },
            supportingText =
                if (provider == AssistantProvider.Ollama) ({ Text(t("ollama_address_detail")) }) else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (provider.needsKey || provider == AssistantProvider.Custom) {
        OutlinedTextField(
            value = edited.apiKey,
            onValueChange = { onChange(edited.copy(apiKey = it.trim())) },
            label = { Text(t("api_key")) },
            placeholder = { Text(t("api_key_hint")) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun providerLabel(provider: AssistantProvider): String =
    when (provider) {
        AssistantProvider.Ollama -> t("provider_ollama")
        AssistantProvider.Custom -> t("provider_custom")
        else -> provider.name
    }

private fun providerDetail(provider: AssistantProvider): String =
    when (provider) {
        AssistantProvider.Ollama -> t("provider_ollama_detail")
        AssistantProvider.Custom -> t("provider_custom_detail")
        else -> t("provider_hosted_detail", provider.company)
    }
