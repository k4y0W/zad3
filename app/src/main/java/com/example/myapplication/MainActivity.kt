package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.PasswordVisualTransformation

data class ExchangeTransaction(
    val fromCurrency: String = "",
    val toCurrency: String = "",
    val amount: Double = 0.0,
    val rate: Double = 0.0,
    val result: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    val userId: String = ""
)

class ExchangeViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    var transactions by mutableStateOf<List<ExchangeTransaction>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var isLoggedIn by mutableStateOf(auth.currentUser != null)
        private set

    private val exchangeRates = mapOf(
        "USD" to 4.0,
        "EUR" to 4.35,
        "GBP" to 5.05,
        "CHF" to 4.45
    )

    suspend fun signIn(email: String, password: String): Boolean {
        return try {
            isLoading = true
            error = null
            auth.signInWithEmailAndPassword(email, password).await()
            isLoggedIn = true
            true
        } catch (e: Exception) {
            error = e.message
            false
        } finally {
            isLoading = false
        }
    }

    suspend fun signUp(email: String, password: String): Boolean {
        return try {
            isLoading = true
            error = null
            auth.createUserWithEmailAndPassword(email, password).await()
            isLoggedIn = true
            true
        } catch (e: Exception) {
            error = e.message
            false
        } finally {
            isLoading = false
        }
    }

    suspend fun performExchange(fromCurrency: String, toCurrency: String, amount: Double) {
        try {
            isLoading = true
            error = null

            val rate = when {
                fromCurrency == "PLN" -> exchangeRates[toCurrency] ?: 1.0
                toCurrency == "PLN" -> 1.0 / (exchangeRates[fromCurrency] ?: 1.0)
                else -> (exchangeRates[toCurrency] ?: 1.0) / (exchangeRates[fromCurrency] ?: 1.0)
            }

            val result = amount * rate

            val transaction = ExchangeTransaction(
                fromCurrency = fromCurrency,
                toCurrency = toCurrency,
                amount = amount,
                rate = rate,
                result = result,
                userId = auth.currentUser?.uid ?: ""
            )

            auth.currentUser?.uid?.let { uid ->
                db.collection("users").document(uid)
                    .collection("transactions")
                    .add(transaction)
                    .await()
            }

            loadTransactions()
        } catch (e: Exception) {
            error = e.message
        } finally {
            isLoading = false
        }
    }

    suspend fun loadTransactions() {
        try {
            isLoading = true
            error = null
            auth.currentUser?.uid?.let { uid ->
                val snapshot = db.collection("users").document(uid)
                    .collection("transactions")
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .get()
                    .await()
                transactions = snapshot.documents.mapNotNull {
                    it.toObject(ExchangeTransaction::class.java)
                }
            }
        } catch (e: Exception) {
            error = e.message
        } finally {
            isLoading = false
        }
    }

    fun signOut() {
        auth.signOut()
        isLoggedIn = false
        transactions = emptyList()
    }
}

