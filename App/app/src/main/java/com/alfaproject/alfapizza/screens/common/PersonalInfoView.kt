package com.alfaproject.alfapizza.screens.common

import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.alfaproject.alfapizza.MainActivity.Companion.isAdmin
import com.alfaproject.alfapizza.R
import com.alfaproject.alfapizza.model.User
import com.alfaproject.alfapizza.network.rememberNetworkConnectionState
import com.alfaproject.alfapizza.screens.BoxOffline
import com.alfaproject.alfapizza.screens.FirstRowAdmin
import com.alfaproject.alfapizza.screens.FirstRowRider
import com.alfaproject.alfapizza.ui.theme.BoxColor
import com.alfaproject.alfapizza.ui.theme.MyTextFieldColor
import com.alfaproject.alfapizza.view_model.common.PersonalInfoViewModel

@Composable
fun PersonalInfoView(navController: NavHostController) {
    val vm = remember { PersonalInfoViewModel() }
    var userState by remember { mutableStateOf<User?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val connected by rememberNetworkConnectionState()

    fun loadInfo() {
        isLoading = true
        loadFailed = false
        vm.getInfo(context) { result ->
            userState = result
            isLoading = false
            loadFailed = result == null
        }
    }

    LaunchedEffect(connected) {
        if (connected) loadInfo() else isLoading = false
    }

    val string: String = stringResource(R.string.personal_info)
    Column {
        if (isAdmin) {
            FirstRowAdmin(navController, string)
        } else {
            FirstRowRider(navController, string)
        }

        if (!connected) {
            BoxOffline()
        } else if (isLoading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = stringResource(R.string.please_wait))
            }
        } else if (loadFailed) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = stringResource(R.string.error), color = Color.Red)
                Button(onClick = { loadInfo() }, shape = RoundedCornerShape(10.dp)) {
                    Text(stringResource(R.string.retry))
                }
            }
        } else if (userState != null) {
            PersonalInfoColumn(vm, userState!!) { result ->
                userState = result
            }
        }
    }
}

@Composable
fun PersonalInfoColumn(vm: PersonalInfoViewModel, user: User, onUserChanged: (User) -> Unit) {
    val context = LocalContext.current
    var newEmail by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var emailTextField by remember { mutableStateOf(false) }
    var phoneTextField by remember { mutableStateOf(false) }
    var passwordField by remember { mutableStateOf(false) }
    var updateInProgress by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { _ ->
                    emailTextField = false
                    phoneTextField = false
                    passwordField = false
                }
            }
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(15.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(40.dp)) {
            // Nome e Cognome (Sola Lettura)
            InfoRow(label = stringResource(R.string.name_person_info), value = user.name)
            InfoRow(label = stringResource(R.string.surname_person_info), value = user.surname)

            // Sezione Telefono
            EditableSection(
                label = stringResource(R.string.phone_person_info),
                value = user.phone ?: "",
                isEditing = phoneTextField,
                tempValue = newPhone,
                placeholder = stringResource(R.string.insert_new_phone),
                onEditClick = { phoneTextField = true; emailTextField = false; passwordField = false },
                onValueChange = { newPhone = it },
                isBusy = updateInProgress,
                onConfirm = {
                    if (newPhone.isNotBlank()) {
                        updateInProgress = true
                        vm.updatePhone(context, newPhone) { updatedUser ->
                            updateInProgress = false
                            if (updatedUser != null) {
                                onUserChanged(updatedUser)
                                phoneTextField = false
                                newPhone = ""
                            } else {
                                Toast.makeText(context, context.getString(R.string.update_phone_error), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            )

            // Sezione Email
            EditableSection(
                label = stringResource(R.string.email_person_info),
                value = user.email ?: "",
                isEditing = emailTextField,
                tempValue = newEmail,
                placeholder = stringResource(R.string.insert_new_email),
                onEditClick = { emailTextField = true; phoneTextField = false; passwordField = false },
                onValueChange = { newEmail = it },
                isBusy = updateInProgress,
                onConfirm = {
                    if (newEmail.isNotBlank()) {
                        updateInProgress = true
                        vm.updateEmail(context, newEmail) { updatedUser ->
                            updateInProgress = false
                            if (updatedUser != null) {
                                onUserChanged(updatedUser)
                                emailTextField = false
                                newEmail = ""
                            } else {
                                Toast.makeText(context, context.getString(R.string.update_email_error), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            )

            // Sezione Password
            EditableSection(
                label = stringResource(R.string.password_person_info),
                value = "********",
                isEditing = passwordField,
                tempValue = newPassword,
                placeholder = stringResource(R.string.insert_new_password),
                isPassword = true,
                onEditClick = { passwordField = true; emailTextField = false; phoneTextField = false },
                onValueChange = { newPassword = it },
                isBusy = updateInProgress,
                onConfirm = {
                    if (newPassword.isNotBlank()) {
                        updateInProgress = true
                        vm.updatePassword(context, newPassword) { updatedUser ->
                            updateInProgress = false
                            if (updatedUser != null) {
                                onUserChanged(updatedUser)
                                passwordField = false
                                newPassword = ""
                                Toast.makeText(context, context.getString(R.string.password_updated), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, context.getString(R.string.update_password_error), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(40.dp)) {
        Text(text = label, color = BoxColor, fontSize = 18.sp, modifier = Modifier.fillMaxWidth(0.4f))
        Text(text = value, color = Color.Black, fontSize = 18.sp)
    }
}

@Composable
fun EditableSection(
    label: String,
    value: String,
    isEditing: Boolean,
    tempValue: String,
    placeholder: String,
    isPassword: Boolean = false,
    isBusy: Boolean = false,
    onEditClick: () -> Unit,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(40.dp)) {
            Text(text = label, color = BoxColor, fontSize = 18.sp, modifier = Modifier.fillMaxWidth(0.4f))
            if (!isEditing) Text(text = value, color = Color.Black, fontSize = 18.sp)
        }
        if (isEditing) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 10.dp)
            ) {
                TextField(
                    value = tempValue,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f).height(55.dp),
                    textStyle = TextStyle(fontSize = 14.sp),
                    singleLine = true,
                    visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                    enabled = !isBusy,
                    shape = RoundedCornerShape(10.dp),
                    colors = TextFieldDefaults.textFieldColors(backgroundColor = MyTextFieldColor),
                    placeholder = { Text(placeholder, fontSize = 12.sp) }
                )
                Button(
                    onClick = onConfirm,
                    enabled = !isBusy && tempValue.isNotBlank(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(if (isBusy) R.string.please_wait else R.string.confirm))
                }
            }
        } else {
            Button(
                onClick = onEditClick,
                enabled = !isBusy,
                modifier = Modifier.padding(top = 5.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.change))
            }
        }
    }
}
