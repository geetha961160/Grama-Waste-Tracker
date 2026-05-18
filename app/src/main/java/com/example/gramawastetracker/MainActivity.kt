package com.example.gramawastetracker

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.example.gramawastetracker.ui.theme.GramaWasteTrackerTheme
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.location.LocationServices
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import coil.compose.AsyncImage

class MainActivity : ComponentActivity() {
    private var selectedPhotoUri by mutableStateOf<Uri?>(null)
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val reportPhotoPicker =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            selectedPhotoUri = uri
            val message = if (uri != null) "Photo selected" else "No photo selected"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        enableEdgeToEdge()
        setContent {
            // Force light theme for better readability in outdoor/low-end displays.
            GramaWasteTrackerTheme(darkTheme = false) {
                GramaWasteTrackerApp(
                    onPickPhoto = {
                        reportPhotoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    selectedPhotoUri = selectedPhotoUri,
                    onClearPhoto = { selectedPhotoUri = null }
                )
            }
        }
    }
}

private data class TractorLocation(
    val latLng: LatLng,
    val stopIndex: Int,
    val status: String
)

private enum class AppScreen(val title: String) {
    Track("ಲೈವ್ ಟ್ರ್ಯಾಕ್"),
    Report("ಕಸ ವರದಿ"),
    Complaint("ದೂರು"),
    Guide("ತ್ಯಾಜ್ಯ ಮಾರ್ಗದರ್ಶಿ"),
    Chat("ಸಹಾಯ ಬಾಟ್")
}

private data class WasteReport(
    val location: String,
    val notes: String,
    val photoUri: String?,
    val createdAt: String,
    val lat: Double,
    val lng: Double
)

private data class ChatMessage(
    val fromUser: Boolean,
    val text: String
)

private data class ComplaintEntry(
    val name: String,
    val ward: String,
    val complaint: String,
    val createdAt: String,
    val lat: Double,
    val lng: Double
)

