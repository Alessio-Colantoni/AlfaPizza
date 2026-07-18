package com.alfaproject.alfapizza.screens.rider

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.AlertDialog
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.alfaproject.alfapizza.MainActivity.Companion.daysOfWeek
import com.alfaproject.alfapizza.R
import com.alfaproject.alfapizza.model.Constraint
import com.alfaproject.alfapizza.network.rememberNetworkConnectionState
import com.alfaproject.alfapizza.screens.BoxOffline
import com.alfaproject.alfapizza.screens.FirstRowRider
import com.alfaproject.alfapizza.ui.theme.BackgroundColor
import com.alfaproject.alfapizza.ui.theme.BoxColor
import com.alfaproject.alfapizza.ui.theme.MyTextFieldColor
import com.alfaproject.alfapizza.view_model.rider.RiderManageYourConstraintsViewModel
import com.alfaproject.alfapizza.view_model.common.HomeViewModel
import com.alfaproject.alfapizza.network.ServerApi
import com.alfaproject.alfapizza.network.Constants

@Composable
fun RiderManageYourConstraintsView(navController: NavHostController) {
    val context = LocalContext.current
    val vm by remember { mutableStateOf(RiderManageYourConstraintsViewModel()) }
    val homeVm by remember { mutableStateOf(HomeViewModel()) }
    var list by remember { mutableStateOf(mutableListOf<Constraint>()) }
    var lastDayConstraint by remember { mutableIntStateOf(7) }
    var publicationDayLabel by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    val string = stringResource(R.string.constraints)
    val connected by rememberNetworkConnectionState()

    fun loadConstraints() {
        isLoading = true
        loadFailed = false
        ServerApi.getJsonArray(context, Constants.Endpoints.WEEK_STRUCTURE) { jsonArray ->
            if (jsonArray != null && jsonArray.length() > 0) {
                var nextWeekIndex = 0
                for (i in 0 until jsonArray.length()) {
                    if (jsonArray.getJSONObject(i).optBoolean("isNext", false)) {
                        nextWeekIndex = i
                        break
                    }
                }
                lastDayConstraint = jsonArray.getJSONObject(nextWeekIndex).optInt("lastDayConstraint", 7)
                publicationDayLabel = if (lastDayConstraint in 0..6) daysOfWeek[lastDayConstraint] else ""
                vm.getInfo(context) { result, success ->
                    if (success) list = result.toList().toMutableList()
                    isLoading = false
                    loadFailed = !success
                }
            } else {
                isLoading = false
                loadFailed = true
            }
        }
    }

    LaunchedEffect(connected) {
        if (connected) loadConstraints() else isLoading = false
    }

    val isLocked = !com.alfaproject.alfapizza.MainActivity.isAdmin && (homeVm.getDayOfWeek() > lastDayConstraint)

    Column {
        FirstRowRider(navController, string)
        if (connected && isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.please_wait))
            }
        } else if (connected && loadFailed) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(stringResource(R.string.error), color = Color.Red)
                Button(onClick = { loadConstraints() }, shape = RoundedCornerShape(10.dp)) {
                    Text(stringResource(R.string.retry))
                }
            }
        } else if (connected) {
            if (isLocked) {
                Box(modifier = Modifier.fillMaxWidth().padding(15.dp).background(Color.Red.copy(alpha = 0.1f), RoundedCornerShape(8.dp)).padding(15.dp)) {
                    Text(
                        stringResource(R.string.publication_day_passed, publicationDayLabel),
                        color = Color.Red,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
            ManageConstraintsColumn(vm, list, isLocked, publicationDayLabel) { result -> list = result.toList().toMutableList() }
        } else {
            BoxOffline()
        }
    }
}

@Composable
fun ManageConstraintsColumn(
    vm: RiderManageYourConstraintsViewModel,
    list: MutableList<Constraint>,
    isLocked: Boolean,
    publicationDayLabel: String,
    callback: (MutableList<Constraint>) -> Unit
) {
    val isAddingConstraint = remember { mutableStateOf(false) }
    val currentList = mutableListOf<Constraint>()
    val nextList = mutableListOf<Constraint>()
    for (i in 0..6) {
        currentList.addAll(list.filter { !it.isNext && it.day == i })
        nextList.addAll(list.filter { it.isNext && it.day == i })
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(5.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = stringResource(R.string.current_week_admin_manage),
                modifier = Modifier.fillMaxWidth().padding(15.dp, 10.dp, 15.dp, 0.dp),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = BoxColor
            )
        }
        if (currentList.isEmpty()) {
            item { Text(text = stringResource(R.string.no_constraints), modifier = Modifier.padding(15.dp), color = Color.Black) }
        }
        items(currentList.size) { index ->
            DayRow(vm, currentList[index], isReadOnly = true) { callback(it) }
        }
        item {
            Text(
                text = stringResource(R.string.next_week_admin_manage),
                modifier = Modifier.fillMaxWidth().padding(15.dp, 18.dp, 15.dp, 0.dp),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = BoxColor
            )
        }
        item {
            Text(
                text = stringResource(R.string.constraints_publication_day_info, publicationDayLabel),
                modifier = Modifier.padding(15.dp, 4.dp),
                textAlign = TextAlign.Center,
                color = BoxColor,
                fontSize = 14.sp
            )
        }
        if (nextList.isEmpty()) {
            item { Text(text = stringResource(R.string.no_constraints), modifier = Modifier.padding(15.dp), color = Color.Black) }
        }
        items(nextList.size) { index ->
            DayRow(vm, nextList[index], isReadOnly = isLocked) { callback(it) }
        }
        item {
            AddConstraint(vm, isAddingConstraint, isLocked) { callback(it) }
        }
    }
}

