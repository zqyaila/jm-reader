package com.jm.reader.ui.daily

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.jm.reader.data.model.ComicListItem
import com.jm.reader.data.model.DayCell
import com.jm.reader.data.model.bool
import com.jm.reader.data.model.parseDailyRecord
import com.jm.reader.data.model.str
import com.jm.reader.data.repo.RepoResult
import com.jm.reader.ui.LocalAppStrings
import com.jm.reader.ui.LocalRepository
import com.jm.reader.ui.LocalSession
import com.jm.reader.ui.components.AppTopBar
import com.jm.reader.ui.components.ComicGrid
import com.jm.reader.ui.components.ErrorView
import com.jm.reader.ui.components.LoadingView
import com.jm.reader.ui.nav.Routes
import com.jm.reader.ui.theme.GlassPanel
import com.jm.reader.ui.theme.GlassShape
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** One cell of the sign-in calendar is [com.jm.reader.data.model.DayCell]. */
private data class DailyState(
    val dailyId: String = "",
    val eventName: String = "",
    val progress: String = "",
    val threeDaysCoin: String = "",
    val sevenDaysCoin: String = "",
    val signedCount: Int = 0,
    val signedToday: Boolean = false,
    val weeks: List<List<DayCell>> = emptyList(),
    val items: List<ComicListItem> = emptyList(),
)

/**
 * 每日签到.
 *
 * Contract verified against the live API and JMComic-qt:
 *  - `GET /daily?user_id=<uid>` -> `daily_id`, `event_name`, `currentProgress`, rewards and a
 *    `record` matrix of `{date, signed, bonus}` (note `signed` may be `null`, not just false).
 *  - `POST /daily_chk {user_id, daily_id}` -> `{msg}`; an unauthenticated call instead answers
 *    `{"code":200,"data":[]}`, which [com.jm.reader.data.repo.AppRepository.dailyCheck] reports as
 *    "please log in" rather than as a crash or a silent success.
 *
 * Every parse here is defensive and the whole load runs inside `runCatching`: a malformed or
 * unexpected payload shows the error view instead of taking the app down.
 */
