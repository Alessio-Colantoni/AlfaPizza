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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.alfaproject.alfapizza.R
import com.alfaproject.alfapizza.model.Swap
import com.alfaproject.alfapizza.network.rememberNetworkConnectionState
import com.alfaproject.alfapizza.screens.BoxOffline
import com.alfaproject.alfapizza.screens.FirstRowAdmin
import com.alfaproject.alfapizza.ui.theme.BoxColor
import com.alfaproject.alfapizza.ui.theme.MyTextFieldColor
import com.alfaproject.alfapizza.view_model.admin.AdminManageSwapRequestsViewModel

@Composable
fun AdminManageSwapRequestsView(navController: NavHostController) {
    val context = LocalContext.current
    val vm = remember { AdminManageSwapRequestsViewModel() }
    var swaps by remember { mutableStateOf<List<Swap>>(emptyList()) }
    var users by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var operationFailed by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<Pair<Swap, Boolean>?>(null) }
    var busySwap by remember { mutableStateOf<Swap?>(null) }
    val connected by rememberNetworkConnectionState()

    fun loadData() {
        isLoading = true
        loadFailed = false
        vm.loadData(context) { loadedSwaps, loadedUsers, success ->
            isLoading = false
            if (success && loadedSwaps != null && loadedUsers != null) {
                swaps = loadedSwaps
                users = loadedUsers
            } else {
                loadFailed = true
            }
        }
    }

    LaunchedEffect(connected) {
        if (connected) loadData() else isLoading = false
    }

    pendingAction?.let { (swap, approve) ->
        AlertDialog(
            onDismissRequest = { if (busySwap == null) pendingAction = null },
            title = { Text(stringResource(R.string.are_you_sure), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (approve) stringResource(R.string.approve_swap_confirm)
                    else stringResource(R.string.reject_swap_confirm)
                )
            },
            confirmButton = {
                TextButton(
                    enabled = busySwap == null,
                    onClick = {
                        if (busySwap != null) return@TextButton
                        busySwap = swap
                        operationFailed = false
                        val callback: (List<Swap>, Boolean) -> Unit = { updated, success ->
                            busySwap = null
                            pendingAction = null
                            if (success) {
                                swaps = updated
                            } else {
                                operationFailed = true
                                Toast.makeText(context, context.getString(R.string.error), Toast.LENGTH_SHORT).show()
                                loadData()
                            }
                        }
                        if (approve) vm.approveSwap(context, swap, callback)
                        else vm.rejectSwap(context, swap, callback)
                    }
                ) {
                    if (busySwap != null) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.defaultMinSize(20.dp, 20.dp))
                    } else {
                        Text(
                            text = if (approve) stringResource(R.string.approve) else stringResource(R.string.reject),
                            color = if (approve) BoxColor else MaterialTheme.colors.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(enabled = busySwap == null, onClick = { pendingAction = null }) {
                    Text(stringResource(R.string.cancel), color = BoxColor)
                }
            },
            shape = RoundedCornerShape(10.dp),
            backgroundColor = MyTextFieldColor,
            contentColor = Color.Black
        )
    }

    Column {
        FirstRowAdmin(navController, stringResource(R.string.swap_requests))
        if (!connected) {
            BoxOffline()
            return@Column
        }

        if (operationFailed) {
            Text(
                text = stringResource(R.string.error),
                color = MaterialTheme.colors.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            )
        }

        when {
            isLoading -> AdminSwapStateMessage(stringResource(R.string.please_wait), showProgress = true)
            loadFailed -> AdminSwapStateMessage(
                text = stringResource(R.string.error),
                actionLabel = stringResource(R.string.retry),
                onAction = { loadData() }
            )
            swaps.isEmpty() -> NoRequestsBox()
            else -> LazyColumn {
                items(swaps, key = { it._id ?: "${it.fromRider}-${it.fromDay}-${it.toDay}-${it.requestDate}" }) { swap ->
                    SwapBox(
                        swap = swap,
                        users = users,
                        listOfDays = com.alfaproject.alfapizza.MainActivity.daysOfWeek,
                        enabled = busySwap == null && !isLoading,
                        isBusy = busySwap == swap,
                        onApprove = { pendingAction = swap to true },
                        onReject = { pendingAction = swap to false }
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminSwapStateMessage(
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
            ) { Text(actionLabel) }
        }
    }
}

@Composable
fun SwapBox(
    swap: Swap,
    users: Map<Int, String>,
    listOfDays: List<String>,
    enabled: Boolean,
    isBusy: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 150.dp)
                .border(1.dp, BoxColor, RoundedCornerShape(10.dp))
                .background(MyTextFieldColor, RoundedCornerShape(10.dp))
                .padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(users[swap.fromRider] ?: "Rider ${swap.fromRider}", fontWeight = FontWeight.Bold, color = Color.Black)
                Text(dayConverter(swap.fromDay, listOfDays), fontWeight = FontWeight.Bold, color = Color.Black)
            }
            Text(text = stringResource(R.string.swap_with), color = Color.Black)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(users[swap.firstRiderAccepted] ?: "Rider ${swap.firstRiderAccepted}", fontWeight = FontWeight.Bold, color = Color.Black)
                Text(dayConverter(swap.toDay, listOfDays), fontWeight = FontWeight.Bold, color = Color.Black)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = stringResource(R.string.for_), color = Color.Black)
                Text(
                    text = if (swap.isNext) stringResource(R.string.next_week_admin_manage_swap)
                    else stringResource(R.string.this_week_admin_manage_swap),
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                enabled = enabled,
                onClick = onApprove,
                colors = ButtonDefaults.buttonColors(backgroundColor = BoxColor, contentColor = Color.White),
                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 50.dp),
                shape = RoundedCornerShape(10.dp)
            ) { Text(stringResource(R.string.approve)) }
            OutlinedButton(
                enabled = enabled,
                onClick = onReject,
                border = BorderStroke(1.dp, MaterialTheme.colors.error),
                colors = ButtonDefaults.outlinedButtonColors(
                    backgroundColor = MyTextFieldColor,
                    contentColor = MaterialTheme.colors.error,
                    disabledContentColor = Color.Gray
                ),
                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 50.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isBusy) CircularProgressIndicator(strokeWidth = 2.dp)
                else Text(stringResource(R.string.reject))
            }
        }
    }
}

@Composable
fun NoRequestsBox() {
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(text = stringResource(R.string.requests), color = BoxColor, fontSize = 20.sp)
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(text = stringResource(R.string.there_are_not_swap_requests), fontSize = 18.sp, color = Color.Black)
        }
    }
}

fun dayConverter(day: Int, listOfDays: List<String>): String = if (day in 0..6) listOfDays[day] else ""
