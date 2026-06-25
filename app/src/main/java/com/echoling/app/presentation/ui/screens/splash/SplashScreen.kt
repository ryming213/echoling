package com.echoling.app.presentation.ui.screens.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.echoling.app.R

/**
 * Cold-start splash page. Painted before the rest of the app graph is
 * composed so the user always sees a deliberate hero image for ~1s, then
 * the navigation host takes over. The image already contains its own
 * caption ("不必急于求成，每天进步一点"), so we just paint it once.
 *
 * The 1-second timing itself is enforced by
 * [com.echoling.app.presentation.ui.navigation.MainScaffold] — this
 * composable just renders the image.
 *
 * Sizing strategy matches `splash_background.xml` (the launcher theme's
 * `windowBackground`) so the pre-Compose frame and the Compose splash
 * are visually identical:
 *   - The light-blue `Box` background matches the splash image's base
 *     color, so any letterbox margin blends in seamlessly.
 *   - The image uses `ContentScale.Fit` (preserve aspect ratio, show
 *     full image, possibly with margins) instead of `Crop` so the
 *     figure and caption never get clipped on phones whose aspect
 *     ratio differs from 9:16. Centering it with `Alignment.Center`
 *     keeps the caption where the eye expects it.
 *
 * The `matchParentSize` modifier makes the Image obey the Box's
 * intrinsic 1080×1920 aspect ratio while still respecting its layout
 * constraints — same visual outcome as `gravity="center"` in the
 * layer-list XML, just in Compose.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(0xFF9C84C2)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.splash_image),
            contentDescription = null,
            // Use the image's natural intrinsic size, then Fit to scale it
            // down only if the box is smaller than the image. Combined
            // with the centered Box, this guarantees no distortion and
            // shows the full image with margin on non-9:16 phones.
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}