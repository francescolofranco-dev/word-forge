package com.wordforge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wordforge.ui.theme.WordForgeTheme

@Composable
fun TierIndicator(
    tier: Int,
    modifier: Modifier = Modifier,
    total: Int = 8,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier.semantics {
            contentDescription = "Tier $tier of $total"
        },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        repeat(total) { i ->
            Box(
                Modifier
                    .height(6.dp)
                    .width(16.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (i < tier) color else MaterialTheme.colorScheme.outline)
            )
        }
    }
}

@Preview
@Composable
private fun TierIndicatorPreview() {
    WordForgeTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TierIndicator(tier = 0)
            TierIndicator(tier = 3)
            TierIndicator(tier = 8)
        }
    }
}
