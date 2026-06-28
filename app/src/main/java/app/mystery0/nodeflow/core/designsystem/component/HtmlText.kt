package app.mystery0.nodeflow.core.designsystem.component

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import app.mystery0.nodeflow.core.parser.V2exHtmlParser
import coil.compose.AsyncImage

@Composable
fun HtmlText(
    html: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val parser = remember { V2exHtmlParser() }
    val imageUrls = remember(html) { parser.extractImageUrls(html) }
    Column(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = {
                TextView(context).apply {
                    movementMethod = LinkMovementMethod.getInstance()
                    textSize = 16f
                    typeface = android.graphics.Typeface.DEFAULT
                    setLineSpacing(0f, 1.12f)
                }
            },
            update = { view ->
                view.setTextColor(textColor)
                view.setLinkTextColor(linkColor)
                view.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
            },
        )
        imageUrls.forEach { imageUrl ->
            Spacer(Modifier.height(12.dp))
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.FillWidth,
            )
        }
    }
}
