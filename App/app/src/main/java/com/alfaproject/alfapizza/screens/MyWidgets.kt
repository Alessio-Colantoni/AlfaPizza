package com.alfaproject.alfapizza.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.alfaproject.alfapizza.MainActivity.Companion.isAdmin
import com.alfaproject.alfapizza.MainActivity.Companion.userCode
import com.alfaproject.alfapizza.R
import com.alfaproject.alfapizza.network.SessionManager
import com.alfaproject.alfapizza.network.ServerApi
import com.alfaproject.alfapizza.ui.theme.BackgroundColor
import com.alfaproject.alfapizza.ui.theme.BoxColor
import com.alfaproject.alfapizza.ui.theme.MyTextFieldColor

@Composable
fun MyOutlinedTextField(
    field: MutableState<String>,
    label: String,
    modifier: Modifier,
    enabled: Boolean = true,
    keyboardActions: KeyboardActions = KeyboardActions { },
    onValueChange: (String) -> Unit = { },
    isPassword: Boolean = false
) {
    val visualTransformation =
    if (isPassword) PasswordVisualTransformation() else VisualTransformation.None
    val keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text

    OutlinedTextField(
        value = field.value,
        onValueChange = {
            field.value =
                it
            onValueChange(it)
        },
        label = {
            Text(text = label, color = BoxColor, textAlign = TextAlign.Center)
        },
        enabled = enabled,
        keyboardActions = keyboardActions,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        singleLine = true,
        modifier = modifier,
        textStyle = TextStyle(color = Color.Black, fontSize = 16.sp),
        shape = RoundedCornerShape(20.dp),
        colors = TextFieldDefaults.outlinedTextFieldColors(
            focusedBorderColor = BoxColor,
            unfocusedBorderColor = BoxColor,
            disabledBorderColor = BoxColor,
            backgroundColor = MyTextFieldColor
        )
    )

}

@Composable
fun FirstRowAdmin(navController: NavHostController, string: String) {
    InternalTopBar(string, menu = { MyAdminBoxMenu(navController) })
}

@Composable
fun FirstRowRider(navController: NavHostController, string: String) {
    InternalTopBar(string, menu = { MyRiderBoxMenu(navController) })
}

@Composable
private fun InternalTopBar(string: String, menu: @Composable () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = string,
                color = BoxColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)
            )
        },
        navigationIcon = { LogoTopBar() },
        actions = { menu() },
        backgroundColor = MyTextFieldColor,
        elevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .heightIn(min = 72.dp)
    )
}

@Composable
fun LogoTopBar() {
    Box(
        modifier = Modifier
    ) {
        Image(
            painter = painterResource(R.drawable.alfa_pizza_logo),
            contentDescription = stringResource(R.string.logo_content_description),
            modifier = Modifier
                .size(56.dp)
                .align(Alignment.Center)
        )
    }
}

@Composable
fun MyAdminBoxMenu(navController: NavHostController) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(50.dp)
            .background(BoxColor, RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(5.dp))
            .border(BorderStroke(1.dp, Color.Black))
    ) {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(50.dp)) {
            Icon(
                imageVector = Icons.Default.Menu,
                tint = Color.White,
                contentDescription = stringResource(R.string.open_navigation_menu)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(color = BoxColor)
                .padding(start = 0.dp, top = 0.dp, end = 0.dp, bottom = 0.dp)

        ) {

            DropdownMenuItem(onClick = {
                navController.popBackStack(navController.graph.startDestinationId, true)
                navController.graph.setStartDestination("HomeView")
                navController.navigate("HomeView")
            }) {
                Text(stringResource(R.string.home), color = BackgroundColor)
            }

            DropdownMenuItem(onClick = { navController.navigate("AdminManageSwapRequestsView") }) {
                Text(stringResource(R.string.manage_swap_request), color = BackgroundColor)
            }

            DropdownMenuItem(onClick = { navController.navigate("AdminManageNextWeekView") }) {
                Text(stringResource(R.string.manage_next_week), color = BackgroundColor)
            }

            DropdownMenuItem(onClick = { navController.navigate("AdminManageRidersView") }) {
                Text(stringResource(R.string.manage_riders), color = BackgroundColor)
            }

            DropdownMenuItem(onClick = { navController.navigate("PersonalInfoView") }) {
                Text(stringResource(R.string.personal_info), color = BackgroundColor)
            }

            DropdownMenuItem(onClick = {
                val sessionManager = SessionManager(context)
                ServerApi.logout(context, sessionManager.getAuthToken()) { }
                sessionManager.clearSession()
                isAdmin=false
                userCode=-1
                com.alfaproject.alfapizza.MainActivity.currentUserEmail = null
                navController.popBackStack(navController.graph.startDestinationId, true)
                navController.graph.setStartDestination("LoginView")
                navController.navigate("LoginView")
            }) {
                Text(stringResource(R.string.log_out), color = BackgroundColor)
            }
        }
    }
}

@Composable
fun MyRiderBoxMenu(navController: NavHostController) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(50.dp)
            .background(BoxColor, RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(5.dp))
            .border(BorderStroke(1.dp, Color.Black))
    ) {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(50.dp)) {
            Icon(
                imageVector = Icons.Default.Menu,
                tint = Color.White,
                contentDescription = stringResource(R.string.open_navigation_menu)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .width(280.dp)
                .background(color = BoxColor)
                .padding(start = 0.dp, top = 0.dp, end = 0.dp, bottom = 0.dp)

        ) {

            DropdownMenuItem(onClick = {
                navController.popBackStack(navController.graph.startDestinationId, true)
                navController.graph.setStartDestination("HomeView")
                navController.navigate("HomeView")
            }) {
                Text(stringResource(R.string.home), color = BackgroundColor)
            }

            DropdownMenuItem(onClick = { navController.navigate("RiderShiftChangeRequestsView") }) {
                Text(stringResource(R.string.shift_change_request), color = BackgroundColor)
            }

            DropdownMenuItem(onClick = { navController.navigate("RiderManageYourConstraintsView") }) {
                Text(stringResource(R.string.manage_your_constraints), color = BackgroundColor)
            }

            DropdownMenuItem(onClick = { navController.navigate("PersonalInfoView") }) {
                Text(stringResource(R.string.personal_info), color = BackgroundColor)
            }

            DropdownMenuItem(onClick = {
                val sessionManager = SessionManager(context)
                ServerApi.logout(context, sessionManager.getAuthToken()) { }
                sessionManager.clearSession()
                isAdmin=false
                userCode=-1
                com.alfaproject.alfapizza.MainActivity.currentUserEmail = null
                navController.popBackStack(navController.graph.startDestinationId, true)
                navController.graph.setStartDestination("LoginView")
                navController.navigate("LoginView")
            }) {
                Text(stringResource(R.string.log_out), color = BackgroundColor)
            }
        }
    }
}

@Composable
fun BoxOffline() {
    Box(modifier = Modifier.fillMaxHeight(),
        contentAlignment = Alignment.Center) {
        Text(text = stringResource(R.string.you_are_offline), modifier = Modifier
            .padding(10.dp)
            .fillMaxWidth(),
            color = Color.Black,
            textAlign = TextAlign.Center,
            fontSize = 20.sp, softWrap = true
        )
    }
}
