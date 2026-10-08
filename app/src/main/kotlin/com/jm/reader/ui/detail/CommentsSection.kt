package com.jm.reader.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jm.reader.data.model.CommentItem
import com.jm.reader.data.model.buildCommentThreads
import com.jm.reader.data.repo.AppRepository
import com.jm.reader.data.repo.RepoResult
import com.jm.reader.ui.LocalAppStrings
import com.jm.reader.ui.LocalRepository
import com.jm.reader.ui.strings.AppStrings
import com.jm.reader.ui.theme.GlassShape
import com.jm.reader.ui.theme.GlassShapeSmall
import com.jm.reader.ui.theme.glassSurface
import com.jm.reader.util.JmId
import com.jm.reader.util.stripHtml
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Paged album comments.
 *
 * The endpoint is `GET /forum?mode=manhua&aid=<album>&page=<n>`, answering
 * `{"total": <int>, "list": [...]}`; each entry can carry nested `replys`, which are rendered
 * inline under their parent (collapsed behind a "view N replies" row until tapped).
 *
 * The controller is a plain state holder rather than a ViewModel because the detail screen already
 * owns the album's lifetime - comments live and die with it.
 */
@Stable
class CommentsController(
    private val repo: AppRepository,
    private val albumId: String,
) {
    /** Threaded view (top-level comments, each carrying its replies) - what the UI renders. */
    var items by mutableStateOf<List<CommentItem>>(emptyList())
        private set
    var total by mutableIntStateOf(0)
        private set
    var loading by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var endReached by mutableStateOf(false)
        private set

    /** The flat rows exactly as the API returned them, across all loaded pages. */
    private var flat: List<CommentItem> = emptyList()
    private var page = 0

    /** Set when a reset arrives while a page is already in flight; replayed once that page lands. */
    private var pendingReset = false

    /** Loads the next page; pass `reset = true` to replace the list from page 1. */
    suspend fun load(reset: Boolean = false) {
        if (loading || loadingMore) {
            // Dropping this would be visible: the reader posts a comment, `onPost` calls
            // `load(reset = true)`, and if a "load more" happens to be in flight the new comment
            // never appears even though the UI said it was posted.
            if (reset) pendingReset = true
            return
        }
        if (reset) {
            page = 0
            flat = emptyList()
            items = emptyList()
            endReached = false
            error = null
        }
        val first = page == 0
        if (first) loading = true else loadingMore = true

        try {
            val next = page + 1
            when (val r = repo.albumComments(albumId, next)) {
                is RepoResult.Ok -> {
                    val incoming = r.data.items
                    // Reply rows can land on a later page than their parent, so threads are rebuilt
                    // from the whole accumulated set rather than from each page in isolation.
                    flat = if (first) incoming else (flat + incoming).distinctBy { it.key() }
                    items = buildCommentThreads(flat)
                    total = maxOf(r.data.total, flat.size)
                    page = next
                    // Stops paging when the server has no more rows, or when we have them all.
                    endReached = incoming.isEmpty() || flat.size >= total
                    error = null
                }
                is RepoResult.Err -> if (first) error = r.message
            }
        } finally {
            // Always cleared, including on cancellation: leaving `loading` set would wedge the
            // section on its spinner, and the `loading || loadingMore` guard above would then make
            // every later load a no-op.
            loading = false
            loadingMore = false
        }

        if (pendingReset) {
            pendingReset = false
            load(reset = true)
        }
    }
}

/** A comment needs a stable identity even when the API omits `CID`. */
private fun CommentItem.key(): String =
    cid.ifBlank { "${uid}:${content.hashCode()}" }

/** Transient UI state for the comment composer and the expanded reply threads. */
@Stable
class CommentsUi {
    var replyTarget by mutableStateOf<CommentItem?>(null)
    var draft by mutableStateOf("")
    var posting by mutableStateOf(false)
    var expanded by mutableStateOf<Set<String>>(emptySet())
}

@Composable
fun rememberCommentsController(albumId: String): CommentsController {
    val repo = LocalRepository.current
    return remember(albumId, repo) { CommentsController(repo, albumId) }
}

@Composable
fun rememberCommentsUi(): CommentsUi = remember { CommentsUi() }

/**
 * Emits the comment section into an enclosing [androidx.compose.foundation.lazy.LazyColumn]:
 * a header, the composer, every loaded comment (with its replies) and a "load more" row.
 */
