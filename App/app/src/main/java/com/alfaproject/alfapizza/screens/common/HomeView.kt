package com.alfaproject.alfapizza.screens.common

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.navigation.NavHostController
import com.alfaproject.alfapizza.MainActivity.Companion.isAdmin
import com.alfaproject.alfapizza.MainActivity.Companion.userCode
import com.alfaproject.alfapizza.R
import com.alfaproject.alfapizza.model.MyCalendar
import com.alfaproject.alfapizza.model.Constraint
import com.alfaproject.alfapizza.model.ShiftVisualStatus
import com.alfaproject.alfapizza.model.shiftVisualStatus
import com.alfaproject.alfapizza.network.rememberNetworkConnectionState
import com.alfaproject.alfapizza.screens.BoxOffline
import com.alfaproject.alfapizza.screens.FirstRowAdmin
import com.alfaproject.alfapizza.screens.FirstRowRider
import com.alfaproject.alfapizza.screens.rider.dayConverter
import com.alfaproject.alfapizza.ui.theme.BoxColor
import com.alfaproject.alfapizza.ui.theme.MyTextFieldColor
import com.alfaproject.alfapizza.ui.theme.MyYellow
import com.alfaproject.alfapizza.view_model.common.HomeViewModel

@Composable
fun HomeView(navController: NavHostController) {
    val string: String = stringResource(R.string.home)
    val vm by remember{ mutableStateOf(HomeViewModel())}
    val calendar = remember { mutableStateOf(MyCalendar("-1", false, listOf(), -1)) }
    val nextCalendar = remember { mutableStateOf(MyCalendar("-1", false, listOf(), -1)) }
    val context = LocalContext.current

    // Funzione helper per tradurre le anomalie dal server
    fun translateAnomaly(anomaly: String): String {
        if (!anomaly.startsWith("@")) return anomaly
        return try {
            val parts = anomaly.substring(1).split(":")
            val subject = parts[0]
            val stringKey = parts[1]

            val resId = context.resources.getIdentifier(stringKey, "string", context.packageName)
            val translatedAction = if (resId != 0) context.getString(resId) else stringKey

            val translatedSubject = when {
                subject.startsWith("DAY_") -> dayConverter(subject.replace("DAY_", "").toInt())
                subject.startsWith("RIDER_") -> "Rider ${subject.replace("RIDER_", "")}"
                subject == "CONFIG" -> context.getString(R.string.configuration)
                else -> subject
            }

            "$translatedSubject: $translatedAction"
        } catch (e: Exception) {
            anomaly
        }
    }

    var notifies by remember { mutableStateOf(mutableListOf<String>()) }
    var users by remember { mutableStateOf(mutableMapOf<Int, String>()) }
    var constraints by remember { mutableStateOf(listOf<Constraint>()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var calendarActionBusy by remember { mutableStateOf(false) }
    val showNextCalendar = isAdmin || (
        nextCalendar.value.isNext && nextCalendar.value.publicationDay <= vm.getDayOfWeek()
    )

    // Stato per la modifica manuale dei calendari
    var isEditingCurrent by remember { mutableStateOf(false) }
    var isEditingNext by remember { mutableStateOf(false) }
    var editedCalendar by remember { mutableStateOf<MyCalendar?>(null) }
    var calendarRegenerationTarget by remember { mutableStateOf<Boolean?>(null) }

    val connected by rememberNetworkConnectionState()
    fun refreshCalendars(callback: (Boolean) -> Unit = {}) {
        vm.getCalendars(context) { result, success ->
            if (!success) {
                callback(false)
                return@getCalendars
            }
            val current = result.find { !it.isNext }
            val next = result.find { it.isNext }
            calendar.value = current ?: MyCalendar("-1", false, listOf(), -1)
            nextCalendar.value = next ?: MyCalendar("-1", true, listOf(), -1)
            callback(true)
        }
    }

    if (calendarRegenerationTarget != null) {
        val targetIsNext = calendarRegenerationTarget == true
        AlertDialog(
            onDismissRequest = { calendarRegenerationTarget = null },
            title = { Text(stringResource(R.string.attention), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (targetIsNext) stringResource(R.string.recalculate_next_warning)
                    else stringResource(R.string.recalculate_current_warning)
                )
            },
            confirmButton = {
                TextButton(enabled = !calendarActionBusy, onClick = {
                    calendarRegenerationTarget = null
                    calendarActionBusy = true
                    val callback: (Boolean) -> Unit = { success ->
                        if (success) {
                            refreshCalendars { refreshed ->
                                calendarActionBusy = false
                                Toast.makeText(
                                    context,
                                    context.getString(if (refreshed) R.string.calendar_updated else R.string.error),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            calendarActionBusy = false
                            Toast.makeText(context, context.getString(R.string.error), Toast.LENGTH_SHORT).show()
                        }
                    }
                    if (targetIsNext) vm.generateCalendar(context, callback)
                    else vm.generateCurrentCalendar(context, callback)
                }) {
                    Text(stringResource(R.string.confirm), color = BoxColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { calendarRegenerationTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    fun loadHomeData() {
        isLoading = true
        loadFailed = false
        vm.getUsers(context) { result, usersLoaded ->
            if (!usersLoaded) {
                isLoading = false
                loadFailed = true
                return@getUsers
            }
            users = result
            vm.getCalendars(context) { resultCalendars, calendarsLoaded ->
                if (!calendarsLoaded) {
                    isLoading = false
                    loadFailed = true
                    return@getCalendars
                }
                calendar.value = resultCalendars.find { !it.isNext }
                    ?: MyCalendar("-1", false, listOf(), -1)
                nextCalendar.value = resultCalendars.find { it.isNext }
                    ?: MyCalendar("-1", true, listOf(), -1)
                vm.getConstraints(context) { resultConstraints, constraintsLoaded ->
                    if (!constraintsLoaded) {
                        isLoading = false
                        loadFailed = true
                        return@getConstraints
                    }
                    constraints = resultConstraints.toList()
                    vm.getSwaps(context) { _, swapsLoaded ->
                        if (!swapsLoaded) {
                            isLoading = false
                            loadFailed = true
                            return@getSwaps
                        }
                        vm.getNotifies(context) { resultNotifies ->
                            notifies = resultNotifies
                            isLoading = false
                            vm.updateUserAccess(context) {}
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(connected) {
        if (connected) loadHomeData() else isLoading = false
    }
    Column {
        if (isAdmin) {
            FirstRowAdmin(navController, string)
        } else {
            FirstRowRider(navController, string)
        }
        if (connected && isLoading) {
            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.please_wait))
            }
        } else if (connected && loadFailed) {
            Column(
                modifier = Modifier.fillMaxHeight().fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(stringResource(R.string.error), color = Color.Red)
                TextButton(onClick = { loadHomeData() }) {
                    Text(stringResource(R.string.retry), color = BoxColor)
                }
            }
        } else if (connected) {
            LazyColumn(modifier = Modifier.padding(0.dp, 0.dp, 0.dp, 15.dp)) {
                for (i in notifies) {
                    item {
                            Text(text = i.toString(), modifier = Modifier
                                .padding(15.dp, 3.dp)
                                .fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                softWrap = true)
                        }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 15.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.current_calendar),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center, color = BoxColor, fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (isAdmin && !isEditingNext) {
                            if (!isEditingCurrent) {
                                IconButton(enabled = !calendarActionBusy, onClick = { calendarRegenerationTarget = false }) {
                                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.generate), tint = BoxColor)
                                }
                                IconButton(enabled = !calendarActionBusy && calendar.value.days.isNotEmpty(), onClick = {
                                    editedCalendar = calendar.value.copy(
                                        days = calendar.value.days.map { day ->
                                            day.copy(listShift = day.listShift.map { it.copy() })
                                        }
                                    )
                                    isEditingCurrent = true
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.change), tint = BoxColor)
                                }
                            } else {
                                Row {
                                    IconButton(enabled = !calendarActionBusy, onClick = {
                                        val calendarToSave = editedCalendar ?: return@IconButton
                                        calendarActionBusy = true
                                        vm.saveCurrentCalendar(context, calendarToSave) { success ->
                                            if (success) {
                                                refreshCalendars { refreshed ->
                                                    calendarActionBusy = false
                                                    if (refreshed) {
                                                        isEditingCurrent = false
                                                        editedCalendar = null
                                                    }
                                                    Toast.makeText(context, context.getString(if (refreshed) R.string.calendar_updated else R.string.error), Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                calendarActionBusy = false
                                                Toast.makeText(context, context.getString(R.string.error), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }) {
                                        Icon(Icons.Default.Save, contentDescription = stringResource(R.string.confirm), tint = BoxColor)
                                    }
                                    IconButton(enabled = !calendarActionBusy, onClick = { isEditingCurrent = false; editedCalendar = null }) {
                                        Icon(Icons.Default.Cancel, contentDescription = stringResource(R.string.cancel), tint = BoxColor)
                                    }
                                }
                            }
                        }
                    }
                }

                if (isEditingCurrent && editedCalendar != null) {
                    item {
                        EditableCalendar(
                            calendar = editedCalendar!!,
                            users = users,
                            constraints = constraints,
                            onCalendarChanged = { editedCalendar = it }
                        )
                    }
                } else {
                    item { Calendar(calendar = calendar, users = users) }
                }

                if (isAdmin && !calendar.value.anomalies.isNullOrBlank()) {
                    item {
                        Column(
                            modifier = Modifier
                                .padding(18.dp, 8.dp)
                                .fillMaxWidth()
                                .background(Color.Red.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .border(1.dp, Color.Red, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.anomalies_detected),
                                color = Color.Red,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            val translatedAnomalies = calendar.value.anomalies!!.split("\n")
                                .joinToString("\n") { translateAnomaly(it) }
                            Text(
                                text = translatedAnomalies,
                                fontSize = 14.sp,
                                color = Color.DarkGray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 15.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.next_calendar),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center, color = BoxColor, fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (isAdmin && !isEditingCurrent) {
                            Row {
                                if (!isEditingNext) {
                                    IconButton(enabled = !calendarActionBusy, onClick = { calendarRegenerationTarget = true }) {
                                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.generate), tint = BoxColor)
                                    }
                                    IconButton(enabled = !calendarActionBusy && nextCalendar.value.days.isNotEmpty(), onClick = {
                                        editedCalendar = nextCalendar.value.copy(
                                            days = nextCalendar.value.days.map { day ->
                                                day.copy(listShift = day.listShift.map { it.copy() })
                                            }
                                        )
                                        isEditingNext = true
                                    }) {
                                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.change), tint = BoxColor)
                                    }
                                } else {
                                    Row {
                                        IconButton(enabled = !calendarActionBusy, onClick = {
                                            val calendarToSave = editedCalendar ?: return@IconButton
                                            calendarActionBusy = true
                                            vm.saveCurrentCalendar(context, calendarToSave) { success ->
                                                if (success) {
                                                    refreshCalendars { refreshed ->
                                                        calendarActionBusy = false
                                                        if (refreshed) {
                                                            isEditingNext = false
                                                            editedCalendar = null
                                                        }
                                                        Toast.makeText(context, context.getString(if (refreshed) R.string.calendar_updated else R.string.error), Toast.LENGTH_SHORT).show()
                                                    }
                                                } else {
                                                    calendarActionBusy = false
                                                    Toast.makeText(context, context.getString(R.string.error), Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }) {
                                            Icon(Icons.Default.Save, contentDescription = stringResource(R.string.confirm), tint = BoxColor)
                                        }
                                        IconButton(enabled = !calendarActionBusy, onClick = { isEditingNext = false; editedCalendar = null }) {
                                            Icon(Icons.Default.Cancel, contentDescription = stringResource(R.string.cancel), tint = BoxColor)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (isEditingNext && editedCalendar != null) {
                    item {
                        EditableCalendar(
                            calendar = editedCalendar!!,
                            users = users,
                            constraints = constraints,
                            onCalendarChanged = { editedCalendar = it }
                        )
                    }
                } else if (!showNextCalendar) {
                    item {
                        Text(
                            text = stringResource(R.string.next_calendar_not_published),
                            modifier = Modifier.fillMaxWidth().padding(18.dp, 8.dp),
                            textAlign = TextAlign.Center,
                            color = BoxColor,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    item { Calendar(calendar = nextCalendar, users = users) }
                }
            }
        } else {
            BoxOffline()
        }
    }
}

@Composable
fun EditableCalendar(
    calendar: MyCalendar,
    users: MutableMap<Int, String>,
    constraints: List<Constraint>,
    onCalendarChanged: (MyCalendar) -> Unit
) {
    Column {
        calendar.days.forEachIndexed { dayIdx, workday ->
            Box(
                modifier = Modifier
                    .padding(18.dp, 0.dp)
                    .fillMaxWidth()
                    .border(1.dp, BoxColor)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(27.dp).background(BoxColor),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dayConverter(workday.day),
                            textAlign = TextAlign.Center,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    workday.listShift.forEachIndexed { shiftIdx, rider ->
                        var expanded by remember { mutableStateOf(false) }
                        val statusDescription = shiftStatusDescription(rider.color, rider.code)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .background(MyTextFieldColor)
                                .semantics(mergeDescendants = true) {
                                    if (statusDescription != null) stateDescription = statusDescription
                                }
                                .clickable { expanded = true },
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (rider.code == -1) stringResource(R.string.make_empty) else if (rider.code == -99) stringResource(R.string.missing_rider) else (users[rider.code] ?: "Rider ${rider.code}"),
                                textAlign = TextAlign.Center,
                                fontSize = 16.sp,
                                modifier = Modifier.fillMaxWidth(0.7f)
                            )

                            // Ripristino pallini colorati anche in modalità modifica
                            val color = parseShiftColor(rider.color)

                            if (color != Color.Transparent) {
                                Spacer(modifier = Modifier.size(8.dp))
                                ConstraintDot(color)
                            }

                            Spacer(modifier = Modifier.weight(1f))
                            Icon(Icons.Default.MoreVert, contentDescription = null, modifier = Modifier.size(16.dp))

                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                DropdownMenuItem(onClick = {
                                    val newDays = calendar.days.toMutableList()
                                    val newShifts = workday.listShift.toMutableList()
                                    // Cambiato da -1 a -99 per indicare "RIDER MANCANTE" coerentemente con l'algoritmo
                                    newShifts[shiftIdx] = rider.copy(code = -99, color = "red")
                                    newDays[dayIdx] = workday.copy(listShift = newShifts)
                                    onCalendarChanged(calendar.copy(days = newDays))
                                    expanded = false
                                }) {
                                    Text(stringResource(R.string.make_empty))
                                }
                                // Filtriamo la lista utenti per mostrare solo i Rider (escludendo Admin con codice 0)
                                // ED ESCLUDIAMO chi è già presente in questo giorno specifico (Punto 3 richiesto)
                                val alreadyInDay = workday.listShift.map { it.code }
                                users.filter { it.key != 0 && !alreadyInDay.contains(it.key) }.forEach { (code, name) ->
                                    DropdownMenuItem(onClick = {
                                        val newDays = calendar.days.toMutableList()
                                        val newShifts = workday.listShift.toMutableList()
                                        newShifts[shiftIdx] = rider.copy(
                                            code = code,
                                            color = constraintColor(code, workday.day, calendar.isNext, constraints)
                                        )
                                        newDays[dayIdx] = workday.copy(listShift = newShifts)
                                        onCalendarChanged(calendar.copy(days = newDays))
                                        expanded = false
                                    }) {
                                        Text(name)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private fun constraintColor(code: Int, day: Int, isNext: Boolean, constraints: List<Constraint>): String {
    return when (constraints.firstOrNull {
        it.riderCode == code && it.day == day && it.isNext == isNext
    }?.priority) {
        1 -> "red"
        2 -> "yellow"
        else -> "transparent"
    }
}

private fun parseShiftColor(value: String): Color {
    val normalized = value.lowercase().trim()
    return try {
        when {
            normalized == "red" || normalized.contains("red") -> Color.Red
            normalized == "yellow" || normalized.contains("yellow") -> MyYellow
            normalized.startsWith("#") -> Color(android.graphics.Color.parseColor(normalized))
            else -> Color.Transparent
        }
    } catch (_: Exception) {
        Color.Transparent
    }
}

@Composable
private fun shiftStatusDescription(color: String, riderCode: Int): String? = when (
    shiftVisualStatus(color, riderCode)
) {
    ShiftVisualStatus.ABSOLUTE_CONSTRAINT -> stringResource(R.string.status_absolute_constraint)
    ShiftVisualStatus.PREFERENCE -> stringResource(R.string.status_preference)
    ShiftVisualStatus.UNCOVERED -> stringResource(R.string.status_uncovered_shift)
    ShiftVisualStatus.NONE -> null
}

@Composable
private fun ConstraintDot(color: Color) {
    Box(
        modifier = Modifier
            .size(14.dp)
            .background(color, CircleShape)
            .border(1.5.dp, Color.Black, CircleShape)
    )
}

@Composable
fun Calendar(calendar: MutableState<MyCalendar>, users: MutableMap<Int, String>) {
    if (calendar.value.days.isNotEmpty()) {
        calendar.value.days.forEachIndexed { index, _ ->
            ShiftsDayBox(calendar = calendar, users = users, index)
        }
    } else {
        Text(
            text = stringResource(R.string.no_shifts_found),
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ShiftsDayBox(calendar: MutableState<MyCalendar>, users: MutableMap<Int, String>, index: Int) {
    Box(
        modifier = Modifier
            .padding(18.dp, 0.dp)
            .fillMaxWidth()
            .border(1.dp, BoxColor)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(27.dp)
                    .background(BoxColor),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dayConverter(calendar.value.days.get(index).day),
                    textAlign = TextAlign.Center,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            for (rider in calendar.value.days.get(index).listShift) {
                val statusDescription = shiftStatusDescription(rider.color, rider.code)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(27.dp)
                        .background(MyTextFieldColor)
                        .semantics(mergeDescendants = true) {
                            if (statusDescription != null) stateDescription = statusDescription
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    val displayText = when(rider.code) {
                        -1 -> stringResource(R.string.make_empty)
                        -99 -> stringResource(R.string.missing_rider)
                        else -> users[rider.code] ?: "Rider ${rider.code}"
                    }

                    Text(
                        text = displayText,
                        textAlign = TextAlign.Center,
                        fontSize = 16.sp,
                        fontWeight = if (rider.code == userCode) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.fillMaxWidth(0.5f)
                    )

                    val finalDotColor = parseShiftColor(rider.color)

                    if (finalDotColor != Color.Transparent) {
                        Spacer(modifier = Modifier.size(10.dp))
                        ConstraintDot(finalDotColor)
                    }
                }
            }
        }
    }
}
