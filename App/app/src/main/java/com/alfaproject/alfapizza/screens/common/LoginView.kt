package com.alfaproject.alfapizza.screens.common

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.alfaproject.alfapizza.R
import com.alfaproject.alfapizza.network.rememberNetworkConnectionState
import com.alfaproject.alfapizza.ui.theme.BoxColor
import com.alfaproject.alfapizza.screens.MyOutlinedTextField
import com.alfaproject.alfapizza.view_model.common.LoginViewModel

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun LoginView(navController: NavHostController) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val vm = remember { LoginViewModel() }
    val email = remember { mutableStateOf("") }
    val password = remember { mutableStateOf("") }
    var isAuthenticating by remember { mutableStateOf(false) }
    val connected by rememberNetworkConnectionState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures { _ ->
                    focusManager.clearFocus()
                }
            }
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    )
    {
        val toastFailedLogin = stringResource(R.string.login_failed_please_retry)
        val toastOffline = stringResource(R.string.you_are_offline)
        CustomRow()

        MyOutlinedTextField(
            field = email,
            label = stringResource(R.string.email),
            modifier = Modifier.fillMaxWidth().height(65.dp),
            enabled = !isAuthenticating
        )

        MyOutlinedTextField(
            field = password,
            onValueChange = { password.value = it },
            label = stringResource(R.string.password),
            modifier = Modifier.fillMaxWidth().height(65.dp),
            enabled = !isAuthenticating,
            isPassword = true
        )

        Button(
            onClick = {
                if (connected) {
                    isAuthenticating = true
                    vm.authenticate(context, email.value, password.value) { result ->
                        isAuthenticating = false
                        if (result) {
                            navController.graph.setStartDestination("HomeView")
                            navController.navigate("HomeView") {
                                popUpTo("LoginView") { inclusive = true }
                            }
                        } else {
                            Toast.makeText(context, toastFailedLogin, Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(context, toastOffline, Toast.LENGTH_LONG).show()
                }
            },
            colors = ButtonDefaults.buttonColors(
                backgroundColor = BoxColor,
                contentColor = Color.White
            ),
            modifier = Modifier
                .height(50.dp)
                .width(150.dp),
            enabled = !isAuthenticating && email.value.isNotBlank() && password.value.isNotBlank(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(stringResource(if (isAuthenticating) R.string.please_wait else R.string.login))
        }
    }
}

@Composable
fun CustomRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 50.dp)
            .padding(bottom = 0.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .fillMaxWidth()
        ) {
            Image(
                painter = painterResource(R.drawable.alfa_pizza_logo),
                contentDescription = stringResource(R.string.logo_content_description),
                modifier = Modifier
                    .size(160.dp)
                    .align(Alignment.Center)
            )
        }
    }
}
