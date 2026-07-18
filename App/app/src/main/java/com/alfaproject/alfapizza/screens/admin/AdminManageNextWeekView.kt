package com.alfaproject.alfapizza.screens.admin

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
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
import com.alfaproject.alfapizza.MainActivity.Companion.daysOfWeek
import com.alfaproject.alfapizza.R
import com.alfaproject.alfapizza.model.WeekStructure
import com.alfaproject.alfapizza.network.rememberNetworkConnectionState
import com.alfaproject.alfapizza.screens.BoxOffline
import com.alfaproject.alfapizza.screens.FirstRowAdmin
import com.alfaproject.alfapizza.time.AppTime
import com.alfaproject.alfapizza.ui.theme.BackgroundColor
import com.alfaproject.alfapizza.ui.theme.BoxColor
import com.alfaproject.alfapizza.ui.theme.MyTextFieldColor
import com.alfaproject.alfapizza.view_model.admin.AdminManageNextWeekViewModel

@Composable
fun AdminManageNextWeekView(navController: NavHostController) {
    val vm = remember { AdminManageNextWeekViewModel() }
    var currentWeek by remember { mutableStateOf<WeekStructure?>(null) }
    var nextWeek by remember { mutableStateOf<WeekStructure?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var isUpdating by remember { mutableStateOf(false) }
    var operationFailed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val connected by rememberNetworkConnectionState()

    fun loadConfiguration() {
        isLoading = true
        loadFailed = false
        vm.getInfo(context) { current, next, requestSucceeded ->
            isLoading = false
            if (requestSucceeded) {
                currentWeek = current
                nextWeek = next
            } else {
                loadFailed = true
            }
        }
    }

    fun updateWeek(request: ((WeekStructure?, Boolean) -> Unit) -> Unit) {
        if (isUpdating) return
        isUpdating = true
        operationFailed = false
        request { updated, success ->
            isUpdating = false
            if (success && updated != null) {
                if (updated.isNext) nextWeek = updated else currentWeek = updated
            } else {
                operationFailed = true
                Toast.makeText(context, context.getString(R.string.error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(connected) {
        if (connected) loadConfiguration() else isLoading = false
    }

    Column {
        FirstRowAdmin(navController, stringResource(R.string.manage_next_week))
        if (!connected) {
            BoxOffline()
            return@Column
        }

        if (isUpdating) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = BoxColor)
        if (operationFailed) {
            Text(
                text = stringResource(R.string.error),
                color = MaterialTheme.colors.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            )
        }

        when {
            isLoading -> AdminWeekStateMessage(stringResource(R.string.please_wait), showProgress = true)
            loadFailed -> AdminWeekStateMessage(
                text = stringResource(R.string.error),
                actionLabel = stringResource(R.string.retry),
                onAction = { loadConfiguration() }
            )
            currentWeek == null || nextWeek == null -> AdminWeekStateMessage(stringResource(R.string.no_week_configuration))
            else -> LazyColumn {
                item {
                    PublicationDayBox(nextWeek = nextWeek!!, enabled = !isUpdating) { input ->
                        if (input < getTodayIndex()) {
                            Toast.makeText(context, context.getString(R.string.past_publication_day_error), Toast.LENGTH_SHORT).show()
                        } else if (!isUpdating) {
                            isUpdating = true
                            operationFailed = false
                            vm.changePublicationDay(context, input) { current, next, success ->
                                isUpdating = false
                                if (success && current != null && next != null) {
                                    currentWeek = current
                                    nextWeek = next
                                } else {
                                    operationFailed = true
                                    Toast.makeText(context, context.getString(R.string.error), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
                item { Divider(modifier = Modifier.padding(25.dp, 12.dp), color = BoxColor) }
                item {
                    WeekStructureBox(
                        title = stringResource(R.string.current_week_admin_manage),
                        week = currentWeek!!,
                        enabled = !isUpdating,
                        onDailyChanged = { index, value ->
                            updateWeek { callback -> vm.changeDailyRider(context, false, index, value, callback) }
                        },
                        onMinChanged = { value ->
                            updateWeek { callback -> vm.changeMin(context, false, value, callback) }
                        },
                        onMaxChanged = { value ->
                            updateWeek { callback -> vm.changeMax(context, false, value, callback) }
                        }
                    )
                }
                item { Divider(modifier = Modifier.padding(25.dp, 12.dp), color = BoxColor) }
                item {
                    WeekStructureBox(
                        title = stringResource(R.string.next_week_admin_manage),
                        week = nextWeek!!,
                        enabled = !isUpdating,
                        onDailyChanged = { index, value ->
                            updateWeek { callback -> vm.changeDailyRider(context, true, index, value, callback) }
                        },
                        onMinChanged = { value ->
                            updateWeek { callback -> vm.changeMin(context, true, value, callback) }
                        },
                        onMaxChanged = { value ->
                            updateWeek { callback -> vm.changeMax(context, true, value, callback) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminWeekStateMessage(
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
fun PublicationDayBox(nextWeek: WeekStructure, enabled: Boolean, onDaySelected: (Int) -> Unit) {
    val publicationDay = nextWeek.lastDayConstraint
    val currentDay = if (publicationDay in 0..6) daysOfWeek[publicationDay] else stringResource(R.string.choose_day)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
    ) {
        Text(
            text = stringResource(R.string.last_day_for_rider_s_contstraints_communications),
            modifier = Modifier.fillMaxWidth().padding(13.dp, 12.dp),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = BoxColor
        )
        DayOfWeekPicker(currentDay, enabled) { onDaySelected(daysOfWeek.indexOf(it)) }
    }
}

@Composable
fun WeekStructureBox(
    title: String,
    week: WeekStructure,
    enabled: Boolean,
    onDailyChanged: (Int, Int) -> Unit,
    onMinChanged: (Int) -> Unit,
    onMaxChanged: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth().padding(13.dp, 12.dp),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = BoxColor
        )
        Text(
            text = stringResource(R.string.indicate_for_each_day_of_the_week_the_number_of_riders_required),
            modifier = Modifier.padding(13.dp, 5.dp),
            fontSize = 18.sp
        )
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp, 15.dp)) {
            Column(modifier = Modifier.weight(1f).padding(5.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                for (index in 0..3) {
                    DayShiftSelector(daysOfWeek[index], week.listShift[index], enabled) { onDailyChanged(index, it) }
                }
            }
            Column(modifier = Modifier.weight(1f).padding(5.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                for (index in 4..6) {
                    DayShiftSelector(daysOfWeek[index], week.listShift[index], enabled) { onDailyChanged(index, it) }
                }
            }
        }

        Divider(modifier = Modifier.padding(25.dp, 8.dp), color = BoxColor)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)) {
            Text(
                text = stringResource(R.string.minimum_and_maximum_number_of_shifts_per_rider_per_week),
                fontSize = 18.sp,
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                textAlign = TextAlign.Center
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val minLabel = stringResource(R.string.min)
                Text("$minLabel: ", fontSize = 14.sp)
                Box(modifier = Modifier.border(1.dp, BoxColor).background(MyTextFieldColor)) {
                    NumberPicker(week.minRider, onMinChanged, 1, 7, enabled, minLabel)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val maxLabel = stringResource(R.string.max)
                Text("$maxLabel: ", fontSize = 14.sp)
                Box(modifier = Modifier.border(1.dp, BoxColor).background(MyTextFieldColor)) {
                    NumberPicker(week.maxRider, onMaxChanged, 1, 7, enabled, maxLabel)
                }
            }
        }
    }
}

fun getTodayIndex(): Int {
    return AppTime.dayOfWeek()
}

@Composable
fun DayShiftSelector(label: String, value: Int, enabled: Boolean, onValueChange: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth().border(1.dp, BoxColor).background(BoxColor),
            contentAlignment = Alignment.Center
        ) {
            Text(text = label, color = Color.White)
        }
        Box(
            modifier = Modifier.fillMaxWidth().border(1.dp, BoxColor).background(MyTextFieldColor),
            contentAlignment = Alignment.Center
        ) {
            NumberPicker(value, onValueChange, 0, 15, enabled, label)
        }
    }
}

@Composable
fun NumberPicker(
    value: Int,
    onValueChange: (Int) -> Unit,
    minValue: Int,
    maxValue: Int,
    enabled: Boolean,
    label: String
) {
    val canDecrease = enabled && value > minValue
    val canIncrease = enabled && value < maxValue
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(enabled = canDecrease, onClick = { onValueChange(value - 1) }) {
            Icon(
                Icons.Default.Remove,
                contentDescription = "${stringResource(R.string.decrease_value)}: $label",
                tint = if (canDecrease) BoxColor else Color.Gray
            )
        }
        Text(text = value.toString(), modifier = Modifier.padding(horizontal = 8.dp), color = Color.Black)
        IconButton(enabled = canIncrease, onClick = { onValueChange(value + 1) }) {
            Icon(
                Icons.Default.Add,
                contentDescription = "${stringResource(R.string.increase_value)}: $label",
                tint = if (canIncrease) BoxColor else Color.Gray
            )
        }
    }
}

@Composable
fun DayOfWeekPicker(selectedDay: String, enabled: Boolean, onDaySelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .padding(10.dp)
            .border(1.dp, BoxColor, RoundedCornerShape(20.dp))
            .background(MyTextFieldColor, RoundedCornerShape(20.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(text = selectedDay, fontSize = 18.sp, color = if (enabled) Color.Black else Color.Gray)
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.choose_day), tint = if (enabled) BoxColor else Color.Gray)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(180.dp).background(BoxColor)
        ) {
            daysOfWeek.forEach { item ->
                DropdownMenuItem(onClick = { expanded = false; onDaySelected(item) }) {
                    Text(text = item, color = BackgroundColor)
                }
            }
        }
    }
}