private enum class UserRole {
    USER, ADMIN
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GramaWasteTrackerApp(
    onPickPhoto: () -> Unit,
    selectedPhotoUri: Uri?,
    onClearPhoto: () -> Unit
) {
    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.Track) }
    val reports = remember { mutableStateListOf<WasteReport>() }
    val complaints = remember { mutableStateListOf<ComplaintEntry>() }
    var currentRole by rememberSaveable { mutableStateOf(UserRole.USER) }
    var isAdminLoggedIn by rememberSaveable { mutableStateOf(false) }
    var tractorCount by rememberSaveable { mutableStateOf("3") }
    var wasteCollectedKg by rememberSaveable { mutableStateOf("1240") }
    var completedTrips by rememberSaveable { mutableStateOf("18") }
    var roleMenuExpanded by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFFF5F7FA),
        topBar = {
            TopAppBar(
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1B5E20),
                    titleContentColor = Color.White
                ),
                title = {
                    Text("Grama-Waste Tracker / ಗ್ರಾಮ ತ್ಯಾಜ್ಯ ಟ್ರ್ಯಾಕರ್")
                },
                actions = {
                    Box {
                        TextButton(onClick = { roleMenuExpanded = true }) {
                            val roleLabel = if (currentRole == UserRole.ADMIN) "Admin" else "User"
                            Text("Role: $roleLabel", color = Color.White)
                        }
                        DropdownMenu(
                            expanded = roleMenuExpanded,
                            onDismissRequest = { roleMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Login as User") },
                                onClick = {
                                    currentRole = UserRole.USER
                                    roleMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Login as Admin") },
                                onClick = {
                                    currentRole = UserRole.ADMIN
                                    roleMenuExpanded = false
                                }
                            )
                            if (currentRole == UserRole.ADMIN && isAdminLoggedIn) {
                                DropdownMenuItem(
                                    text = { Text("Logout Admin") },
                                    onClick = {
                                        isAdminLoggedIn = false
                                        roleMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (currentRole == UserRole.USER) {
                NavigationBar(containerColor = Color.White) {
                    AppScreen.entries.forEach { screen ->
                        NavigationBarItem(
                            selected = currentScreen == screen,
                            onClick = { currentScreen = screen },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF1B5E20),
                                selectedTextColor = Color(0xFF1B5E20),
                                indicatorColor = Color(0xFFCDE9D1),
                                unselectedIconColor = Color(0xFF455A64),
                                unselectedTextColor = Color(0xFF455A64)
                            ),
                            label = { Text(screen.title, color = Color.Black) },
                            icon = {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(100))
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        if (currentRole == UserRole.ADMIN) {
            if (isAdminLoggedIn) {
                AdminDashboardScreen(
                    paddingValues = innerPadding,
                    reports = reports,
                    complaints = complaints,
                    tractorCount = tractorCount,
                    wasteCollectedKg = wasteCollectedKg,
                    completedTrips = completedTrips,
                    onTractorCountChange = { tractorCount = it },
                    onWasteCollectedChange = { wasteCollectedKg = it },
                    onCompletedTripsChange = { completedTrips = it }
                )
            } else {
                AdminLoginScreen(
                    paddingValues = innerPadding,
                    onLoginSuccess = { isAdminLoggedIn = true }
                )
            }
        } else {
            when (currentScreen) {
                AppScreen.Track -> TrackScreen(innerPadding)
                AppScreen.Report -> ReportScreen(
                    paddingValues = innerPadding,
                    onPickPhoto = onPickPhoto,
                    selectedPhotoUri = selectedPhotoUri,
                    onClearPhoto = onClearPhoto,
                    reports = reports
                )
                AppScreen.Complaint -> ComplaintScreen(
                    paddingValues = innerPadding,
                    onComplaintCreated = { complaints.add(0, it) }
                )
                AppScreen.Guide -> GuideScreen(innerPadding)
                AppScreen.Chat -> ChatbotScreen(innerPadding)
            }
        }
    }
}

@Composable
private fun TrackScreen(paddingValues: PaddingValues) {
    val context = LocalContext.current
    val route = remember {
        listOf(
            "Panchayat" to LatLng(12.9716, 77.5946),
            "Main Rd" to LatLng(12.9723, 77.5954),
            "4th St" to LatLng(12.9730, 77.5962),
            "3rd St" to LatLng(12.9737, 77.5970),
            "2nd St" to LatLng(12.9744, 77.5978),
            "1st St" to LatLng(12.9751, 77.5986)
        )
    }
    val updateLines = remember { mutableStateListOf("ಟ್ರ್ಯಾಕ್ಟರ್ ಸ್ಥಳದ ಮಾಹಿತಿ ಕಾಯುತ್ತಿದೆ...") }
    var currentLocation by rememberSaveable { mutableStateOf(route.first().second.toString()) }
    var currentStopIndex by rememberSaveable { mutableIntStateOf(0) }
    var notificationSent by rememberSaveable { mutableStateOf(false) }
    var homeLatLng by remember { mutableStateOf<LatLng?>(null) }
    val distanceInStreets = (route.lastIndex - currentStopIndex).coerceAtLeast(0)
    val cameraState: CameraPositionState = rememberCameraPositionState()
    val hasLocationPermission =
        ActivityCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) loadHomeLocation(context) { homeLatLng = it }
    }

    LaunchedEffect(Unit) {
        if (hasLocationPermission) {
            loadHomeLocation(context) { homeLatLng = it }
        } else {
            locationPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    DisposableEffect(Unit) {
        val dbRef = try {
            FirebaseDatabase.getInstance().getReference("tractor/live")
        } catch (_: Exception) {
            null
        }
        if (dbRef == null) {
            onDispose { }
        } else {
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val lat = snapshot.child("lat").getValue(Double::class.java)
                    val lng = snapshot.child("lng").getValue(Double::class.java)
                    val idx = snapshot.child("stopIndex").getValue(Int::class.java)
                    val status = snapshot.child("status").getValue(String::class.java)

                    if (lat != null && lng != null) {
                        currentLocation = LatLng(lat, lng).toString()
                    }
                    if (idx != null) {
                        currentStopIndex = idx.coerceIn(0, route.lastIndex)
                    }
                    if (!status.isNullOrBlank()) {
                        if (updateLines.size > 9) updateLines.removeAt(0)
                        updateLines.add(status)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    // Silent fallback to demo mode when Firebase is unavailable.
                }
            }
            dbRef.addValueEventListener(listener)
            onDispose { dbRef.removeEventListener(listener) }
        }
    }

    LaunchedEffect(Unit) {
        val statuses = listOf(
            "ಟ್ರ್ಯಾಕ್ಟರ್ ಪಂಚಾಯತ್ ಕಚೇರಿಯಿಂದ ಹೊರಟಿದೆ",
            "ಮುಖ್ಯ ರಸ್ತೆಯಲ್ಲಿ ಚಲಿಸುತ್ತಿದೆ",
            "4ನೇ ಬೀದಿ ತಲುಪಿದೆ",
            "3ನೇ ಬೀದಿ ತಲುಪಿದೆ",
            "2ನೇ ಬೀದಿ ತಲುಪಿದೆ - ನಿವಾಸಿಗಳಿಗೆ ಮಾಹಿತಿ ನೀಡಿ",
            "1ನೇ ಬೀದಿ ತಲುಪಿದೆ - ಸಂಗ್ರಹಣೆ ಪ್ರಾರಂಭವಾಗಿದೆ"
        )
        for ((index, status) in statuses.withIndex()) {
            delay(2200)
            if (updateLines.size > 9) updateLines.removeAt(0)
            updateLines.add(status)
            currentStopIndex = index.coerceIn(0, route.lastIndex)
            currentLocation = route[currentStopIndex].second.toString()
            try {
                val liveRef = FirebaseDatabase.getInstance().getReference("tractor/live")
                val point = route[currentStopIndex].second
                liveRef.setValue(
                    mapOf(
                        "lat" to point.latitude,
                        "lng" to point.longitude,
                        "stopIndex" to currentStopIndex,
                        "status" to status
                    )
                )
                // Keep at least 5 recent live points for admin analytics.
                FirebaseDatabase.getInstance().getReference("tractor/livePoints")
                    .push()
                    .setValue(
                        mapOf(
                            "lat" to point.latitude,
                            "lng" to point.longitude,
                            "stopIndex" to currentStopIndex,
                            "status" to status,
                            "time" to System.currentTimeMillis()
                        )
                    )
            } catch (_: Exception) {
                // Silent fallback when Firebase is unavailable.
            }
        }
    }

    LaunchedEffect(currentStopIndex) {
        cameraState.move(CameraUpdateFactory.newLatLngZoom(route[currentStopIndex].second, 15f))
        if (distanceInStreets <= 2 && !notificationSent) {
            showNearbyAlertNotification(context, distanceInStreets)
            notificationSent = true
        }
        if (distanceInStreets > 2) notificationSent = false
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "ಲೈವ್ ಟ್ರ್ಯಾಕ್ಟರ್ ಡ್ಯಾಶ್‌ಬೋರ್ಡ್",
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        "ನಕ್ಷೆಯಲ್ಲಿ ಚಲಿಸುವ ಟ್ರ್ಯಾಕ್ಟರ್ ಲೈವ್ ಸ್ಥಿತಿ.",
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { (currentStopIndex.toFloat() / route.lastIndex.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(100))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .padding(top = 12.dp)
                            .background(Color(0xFFE2F2E8), RoundedCornerShape(12.dp))
                    ) {
                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraState,
                            properties = MapProperties(isMyLocationEnabled = hasLocationPermission)
                        ) {
                            val routePoints = route.map { it.second }
                            Polyline(
                                points = routePoints,
                                color = Color(0xFF1B5E20),
                                width = 10f
                            )

                            homeLatLng?.let { home ->
                                Marker(
                                    state = MarkerState(position = home),
                                    title = "My Home",
                                    snippet = "ನನ್ನ ಮನೆ"
                                )
                            }
                            Marker(
                                state = MarkerState(position = route[currentStopIndex].second),
                                title = "🚜 Tractor Coming",
                                snippet = "ಪ್ರಸ್ತುತ ಸ್ಥಳ: ${route[currentStopIndex].first}"
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(route.size) { index ->
                            val isCurrent = index == currentStopIndex
                            val bg = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White
                            val fg = if (isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            Card(colors = CardDefaults.cardColors(containerColor = bg)) {
                                Text(
                                    text = if (isCurrent) "🚜 ${route[index].first}" else route[index].first,
                                    color = fg,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ಬರುವಿಕೆ ಎಚ್ಚರಿಕೆ", fontWeight = FontWeight.Bold, color = Color.Black)
                    Text("ಟ್ರ್ಯಾಕ್ಟರ್ ನಿಮ್ಮ ಮನೆಗೆ ~5 ನಿಮಿಷಗಳಲ್ಲಿ ಬರುತ್ತದೆ.", color = Color.Black)
                    Text(
                        "ಪ್ರಸ್ತುತ ದೂರ: $distanceInStreets ಬೀದಿಗಳು",
                        color = Color.Black,
                        fontWeight = FontWeight.Medium
                    )
                    Text("ಪ್ರಸ್ತುತ GPS: $currentLocation", color = Color.Black)
                }
            }
        }
        item {
            Text(
                "ಸ್ಥಿತಿ ಅಪ್ಡೇಟ್‌ಗಳು",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
        items(updateLines.reversed()) { update ->
            Card {
                Text(
                    text = update,
                    modifier = Modifier.padding(12.dp),
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
private fun ComplaintScreen(
    paddingValues: PaddingValues,
    onComplaintCreated: (ComplaintEntry) -> Unit
) {
    var complainantName by rememberSaveable { mutableStateOf("") }
    var wardNumber by rememberSaveable { mutableStateOf("") }
    var complaintText by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("ಪಂಚಾಯತ್ ದೂರು ಸಲ್ಲಿಕೆ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("ಸೇವೆ ಸಮಸ್ಯೆಗೆ ನೇರ ದೂರು ನೀಡಿ. ಇದು ಆಡ್ಮಿನ್ ಪ್ಯಾನೆಲ್‌ನಲ್ಲಿ ಕಾಣಿಸುತ್ತದೆ.")

        OutlinedTextField(
            value = complainantName,
            onValueChange = { complainantName = it },
            label = { Text("ನಿಮ್ಮ ಹೆಸರು") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = wardNumber,
            onValueChange = { wardNumber = it },
            label = { Text("ವಾರ್ಡ್ / ಬೀದಿ ಸಂಖ್ಯೆ") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = complaintText,
            onValueChange = { complaintText = it },
            label = { Text("ದೂರು ವಿವರ") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (complainantName.isBlank() || wardNumber.isBlank() || complaintText.isBlank()) {
                    Toast.makeText(context, "ಎಲ್ಲ ದೂರು ಕ್ಷೇತ್ರಗಳನ್ನು ಭರ್ತಿ ಮಾಡಿ", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                onComplaintCreated(
                    ComplaintEntry(
                        name = complainantName.trim(),
                        ward = wardNumber.trim(),
                        complaint = complaintText.trim(),
                        createdAt = java.time.LocalDateTime.now()
                            .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")),
                        lat = reportToLatLng(wardNumber.trim()).latitude,
                        lng = reportToLatLng(wardNumber.trim()).longitude
                    )
                )
                Toast.makeText(context, "ದೂರು ಯಶಸ್ವಿಯಾಗಿ ಸಲ್ಲಿಕೆಯಾಗಿದೆ", Toast.LENGTH_SHORT).show()
                complainantName = ""
                wardNumber = ""
                complaintText = ""
            }) {
                Text("ದೂರು ಕಳುಹಿಸಿ")
            }
            Button(onClick = {
                complainantName = ""
                wardNumber = ""
                complaintText = ""
            }) {
                Text("ಅಳಿಸಿ")
            }
        }
    }
}

@Composable
private fun AdminLoginScreen(
    paddingValues: PaddingValues,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    var password by rememberSaveable { mutableStateOf("") }
    val adminPassword = "admin123"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Admin Login", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Enter admin password to view reports, complaints, and dashboard metrics.")
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Admin Password") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors()
        )
        Button(onClick = {
            if (password == adminPassword) {
                onLoginSuccess()
                Toast.makeText(context, "Admin login successful", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Invalid admin password", Toast.LENGTH_SHORT).show()
            }
        }) {
            Text("Login")
        }
    }
}

@Composable
private fun AdminDashboardScreen(
    paddingValues: PaddingValues,
    reports: List<WasteReport>,
    complaints: List<ComplaintEntry>,
    tractorCount: String,
    wasteCollectedKg: String,
    completedTrips: String,
    onTractorCountChange: (String) -> Unit,
    onWasteCollectedChange: (String) -> Unit,
    onCompletedTripsChange: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 6.dp)
    ) {
        item {
            Text("Admin Dashboard", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Central view for reports, complaints, and collection performance.")
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Operations Metrics", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = tractorCount,
                        onValueChange = onTractorCountChange,
                        label = { Text("Number of Tractors") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = wasteCollectedKg,
                        onValueChange = onWasteCollectedChange,
                        label = { Text("Waste Collected (kg)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = completedTrips,
                        onValueChange = onCompletedTripsChange,
                        label = { Text("Completed Trips Today") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Quick Summary", fontWeight = FontWeight.Bold)
                    Text("Total Reports: ${reports.size}")
                    Text("Total Complaints: ${complaints.size}")
                    Text("Tractors Active: ${tractorCount.ifBlank { "0" }}")
                    Text("Waste Collected: ${wasteCollectedKg.ifBlank { "0" }} kg")
                }
            }
        }
        item { Text("Submitted Reports", fontWeight = FontWeight.Bold) }
        if (reports.isEmpty()) {
            item { Text("No reports available yet.") }
        } else {
            items(reports) { report ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(report.location, fontWeight = FontWeight.SemiBold)
                        Text(report.notes)
                        if (report.photoUri != null) {
                            AsyncImage(
                                model = report.photoUri,
                                contentDescription = "Report photo",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                            )
                        }
                        Text("Map: ${report.lat}, ${report.lng}", style = MaterialTheme.typography.bodySmall)
                        Text("Time: ${report.createdAt}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { Text("Submitted Complaints", fontWeight = FontWeight.Bold) }
        if (complaints.isEmpty()) {
            item { Text("No complaints available yet.") }
        } else {
            items(complaints) { complaint ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${complaint.name} - ${complaint.ward}", fontWeight = FontWeight.SemiBold)
                        Text(complaint.complaint)
                        Text("Map: ${complaint.lat}, ${complaint.lng}", style = MaterialTheme.typography.bodySmall)
                        Text("Time: ${complaint.createdAt}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportScreen(
    paddingValues: PaddingValues,
    onPickPhoto: () -> Unit,
    selectedPhotoUri: Uri?,
    onClearPhoto: () -> Unit,
    reports: MutableList<WasteReport>
) {
    var locationText by rememberSaveable { mutableStateOf("") }
    var notesText by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("ಕಸದ ಸಮಸ್ಯೆ ವರದಿ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("ಪಂಚಾಯತ್ ಪರಿಶೀಲನೆಗಾಗಿ ಫೋಟೋ + ಸ್ಥಳವನ್ನು ಸೇರಿಸಿ. ಇದು ಆಡ್ಮಿನ್ ಪ್ಯಾನೆಲ್‌ನಲ್ಲಿ ಕಾಣಿಸುತ್ತದೆ.")

        OutlinedTextField(
            value = locationText,
            onValueChange = { locationText = it },
            label = { Text("ಸ್ಥಳ (ಬೀದಿ / ಗುರುತು ಸ್ಥಳ)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = notesText,
            onValueChange = { notesText = it },
            label = { Text("ಸಮಸ್ಯೆ ವಿವರ") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Button(onClick = onPickPhoto) {
            Text("ಫೋಟೋ ಆಯ್ಕೆ ಮಾಡಿ")
        }
        Text(
            text = selectedPhotoUri?.toString() ?: "ಫೋಟೋ ಆಯ್ಕೆ ಆಗಿಲ್ಲ",
            style = MaterialTheme.typography.bodySmall
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (locationText.isBlank() || notesText.isBlank()) {
                        Toast.makeText(context, "ಎಲ್ಲ ಕ್ಷೇತ್ರಗಳನ್ನು ಭರ್ತಿ ಮಾಡಿ", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val report = WasteReport(
                        location = locationText.trim(),
                        notes = notesText.trim(),
                        photoUri = selectedPhotoUri?.toString(),
                        createdAt = java.time.LocalDateTime.now()
                            .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")),
                        lat = reportToLatLng(locationText.trim()).latitude,
                        lng = reportToLatLng(locationText.trim()).longitude
                    )
                    reports.add(0, report)
                    Toast.makeText(context, "ವರದಿ ಯಶಸ್ವಿಯಾಗಿ ಸಲ್ಲಿಕೆಯಾಗಿದೆ", Toast.LENGTH_LONG).show()
                    locationText = ""
                    notesText = ""
                    onClearPhoto()
                }
            ) {
                Text("ವರದಿ ಸಲ್ಲಿಸಿ")
            }
            Button(
                onClick = {
                    locationText = ""
                    notesText = ""
                    onClearPhoto()
                    Toast.makeText(context, "ಫಾರ್ಮ್ ಮರುಹೊಂದಿಸಲಾಗಿದೆ", Toast.LENGTH_SHORT).show()
                }
            ) {
                Text("ಮರುಹೊಂದಿಸಿ")
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text(
            "ಸಲ್ಲಿಸಿದ ಎಲ್ಲಾ ವರದಿಗಳು ಆಡ್ಮಿನ್ ಪ್ಯಾನೆಲ್‌ನಲ್ಲಿ ಮಾತ್ರ ಕಾಣಿಸುತ್ತವೆ.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun reportToLatLng(location: String): LatLng {
    val baseLat = 12.9716
    val baseLng = 77.5946
    val offset = (location.hashCode() % 1000) / 100000.0
    return LatLng(baseLat + offset, baseLng + offset)
}

@Composable
private fun GuideScreen(paddingValues: PaddingValues) {
    val guideItems = listOf(
        "Wet Waste / ತೇವ ತ್ಯಾಜ್ಯ" to
            "Food scraps, fruit/vegetable peels, tea powder, eggshells, flowers. " +
            "Put in green bin daily. Convert to compost where possible.",
        "Dry Recyclables / ಒಣ ಮರುಬಳಕೆ ತ್ಯಾಜ್ಯ" to
            "Clean paper, cardboard, plastic bottles, metal cans, glass bottles. " +
            "Wash, dry, and store separately in blue bin.",
        "Sanitary Waste / ಸ್ವಚ್ಛತಾ ತ್ಯಾಜ್ಯ" to
            "Diapers, sanitary pads, bandages, masks. Wrap securely in paper and mark clearly before handover.",
        "E-Waste / ಇ-ತ್ಯಾಜ್ಯ" to
            "Mobile phones, chargers, cables, bulbs, small electronics. Do not mix with kitchen waste. " +
            "Give only in special collection drives.",
        "Hazardous Waste / ಅಪಾಯಕಾರಿ ತ್ಯಾಜ್ಯ" to
            "Batteries, expired medicines, paint cans, chemical containers, pesticide bottles. " +
            "Store safely and hand over to authorized collection.",
        "Construction Waste / ನಿರ್ಮಾಣ ತ್ಯಾಜ್ಯ" to
            "Bricks, debris, tiles, cement waste. Keep separate and inform Panchayat for pickup."
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("ತ್ಯಾಜ್ಯ ವಿಂಗಡಣೆ ಮಾರ್ಗದರ್ಶಿ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Segregate at source to reduce landfill load and improve village cleanliness.",
            color = Color.Black
        )
        guideItems.forEach { (title, description) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(title, fontWeight = FontWeight.Bold, color = Color.Black)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    Text(description, color = Color.Black)
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ಮಾಡಬೇಕಾದುದು / ಮಾಡಬಾರದದ್ದು", fontWeight = FontWeight.Bold, color = Color.Black)
                Text("Do: Keep 2 bins at home, rinse recyclables, hand over on collection time.", color = Color.Black)
                Text("Don't: Mix wet + dry waste, burn plastic, dump in empty plots or drains.", color = Color.Black)
            }
        }
        Text(
            text = "Keep two bins at home: Green (Wet), Blue (Dry)",
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            color = Color.Black,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ChatbotScreen(paddingValues: PaddingValues) {
    var userQuestion by rememberSaveable { mutableStateOf("") }
    val chatLines = remember {
        mutableStateListOf(
            ChatMessage(
                fromUser = false,
                text = "ನಮಸ್ಕಾರ! I am your Grama Waste AI Assistant. ನೀವು ಕನ್ನಡ ಅಥವಾ English ನಲ್ಲಿ ಕೇಳಬಹುದು."
            )
        )
    }
    var lastTopic by rememberSaveable { mutableStateOf("general") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("ಗ್ರಾಮ ಸಹಾಯ ಬಾಟ್", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("ಸಾಮಾನ್ಯ ಪ್ರಶ್ನೆಗಳಿಗೆ ತ್ವರಿತ ಉತ್ತರಗಳು.")

        OutlinedTextField(
            value = userQuestion,
            onValueChange = { userQuestion = it },
            label = { Text("ನಿಮ್ಮ ಪ್ರಶ್ನೆ ಬರೆಯಿರಿ") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (userQuestion.isBlank()) return@Button
                    val cleanQuestion = userQuestion.trim()
                    chatLines.add(ChatMessage(fromUser = true, text = cleanQuestion))
                    val answer = generateSmartReply(
                        question = cleanQuestion,
                        previousTopic = lastTopic
                    )
                    lastTopic = answer.topic
                    chatLines.add(ChatMessage(fromUser = false, text = answer.reply))
                    userQuestion = ""
                }
            ) { Text("ಕೇಳಿ") }

            Button(onClick = {
                userQuestion = ""
                chatLines.clear()
                chatLines.add(
                    ChatMessage(
                        fromUser = false,
                        text = "ಚಾಟ್ ಅಳಿಸಲಾಗಿದೆ. Ask me anything about village waste management."
                    )
                )
                lastTopic = "general"
            }) { Text("ಚಾಟ್ ಅಳಿಸಿ") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("When will tractor come?", "Wet vs dry waste?", "How to report issue?").forEach { hint ->
                AssistChip(
                    onClick = { userQuestion = hint },
                    label = { Text(hint) },
                    colors = AssistChipDefaults.assistChipColors()
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(chatLines) { line ->
                    val bgColor = if (line.fromUser) Color(0xFFD7ECFF) else Color(0xFFE9F5E9)
                    val align = if (line.fromUser) Alignment.CenterEnd else Alignment.CenterStart
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = align) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = bgColor),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (line.fromUser) "You: ${line.text}" else "AI: ${line.text}",
                                modifier = Modifier.padding(10.dp),
                                color = Color.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class SmartReply(
    val topic: String,
    val reply: String
)

private fun MainActivity.createNotificationChannel() {
    val channel = NotificationChannelCompat.Builder("nearby_alerts", NotificationManagerCompat.IMPORTANCE_HIGH)
        .setName("Nearby Tractor Alerts")
        .setDescription("Alerts when tractor is near your street")
        .build()
    NotificationManagerCompat.from(this).createNotificationChannel(channel)
}

private fun showNearbyAlertNotification(context: android.content.Context, distanceInStreets: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    val notification = NotificationCompat.Builder(context, "nearby_alerts")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("Tractor Nearby Alert")
        .setContentText("Collection tractor is only $distanceInStreets streets away.")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()
    NotificationManagerCompat.from(context).notify(1001, notification)
}

private fun loadHomeLocation(
    context: android.content.Context,
    onLocation: (LatLng) -> Unit
) {
    if (
        ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
        ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
    ) return

    try {
        LocationServices.getFusedLocationProviderClient(context).lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    onLocation(LatLng(location.latitude, location.longitude))
                }
            }
    } catch (_: Exception) {
        // Keep map running even if location provider fails.
    }
}

private fun generateSmartReply(question: String, previousTopic: String): SmartReply {
    val input = question.lowercase()
    val compactInput = input.trim()

    val topicKeywords = mapOf(
        "timing" to listOf("time", "when", "tractor", "arrive", "coming", "eta", "timing"),
        "segregation" to listOf("wet", "dry", "segregation", "waste type", "bin", "organic", "recycle"),
        "report" to listOf("report", "blackspot", "issue", "photo", "submit"),
        "complaint" to listOf("complaint", "panchayat", "problem", "not coming", "missed", "delay"),
        "contact" to listOf("email", "contact", "mail", "phone", "reach"),
        "smalltalk" to listOf("hi", "hello", "hey", "thanks", "thank you", "how are you"),
        "health" to listOf("smell", "mosquito", "dengue", "disease", "health", "hygiene"),
        "penalty" to listOf("fine", "penalty", "rule", "illegal", "burning", "dumping"),
        "reduction" to listOf("reduce", "reuse", "compost", "home compost", "less waste")
    )

    val matchedTopic = topicKeywords.maxByOrNull { (_, keys) ->
        keys.count { key -> input.contains(key) }
    }?.let { (topic, keys) ->
        if (keys.any { key -> input.contains(key) }) topic else null
    } ?: run {
        previousTopic
    }

    val acknowledgement = when {
        compactInput.isBlank() -> "ನಿಮ್ಮ ಪ್ರಶ್ನೆ ಸ್ಪಷ್ಟವಾಗಿಲ್ಲ. / I did not receive your question clearly."
        compactInput.length < 12 -> "ಸರಿ, ಪಡೆದಿದ್ದೇನೆ. / Got it."
        else -> "ಒಳ್ಳೆಯ ಪ್ರಶ್ನೆ. / Good question."
    }

    val replyBody = when {
        compactInput.contains("?") && compactInput.contains("your name") ->
            "ನಾನು Grama Waste AI Assistant. Collection tracking, report, complaint ನಲ್ಲಿ ಸಹಾಯ ಮಾಡುತ್ತೇನೆ. / I help with tracking, reports, and complaints."
        compactInput.contains("today") && (compactInput.contains("collection") || compactInput.contains("pickup")) ->
            "ಇಂದಿನ ಟ್ರ್ಯಾಕ್ಟರ್ ಸ್ಥಿತಿಗಾಗಿ Live Track ತೆರೆಯಿರಿ. ನಿಮ್ಮ ಪ್ರದೇಶ ಮಿಸ್ ಆದರೆ ward number ಜೊತೆ complaint ಸಲ್ಲಿಸಿ. / Open Live Track for today; if skipped, submit complaint with ward number."
        compactInput.contains("kannada") || compactInput.contains("ಕನ್ನಡ") ->
            "ಹೌದು, ಕನ್ನಡದಲ್ಲೂ ಕೇಳಬಹುದು. / Yes, you can ask in Kannada too."
        matchedTopic == "smalltalk" ->
            "ನಮಸ್ಕಾರ! ನಾನು ಸಹಾಯಕ್ಕೆ ಸಿದ್ಧ. / Hello! Ask about collection, segregation, reporting, health, or Panchayat complaints."
        matchedTopic == "timing" ->
            "ಟ್ರ್ಯಾಕ್ಟರ್ ಮಾರ್ಗ ಪಂಚಾಯತ್‌ನಿಂದ ಆರಂಭವಾಗಿ ಬೀದಿ ಬೀದಿಗೆ ಹೋಗುತ್ತದೆ. Live Track ನಲ್ಲಿ current stop ಮತ್ತು distance ನೋಡಿ. ಸೇವೆಯಲ್ಲಿ ವಿಳಂಬ ಇದ್ದರೆ Complaint tab ಬಳಸಿ. / Route starts from Panchayat; check Live Track and use Complaint tab if delayed."
        matchedTopic == "segregation" ->
            "Wet waste ಗೆ Green bin, dry recyclables ಗೆ Blue bin ಬಳಸಿ; sanitary/e-waste ಬೇರೆ ಇಡಿ. Wet + dry ಮಿಶ್ರಣ ಮಾಡಬೇಡಿ. / Use green for wet, blue for dry, and keep sanitary/e-waste separate."
        matchedTopic == "report" ->
            "Blackspot report ಮಾಡಲು: Blackspot Report tab ತೆರೆಯಿರಿ -> location + issue ಬರೆಯಿರಿ -> photo ಸೇರಿಸಿ -> Submit Report. Admin dashboard ನಲ್ಲಿ ಕಾಣುತ್ತದೆ. / Submit via Report tab with photo and admin can view it."
        matchedTopic == "complaint" ->
            "Pickup ಮಿಸ್ ಅಥವಾ delay ಇದ್ದರೆ Complaint tab ನಲ್ಲಿ ward + details ನೀಡಿ. Admin dashboard ನಲ್ಲಿ follow-up ಮಾಡಬಹುದು. / For missed pickup/delay, submit complaint with ward details."
        matchedTopic == "contact" ->
            "Reports, complaints, tractor count, waste collection ಎಲ್ಲವೂ Admin dashboard ನಲ್ಲಿ ಕಾಣುತ್ತದೆ. / All monitoring is available in Admin dashboard."
        matchedTopic == "health" ->
            "ದುರ್ವಾಸನೆ/ಸೊರೆಕೀಟ ಸಮಸ್ಯೆಗೆ wet waste ಮುಚ್ಚಿ ಇಡಿ, ಪ್ರತಿದಿನ ಹಸ್ತಾಂತರಿಸಿ, ಕಾಲುವೆಗೆ ಹಾಕಬೇಡಿ, blackspot ತಕ್ಷಣ report ಮಾಡಿ. / For odor/mosquito risk, keep wet waste closed and report quickly."
        matchedTopic == "penalty" ->
            "Plastic ಸುಡುವುದು ಮತ್ತು ಅಕ್ರಮ ಕಸ ತ್ಯಜಿಸುವುದು ಅಪಾಯಕಾರಿ ಮತ್ತು ದಂಡನೀಯ. ಸ್ಥಳ/ಫೋಟೋ ದಾಖಲಿಸಿ complaint ನೀಡಿ. / Burning plastic and illegal dumping are punishable; record and complain."
        matchedTopic == "reduction" ->
            "Wet waste compost ಮಾಡಿ, ಚೀಲ/ಡಬ್ಬಿ ಮರುಬಳಕೆ ಮಾಡಿ, refill packs ಬಳಸಿ, recyclables ಸ್ವಚ್ಛವಾಗಿ ಬೇರ್ಪಡಿಸಿ. / Compost, reuse, refill, and separate recyclables."
        else ->
            "ನಾನು ಪ್ರತಿಯೊಂದು ವಿಷಯ ತಿಳಿದಿರದೇ ಇರಬಹುದು, ಆದರೆ ಸಹಾಯ ಮಾಡುತ್ತೇನೆ. ಸ್ಥಳ/ವಾರ್ಡ್/ಸಮಸ್ಯೆ ವಿವರ ನೀಡಿ. / Share location, ward, and issue details; I will guide next action."
    }

    val followUp = when (matchedTopic ?: "general") {
        "timing" -> "Pickup late ಆದರೆ complaint draft ಕೊಡಲೇನಾ? / Need a complaint draft if pickup is late?"
        "report" -> "ತಕ್ಷಣ paste ಮಾಡಲು sample report text ಬೇಕೇ? / Need a quick sample report text?"
        "complaint" -> "Ward number ಕೊಟ್ಟರೆ strong complaint format ಕೊಡುತ್ತೇನೆ. / Share ward number for stronger complaint format."
        "segregation" -> "ಒಂದು ವಾರದ segregation checklist ಬೇಕೇ? / Want a 1-week segregation checklist?"
        else -> "ಇನ್ನೂ ಪ್ರಶ್ನೆ ಕೇಳಬಹುದು. / Ask me anything else."
    }

    return SmartReply(
        topic = matchedTopic ?: "general",
        reply = "$acknowledgement $replyBody\n\n$followUp"
    )
}
