package com.alfaproject.alfapizza.screens.rider

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.alfaproject.alfapizza.MainActivity.Companion.daysOfWeek
import com.alfaproject.alfapizza.MainActivity.Companion.userCode
import com.alfaproject.alfapizza.R
import com.alfaproject.alfapizza.model.Swap
import com.alfaproject.alfapizza.network.rememberNetworkConnectionState
import com.alfaproject.alfapizza.screens.BoxOffline
import com.alfaproject.alfapizza.screens.FirstRowRider
import com.alfaproject.alfapizza.ui.theme.BoxColor
import com.alfaproject.alfapizza.ui.theme.MyTextFieldColor
import com.alfaproject.alfapizza.view_model.rider.RiderShiftChangeRequestsViewModel
import com.alfaproject.alfapizza.view_model.common.HomeViewModel

@Composable
fun RiderShiftChangeRequestsView(navController: NavHostController) {
    val context = LocalContext.current
    val vm = remember { RiderShiftChangeRequestsViewModel() }
    var receivedList by remember { mutableStateOf(mutableListOf<Swap>()) }
    var sentList by remember { mutableStateOf(mutableListOf<Swap>()) }
    var users by remember { mutableStateOf(mutableMapOf<Int, String>()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var busySwapIds by remember { mutableStateOf(setOf<String>()) }
    val connected by rememberNetworkConnectionState()

    fun loadRequests() {
        isLoading = true
        loadFailed = false
        vm.loadData(context) { success ->
            if (success) {
                receivedList = vm.receivedRequestsList.toMutableList()
                sentList = vm.sentRequestsList.toMutableList()
                users = vm.users.toMutableMap()
            }
            isLoading = false
            loadFailed = !success
        }
    }

    LaunchedEffect(connected) {
        if (connected) loadRequests() else isLoading = false
    }

    Column {
        FirstRowRider(navController, stringResource(R.string.swap_requests))
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
                Button(onClick = { loadRequests() }, shape = RoundedCornerShape(10.dp)) {
                    Text(stringResource(R.string.retry))
                }
            }
        } else if (connected) {
            LazyColumn {
                item {
                    Text(text = stringResource(R.string.requests_received), fontSize = 22.sp, modifier = Modifier.padding(10.dp), color = BoxColor)
                    if (receivedList.isNotEmpty()) {
                        receivedList.forEach { swap ->
                            RequestsSwapBox(swap, users, isReceived = true,
                                isBusy = busySwapIds.contains(swap._id.orEmpty()),
                                onAction = {
                                    val id = swap._id.orEmpty()
                                    busySwapIds = busySwapIds + id
                                    vm.acceptSwap(context, swap) { _, success ->
                                        busySwapIds = busySwapIds - id
                                        receivedList = vm.receivedRequestsList.toMutableList()
                                        sentList = vm.sentRequestsList.toMutableList()
                                        if (!success) Toast.makeText(context, context.getString(R.string.swap_accept_error), Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onDelete = {} // Non serve per ricevute
                            )
                        }
                    } else {
                        NoRequestsBox(stringResource(R.string.there_are_not_swap_requests))
                    }
                }

                item { Divider(modifier = Modifier.padding(25.dp, 8.dp), color = BoxColor) }

                item {
                    Text(text = stringResource(R.string.requests_sent_section), fontSize = 22.sp, modifier = Modifier.padding(10.dp), color = BoxColor)
                    if (sentList.isNotEmpty()) {
                        sentList.forEach { swap ->
                            RequestsSwapBox(swap, users, isReceived = false,
                                isBusy = busySwapIds.contains(swap._id.orEmpty()),
                                onAction = {},
                                onDelete = {
                                    val id = swap._id.orEmpty()
                                    busySwapIds = busySwapIds + id
                                    vm.deleteSwap(context, swap) { _, success ->
                                        busySwapIds = busySwapIds - id
                                        receivedList = vm.receivedRequestsList.toMutableList()
                                        sentList = vm.sentRequestsList.toMutableList()
                                        Toast.makeText(
                                            context,
                                            context.getString(if (success) R.string.request_deleted else R.string.error),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        }
                    } else {
                        NoRequestsBox(stringResource(R.string.no_requests_sent))
                    }
                }

                item { Divider(modifier = Modifier.padding(25.dp, 8.dp), color = BoxColor) }

                item {
                    RequireSwapBox(vm) {
                        receivedList = vm.receivedRequestsList.toMutableList()
                        sentList = vm.sentRequestsList.toMutableList()
                    }
                }
            }
        } else {
            BoxOffline()
        }
    }
}

@Composable
fun NoRequestsBox(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color.Black)
    }
}

@Composable
fun RequireSwapBox(vm: RiderShiftChangeRequestsViewModel, onUpdate: () -> Unit) {
    val context = LocalContext.current
    var fromDay by remember { mutableIntStateOf(-1) }
    var toDay by remember { mutableIntStateOf(-1) }
    var isNext by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var statusIsError by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }

    val nextWeekTag = stringResource(R.string.next_week_tag)
    val today = remember { HomeViewModel().getDayOfWeek() }

    val myShifts = mutableListOf<Triple<String, Int, Boolean>>()
    vm.myShiftsThisWeekList.filter { it >= today }.forEach { myShifts.add(Triple(dayConverter(it), it, false)) }
    vm.myShiftsNextWeekList.forEach { myShifts.add(Triple(dayConverter(it) + " " + nextWeekTag, it, true)) }

    val availableDays = mutableListOf<Triple<String, Int, Boolean>>()
    for (i in today..6) {
        if (!vm.myShiftsThisWeekList.contains(i)) {
            availableDays.add(Triple(dayConverter(i), i, false))
        }
    }
    for (i in 0..6) {
        if (!vm.myShiftsNextWeekList.contains(i)) {
            availableDays.add(Triple(dayConverter(i) + " " + nextWeekTag, i, true))
        }
    }

    Column(modifier = Modifier.padding(15.dp)) {
        Text(stringResource(R.string.require_swap), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = BoxColor)

        if (statusText.isNotEmpty()) {
            Text(statusText, color = if (statusIsError) Color.Red else BoxColor, fontSize = 14.sp, modifier = Modifier.padding(vertical = 5.dp))
        }

        Text(stringResource(R.string.your_actual_shift), modifier = Modifier.padding(top = 10.dp))
        var exp1 by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(
                onClick = { exp1 = true },
                enabled = !isSending,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, BoxColor),
                colors = ButtonDefaults.outlinedButtonColors(
                    backgroundColor = MyTextFieldColor,
                    contentColor = Color.Black
                )
            ) {
                val label = if (fromDay == -1) stringResource(R.string.choose_day)
                            else (dayConverter(fromDay) + if (isNext) " $nextWeekTag" else "")
                Text(label, color = Color.Black)
                Icon(Icons.Default.ArrowDropDown, null, tint = BoxColor)
            }
            DropdownMenu(expanded = exp1, onDismissRequest = { exp1 = false }, modifier = Modifier.background(Color.White)) {
                if (myShifts.isEmpty()) {
                    DropdownMenuItem(onClick = { exp1 = false }) { Text(stringResource(R.string.no_shifts_found)) }
                } else {
                    myShifts.forEach { (label, day, next) ->
                        DropdownMenuItem(onClick = {
                            fromDay = day
                            isNext = next
                            toDay = -1
                            exp1 = false
                        }) { Text(label, color = Color.Black) }
                    }
                }
            }
        }

        Text(stringResource(R.string.with), modifier = Modifier.padding(top = 10.dp))
        var exp2 by remember { mutableStateOf(false) }
        Box {
            val enabled = fromDay != -1 && !isSending
            OutlinedButton(
                onClick = { if (enabled) exp2 = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                border = BorderStroke(1.dp, BoxColor),
                colors = ButtonDefaults.outlinedButtonColors(
                    backgroundColor = MyTextFieldColor,
                    contentColor = Color.Black,
                    disabledContentColor = Color.Gray
                )
            ) {
                val label = if (toDay == -1) stringResource(R.string.choose_day)
                            else (dayConverter(toDay) + if (isNext) " $nextWeekTag" else "")
                Text(label, color = if (enabled) Color.Black else Color.Gray)
                Icon(Icons.Default.ArrowDropDown, null, tint = if (enabled) BoxColor else Color.Gray)
            }
            DropdownMenu(expanded = exp2, onDismissRequest = { exp2 = false }, modifier = Modifier.background(Color.White)) {
                val filtered = availableDays.filter { it.third == isNext }
                if (filtered.isEmpty()) {
                    DropdownMenuItem(onClick = { exp2 = false }) { Text(stringResource(R.string.no_requests)) }
                } else {
                    filtered.forEach { (label, day, _) ->
                        DropdownMenuItem(onClick = { toDay = day; exp2 = false }) { Text(label, color = Color.Black) }
                    }
                }
            }
        }

        Button(
            onClick = {
                if (fromDay == -1 || toDay == -1) {
                    statusText = context.getString(R.string.select_both_days)
                    statusIsError = true
                } else {
                    statusText = context.getString(R.string.sending_request)
                    statusIsError = false
                    isSending = true
                    vm.newRequest(
                        context,
                        Swap(
                            fromRider = userCode,
                            firstRiderAccepted = -1,
                            fromDay = fromDay,
                            toDay = toDay,
                            requestDate = "",
                            isNext = isNext,
                            isReadyForAdmin = false
                        )
                    ) { _, success ->
                        isSending = false
                        if (success) {
                            statusText = context.getString(R.string.request_sent_success)
                            statusIsError = false
                            fromDay = -1; toDay = -1; isNext = false
                            onUpdate()
                        } else {
                            statusText = context.getString(R.string.request_send_error)
                            statusIsError = true
                        }
                    }
                }
            },
            enabled = !isSending,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(50.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = BoxColor, contentColor = Color.White),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(stringResource(if (isSending) R.string.please_wait else R.string.confirm))
        }
    }
}

@Composable
fun RequestsSwapBox(
    swap: Swap,
    users: Map<Int, String>,
    isReceived: Boolean,
    isBusy: Boolean,
    onAction: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val nextTag = if (swap.isNext) " " + stringResource(R.string.next_week_tag) else ""

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!isBusy) showDeleteDialog = false },
            title = { Text(stringResource(R.string.are_you_sure), fontWeight = FontWeight.Bold) },
            confirmButton = {
                TextButton(
                    enabled = !isBusy,
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) { Text(stringResource(R.string.confirm), color = BoxColor) }
            },
            dismissButton = {
                TextButton(enabled = !isBusy, onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    Card(modifier = Modifier.padding(10.dp).fillMaxWidth(), elevation = 2.dp, backgroundColor = MyTextFieldColor) {
        Column(modifier = Modifier.padding(15.dp)) {
            if (isReceived) {
                Text(stringResource(R.string.your_actual_shift_label, dayConverter(swap.toDay) + nextTag))
                Text(stringResource(R.string.swap_with_label, (users[swap.fromRider] ?: "Rider") + " (" + dayConverter(swap.fromDay) + nextTag + ")"))
            } else {
                Text(stringResource(R.string.your_actual_shift_label, dayConverter(swap.fromDay) + nextTag))
                Text(stringResource(R.string.swap_with_label, dayConverter(swap.toDay) + nextTag))
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (isReceived) {
                    Button(
                        onClick = onAction,
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(backgroundColor = BoxColor, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text(stringResource(R.string.accept)) }
                } else {
                    val statusText = if (swap.isReadyForAdmin) stringResource(R.string.waiting_admin)
                                     else stringResource(R.string.waiting_rider)
                    Text(statusText, style = MaterialTheme.typography.caption, color = BoxColor)

                    IconButton(enabled = !isBusy, onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_action), tint = BoxColor)
                    }
                }
            }
        }
    }
}

fun dayConverter(day: Int): String = if (day in 0..6) daysOfWeek[day] else ""
