package com.alfaproject.alfapizza.screens.admin

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.alfaproject.alfapizza.R
import com.alfaproject.alfapizza.model.User
import com.alfaproject.alfapizza.network.rememberNetworkConnectionState
import com.alfaproject.alfapizza.screens.BoxOffline
import com.alfaproject.alfapizza.screens.FirstRowAdmin
import com.alfaproject.alfapizza.screens.MyOutlinedTextField
import com.alfaproject.alfapizza.ui.theme.BoxColor
import com.alfaproject.alfapizza.ui.theme.MyTextFieldColor
import com.alfaproject.alfapizza.view_model.admin.AdminManageRidersViewModel

@Composable
fun AdminManageRidersView(navController: NavHostController) {
    val context = LocalContext.current
    val vm = remember { AdminManageRidersViewModel() }
    var riders by remember { mutableStateOf<List<User>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var operationFailed by remember { mutableStateOf(false) }
    var busyRiderCode by remember { mutableStateOf<Int?>(null) }
    var isCreating by remember { mutableStateOf(false) }
    val isAddingRider = remember { mutableStateOf(false) }
    val connected by rememberNetworkConnectionState()

    fun loadRiders() {
        isLoading = true
        loadFailed = false
        vm.getRiders(context) { result, success ->
            isLoading = false
            if (success && result != null) {
                riders = result.toList()
            } else {
                loadFailed = true
            }
        }
    }

    LaunchedEffect(connected) {
        if (connected) loadRiders() else isLoading = false
    }

    Column {
        FirstRowAdmin(navController, stringResource(R.string.manage_riders))
        if (!connected) {
            BoxOffline()
            return@Column
        }

        if (operationFailed) {
            Text(
                text = stringResource(R.string.error),
                color = MaterialTheme.colors.error,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                textAlign = TextAlign.Center
            )
        }

        when {
            isLoading -> AdminRidersStateMessage(showProgress = true, text = stringResource(R.string.please_wait))
            loadFailed -> AdminRidersStateMessage(
                text = stringResource(R.string.error),
                actionLabel = stringResource(R.string.retry),
                onAction = { loadRiders() }
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (riders.isEmpty()) {
                    item(key = "empty_riders") {
                        AdminRidersStateMessage(text = stringResource(R.string.no_riders))
                    }
                } else {
                    items(items = riders, key = { it.code }) { user ->
                        RidersBox(
                            user = user,
                            enabled = busyRiderCode == null && !isCreating,
                            isDeleting = busyRiderCode == user.code,
                            onDelete = {
                                operationFailed = false
                                busyRiderCode = user.code
                                vm.deleteRider(context, user) { succeeded, refreshedRiders, refreshSucceeded ->
                                    busyRiderCode = null
                                    if (succeeded) {
                                        riders = refreshedRiders?.toList()
                                            ?: riders.filterNot { it.code == user.code }
                                    }
                                    if (!succeeded || !refreshSucceeded) {
                                        operationFailed = true
                                        Toast.makeText(context, context.getString(R.string.error), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }
                item(key = "add_rider") {
                    AddRiderColumn(
                        vm = vm,
                        isAddingRider = isAddingRider,
                        enabled = busyRiderCode == null && !isCreating,
                        isSaving = isCreating,
                        onSavingChanged = { isCreating = it },
                        onAdded = { createdUser, refreshedRiders, refreshSucceeded ->
                            riders = refreshedRiders?.toList()
                                ?: (riders.filterNot { it.code == createdUser.code } + createdUser)
                            if (!refreshSucceeded) operationFailed = true
                        },
                        onError = {
                            operationFailed = true
                            Toast.makeText(context, context.getString(R.string.error), Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminRidersStateMessage(
    text: String,
    showProgress: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (showProgress) CircularProgressIndicator(color = BoxColor)
        Text(text = text, color = Color.Black, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(backgroundColor = BoxColor, contentColor = Color.White),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun RidersBox(
    user: User,
    enabled: Boolean,
    isDeleting: Boolean,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteDialog = false },
            title = { Text(stringResource(R.string.are_you_sure), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.delete_rider_confirm)) },
            confirmButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.delete_action), color = MaterialTheme.colors.error)
                    }
                }
            },
            dismissButton = {
                TextButton(enabled = !isDeleting, onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel), color = BoxColor)
                }
            },
            shape = RoundedCornerShape(10.dp),
            backgroundColor = MyTextFieldColor,
            contentColor = Color.Black
        )
    }

    Box(
        modifier = Modifier
            .padding(horizontal = 15.dp, vertical = 5.dp)
            .fillMaxWidth()
            .defaultMinSize(minHeight = 60.dp)
            .border(1.dp, BoxColor, RoundedCornerShape(10.dp))
            .background(MyTextFieldColor, RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${user.name} ${user.surname}",
                fontSize = 16.sp,
                color = Color.Black,
                modifier = Modifier.weight(1f)
            )
            if (isDeleting) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(12.dp).height(24.dp),
                    color = BoxColor,
                    strokeWidth = 2.dp
                )
            } else {
                IconButton(enabled = enabled, onClick = { showDeleteDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete_action),
                        tint = if (enabled) MaterialTheme.colors.error else Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun AddRiderColumn(
    vm: AdminManageRidersViewModel,
    isAddingRider: MutableState<Boolean>,
    enabled: Boolean,
    isSaving: Boolean,
    onSavingChanged: (Boolean) -> Unit,
    onAdded: (createdUser: User, riders: MutableList<User>?, refreshSucceeded: Boolean) -> Unit,
    onError: () -> Unit
) {
    val context = LocalContext.current
    val name = remember { mutableStateOf("") }
    val surname = remember { mutableStateOf("") }
    val email = remember { mutableStateOf("") }
    val phone = remember { mutableStateOf("") }
    var showPasswordDialog by rememberSaveable { mutableStateOf(false) }
    var generatedPassword by rememberSaveable { mutableStateOf("") }

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.user_created), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = stringResource(R.string.temp_pass_msg) + generatedPassword,
                    fontSize = 18.sp,
                    color = Color.Black
                )
            },
            confirmButton = {
                TextButton(onClick = { showPasswordDialog = false }) {
                    Text(
                        text = stringResource(R.string.confirm),
                        color = BoxColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            shape = RoundedCornerShape(10.dp),
            backgroundColor = MyTextFieldColor,
            contentColor = Color.Black
        )
    }

    if (!isAddingRider.value) {
        Button(
            enabled = enabled,
            onClick = { isAddingRider.value = true },
            modifier = Modifier.padding(vertical = 20.dp).height(50.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = BoxColor,
                contentColor = Color.White,
                disabledBackgroundColor = BoxColor.copy(alpha = 0.45f),
                disabledContentColor = Color.White.copy(alpha = 0.7f)
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(text = stringResource(R.string.add_rider))
        }
        return
    }

    Column(
        modifier = Modifier.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        MyOutlinedTextField(name, stringResource(R.string.name), Modifier.fillMaxWidth(), enabled = !isSaving)
        MyOutlinedTextField(surname, stringResource(R.string.surname), Modifier.fillMaxWidth(), enabled = !isSaving)
        MyOutlinedTextField(email, stringResource(R.string.email), Modifier.fillMaxWidth(), enabled = !isSaving)
        MyOutlinedTextField(phone, stringResource(R.string.phone), Modifier.fillMaxWidth(), enabled = !isSaving)

        val allFieldsFilled = name.value.isNotBlank() && surname.value.isNotBlank() &&
            email.value.isNotBlank() && phone.value.isNotBlank()
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                enabled = !isSaving && allFieldsFilled,
                onClick = {
                    val password = vm.generateRandomPassword(8)
                    val newUser = User(
                        name.value,
                        surname.value,
                        email.value,
                        phone.value,
                        vm.generateRandomCode(),
                        password,
                        false,
                        ""
                    )
                    onSavingChanged(true)
                    vm.addRider(context, newUser) { succeeded, refreshedRiders, refreshSucceeded ->
                        onSavingChanged(false)
                        if (succeeded) {
                            generatedPassword = password
                            showPasswordDialog = true
                            isAddingRider.value = false
                            name.value = ""
                            surname.value = ""
                            email.value = ""
                            phone.value = ""
                            onAdded(newUser, refreshedRiders, refreshSucceeded)
                        } else {
                            onError()
                        }
                    }
                },
                modifier = Modifier.height(45.dp).weight(1f),
                colors = ButtonDefaults.buttonColors(backgroundColor = BoxColor, contentColor = Color.White),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.height(20.dp))
                } else {
                    Text(text = stringResource(R.string.confirm))
                }
            }
            OutlinedButton(
                enabled = !isSaving,
                onClick = { isAddingRider.value = false },
                border = BorderStroke(1.dp, BoxColor),
                colors = ButtonDefaults.outlinedButtonColors(
                    backgroundColor = MyTextFieldColor,
                    contentColor = BoxColor,
                    disabledContentColor = Color.Gray
                ),
                modifier = Modifier.height(45.dp).weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    }
}