@Composable
fun ExchangeScreen(viewModel: ExchangeViewModel) {
    var fromCurrency by remember { mutableStateOf("PLN") }
    var toCurrency by remember { mutableStateOf("EUR") }
    var amount by remember { mutableStateOf("") }
    var fromCurrencyExpanded by remember { mutableStateOf(false) }
    var toCurrencyExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val currencies = listOf("PLN", "EUR", "USD", "GBP", "CHF")

    LaunchedEffect(Unit) {
        viewModel.loadTransactions()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = fromCurrency,
                onValueChange = {},
                readOnly = true,
                label = { Text("Z waluty") },
                trailingIcon = {
                    IconButton(onClick = { fromCurrencyExpanded = !fromCurrencyExpanded }) {
                        Icon(
                            imageVector = if (fromCurrencyExpanded)
                                Icons.Default.KeyboardArrowUp
                            else
                                Icons.Default.KeyboardArrowDown,
                            contentDescription = "Select currency"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            DropdownMenu(
                expanded = fromCurrencyExpanded,
                onDismissRequest = { fromCurrencyExpanded = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                currencies.forEach { currency ->
                    DropdownMenuItem(
                        text = { Text(currency) },
                        onClick = {
                            fromCurrency = currency
                            fromCurrencyExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = toCurrency,
                onValueChange = {},
                readOnly = true,
                label = { Text("Na walutę") },
                trailingIcon = {
                    IconButton(onClick = { toCurrencyExpanded = !toCurrencyExpanded }) {
                        Icon(
                            imageVector = if (toCurrencyExpanded)
                                Icons.Default.KeyboardArrowUp
                            else
                                Icons.Default.KeyboardArrowDown,
                            contentDescription = "Select currency"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            DropdownMenu(
                expanded = toCurrencyExpanded,
                onDismissRequest = { toCurrencyExpanded = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                currencies.forEach { currency ->
                    DropdownMenuItem(
                        text = { Text(currency) },
                        onClick = {
                            toCurrency = currency
                            toCurrencyExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = amount,
            onValueChange = { if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) amount = it },
            label = { Text("Kwota") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                scope.launch {
                    amount.toDoubleOrNull()?.let { amt ->
                        viewModel.performExchange(fromCurrency, toCurrency, amt)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = amount.isNotEmpty()
        ) {
            Text("Wymień walutę")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Historia transakcji", style = MaterialTheme.typography.titleLarge)

        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(viewModel.transactions) { transaction ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "${transaction.amount} ${transaction.fromCurrency} -> " +
                                    "${String.format("%.2f", transaction.result)} ${transaction.toCurrency}"
                        )
                        Text(
                            "Kurs: ${String.format("%.4f", transaction.rate)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        if (viewModel.isLoading) {
            CircularProgressIndicator()
        }

        viewModel.error?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.signOut() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Wyloguj się")
        }
    }
}

@Composable
fun LoginScreen(
    viewModel: ExchangeViewModel,
    onNavigateToRegister: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.icon),
            contentDescription = "Logo Kantoru",
            modifier = Modifier
                .size(128.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = 2.dp,
                    color = Color(0xFF6750A4),
                    shape = RoundedCornerShape(16.dp)
                )
        )

        Text(
            text = "Zaloguj się!",
            style = MaterialTheme.typography.titleLarge,
            color = Color(0xFF6750A4),
            modifier = Modifier
                .padding(16.dp),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Hasło") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(

            onClick = {

                scope.launch {
                    viewModel.signIn(email, password)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Zaloguj się")

        }

        TextButton(

            onClick = onNavigateToRegister
        ) {
            Text("Nie masz konta? Zarejestruj się")

        }

        if (viewModel.isLoading) {
            CircularProgressIndicator()
        }

        viewModel.error?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun RegisterScreen(
    viewModel: ExchangeViewModel,
    onNavigateToLogin: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.icon),
            contentDescription = "Logo Kantoru",
            modifier = Modifier
                .size(128.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = 2.dp,
                    color = Color(0xFF6750A4),
                    shape = RoundedCornerShape(16.dp)
                )
        )
        Text(
            text = "Zarejestruj sie!",
            style = MaterialTheme.typography.titleLarge,
            color = Color(0xFF6750A4),
            modifier = Modifier
                .padding(16.dp),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Hasło") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                scope.launch {
                    viewModel.signUp(email, password)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Zarejestruj się")
        }

        TextButton(
            onClick = onNavigateToLogin
        ) {
            Text("Masz już konto? Zaloguj się")
        }

        if (viewModel.isLoading) {
            CircularProgressIndicator()
        }

        viewModel.error?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error)
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                val navController = rememberNavController()
                val viewModel: ExchangeViewModel = viewModel()

                NavHost(
                    navController = navController,
                    startDestination = if (viewModel.isLoggedIn) "exchange" else "login"
                ) {
                    composable("login") {
                        LoginScreen(
                            viewModel = viewModel,
                            onNavigateToRegister = { navController.navigate("register") }
                        )
                    }
                    composable("register") {
                        RegisterScreen(
                            viewModel = viewModel,
                            onNavigateToLogin = { navController.navigate("login") }
                        )
                    }
                    composable("exchange") {
                        ExchangeScreen(viewModel = viewModel)
                    }
                }

                LaunchedEffect(viewModel.isLoggedIn) {
                    if (viewModel.isLoggedIn) {
                        navController.navigate("exchange") {
                            popUpTo("login") { inclusive = true }
                        }
                    } else {
                        navController.navigate("login") {
                            popUpTo("exchange") { inclusive = true }
                        }
                    }
                }
            }
        }
    }
}
