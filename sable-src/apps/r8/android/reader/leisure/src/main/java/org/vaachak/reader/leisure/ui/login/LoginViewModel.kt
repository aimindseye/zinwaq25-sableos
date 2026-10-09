/*
 *  Copyright (c) 2026 Piyush Daiya
 *  *
 *  * Permission is hereby granted, free of charge, to any person obtaining a copy
 *  * of this software and associated documentation files (the "Software"), to deal
 *  * in the Software without restriction, including without limitation the rights
 *  * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  * copies of the Software, and to permit persons to whom the Software is
 *  * furnished to do so, subject to the following conditions:
 *  *
 *  * The above copyright notice and this permission notice shall be included in all
 *  * copies or substantial portions of the Software.
 *  *
 *  * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  * SOFTWARE.
 */

package org.vaachak.reader.leisure.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.vaachak.reader.core.data.repository.SettingsRepository
import org.vaachak.reader.core.data.repository.SyncRepository
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val syncRepo: SyncRepository,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    // UI State
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    // Form Data
    val username = MutableStateFlow("")
    val password = MutableStateFlow("")
    val serverUrl = MutableStateFlow("https://vaachak-sync.piyush.workers.dev") // Default
    val useCustomServer = MutableStateFlow(false)

    // Add these to LoginViewModel.kt
    val deviceName = MutableStateFlow("")
    val deviceId = MutableStateFlow("")
    val testStatus = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            // 1. Get the saved name from Repo
            val savedName = settingsRepo.deviceName.first()

            // 2. CHECK: If it is blank, force the hardware model
            if (savedName.isNotBlank()) {
                deviceName.value = savedName
            } else {
                // FORCE HARDWARE NAME (e.g. "Onyx Boox", "Pixel 6")
                val manufacturer = android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
                val model = android.os.Build.MODEL

                // Clean up name (avoid "Google Google Pixel")
                deviceName.value = if (model.startsWith(manufacturer)) {
                    model
                } else {
                    "$manufacturer $model"
                }
            }
            deviceId.value = settingsRepo.deviceId.first()
        }
    }


    fun testUrlConnection() {
        viewModelScope.launch {
            _isLoading.value = true
            testStatus.value = "Pinging server..."

            val url = serverUrl.value.trim()
            if (url.isEmpty()) {
                testStatus.value = "Error: URL is empty"
                _isLoading.value = false
                return@launch
            }

            // USE THE REPO!
            val result = syncRepo.testConnection(url)

            testStatus.value = if (result.isSuccess) {
                "Connection Successful!"
            } else {
                "Failed: ${result.exceptionOrNull()?.localizedMessage ?: "Unknown error"}"
            }
            _isLoading.value = false
        }
    }



    fun submit(isRegister: Boolean, onSuccess: () -> Unit) {
        val user = username.value.trim()
        val pass = password.value.trim()
        val url = serverUrl.value.trim()
        val devName = deviceName.value.trim()
        val useLocal = useCustomServer.value

        if (user.isBlank() || pass.isBlank()) {
            _error.value = "Username and password are required"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            // 1. SAVE SETTINGS FIRST (Critical Step)
            // This ensures SyncRepository has the correct URL and Device Name immediately.
            settingsRepo.saveSyncSettings(
                syncCloudUrl = "https://vaachak-sync.ai-mindseye.workers.dev", // Default Cloud URL (Keep hardcoded or fetch from existing)
                localUrl = url,     // The custom URL typed by user
                useLocal = useLocal,// The toggle state
                deviceName = devName // <--- PASS THE DEVICE NAME
            )

            // 2. Perform Auth Action
            val result = if (isRegister) {
                syncRepo.register(user, pass)
            } else {
                syncRepo.login(user, pass)
            }

            // 3. Handle Result
            _isLoading.value = false

            result.fold(
                onSuccess = {
                    if (isRegister) {
                        // Auto-login after register to get the token
                        syncRepo.login(user, pass)
                    }
                    onSuccess()
                },
                onFailure = { e ->
                    _error.value = e.message ?: "Authentication failed"
                }
            )
        }
    }
}