fun LazyListScope.commentsSection(
    controller: CommentsController,
    ui: CommentsUi,
    loggedIn: Boolean,
    onLoginRequired: () -> Unit,
    onPost: suspend (content: String, replyTo: CommentItem?) -> Unit,
    onLike: (CommentItem) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    /**
     * Scope for the send action. It must come from the *screen*, not from `rememberCoroutineScope()`
     * inside this item: a LazyColumn item's scope dies when the item scrolls out of the viewport,
     * which would cancel an in-flight post and leave the composer stuck on its spinner.
     */
    scope: CoroutineScope,
) {
    item(key = "comments-header") {
        val s = LocalAppStrings.current
        val shown = if (controller.total > 0) controller.total else controller.items.size
        CommentsHeader(if (shown > 0) s.commentsCountFmt.format(shown) else s.comments)
    }

    item(key = "comments-composer") {
        CommentComposer(
            ui = ui,
            loggedIn = loggedIn,
            onLoginRequired = onLoginRequired,
            onPost = onPost,
            scope = scope,
        )
    }

    when {
        controller.loading -> item(key = "comments-loading") {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            }
        }

        controller.error != null && controller.items.isEmpty() -> item(key = "comments-error") {
            val s = LocalAppStrings.current
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    controller.error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onRetry) { Text(s.retry) }
            }
        }

        controller.items.isEmpty() -> item(key = "comments-empty") {
            val s = LocalAppStrings.current
            Text(
                s.commentsEmpty,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(24.dp),
            )
        }

        else -> items(controller.items, key = { "c-" + it.key() }) { comment ->
            CommentThread(
                comment = comment,
                ui = ui,
                loggedIn = loggedIn,
                onLoginRequired = onLoginRequired,
                onReply = { ui.replyTarget = it },
                onLike = onLike,
                onOpenAlbum = onOpenAlbum,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }

    if (!controller.endReached && controller.items.isNotEmpty()) {
        item(key = "comments-more") {
            val s = LocalAppStrings.current
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                if (controller.loadingMore) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = onLoadMore) { Text(s.commentsLoadMore) }
                }
            }
        }
    }
}

@Composable
private fun CommentsHeader(title: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            Icons.Filled.ChatBubbleOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

/** One top-level comment, in its own glass pane. */
@Composable
private fun CommentThread(
    comment: CommentItem,
    ui: CommentsUi,
    loggedIn: Boolean,
    onLoginRequired: () -> Unit,
    onReply: (CommentItem) -> Unit,
    onLike: (CommentItem) -> Unit,
    onOpenAlbum: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Flat pane (no drop shadow): the spec warns against a full glass effect on every row of a
    // long list, and a 20-comment thread would otherwise pay for 20 shadows. The fog, rim and
    // specular alone still separate it from the canvas, and replies nest inside this same pane
    // rather than stacking a second glass layer on top of it.
    Column(
        modifier
            .fillMaxWidth()
            .glassSurface(shape = GlassShape, elevation = 0.dp)
            .padding(10.dp),
    ) {
        CommentBranch(
            comment = comment,
            ui = ui,
            loggedIn = loggedIn,
            onLoginRequired = onLoginRequired,
            onReply = onReply,
            onLike = onLike,
            onOpenAlbum = onOpenAlbum,
            depth = 0,
        )
    }
}

/**
 * A comment plus its replies, recursively.
 *
 * The flat API links replies by `parent_CID` and a chain is not limited to one level, so this has
 * to recurse: rendering only `comment.replies` once would silently hide every comment nested at
 * depth 2 or deeper. Each level has its own collapse toggle and is indented one step.
 */
@Composable
private fun CommentBranch(
    comment: CommentItem,
    ui: CommentsUi,
    loggedIn: Boolean,
    onLoginRequired: () -> Unit,
    onReply: (CommentItem) -> Unit,
    onLike: (CommentItem) -> Unit,
    onOpenAlbum: (String) -> Unit,
    depth: Int,
) {
    CommentBody(
        comment = comment,
        loggedIn = loggedIn,
        onLoginRequired = onLoginRequired,
        onReply = onReply,
        onLike = onLike,
        onOpenAlbum = onOpenAlbum,
        compact = depth > 0,
    )
    if (comment.replies.isEmpty() || depth >= MAX_THREAD_RENDER_DEPTH) return

    val s = LocalAppStrings.current
    val key = comment.key()
    val expanded = key in ui.expanded

    // Replies are collapsed by default: threads of 20+ replies would otherwise bury the rest.
    TextButton(
        onClick = { ui.expanded = if (expanded) ui.expanded - key else ui.expanded + key },
        modifier = Modifier.padding(start = 4.dp),
    ) {
        Text(
            if (expanded) s.commentHideReplies else s.commentRepliesFmt.format(comment.replies.size),
            style = MaterialTheme.typography.labelMedium,
        )
    }
    if (expanded) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, top = 2.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            comment.replies.forEach { reply ->
                CommentBranch(
                    comment = reply,
                    ui = ui,
                    loggedIn = loggedIn,
                    onLoginRequired = onLoginRequired,
                    onReply = onReply,
                    onLike = onLike,
                    onOpenAlbum = onOpenAlbum,
                    depth = depth + 1,
                )
            }
        }
    }
}