@Composable
fun DailyScreen(navController: NavHostController) {
    val repo = LocalRepository.current
    val session = LocalSession.current
    val s = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val loggedIn by session.loggedInFlow.collectAsState()

    var state by remember { mutableStateOf(DailyState()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var signing by remember { mutableStateOf(false) }

    fun uid(): String? = session.memberJson
        ?.let { runCatching { JSONObject(it).str("uid") }.getOrNull() }
        ?.takeIf { it.isNotBlank() }

    suspend fun load() {
        val id = uid()
        if (id == null) {
            loading = false
            error = null
            return
        }
        loading = true
        error = null
        runCatching {
            when (val r = repo.getDaily(id)) {
                is RepoResult.Ok -> {
                    val parsed = parseDailyRecord(r.data)
                    state = state.copy(
                        dailyId = parsed.dailyId.ifBlank { state.dailyId },
                        eventName = parsed.eventName.ifBlank { state.eventName },
                        progress = parsed.progress.ifBlank { state.progress },
                        threeDaysCoin = parsed.threeDaysCoin.ifBlank { state.threeDaysCoin },
                        sevenDaysCoin = parsed.sevenDaysCoin.ifBlank { state.sevenDaysCoin },
                        signedCount = parsed.signedCount,
                        signedToday = parsed.signedToday,
                        weeks = parsed.weeks,
                    )
                }
                is RepoResult.Err -> error = r.message
            }
            // The comics of the day are optional: a failure here must not blank the screen.
            when (val l = repo.getDailyList(id)) {
                is RepoResult.Ok -> {
                    val list = l.data.optJSONArray("list")
                    if (list != null) state = state.copy(items = parseItems(list))
                }
                is RepoResult.Err -> Unit
            }
        }.onFailure { error = it.message ?: s.errServer }
        loading = false
    }

    LaunchedEffect(loggedIn) { load() }

    Scaffold(
        // Near-opaque canvas; the check-in card is the only glass surface here.
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(s.checkIn, onBack = { navController.popBackStack() }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !loggedIn -> SignInRequired(onLogin = { navController.navigate(Routes.login()) })
                loading -> LoadingView()
                error != null -> ErrorView(error!!, onRetry = { scope.launch { load() } })
                else -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    DailyCard(
                        state = state,
                        signing = signing,
                        s = s,
                        onSignIn = {
                            val id = uid() ?: return@DailyCard
                            if (state.dailyId.isBlank()) return@DailyCard
                            scope.launch {
                                signing = true
                                when (val r = repo.dailyCheck(id, state.dailyId)) {
                                    is RepoResult.Ok -> {
                                        val message = when {
                                            r.data.alreadyCheckedIn -> s.alreadySignedToday
                                            r.data.message?.isNotBlank() == true -> r.data.message!!
                                            else -> s.signInSuccessMsg
                                        }
                                        snackbar.showSnackbar(message)
                                        load()
                                    }
                                    is RepoResult.Err -> snackbar.showSnackbar(r.message)
                                }
                                signing = false
                            }
                        },
                    )
                    if (state.items.isEmpty()) {
                        Text(
                            s.todayNoWork,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        Text(
                            s.latest,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 14.dp, top = 4.dp, bottom = 2.dp),
                        )
                        ComicGrid(
                            items = state.items,
                            repo = repo,
                            onItemClick = { navController.navigate(Routes.comicDetail(it.id)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SignInRequired(onLogin: () -> Unit) {
    val s = LocalAppStrings.current
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.EventAvailable,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Text(s.loginFirst, modifier = Modifier.padding(top = 12.dp))
        Button(onClick = onLogin, modifier = Modifier.padding(top = 12.dp)) { Text(s.goLogin) }
    }
}

@Composable
private fun DailyCard(
    state: DailyState,
    signing: Boolean,
    s: com.jm.reader.ui.strings.AppStrings,
    onSignIn: () -> Unit,
) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        shape = GlassShape,
        blurRadius = 28.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                state.eventName.ifBlank { s.checkIn },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                s.signedDaysFmt.format(state.signedCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (state.progress.isNotBlank()) {
                Text(
                    s.dailyProgressFmt.format(state.progress),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                val fraction = state.progress.removeSuffix("%").toFloatOrNull()?.div(100f)
                if (fraction != null) {
                    LinearProgressIndicator(
                        progress = { fraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }
            }
            if (state.threeDaysCoin.isNotBlank() || state.sevenDaysCoin.isNotBlank()) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.threeDaysCoin.isNotBlank()) {
                        RewardChip("3D", state.threeDaysCoin)
                    }
                    if (state.sevenDaysCoin.isNotBlank()) {
                        RewardChip("7D", state.sevenDaysCoin)
                    }
                }
            }

            if (state.weeks.isNotEmpty()) {
                SignCalendar(state.weeks, Modifier.padding(top = 12.dp))
            }

            Button(
                onClick = onSignIn,
                enabled = !signing && state.dailyId.isNotBlank() && !state.signedToday,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                if (signing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (state.signedToday) s.alreadySignedToday else s.signIn)
                }
            }
        }
    }
}

@Composable
private fun RewardChip(label: String, value: String) {
    Row(
        Modifier
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$label  $value",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Calendar matrix of the check-in record: weeks as rows, one cell per day. */
@Composable
private fun SignCalendar(weeks: List<List<DayCell>>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        weeks.forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    val signed = day.signed
                    val fill = when {
                        signed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        day.bonus -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        day.pending -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .background(fill, RoundedCornerShape(8.dp))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                RoundedCornerShape(8.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            day.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (signed) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Defensive parse of the `/daily` payload; delegates to the unit-tested parser. */
private fun parseItems(list: JSONArray): List<ComicListItem> =
    (0 until list.length()).mapNotNull { i ->
        list.optJSONObject(i)?.let { runCatching { ComicListItem.fromJson(it) }.getOrNull() }
    }.distinctBy { it.id }
