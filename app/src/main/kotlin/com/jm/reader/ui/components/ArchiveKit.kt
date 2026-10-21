package com.jm.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jm.reader.ui.theme.CodeTextStyle
import com.jm.reader.ui.theme.glassClickable

/**
 * The small printed components the archive vocabulary is built from.
 *
 * The reference (`Wenku8_Flutter`) carries hierarchy with four things and nothing else: a 9 sp
 * monospace address, a hairline, a 3 px registration dot and weight. There is no card, no accent
 * fill and no shadow on content surfaces - covers are the only saturated rectangle on screen.
 */

/**
 * Monospace file address, optionally inverted into a solid ink plaque.
 *
 * The plaque is filled and lettered from one explicit pair - `onSurface` for the fill,
 * `surface` for the text - so the two can never collapse into each other, which is what happened
 * when the fill came from the theme's `background` and the text from a colour that happened to
 * match it. The fill is drawn with `drawBehind` rather than `background`, because a background
 * modifier nested behind caller padding has already been reported as an invisible plaque twice;
 * a draw call on the block's own canvas cannot be lost that way. The hairline keeps the block's
 * edge readable on paper of any tone.
 */
@Composable
fun ArchiveCode(
    text: String,
    modifier: Modifier = Modifier,
    inverse: Boolean = false,
    color: Color? = null,
) {
    val scheme = MaterialTheme.colorScheme
    if (inverse) {
        val fill = scheme.onSurface
        val ink = scheme.surface
        Box(
            modifier
                .heightIn(min = 24.dp)
                .drawBehind { drawRoundRect(fill, cornerRadius = CornerRadius(2.dp.toPx())) }
                .border(0.5.dp, scheme.outline, RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = CodeTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                color = color ?: ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            )
        }
    } else {
        Box(modifier) {
            Text(
                text = text,
                style = CodeTextStyle,
                color = color ?: scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Section mark: `02 / READING LOG` over a rule that ends in a printed dot, then the heading.
 *
 * The rule opens first and the heading follows in the reference's 80 ms band; both are paint-only
 * transforms so a long list never re-measures while it assembles.
 */
@Composable
fun ArchiveSectionHeader(
    code: String,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    /** Right-aligned count or address, e.g. the number of chapters. Never clickable. */
    trailing: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(top = 16.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArchiveCode(code)
            Spacer(Modifier.width(9.dp))
            Box(
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Spacer(Modifier.width(5.dp))
            Box(
                Modifier
                    .size(3.dp)
                    .background(MaterialTheme.colorScheme.onSurface),
            )
        }
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                fontSize = 18.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                ArchiveCode(trailing)
            }
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.width(10.dp))
                Text(
                    actionLabel,
                    style = CodeTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .glassClickable(shape = RoundedCornerShape(2.dp)) { onAction() }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
        }
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(
                subtitle,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A book as one ruled line of a register: 56 × 76 cover, address, title, author, chevron.
 *
 * This is the reference's `NovelRow`, not a card grid. The hairline under each row is what keeps
 * forty of them from turning into a wall.
 */
@Composable
fun ArchiveBookRow(
    id: String,
    title: String,
    author: String,
    cover: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val hairline = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier
            .fillMaxWidth()
            .glassClickable(shape = RoundedCornerShape(0.dp), onClick = onClick)
            .drawBehind {
                drawLine(
                    color = hairline,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 0.7.dp.toPx(),
                )
            }
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 56.dp, height = 76.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(cover)
                    .crossfade(160)
                    .build(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 56.dp, height = 76.dp),
            )
        }
        Spacer(Modifier.width(15.dp))
        Column(Modifier.weight(1f)) {
            ArchiveCode(if (id.length <= 8) "JM-${id.padStart(6, '0')}" else "JM-$id")
            Spacer(Modifier.height(4.dp))
            Text(
                title,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                author,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        trailing ?: Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
        )
    }
}

/**
 * An index drawer: a hairline box with a printed address in its corner.
 *
 * Used for the four quick entries. It is deliberately not a tinted icon tile - the archive has no
 * accent fill, so the box is the affordance and the code is the label.
 */
@Composable
fun ArchiveIndexTile(
    code: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .border(0.7.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
            .glassClickable(shape = RoundedCornerShape(4.dp), onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        ArchiveCode(code)
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One ruled text link with its address, for the secondary entries. */
@Composable
fun ArchiveTextLink(
    code: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .glassClickable(shape = RoundedCornerShape(2.dp), onClick = onClick)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            code,
            style = CodeTextStyle.copy(fontSize = 8.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/** Registration marks: four corner brackets, the "this is a printed plate" cue. */
fun Modifier.registrationMarks(color: Color, inset: Float = 0f): Modifier = drawBehind {
    val stroke = Stroke(width = 0.7.dp.toPx())
    val left = inset + 0.5.dp.toPx()
    val top = left
    val right = size.width - left
    val bottom = size.height - left
    val length = minOf(7.dp.toPx(), minOf(size.width, size.height) / 3f)
    if (length <= 0f) return@drawBehind
    listOf(
        Triple(Offset(left, top), 1f, 1f),
        Triple(Offset(right, top), -1f, 1f),
        Triple(Offset(left, bottom), 1f, -1f),
        Triple(Offset(right, bottom), -1f, -1f),
    ).forEach { (corner, dx, dy) ->
        val path = Path().apply {
            moveTo(corner.x + dx * length, corner.y)
            lineTo(corner.x, corner.y)
            lineTo(corner.x, corner.y + dy * length)
        }
        drawPath(path, color, style = stroke)
    }
}

/** A thin two-line "index plate": used where a card would be too loud. */
@Composable
fun ArchivePlate(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "",
    code: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .border(0.7.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
            .glassClickable(shape = RoundedCornerShape(4.dp), onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (code != null) {
                ArchiveCode(code)
                Spacer(Modifier.height(4.dp))
            }
            Text(
                title,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (hint.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    hint,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) trailing()
    }
}

/** Hairline between two stacked blocks, inset from the page edge like a printed rule. */
@Composable
fun ArchiveRule(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(0.7.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/**
 * Page masthead: a solid ink brand block, the navigation title at 23 sp, and the archive address
 * underneath. The block is the only filled shape in the header - it is what makes a page read as
 * a register rather than as a feed.
 */
@Composable
fun ArchiveMasthead(
    title: String,
    code: String,
    modifier: Modifier = Modifier,
    brand: String? = "JM",
    actions: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (brand != null) {
            Box(
                Modifier
                    .size(width = 30.dp, height = 34.dp)
                    .background(MaterialTheme.colorScheme.onBackground),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    brand,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.7).sp,
                    color = MaterialTheme.colorScheme.background,
                )
            }
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 23.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.7).sp,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            ArchiveCode(code)
        }
        if (actions != null) actions()
    }
}

/** Archive tab strip: small addresses with an ink rule under the selected one. */
@Composable
fun ArchiveTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hairline = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .drawBehind {
                drawLine(
                    color = hairline,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 0.7.dp.toPx(),
                )
            },
    ) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            Column(
                Modifier
                    .weight(1f)
                    .glassClickable(shape = RoundedCornerShape(0.dp)) { onSelect(index) }
                    .padding(top = 8.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "${(index + 1).toString().padStart(2, '0')}",
                    style = CodeTextStyle.copy(fontSize = 8.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = if (active) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(7.dp))
                Box(
                    Modifier
                        .height(2.dp)
                        .fillMaxWidth(if (active) 0.55f else 0f)
                        .background(MaterialTheme.colorScheme.onSurface),
                )
            }
        }
    }
}