@Composable
fun DayRow(vm: RiderManageYourConstraintsViewModel, constraint: Constraint, isReadOnly: Boolean, callback: (MutableList<Constraint>) -> Unit) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    val typeLabel = when (constraint.priority) {
        1 -> stringResource(R.string.absolute)
        2 -> stringResource(R.string.preference)
        else -> stringResource(R.string.type)
    }
    val permLabel = if (constraint.permanent) "(${stringResource(R.string.permanent)})" else ""

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!isBusy) showDeleteDialog = false },
            title = { Text(stringResource(R.string.are_you_sure), fontWeight = FontWeight.Bold) },
            confirmButton = {
                TextButton(
                    enabled = !isBusy,
                    onClick = {
                        isBusy = true
                        vm.removeConstraint(context, constraint) { result, success ->
                            isBusy = false
                            if (success) {
                                showDeleteDialog = false
                                callback(result)
                            } else {
                                Toast.makeText(context, context.getString(R.string.constraint_save_error), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) { Text(stringResource(if (isBusy) R.string.please_wait else R.string.confirm), color = BoxColor) }
            },
            dismissButton = {
                TextButton(enabled = !isBusy, onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Box(modifier = Modifier.padding(15.dp, 0.dp).fillMaxWidth().height(60.dp).border(1.dp, BoxColor, RoundedCornerShape(10.dp)).background(MyTextFieldColor, RoundedCornerShape(10.dp))) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "${stringResource(R.string.day)}: ${dayConverter2(constraint.day)} - $typeLabel $permLabel", fontSize = 15.sp, color = Color.Black)
            if (!isReadOnly) {
                IconButton(enabled = !isBusy, onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_action), tint = BoxColor)
                }
            }
        }
    }
}

