package com.berkant.yaninda.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.berkant.yaninda.R

enum class YanindaIconType {
    HOME,
    MEDICATION,
    ALARM,
    CLOCK,
    CHECK,
    PHONE,
    PERSON,
    FAMILY,
    LOCK,
    SETTINGS,
    INFO,
    ADD,
    CALENDAR,
    DEVICE,
    LOCATION,
    SYNC,
    WARNING,
    CHEVRON,
    BACK,
    HISTORY
}

enum class YanindaStatusTone {
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
    NEUTRAL,
}

data class YanindaTabItem(
    val label: String,
    val icon: YanindaIconType,
)

@Composable
fun YanindaIcon(
    type: YanindaIconType,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val semanticsModifier = if (contentDescription == null) {
        Modifier.clearAndSetSemantics { }
    } else {
        Modifier.semantics { this.contentDescription = contentDescription }
    }
    Canvas(modifier = modifier.then(semanticsModifier)) {
        val unit = size.minDimension
        val left = (size.width - unit) / 2f
        val top = (size.height - unit) / 2f
        fun point(x: Float, y: Float) = Offset(left + unit * x, top + unit * y)
        val line = Stroke(
            width = unit * 0.085f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        when (type) {
            YanindaIconType.CHECK -> {
                drawLine(tint, point(0.17f, 0.52f), point(0.41f, 0.74f), line.width, line.cap)
                drawLine(tint, point(0.41f, 0.74f), point(0.84f, 0.26f), line.width, line.cap)
            }

            YanindaIconType.ADD -> {
                drawLine(tint, point(0.18f, 0.5f), point(0.82f, 0.5f), line.width, line.cap)
                drawLine(tint, point(0.5f, 0.18f), point(0.5f, 0.82f), line.width, line.cap)
            }

            YanindaIconType.CHEVRON -> {
                drawLine(tint, point(0.35f, 0.2f), point(0.68f, 0.5f), line.width, line.cap)
                drawLine(tint, point(0.68f, 0.5f), point(0.35f, 0.8f), line.width, line.cap)
            }

            YanindaIconType.BACK -> {
                drawLine(tint, point(0.68f, 0.2f), point(0.35f, 0.5f), line.width, line.cap)
                drawLine(tint, point(0.35f, 0.5f), point(0.68f, 0.8f), line.width, line.cap)
                drawLine(tint, point(0.35f, 0.5f), point(0.84f, 0.5f), line.width, line.cap)
            }

            YanindaIconType.HOME -> {
                val roof = Path().apply {
                    moveTo(point(0.12f, 0.48f).x, point(0.12f, 0.48f).y)
                    lineTo(point(0.5f, 0.15f).x, point(0.5f, 0.15f).y)
                    lineTo(point(0.88f, 0.48f).x, point(0.88f, 0.48f).y)
                }
                drawPath(roof, tint, style = line)
                drawRoundRect(
                    color = tint,
                    topLeft = point(0.24f, 0.43f),
                    size = Size(unit * 0.52f, unit * 0.42f),
                    cornerRadius = CornerRadius(unit * 0.04f),
                    style = line,
                )
                drawLine(tint, point(0.5f, 0.85f), point(0.5f, 0.64f), line.width, line.cap)
            }

            YanindaIconType.HISTORY -> {
                drawCircle(tint, radius = unit * 0.36f, center = point(0.5f, 0.52f), style = line)
                drawLine(tint, point(0.5f, 0.52f), point(0.5f, 0.3f), line.width, line.cap)
                drawLine(tint, point(0.5f, 0.52f), point(0.32f, 0.61f), line.width, line.cap) // Backwards hands
            }

            YanindaIconType.MEDICATION -> rotate(-38f, pivot = center) {
                drawRoundRect(
                    color = tint,
                    topLeft = point(0.18f, 0.34f),
                    size = Size(unit * 0.64f, unit * 0.32f),
                    cornerRadius = CornerRadius(unit * 0.16f),
                    style = line,
                )
                drawLine(tint, point(0.5f, 0.34f), point(0.5f, 0.66f), line.width, line.cap)
            }

            YanindaIconType.CLOCK -> {
                drawCircle(tint, radius = unit * 0.36f, center = point(0.5f, 0.52f), style = line)
                drawLine(tint, point(0.5f, 0.52f), point(0.5f, 0.3f), line.width, line.cap)
                drawLine(tint, point(0.5f, 0.52f), point(0.68f, 0.61f), line.width, line.cap)
            }

            YanindaIconType.ALARM -> {
                val bell = Path().apply {
                    moveTo(point(0.25f, 0.67f).x, point(0.25f, 0.67f).y)
                    cubicTo(
                        point(0.32f, 0.58f).x,
                        point(0.32f, 0.58f).y,
                        point(0.3f, 0.42f).x,
                        point(0.3f, 0.42f).y,
                        point(0.36f, 0.31f).x,
                        point(0.36f, 0.31f).y,
                    )
                    cubicTo(
                        point(0.43f, 0.18f).x,
                        point(0.43f, 0.18f).y,
                        point(0.57f, 0.18f).x,
                        point(0.57f, 0.18f).y,
                        point(0.64f, 0.31f).x,
                        point(0.64f, 0.31f).y,
                    )
                    cubicTo(
                        point(0.7f, 0.42f).x,
                        point(0.7f, 0.42f).y,
                        point(0.68f, 0.58f).x,
                        point(0.68f, 0.58f).y,
                        point(0.75f, 0.67f).x,
                        point(0.75f, 0.67f).y,
                    )
                }
                drawPath(bell, tint, style = line)
                drawLine(tint, point(0.22f, 0.7f), point(0.78f, 0.7f), line.width, line.cap)
                drawLine(tint, point(0.44f, 0.82f), point(0.56f, 0.82f), line.width, line.cap)
            }

            YanindaIconType.PHONE -> {
                val phone = Path().apply {
                    moveTo(point(0.2f, 0.18f).x, point(0.2f, 0.18f).y)
                    cubicTo(
                        point(0.12f, 0.38f).x,
                        point(0.12f, 0.38f).y,
                        point(0.35f, 0.68f).x,
                        point(0.35f, 0.68f).y,
                        point(0.61f, 0.82f).x,
                        point(0.61f, 0.82f).y,
                    )
                    cubicTo(
                        point(0.72f, 0.88f).x,
                        point(0.72f, 0.88f).y,
                        point(0.86f, 0.72f).x,
                        point(0.86f, 0.72f).y,
                        point(0.79f, 0.63f).x,
                        point(0.79f, 0.63f).y,
                    )
                    lineTo(point(0.65f, 0.51f).x, point(0.65f, 0.51f).y)
                    cubicTo(
                        point(0.59f, 0.46f).x,
                        point(0.59f, 0.46f).y,
                        point(0.52f, 0.58f).x,
                        point(0.52f, 0.58f).y,
                        point(0.45f, 0.52f).x,
                        point(0.45f, 0.52f).y,
                    )
                    lineTo(point(0.34f, 0.39f).x, point(0.34f, 0.39f).y)
                    cubicTo(
                        point(0.28f, 0.32f).x,
                        point(0.28f, 0.32f).y,
                        point(0.39f, 0.25f).x,
                        point(0.39f, 0.25f).y,
                        point(0.33f, 0.2f).x,
                        point(0.33f, 0.2f).y,
                    )
                    close()
                }
                drawPath(phone, tint)
            }

            YanindaIconType.PERSON -> {
                drawCircle(tint, radius = unit * 0.17f, center = point(0.5f, 0.31f))
                drawArc(
                    color = tint,
                    startAngle = 190f,
                    sweepAngle = 160f,
                    useCenter = true,
                    topLeft = point(0.18f, 0.47f),
                    size = Size(unit * 0.64f, unit * 0.42f),
                )
            }

            YanindaIconType.FAMILY -> {
                drawCircle(tint, radius = unit * 0.13f, center = point(0.37f, 0.34f))
                drawCircle(tint, radius = unit * 0.13f, center = point(0.66f, 0.38f))
                drawArc(tint, 190f, 160f, true, point(0.12f, 0.52f), Size(unit * 0.5f, unit * 0.34f))
                drawArc(tint, 190f, 160f, true, point(0.4f, 0.55f), Size(unit * 0.48f, unit * 0.31f))
            }

            YanindaIconType.LOCK -> {
                drawRoundRect(
                    tint,
                    topLeft = point(0.2f, 0.42f),
                    size = Size(unit * 0.6f, unit * 0.43f),
                    cornerRadius = CornerRadius(unit * 0.08f),
                    style = line,
                )
                drawArc(
                    tint,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = point(0.32f, 0.15f),
                    size = Size(unit * 0.36f, unit * 0.5f),
                    style = line,
                )
                drawCircle(tint, radius = unit * 0.045f, center = point(0.5f, 0.62f))
            }

            YanindaIconType.SETTINGS -> {
                drawCircle(tint, radius = unit * 0.2f, center = point(0.5f, 0.5f), style = line)
                repeat(8) { index ->
                    val angle = Math.toRadians((index * 45.0))
                    val start = Offset(
                        point(0.5f, 0.5f).x + kotlin.math.cos(angle).toFloat() * unit * 0.29f,
                        point(0.5f, 0.5f).y + kotlin.math.sin(angle).toFloat() * unit * 0.29f,
                    )
                    val end = Offset(
                        point(0.5f, 0.5f).x + kotlin.math.cos(angle).toFloat() * unit * 0.39f,
                        point(0.5f, 0.5f).y + kotlin.math.sin(angle).toFloat() * unit * 0.39f,
                    )
                    drawLine(tint, start, end, line.width, line.cap)
                }
            }

            YanindaIconType.INFO -> {
                drawCircle(tint, radius = unit * 0.38f, center = point(0.5f, 0.5f), style = line)
                drawCircle(tint, radius = unit * 0.045f, center = point(0.5f, 0.31f))
                drawLine(tint, point(0.5f, 0.47f), point(0.5f, 0.69f), line.width, line.cap)
            }

            YanindaIconType.CALENDAR -> {
                drawRoundRect(
                    tint,
                    topLeft = point(0.16f, 0.22f),
                    size = Size(unit * 0.68f, unit * 0.62f),
                    cornerRadius = CornerRadius(unit * 0.07f),
                    style = line,
                )
                drawLine(tint, point(0.16f, 0.42f), point(0.84f, 0.42f), line.width, line.cap)
                drawLine(tint, point(0.33f, 0.15f), point(0.33f, 0.3f), line.width, line.cap)
                drawLine(tint, point(0.67f, 0.15f), point(0.67f, 0.3f), line.width, line.cap)
            }

            YanindaIconType.DEVICE -> {
                drawRoundRect(
                    tint,
                    topLeft = point(0.25f, 0.1f),
                    size = Size(unit * 0.5f, unit * 0.8f),
                    cornerRadius = CornerRadius(unit * 0.09f),
                    style = line,
                )
                drawLine(tint, point(0.43f, 0.78f), point(0.57f, 0.78f), line.width, line.cap)
            }

            YanindaIconType.LOCATION -> {
                drawCircle(tint, radius = unit * 0.3f, center = point(0.5f, 0.4f), style = line)
                drawCircle(tint, radius = unit * 0.08f, center = point(0.5f, 0.4f))
                val pin = Path().apply {
                    moveTo(point(0.23f, 0.42f).x, point(0.23f, 0.42f).y)
                    lineTo(point(0.5f, 0.88f).x, point(0.5f, 0.88f).y)
                    lineTo(point(0.77f, 0.42f).x, point(0.77f, 0.42f).y)
                }
                drawPath(pin, tint, style = line)
            }

            YanindaIconType.SYNC -> {
                drawArc(
                    tint,
                    210f,
                    205f,
                    false,
                    point(0.14f, 0.18f),
                    Size(unit * 0.7f, unit * 0.62f),
                    style = line,
                )
                drawLine(tint, point(0.77f, 0.16f), point(0.84f, 0.34f), line.width, line.cap)
                drawLine(tint, point(0.84f, 0.34f), point(0.66f, 0.31f), line.width, line.cap)
                drawArc(
                    tint,
                    30f,
                    205f,
                    false,
                    point(0.16f, 0.2f),
                    Size(unit * 0.7f, unit * 0.62f),
                    style = line,
                )
                drawLine(tint, point(0.23f, 0.84f), point(0.16f, 0.66f), line.width, line.cap)
                drawLine(tint, point(0.16f, 0.66f), point(0.34f, 0.69f), line.width, line.cap)
            }

            YanindaIconType.WARNING -> {
                val triangle = Path().apply {
                    moveTo(point(0.5f, 0.12f).x, point(0.5f, 0.12f).y)
                    lineTo(point(0.88f, 0.82f).x, point(0.88f, 0.82f).y)
                    lineTo(point(0.12f, 0.82f).x, point(0.12f, 0.82f).y)
                    close()
                }
                drawPath(triangle, tint, style = line)
                drawLine(tint, point(0.5f, 0.35f), point(0.5f, 0.57f), line.width, line.cap)
                drawCircle(tint, radius = unit * 0.04f, center = point(0.5f, 0.7f))
            }
        }
    }
}

@Composable
fun YanindaIconBadge(
    icon: YanindaIconType,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconColor: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = containerColor,
        contentColor = iconColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            YanindaIcon(
                type = icon,
                contentDescription = null,
                modifier = Modifier.size(size * 0.52f),
                tint = iconColor,
            )
        }
    }
}

