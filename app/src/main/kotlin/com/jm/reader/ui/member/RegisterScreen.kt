package com.jm.reader.ui.member

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.jm.reader.data.repo.AppRepository
import com.jm.reader.data.repo.RepoResult
import com.jm.reader.ui.LocalAppStrings
import com.jm.reader.ui.LocalRepository
import com.jm.reader.ui.components.AppTopBar
import com.jm.reader.ui.nav.Routes
import com.jm.reader.ui.theme.GlassPanel
import com.jm.reader.ui.theme.GlassShape
import kotlinx.coroutines.launch

/**
 * Registration.
 *
 * Uses the **mobile** endpoint `POST /register` (the web `/signup` route is Cloudflare-gated and
 * answers 403 — verified live). The server replies HTTP 200 with an inner status, so its
 * `errors` / `msg` text is what the reader sees:
 *   - `{"status":"ok","msg":"您已注册。检查您的电子邮箱中的确认链接！"}` on success
 *   - `{"status":"fail","errors":["密码长度小于 8"]}` on a rejected submission
 *
 * Local checks mirror the server's rules (all fields required, password >= 8 characters,
 * matching confirmation, plausible e-mail) so obvious mistakes are caught before a round trip.
 */
@Composable
fun RegisterScreen(navController: NavHostController) {
    val repo = LocalRepository.current
    val s = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf(AppRepository.GENDER_MALE) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }
    // Mirrors the API's own rule (it answers "電子郵件不是有效的電子郵件地址!" for bad input);
    // the accepted e-mail *domains* are enforced server-side.
    val emailPattern = remember { Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$") }

    fun submit() {
        val name = username.trim()
        val mail = email.trim()
        // The server enforces these too; checking here keeps the errors understandable.
        val problem = when {
            name.isBlank() || mail.isBlank() || password.isBlank() -> s.fillComplete
            !emailPattern.matches(mail) -> s.invalidEmail
            password.length < 8 -> s.passwordTooShort
            password != passwordConfirm -> s.passwordMismatch
            else -> null
        }
        if (problem != null) {
            success = false
            message = problem
            return
        }
        loading = true
        message = null
        scope.launch {
            when (val r = repo.register(name, password, passwordConfirm, mail, gender)) {
                is RepoResult.Ok -> {
                    success = true
                    // The server's text explains that the address still needs confirming.
                    message = r.data.ifBlank { s.registerNeedConfirm }
                    loading = false
                }
                is RepoResult.Err -> {
                    success = false
                    message = r.message
                    loading = false
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(s.register, onBack = { navController.popBackStack() }) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            GlassPanel(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                shape = GlassShape,
                blurRadius = 30.dp,
            ) {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(s.username) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(s.email) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(s.password) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                    OutlinedTextField(
                        value = passwordConfirm,
                        onValueChange = { passwordConfirm = it },
                        label = { Text(s.confirmPassword) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = gender == AppRepository.GENDER_MALE,
                            onClick = { gender = AppRepository.GENDER_MALE },
                            label = { Text(s.male) },
                        )
                        FilterChip(
                            selected = gender == AppRepository.GENDER_FEMALE,
                            onClick = { gender = AppRepository.GENDER_FEMALE },
                            label = { Text(s.female) },
                        )
                    }

                    message?.let {
                        Text(
                            it,
                            color = if (success) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    if (success) {
                        Button(
                            onClick = {
                                // Back to login with the new account name already filled in.
                                navController.navigate(Routes.login(username.trim())) {
                                    popUpTo(Routes.MAIN)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        ) { Text("${s.login} →") }
                    } else {
                        Button(
                            onClick = { submit() },
                            enabled = !loading,
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        ) {
                            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text(s.register)
                        }
                    }
                }
            }
            Text(
                s.registerNeedConfirm,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp),
            )
        }
    }
}
