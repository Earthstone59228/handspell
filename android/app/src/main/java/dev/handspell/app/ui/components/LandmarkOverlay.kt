// Connection set adapted from google-ai-edge/mediapipe-samples, examples/hand_landmarker/android
// (OverlayView.kt), Apache License 2.0:
// https://github.com/google-ai-edge/mediapipe-samples/blob/main/LICENSE
//
// Copyright 2023 The MediaPipe Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package dev.handspell.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import dev.handspell.app.core.model.HandOverlay

private const val DOT_RADIUS_PX = 6f
private const val LINE_WIDTH_PX = 4f

/**
 * Draws the 21 hand landmarks and their connections from [overlay], scaled from the analysed
 * normalised image space onto this composable's own size the same way `PreviewView`'s default
 * `FILL_CENTER` scale type maps the camera stream onto the screen. CameraFrame mirrors its front
 * preview, so this applies the matching visual x/y transform after MediaPipe has seen the
 * selfie-mirrored input (docs/CLASSIFIER.md §1).
 */
@Composable
fun LandmarkOverlay(overlay: HandOverlay?, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        if (overlay == null || overlay.imageWidth == 0 || overlay.imageHeight == 0) return@Canvas

        val scale = maxOf(size.width / overlay.imageWidth, size.height / overlay.imageHeight)
        val offsetX = (size.width - overlay.imageWidth * scale) / 2f
        val offsetY = (size.height - overlay.imageHeight * scale) / 2f

        fun point(index: Int): Offset {
            val landmark = overlay.imageLandmarks[index]
            return Offset(
                (1f - landmark.x) * overlay.imageWidth * scale + offsetX,
                (1f - landmark.y) * overlay.imageHeight * scale + offsetY,
            )
        }

        for (connection in HandLandmarker.HAND_CONNECTIONS) {
            drawLine(
                color = color,
                start = point(connection.start()),
                end = point(connection.end()),
                strokeWidth = LINE_WIDTH_PX,
                cap = Stroke.DefaultCap,
            )
        }
        for (index in overlay.imageLandmarks.indices) {
            drawCircle(color = color, radius = DOT_RADIUS_PX, center = point(index))
        }
    }
}
