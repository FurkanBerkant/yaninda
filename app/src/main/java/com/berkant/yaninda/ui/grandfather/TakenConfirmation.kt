package com.berkant.yaninda.ui.grandfather

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.berkant.yaninda.R
import com.berkant.yaninda.ui.components.YanindaIconBadge
import com.berkant.yaninda.ui.components.YanindaIconType
import com.berkant.yaninda.ui.components.YanindaPrimaryButton
import com.berkant.yaninda.ui.theme.YanindaTheme

@Composable
fun TakenConfirmation(
    onConfirmTaken: () -> Unit,
    onNotTaken: () -> Unit,
    modifier: Modifier = Modifier,
    isWorking: Boolean = false,
) {
    val screenTitle = stringResource(R.string.accessibility_confirmation_screen)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .semantics {
                    paneTitle = screenTitle
                },
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                YanindaIconBadge(
                    icon = YanindaIconType.CHECK,
                    size = 108.dp,
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    iconColor = MaterialTheme.colorScheme.onTertiary,
                )
            }

            item {
                Text(
                    text = stringResource(R.string.taken_confirmation_question),
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() }
                )
            }

            item {
                YanindaPrimaryButton(
                    text = stringResource(R.string.taken_confirmation_yes),
                    onClick = onConfirmTaken,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp),
                    icon = YanindaIconType.CHECK,
                    enabled = !isWorking,
                    minHeight = 80.dp,
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                )
            }

            item {
                YanindaPrimaryButton(
                    text = stringResource(R.string.taken_confirmation_no),
                    onClick = onNotTaken,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp),
                    icon = YanindaIconType.WARNING,
                    enabled = !isWorking,
                    minHeight = 72.dp,
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                )
            }
        }
    }
}

@Preview(
    name = "Galaxy A06",
    showBackground = true,
    widthDp = 360,
    heightDp = 800
)
@Preview(
    name = "Galaxy A06 - Büyük yazı",
    showBackground = true,
    widthDp = 360,
    heightDp = 800,
    fontScale = 1.3f
)
@Composable
private fun TakenConfirmationPreview() {
    YanindaTheme(darkTheme = false) {
        TakenConfirmation(
            onConfirmTaken = {},
            onNotTaken = {}
        )
    }
}
