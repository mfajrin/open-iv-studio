package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ModItemKind
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberOnContainer
import com.example.ui.theme.AmberSecondary
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.CyanTertiary
import com.example.ui.theme.ElevatedContainer
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.HairlineBorder

@Composable
fun OpenVHeaderBar(
  title: String,
  subtitle: String,
  editMode: Boolean,
  onToggleEditMode: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val pillBg by
    animateColorAsState(
      targetValue = if (editMode) AmberContainer else ElevatedContainer,
      animationSpec = spring(),
      label = "edit_mode_bg",
    )
  val pillBorder by
    animateColorAsState(
      targetValue = if (editMode) AmberSecondary else HairlineBorder,
      animationSpec = spring(),
      label = "edit_mode_border",
    )
  val pillTextColor by
    animateColorAsState(
      targetValue = if (editMode) AmberOnContainer else MaterialTheme.colorScheme.onSurfaceVariant,
      animationSpec = spring(),
      label = "edit_mode_text",
    )

  Surface(
    modifier = modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surface,
  ) {
    GtaHeaderBackdrop(
      imageAlpha = 0.48f,
      scrimStartAlpha = 0.86f,
      scrimEndAlpha = 0.62f,
    ) {
      Column {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier =
                Modifier.size(36.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .background(
                    Brush.linearGradient(
                      listOf(
                        EmeraldContainer,
                        CyanContainer,
                      )
                    )
                  ),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Rounded.Layers,
                contentDescription = null,
                tint = EmeraldPrimary,
                modifier = Modifier.size(20.dp),
              )
            }
            Spacer(Modifier.width(10.dp))
            Column {
              Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }

          Surface(
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onToggleEditMode)
                .testTag("edit_mode_toggle"),
            color = pillBg,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, pillBorder),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(
                modifier =
                  Modifier.size(7.dp)
                    .clip(CircleShape)
                    .background(if (editMode) AmberSecondary else EmeraldPrimary)
              )
              Spacer(Modifier.width(6.dp))
              Icon(
                imageVector = if (editMode) Icons.Rounded.Edit else Icons.Rounded.Lock,
                contentDescription = if (editMode) "Edit Mode Aktif" else "Mode Baca Saja",
                tint = if (editMode) AmberSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
              )
              Spacer(Modifier.width(5.dp))
              Text(
                text = if (editMode) "EDIT MODE" else "READ ONLY",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = pillTextColor,
              )
            }
          }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(HairlineBorder.copy(alpha = 0.8f)))
      }
    }
  }
}

@Composable
fun GtaHeaderBackdrop(
  modifier: Modifier = Modifier,
  imageAlpha: Float = 0.42f,
  scrimStartAlpha: Float = 0.88f,
  scrimEndAlpha: Float = 0.65f,
  content: @Composable BoxScope.() -> Unit,
) {
  Box(modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)) {
    Image(
      painter = painterResource(id = R.drawable.img_gtav_header_bg),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      alpha = imageAlpha,
      modifier = Modifier.matchParentSize(),
    )
    Box(
      modifier =
        Modifier.matchParentSize()
          .background(
            Brush.horizontalGradient(
              listOf(
                MaterialTheme.colorScheme.background.copy(alpha = scrimStartAlpha),
                MaterialTheme.colorScheme.background.copy(alpha = scrimEndAlpha),
              )
            )
          )
    )
    content()
  }
}

@Composable
fun ModFileIconBadge(
  kind: ModItemKind,
  badge: String,
  modifier: Modifier = Modifier,
) {
  val (icon: ImageVector, tint: Color, bg: Color) =
    when (kind) {
      ModItemKind.FOLDER -> Triple(Icons.Rounded.Folder, AmberSecondary, AmberContainer)
      ModItemKind.YTD_DICTIONARY ->
        Triple(Icons.Rounded.PhotoLibrary, CyanTertiary, CyanContainer)
      ModItemKind.RPF_ARCHIVE -> Triple(Icons.Rounded.Inventory2, EmeraldPrimary, EmeraldContainer)
      ModItemKind.OIV_ZIP_ARCHIVE -> Triple(Icons.Rounded.Archive, AmberSecondary, AmberContainer)
      ModItemKind.XML_META -> Triple(Icons.Rounded.Code, EmeraldPrimary, EmeraldContainer)
      ModItemKind.IMAGE_TEXTURE -> Triple(Icons.Rounded.Image, CyanTertiary, CyanContainer)
      ModItemKind.OTHER ->
        Triple(Icons.Rounded.InsertDriveFile, MaterialTheme.colorScheme.onSurfaceVariant, ElevatedContainer)
    }

  Box(
    modifier = modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(bg),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = badge,
      tint = tint,
      modifier = Modifier.size(22.dp),
    )
  }
}

@Composable
fun FormatTagPill(
  text: String,
  accent: Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    color = accent.copy(alpha = 0.13f),
    shape = RoundedCornerShape(8.dp),
    border = BorderStroke(1.dp, accent.copy(alpha = 0.32f)),
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
      color = accent,
      modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
      maxLines = 1,
    )
  }
}