/** Belt-and-braces bound on indentation; `buildCommentThreads` already caps the tree depth. */
private const val MAX_THREAD_RENDER_DEPTH = 8

/** Avatar + name + level + time + body + actions. */
@Composable
private fun CommentBody(
    comment: CommentItem,
    loggedIn: Boolean,
    onLoginRequired: () -> Unit,
    onReply: (CommentItem) -> Unit,
    onLike: (CommentItem) -> Unit,
    onOpenAlbum: (String) -> Unit,
    compact: Boolean = false,
) {
    val s = LocalAppStrings.current
    val repo = LocalRepository.current
    var likes by remember(comment.key()) { mutableIntStateOf(comment.likes.toInt()) }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CommentAvatar(
            url = repo.commentAvatar(comment.avatar),
            size = if (compact) 26.dp else 34.dp,
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    comment.username.ifBlank { "?" },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                comment.level.takeIf { it.isNotBlank() }?.let { lv ->
                    Text(
                        "Lv$lv",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (comment.addtime.isNotBlank()) {
                    Text(
                        comment.addtime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Text(
                stripHtml(comment.content),
                style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                lineHeight = if (compact) 18.sp else 20.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
            // The album a comment is attached to, when it is not the one being viewed.
            comment.linkAlbumId
                ?.takeIf { it.isNotBlank() }
                ?.let { aid ->
                    Text(
                        (comment.linkAlbumName ?: JmId.display(aid)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clickable { onOpenAlbum(aid) },
                    )
                }
            Row(
                Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            if (!loggedIn) {
                                onLoginRequired()
                            } else {
                                likes += 1
                                onLike(comment)
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        Icons.Filled.FavoriteBorder,
                        contentDescription = s.commentLiked,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        likes.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = {
                        if (!loggedIn) onLoginRequired() else onReply(comment)
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Reply,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(s.commentReplyToFmt.format(comment.username.ifBlank { "?" }), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun CommentAvatar(url: String?, size: Dp) {
    val shape = CircleShape
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(shape),
            )
        } else {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.6f),
            )
        }
    }
}

/**
 * Composer. It is always on screen; for logged-out readers it is a button that routes to login,
 * because a dead text field that silently rejects the post is worse than an honest prompt.
 */
@Composable
private fun CommentComposer(
    ui: CommentsUi,
    loggedIn: Boolean,
    onLoginRequired: () -> Unit,
    onPost: suspend (content: String, replyTo: CommentItem?) -> Unit,
    scope: CoroutineScope,
) {
    val s: AppStrings = LocalAppStrings.current

    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        ui.replyTarget?.let { target ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    s.commentReplyToFmt.format(target.username.ifBlank { "?" }),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                TextButton(onClick = { ui.replyTarget = null }) { Text(s.cancel) }
            }
        }
        if (!loggedIn) {
            Button(
                onClick = onLoginRequired,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(s.commentLoginRequired) }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = ui.draft,
                    onValueChange = { ui.draft = it },
                    placeholder = { Text(s.commentHint, style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    shape = GlassShapeSmall,
                )
                Button(
                    enabled = ui.draft.isNotBlank() && !ui.posting,
                    onClick = {
                        val text = ui.draft
                        scope.launch {
                            onPost(text, ui.replyTarget)
                        }
                    },
                ) {
                    if (ui.posting) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = s.commentSend, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
