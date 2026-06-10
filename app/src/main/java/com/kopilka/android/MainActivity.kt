package com.kopilka.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kopilka.android.data.storage.CredentialStore
import com.kopilka.android.ui.addspending.AddSpendingScreen
import com.kopilka.android.ui.auth.authenticate
import com.kopilka.android.ui.categories.CategoriesScreen
import com.kopilka.android.ui.categories.CategoriesViewModel
import com.kopilka.android.ui.setup.SetupScreen
import com.kopilka.android.ui.spending_log.SpendingLogScreen
import com.kopilka.android.ui.theme.KopilkaTheme
import com.kopilka.android.ui.widget.EXTRA_DIRECT_ADD
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

private const val ROUTE_SETUP = "setup"
private const val ROUTE_CATEGORIES = "categories"
private const val ROUTE_ADD = "add?entryId={entryId}"
private const val ROUTE_ADD_BASE = "add"
private const val ROUTE_LOG = "log"

class MainActivity : AppCompatActivity() {

    private val authenticated = mutableStateOf(false)
    private val directAdd = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        directAdd.value = intent.getBooleanExtra(EXTRA_DIRECT_ADD, false)

        authenticate(
            activity = this,
            onSuccess = { authenticated.value = true },
            onCancel = { finish() },
        )

        val isConfigured = CredentialStore(this).isConfigured()

        setContent {
            KopilkaTheme {
                Surface(Modifier.fillMaxSize()) {
                    val auth by authenticated
                    val doAdd by directAdd
                    if (!auth) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Locked",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        KopilkaNavGraph(
                            startRoute = if (isConfigured) ROUTE_CATEGORIES else ROUTE_SETUP,
                            directAdd = doAdd,
                            onDirectAddConsumed = { directAdd.value = false },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        directAdd.value = intent.getBooleanExtra(EXTRA_DIRECT_ADD, false)
    }
}

@Composable
private fun KopilkaNavGraph(
    startRoute: String,
    directAdd: Boolean,
    onDirectAddConsumed: () -> Unit,
) {
    val navController = rememberNavController()

    LaunchedEffect(directAdd) {
        if (directAdd) {
            val route = snapshotFlow { navController.currentBackStackEntry?.destination?.route }
                .filterNotNull()
                .filter { it == ROUTE_CATEGORIES || it.startsWith(ROUTE_ADD_BASE) }
                .first()
            if (route == ROUTE_CATEGORIES) navController.navigate(ROUTE_ADD_BASE)
            onDirectAddConsumed()
        }
    }

    // Shared CategoriesViewModel so "See all" log screen and categories screen share state
    val categoriesVm: CategoriesViewModel = viewModel()

    NavHost(navController = navController, startDestination = startRoute) {

        composable(ROUTE_SETUP) {
            SetupScreen(
                onDone = {
                    navController.navigate(ROUTE_CATEGORIES) {
                        popUpTo(ROUTE_SETUP) { inclusive = true }
                    }
                }
            )
        }

        composable(ROUTE_CATEGORIES) {
            CategoriesScreen(
                onOpenSetup = { navController.navigate(ROUTE_SETUP) },
                onAddSpending = { navController.navigate(ROUTE_ADD_BASE) },
                onEditEntry = { entryId -> navController.navigate("add?entryId=$entryId") },
                onViewAll = { navController.navigate(ROUTE_LOG) },
                vm = categoriesVm,
            )
        }

        composable(
            route = ROUTE_ADD,
            arguments = listOf(
                navArgument("entryId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            ),
        ) { backStackEntry ->
            val entryId = backStackEntry.arguments?.getString("entryId")
            AddSpendingScreen(
                onDone = { navController.popBackStack() },
                existingEntryId = entryId,
            )
        }

        composable(ROUTE_LOG) {
            SpendingLogScreen(
                onBack = { navController.popBackStack() },
                onEditEntry = { entryId ->
                    navController.navigate("add?entryId=$entryId")
                },
                myName = categoriesVm.state.value.myName,
            )
        }
    }
}