@Composable
fun YanindaMedicationImage(
    medicationName: String,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
) {
    val imageResource = when (medicationName.hashCode().mod(3)) {
        0 -> R.drawable.yaninda_capsule_blue
        1 -> R.drawable.yaninda_capsule_red
        else -> R.drawable.yaninda_tablet_white
    }
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
    ) {
        Image(
            painter = painterResource(imageResource),
            contentDescription = "$medicationName ilaç görseli",
            modifier = Modifier
                .padding(size * 0.12f)
                .fillMaxWidth(),
            contentScale = ContentScale.Fit,
        )
    }
}


@Composable
fun YanindaCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}

@Composable
fun YanindaSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: YanindaIconType? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon?.let { YanindaIconBadge(icon = it, size = 42.dp) }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun YanindaInfoRow(
    label: String,
    value: String,
    icon: YanindaIconType,
    modifier: Modifier = Modifier,
    tone: YanindaStatusTone = YanindaStatusTone.NEUTRAL,
) {
    val toneColors = statusColors(tone)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            YanindaIconBadge(
                icon = icon,
                size = 42.dp,
                containerColor = toneColors.first,
                iconColor = toneColors.second,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(text = label, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun YanindaStatusPill(
    text: String,
    tone: YanindaStatusTone,
    modifier: Modifier = Modifier,
) {
    val colors = statusColors(tone)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = colors.first,
        contentColor = colors.second,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (tone == YanindaStatusTone.SUCCESS) {
                YanindaIcon(
                    type = YanindaIconType.CHECK,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(text = text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun YanindaPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: YanindaIconType? = null,
    enabled: Boolean = true,
    minHeight: Dp = 64.dp,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = minHeight),
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    ) {
        icon?.let {
            YanindaIcon(it, null, Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun YanindaOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: YanindaIconType? = null,
    enabled: Boolean = true,
    minHeight: Dp = 56.dp,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = minHeight),
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    ) {
        icon?.let {
            YanindaIcon(it, null, Modifier.size(22.dp))
            Spacer(Modifier.width(9.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun YanindaBottomTabs(
    items: List<YanindaTabItem>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                val color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .selectable(
                            selected = selected,
                            onClick = { onSelected(index) },
                            role = Role.Tab,
                        )
                        .heightIn(min = 58.dp)
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    YanindaIcon(item.icon, null, Modifier.size(23.dp), color)
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
fun YanindaListRow(
    title: String,
    supportingText: String,
    icon: YanindaIconType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingText: String? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 78.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            YanindaIconBadge(icon = icon, size = 46.dp)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            trailingText?.let {
                YanindaStatusPill(text = it, tone = YanindaStatusTone.NEUTRAL)
            }
            YanindaIcon(
                type = YanindaIconType.CHEVRON,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun statusColors(tone: YanindaStatusTone): Pair<Color, Color> = when (tone) {
    YanindaStatusTone.INFO -> MaterialTheme.colorScheme.primaryContainer to
        MaterialTheme.colorScheme.onPrimaryContainer
    YanindaStatusTone.SUCCESS -> MaterialTheme.colorScheme.tertiaryContainer to
        MaterialTheme.colorScheme.onTertiaryContainer
    YanindaStatusTone.WARNING -> Color(0xFFFFEFC7) to Color(0xFF573400)
    YanindaStatusTone.ERROR -> MaterialTheme.colorScheme.errorContainer to
        MaterialTheme.colorScheme.onErrorContainer
    YanindaStatusTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant to
        MaterialTheme.colorScheme.onSurfaceVariant
}