@Composable
fun AddConstraint(vm: RiderManageYourConstraintsViewModel, isAddingConstraint: MutableState<Boolean>, isLocked: Boolean, callback: (MutableList<Constraint>) -> Unit) {
    val context = LocalContext.current
    val newCons = remember { mutableStateOf(Constraint(-1, -1, -1, false, true)) }
    var isBusy by remember { mutableStateOf(false) }
    fun resetForm() {
        newCons.value = Constraint(-1, -1, -1, false, true)
    }

    if (!isAddingConstraint.value) {
        Button(
            enabled = !isLocked,
            onClick = { resetForm(); isAddingConstraint.value = true },
            modifier = Modifier.padding(vertical = 20.dp).height(50.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(text = stringResource(R.string.add_preference), color = Color.White)
        }
    } else {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(15.dp)) {
            DayWeekPicker(newCons)
            ConstraintTypePicker(newCons)
            PermanentTypePicker(newCons)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    enabled = !isBusy && !isLocked,
                    onClick = {
                        if (isLocked) {
                            Toast.makeText(context, context.getString(R.string.publication_day_save_error), Toast.LENGTH_SHORT).show()
                        } else if (newCons.value.day != -1 && newCons.value.priority != -1) {
                            isBusy = true
                            vm.addConstraint(context, newCons.value) { result, success ->
                                isBusy = false
                                if (success) {
                                    Toast.makeText(context, context.getString(R.string.constraint_saved_success), Toast.LENGTH_SHORT).show()
                                    isAddingConstraint.value = false
                                    resetForm()
                                    callback(result)
                                } else {
                                    Toast.makeText(context, context.getString(R.string.constraint_save_error), Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            Toast.makeText(context, context.getString(R.string.select_day_priority), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.height(45.dp).weight(1f)
                ) {
                    Text(text = stringResource(if (isBusy) R.string.please_wait else R.string.confirm))
                }
                Button(
                    enabled = !isBusy,
                    onClick = { resetForm(); isAddingConstraint.value = false },
                    colors = ButtonDefaults.buttonColors(backgroundColor = BoxColor),
                    modifier = Modifier.height(45.dp).weight(1f)
                ) {
                    Text(text = stringResource(R.string.cancel), color = Color.White)
                }
            }
        }
    }
}

@Composable
fun ConstraintTypePicker(newCons: MutableState<Constraint>) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(stringResource(R.string.absolute), stringResource(R.string.preference))
    val currentLabel = "${stringResource(R.string.type)}: ${if (newCons.value.priority == 1) options[0] else if (newCons.value.priority == 2) options[1] else stringResource(R.string.type)}"

    Box(modifier = Modifier.fillMaxWidth().border(1.dp, BoxColor, RoundedCornerShape(20.dp)).clickable { expanded = true }.padding(15.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = currentLabel, modifier = Modifier.weight(1f))
            Icon(Icons.Default.MoreVert, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(Color.White)) {
            options.forEachIndexed { index, label ->
                DropdownMenuItem(onClick = { newCons.value = newCons.value.copy(priority = index + 1); expanded = false }) {
                    Text(label, color = Color.Black)
                }
            }
        }
    }
}

@Composable
fun PermanentTypePicker(newCons: MutableState<Constraint>) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(stringResource(R.string.no), stringResource(R.string.yes))
    val currentLabel = "${stringResource(R.string.permanent)}: ${if (newCons.value.permanent) options[1] else options[0]}"

    Box(modifier = Modifier.fillMaxWidth().border(1.dp, BoxColor, RoundedCornerShape(20.dp)).clickable { expanded = true }.padding(15.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = currentLabel, modifier = Modifier.weight(1f))
            Icon(Icons.Default.MoreVert, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(Color.White)) {
            options.forEachIndexed { index, label ->
                DropdownMenuItem(onClick = { newCons.value = newCons.value.copy(permanent = index == 1); expanded = false }) {
                    Text(label, color = Color.Black)
                }
            }
        }
    }
}

@Composable
fun DayWeekPicker(newCons: MutableState<Constraint>) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = if (newCons.value.day in 0..6) daysOfWeek[newCons.value.day] else stringResource(R.string.choose_day)

    Box(modifier = Modifier.fillMaxWidth().border(1.dp, BoxColor, RoundedCornerShape(20.dp)).clickable { expanded = true }.padding(15.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = currentLabel, modifier = Modifier.weight(1f))
            Icon(Icons.Default.MoreVert, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(Color.White)) {
            daysOfWeek.forEachIndexed { index, label ->
                DropdownMenuItem(onClick = { newCons.value = newCons.value.copy(day = index); expanded = false }) {
                    Text(label, color = Color.Black)
                }
            }
        }
    }
}

fun dayConverter2(day: Int): String = if (day in 0..6) daysOfWeek[day] else ""

fun constraintTypeFromInt(type: Int): String = when(type) {
    1 -> "Assoluto"; 2 -> "Preferenza"; else -> ""
}
