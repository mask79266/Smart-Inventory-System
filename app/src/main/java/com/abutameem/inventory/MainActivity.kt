package com.abutameem.inventory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

data class InventoryItem(
    val id: String = "",
    val serial: String = "",
    val name: String = "",
    val type: String = "",
    val unit: String = "",
    val quantity: Double = 0.0,
    val warehouse: String = "",
    val notes: String = "",
    val imagePath: String = ""
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { InventoryApp() }
    }
}

@Composable
fun InventoryApp() {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var signedIn by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser != null) }
    var error by remember { mutableStateOf("") }

    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (signedIn) {
                InventoryHome(onSignOut = {
                    FirebaseAuth.getInstance().signOut()
                    signedIn = false
                })
            } else {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("المخزون الذكي", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("تسجيل الدخول بالبريد المسموح")
                    Spacer(Modifier.height(24.dp))
                    OutlinedTextField(email, { email = it }, label = { Text("البريد الإلكتروني") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(password, { password = it }, label = { Text("كلمة المرور") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            error = ""
                            FirebaseAuth.getInstance().signInWithEmailAndPassword(email.trim(), password)
                                .addOnSuccessListener { result ->
                                    val normalized = result.user?.email?.trim()?.lowercase().orEmpty()
                                    FirebaseFirestore.getInstance().collection("allowedUsers").document(normalized).get()
                                        .addOnSuccessListener { doc ->
                                            if (doc.exists() && doc.getBoolean("enabled") == true) signedIn = true
                                            else { FirebaseAuth.getInstance().signOut(); error = "هذا البريد غير مضاف إلى قائمة المستخدمين المسموح لهم." }
                                        }
                                        .addOnFailureListener { FirebaseAuth.getInstance().signOut(); error = "تعذر التحقق من صلاحية المستخدم." }
                                }
                                .addOnFailureListener { error = "تعذر تسجيل الدخول. تحقق من البريد وكلمة المرور." }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("تسجيل الدخول") }
                }
            }
        }
    }
}

@Composable
fun InventoryHome(onSignOut: () -> Unit) {
    var search by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<InventoryItem>>(emptyList()) }
    var loadError by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        val registration = FirebaseFirestore.getInstance().collection("inventory")
            .addSnapshotListener { snapshot, e ->
                if (e != null) loadError = "تعذر تحميل المخزون"
                else items = snapshot?.documents?.map { d ->
                    InventoryItem(
                        id = d.id,
                        serial = d.getString("الرقم التسلسلي") ?: d.getString("serial") ?: "",
                        name = d.getString("اسم الصنف") ?: d.getString("name") ?: "",
                        type = d.getString("نوع الصنف") ?: d.getString("type") ?: "",
                        unit = d.getString("الوحدة") ?: d.getString("unit") ?: "",
                        quantity = d.getDouble("الكمية") ?: d.getLong("الكمية")?.toDouble() ?: 0.0,
                        warehouse = d.getString("رقم المخزن") ?: "",
                        notes = d.getString("ملاحظات") ?: "",
                        imagePath = d.getString("مسار الصورة") ?: ""
                    )
                } ?: emptyList()
            }
        onDispose { registration.remove() }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("المخزون الذكي", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("إدارة الأصناف", style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onSignOut) { Text("خروج") }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = search, onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("ابحث باسم الصنف أو الرقم") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
        )
        if (loadError.isNotBlank()) Text(loadError, color = MaterialTheme.colorScheme.error)
        Text("عدد السجلات المحمّلة: ${items.size}", style = MaterialTheme.typography.labelMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items.filter { it.name.contains(search, true) || it.serial.contains(search, true) }) { item ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(item.name.ifBlank { "صنف بدون اسم" }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text("الرقم التسلسلي: ${item.serial}")
                        Text("النوع: ${item.type}  •  الوحدة: ${item.unit}")
                        Text("الكمية: ${item.quantity}  •  المخزن: ${item.warehouse}")
                        if (item.notes.isNotBlank()) Text("ملاحظات: ${item.notes}")
                    }
                }
            }
        }
    }
}
