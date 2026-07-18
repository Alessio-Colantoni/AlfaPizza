package com.alfaproject.alfapizza

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.alfaproject.alfapizza.MainActivity.Companion.terminateApplication
import com.alfaproject.alfapizza.network.SessionManager
import com.alfaproject.alfapizza.network.SessionEvents
import com.alfaproject.alfapizza.screens.admin.AdminManageNextWeekView
import com.alfaproject.alfapizza.screens.admin.AdminManageRidersView
import com.alfaproject.alfapizza.screens.admin.AdminManageSwapRequestsView
import com.alfaproject.alfapizza.screens.common.HomeView
import com.alfaproject.alfapizza.screens.common.LoginView
import com.alfaproject.alfapizza.screens.common.PersonalInfoView
import com.alfaproject.alfapizza.screens.rider.RiderManageYourConstraintsView
import com.alfaproject.alfapizza.screens.rider.RiderShiftChangeRequestsView
import com.alfaproject.alfapizza.ui.theme.AlfaPizzaTheme
import com.alfaproject.alfapizza.ui.theme.BackgroundColor

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        setContent {
            val monday = stringResource(R.string.monday)
            val tuesday = stringResource(R.string.tuesday)
            val wednesday = stringResource(R.string.wednesday)
            val thursday = stringResource(R.string.thursday)
            val friday = stringResource(R.string.friday)
            val saturday = stringResource(R.string.saturday)
            val sunday = stringResource(R.string.sunday)
            daysOfWeek = listOf(monday, tuesday, wednesday, thursday, friday, saturday, sunday)

            val notyThisWeekCalendUpdate = stringResource(R.string.this_week_calendar_has_been_updated)
            val notyNextWeekCalendUpdate = stringResource(R.string.next_week_calendar_has_been_updated)
            val notySwapRequestToApprove = stringResource(R.string.there_are_new_swap_requests_to_approve_admin)
            val notySwapThisWeekRider = stringResource(R.string.there_are_new_swap_requests_for_this_week)
            val notySwapNextWeekRider = stringResource(R.string.there_are_new_swap_requests_for_next_week)
            allNotifies = listOf(notyThisWeekCalendUpdate, notyNextWeekCalendUpdate, notySwapRequestToApprove,
                notySwapThisWeekRider, notySwapNextWeekRider)
            val context: Context = this
            AlfaPizzaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BackgroundColor,
                    elevation = 4.dp,
                )
                {
                    Box(modifier = Modifier.safeDrawingPadding()) {
                        App(context)
                    }
                }
            }
        }
    }

    companion object {
        lateinit var daysOfWeek: List<String>
        lateinit var allNotifies: List<String>


        fun terminateApplication(activity: Activity?) {
            activity?.finish()
        }
        var userCode=-1
        var isAdmin=false
        var currentUserEmail: String? = null
    }
    }

    @Composable
    fun App(context: Context) {
        val activity = LocalActivity.current
        val navController = rememberNavController()
        val unauthorizedVersion = SessionEvents.unauthorizedVersion

        LaunchedEffect(unauthorizedVersion) {
            if (unauthorizedVersion > 0 && navController.currentDestination?.route != "LoginView") {
                navController.graph.setStartDestination("LoginView")
                navController.navigate("LoginView") {
                    popUpTo(navController.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }

        BackHandler {
            when (navController.currentDestination?.route) {
                "LoginView", "HomeView"-> {
                    terminateApplication(activity)
                    Toast.makeText(context, context.getString(R.string.app_closed), Toast.LENGTH_LONG)
                        .show()
                }
                else -> {
                    navController.navigateUp()
                }
            }
        }

        val sessionManager = remember { SessionManager(context) }
        val startDest = if (sessionManager.isLoggedIn()) {
            MainActivity.userCode = sessionManager.getUserCode()
            MainActivity.isAdmin = sessionManager.isAdmin()
            MainActivity.currentUserEmail = sessionManager.getUserEmail()
            "HomeView"
        } else {
            "LoginView"
        }

        NavHost(navController = navController, startDestination = startDest) {
            composable("LoginView") {
                LoginView(navController)
            }
            composable("HomeView") {
                HomeView(navController)
            }
            composable("PersonalInfoView") {
                PersonalInfoView(navController)
            }
            composable("AdminManageNextWeekView") {
                AdminManageNextWeekView(navController)
            }
            composable("AdminManageRidersView") {
                AdminManageRidersView(navController)
            }
            composable("AdminManageSwapRequestsView") {
                AdminManageSwapRequestsView(navController)
            }
            composable("RiderManageYourConstraintsView") {
                RiderManageYourConstraintsView(navController)
            }
            composable("RiderShiftChangeRequestsView") {
                RiderShiftChangeRequestsView(navController)
            }
        }
    }

fun isNetworkConnected(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork
    val networkCapabilities = connectivityManager.getNetworkCapabilities(network)

    return networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}
