package com.example.dockerwebui.Views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dockerwebui.AppState
import com.example.dockerwebui.AuthStatus
import com.example.dockerwebui.Font
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    appState: AppState,
    font: Font,
    scope: CoroutineScope
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val usernameFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        usernameFocus.requestFocus()
    }

    fun submitLogin() {
        val trimmedUser = username.trim()
        if (trimmedUser.isBlank() || password.isBlank()) {
            appState.loginError = "Enter both username and password."
            return
        }
        if (appState.isLoggingIn) return

        appState.isLoggingIn = true
        appState.loginError = null
        scope.launch {
            try {
                val result = appState.api.login(trimmedUser, password)
                if (result.authenticated) {
                    appState.authenticatedUser = result.user ?: trimmedUser
                    appState.loginError = null
                    appState.authStatus = AuthStatus.AUTHENTICATED
                } else {
                    appState.loginError = "Invalid username or password."
                }
            } catch (e: Exception) {
                appState.loginError = e.message?.ifBlank { "Sign in failed." } ?: "Sign in failed."
            } finally {
                appState.isLoggingIn = false
            }
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        disabledTextColor = Color(180, 195, 210),
        focusedContainerColor = Color(15, 23, 42),
        unfocusedContainerColor = Color(15, 23, 42),
        disabledContainerColor = Color(15, 23, 42),
        focusedBorderColor = Color(56, 189, 248),
        unfocusedBorderColor = Color(50, 74, 110),
        disabledBorderColor = Color(50, 74, 110),
        focusedLabelColor = Color(196, 186, 154),
        unfocusedLabelColor = Color(160, 175, 195),
        disabledLabelColor = Color(140, 155, 175),
        cursorColor = Color(56, 189, 248)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(11, 18, 27))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .background(Color(27, 45, 68), shape = RoundedCornerShape(12.dp))
                .border(1.dp, Color(35, 55, 80), RoundedCornerShape(12.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "DockerWeb",
                fontSize = 28.sp,
                color = font.fontColor,
                fontFamily = font.font
            )
            Text(
                text = "Sign in to manage containers",
                fontSize = 13.sp,
                color = Color(196, 186, 154),
                fontFamily = font.font
            )

            Spacer(modifier = Modifier.height(4.dp))

            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    if (appState.loginError != null) appState.loginError = null
                },
                label = { Text("Username", fontFamily = font.font) },
                placeholder = { Text("admin", fontFamily = font.font, color = Color(130, 148, 170)) },
                singleLine = true,
                enabled = !appState.isLoggingIn,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { passwordFocus.requestFocus() }
                ),
                shape = RoundedCornerShape(8.dp),
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(usernameFocus)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            (event.key == Key.Enter || event.key == Key.NumPadEnter)
                        ) {
                            if (password.isBlank()) {
                                passwordFocus.requestFocus()
                            } else {
                                submitLogin()
                            }
                            true
                        } else {
                            false
                        }
                    }
            )

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    if (appState.loginError != null) appState.loginError = null
                },
                label = { Text("Password", fontFamily = font.font) },
                placeholder = { Text("Enter password", fontFamily = font.font, color = Color(130, 148, 170)) },
                singleLine = true,
                enabled = !appState.isLoggingIn,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = Color(196, 186, 154)
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { submitLogin() }
                ),
                shape = RoundedCornerShape(8.dp),
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(passwordFocus)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            (event.key == Key.Enter || event.key == Key.NumPadEnter)
                        ) {
                            submitLogin()
                            true
                        } else {
                            false
                        }
                    }
            )

            appState.loginError?.let { errorMsg ->
                Text(
                    text = errorMsg,
                    color = Color(255, 138, 128),
                    fontSize = 13.sp,
                    fontFamily = font.font
                )
            }

            ElevatedButton(
                onClick = { submitLogin() },
                enabled = !appState.isLoggingIn,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(31, 111, 235),
                    contentColor = Color.White,
                    disabledContainerColor = Color(28, 42, 59),
                    disabledContentColor = Color(160, 175, 195)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp)
            ) {
                if (appState.isLoggingIn) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Signing in...", fontFamily = font.font)
                    }
                } else {
                    Text("Sign In", fontFamily = font.font)
                }
            }
        }
    }
}
