package com.jm.reader.ui.member

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.jm.reader.data.model.Member
import com.jm.reader.data.repo.RepoResult
import com.jm.reader.ui.BottomNavReserve
import com.jm.reader.ui.LocalAppStrings
import com.jm.reader.ui.LocalLanguageManager
import com.jm.reader.ui.LocalRepository
import com.jm.reader.ui.LocalSession
import com.jm.reader.ui.nav.Routes
import com.jm.reader.ui.strings.UiLanguage
import com.jm.reader.ui.theme.AdFreeAccent
import com.jm.reader.ui.theme.GlassPanel
import com.jm.reader.ui.theme.GlassShape
import kotlinx.coroutines.launch

@Composable
fun MemberScreen(navController: NavHostController, modifier: Modifier = Modifier) {
    val repo = LocalRepository.current
    val session = LocalSession.current
    val s = LocalAppStrings.current
    val languageManager = LocalLanguageManager.current
    val language by languageManager.language.collectAsState()
    val scope = rememberCoroutineScope()
    // Reads the observable session state so the profile appears as soon as login succeeds.
    val loggedIn by session.loggedInFlow.collectAsState()

    Column(
        modifier
            .fillMaxSize()
            // Scrollable with a bottom reserve so the last row clears the floating glass nav pill.
            .verticalScroll(rememberScrollState())
            .padding(bottom = BottomNavReserve),
    ) {
        Text(s.member, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))

        val member = if (loggedIn) repo.member else null
        if (member == null) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(72.dp))
                Text(s.notLoggedIn, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { navController.navigate(Routes.login()) }) { Text(s.login) }
                    OutlinedButton(onClick = { navController.navigate(Routes.REGISTER) }) { Text(s.register) }
                }
            }
        } else {
            MemberProfile(member, s)
            MenuItem(Icons.Filled.CalendarMonth, s.checkIn) { navController.navigate(Routes.DAILY) }
            MenuItem(Icons.AutoMirrored.Filled.Logout, s.logout) {
                scope.launch {
                    repo.logout()
                    session.clearAuth()
                }
            }
        }

        // Language switcher
        Text(s.language, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp, 20.dp, 16.dp, 8.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UiLanguage.entries.forEach { lang ->
                FilterChip(
                    selected = language == lang,
                    onClick = { languageManager.set(lang) },
                    label = { Text(lang.displayName) },
                )
            }
        }
    }
}

@Composable
private fun MemberProfile(member: Member, s: com.jm.reader.ui.strings.AppStrings) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        GlassPanel(modifier = Modifier.fillMaxWidth(), shape = GlassShape, blurRadius = 26.dp) {
            Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    member.nickName.ifBlank { member.username }.ifBlank { s.memberProfile },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (member.adFree) {
                    Text(
                        s.adFreeMember,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(AdFreeAccent)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Text("${s.uidLabel}: ${member.uid}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            if (member.levelName.isNotBlank() || member.level.isNotBlank()) {
                Text("${s.levelLabel}${member.levelName.ifBlank { member.level }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${s.coinLabel}${member.coin}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 14.dp))
    }
}
