package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object AppIcons {
    private fun materialIcon(name: String, block: androidx.compose.ui.graphics.vector.ImageVector.Builder.() -> Unit): ImageVector {
        return ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply(block).build()
    }

    val Remove: ImageVector by lazy {
        materialIcon("Remove") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(19f, 13f)
                horizontalLineTo(5f)
                verticalLineTo(11f)
                horizontalLineTo(19f)
                verticalLineTo(13f)
                close()
            }
        }
    }

    val Undo: ImageVector by lazy {
        materialIcon("Undo") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12.5f, 8f)
                curveTo(9.85f, 8f, 7.45f, 8.99f, 5.6f, 10.6f)
                lineTo(2f, 7f)
                verticalLineTo(16f)
                horizontalLineTo(11f)
                lineTo(7.38f, 12.38f)
                curveTo(8.77f, 11.22f, 10.54f, 10.5f, 12.5f, 10.5f)
                curveTo(16.04f, 10.5f, 19.05f, 12.81f, 20.1f, 16f)
                lineTo(22.47f, 15.22f)
                curveTo(21.08f, 11.03f, 17.15f, 8f, 12.5f, 8f)
                close()
            }
        }
    }

    val VisibilityOff: ImageVector by lazy {
        materialIcon("VisibilityOff") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 7f)
                curveTo(14.76f, 7f, 17f, 9.24f, 17f, 12f)
                curveTo(17f, 12.65f, 16.87f, 13.26f, 16.64f, 13.83f)
                lineTo(19.56f, 16.75f)
                curveTo(21.07f, 15.49f, 22.26f, 13.86f, 22.99f, 12f)
                curveTo(21.26f, 7.61f, 16.99f, 4.5f, 11.99f, 4.5f)
                curveTo(10.59f, 4.5f, 9.25f, 4.75f, 8.01f, 5.2f)
                lineTo(10.17f, 7.36f)
                curveTo(10.74f, 7.13f, 11.35f, 7f, 12f, 7f)
                close()
                moveTo(2f, 4.27f)
                lineTo(4.28f, 6.55f)
                lineTo(4.74f, 7.01f)
                curveTo(3.08f, 8.3f, 1.78f, 10.02f, 1f, 12f)
                curveTo(2.73f, 16.39f, 7f, 19.5f, 12f, 19.5f)
                curveTo(13.55f, 19.5f, 15.03f, 19.2f, 16.38f, 18.66f)
                lineTo(16.8f, 19.08f)
                lineTo(19.73f, 22f)
                lineTo(21f, 20.73f)
                lineTo(3.27f, 3f)
                lineTo(2f, 4.27f)
                close()
            }
        }
    }

    val Groups: ImageVector by lazy {
        materialIcon("Groups") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 12.75f)
                curveTo(13.63f, 12.75f, 15.07f, 13.14f, 16.24f, 13.65f)
                curveTo(17.32f, 14.13f, 18f, 15.21f, 18f, 16.38f)
                verticalLineTo(18f)
                horizontalLineTo(6f)
                verticalLineTo(16.38f)
                curveTo(6f, 15.21f, 6.68f, 14.13f, 7.76f, 13.65f)
                curveTo(8.93f, 13.14f, 10.37f, 12.75f, 12f, 12.75f)
                close()
                moveTo(12f, 7f)
                curveTo(13.1f, 7f, 14f, 7.9f, 14f, 9f)
                reflectiveQuadTo(12f, 11f)
                reflectiveQuadTo(10f, 9f)
                reflectiveQuadTo(12f, 7f)
                close()
            }
        }
    }

    val CameraAlt: ImageVector by lazy {
        materialIcon("CameraAlt") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(9f, 2f)
                lineTo(7.17f, 4f)
                horizontalLineTo(4f)
                curveTo(2.9f, 4f, 2f, 4.9f, 2f, 6f)
                verticalLineTo(18f)
                curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f)
                horizontalLineTo(20f)
                curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f)
                verticalLineTo(6f)
                curveTo(22f, 4.9f, 21.1f, 4f, 20f, 4f)
                horizontalLineTo(16.83f)
                lineTo(15f, 2f)
                horizontalLineTo(9f)
                close()
                moveTo(12f, 17f)
                curveTo(9.24f, 17f, 7f, 14.76f, 7f, 12f)
                reflectiveCurveTo(9.24f, 7f, 12f, 7f)
                reflectiveCurveTo(17f, 9.24f, 17f, 12f)
                reflectiveCurveTo(14.76f, 17f, 12f, 17f)
                close()
            }
        }
    }

    val Radar: ImageVector by lazy {
        materialIcon("Radar") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveTo(6.48f, 22f, 12f, 22f)
                reflectiveCurveTo(22f, 17.52f, 22f, 12f)
                reflectiveCurveTo(17.52f, 2f, 12f, 2f)
                close()
                moveTo(12f, 20f)
                curveTo(7.58f, 20f, 4f, 16.42f, 4f, 12f)
                reflectiveCurveTo(7.58f, 4f, 12f, 4f)
                reflectiveCurveTo(20f, 7.58f, 20f, 12f)
                reflectiveCurveTo(16.42f, 20f, 12f, 20f)
                close()
                moveTo(12f, 7f)
                curveTo(9.24f, 7f, 7f, 9.24f, 7f, 12f)
                reflectiveCurveTo(9.24f, 17f, 12f, 17f)
                reflectiveCurveTo(17f, 14.76f, 17f, 12f)
                horizontalLineTo(15f)
                curveTo(15f, 13.66f, 13.66f, 15f, 12f, 15f)
                reflectiveCurveTo(9f, 13.66f, 9f, 12f)
                reflectiveCurveTo(10.34f, 9f, 12f, 9f)
                verticalLineTo(7f)
                close()
            }
        }
    }

    val Videocam: ImageVector by lazy {
        materialIcon("Videocam") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(17f, 10.5f)
                verticalLineTo(7f)
                curveTo(17f, 6.45f, 16.55f, 6f, 16f, 6f)
                horizontalLineTo(4f)
                curveTo(3.45f, 6f, 3f, 6.45f, 3f, 7f)
                verticalLineTo(17f)
                curveTo(3f, 17.55f, 3.45f, 18f, 4f, 18f)
                horizontalLineTo(16f)
                curveTo(16.55f, 18f, 17f, 17.55f, 17f, 17f)
                verticalLineTo(13.5f)
                lineTo(21f, 17.5f)
                verticalLineTo(6.5f)
                lineTo(17f, 10.5f)
                close()
            }
        }
    }

    val Cameraswitch: ImageVector by lazy {
        materialIcon("Cameraswitch") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(16f, 7f)
                horizontalLineTo(14.83f)
                lineTo(13f, 5f)
                horizontalLineTo(11f)
                lineTo(9.17f, 7f)
                horizontalLineTo(8f)
                curveTo(6.9f, 7f, 6f, 7.9f, 6f, 9f)
                verticalLineTo(15f)
                curveTo(6f, 16.1f, 6.9f, 17f, 8f, 17f)
                horizontalLineTo(16f)
                curveTo(17.1f, 17f, 18f, 16.1f, 18f, 15f)
                verticalLineTo(9f)
                curveTo(18f, 7.9f, 17.1f, 7f, 16f, 7f)
                close()
                moveTo(12f, 14f)
                curveTo(10.9f, 14f, 10f, 13.1f, 10f, 12f)
                reflectiveCurveTo(10.9f, 10f, 12f, 10f)
                reflectiveCurveTo(14f, 10.9f, 14f, 12f)
                reflectiveCurveTo(13.1f, 14f, 12f, 14f)
                close()
            }
        }
    }

    val Stop: ImageVector by lazy {
        materialIcon("Stop") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 6f)
                horizontalLineTo(18f)
                verticalLineTo(18f)
                horizontalLineTo(6f)
                verticalLineTo(6f)
                close()
            }
        }
    }

    val DeleteSweep: ImageVector by lazy {
        materialIcon("DeleteSweep") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(15f, 16f)
                horizontalLineTo(19f)
                verticalLineTo(18f)
                horizontalLineTo(15f)
                verticalLineTo(16f)
                close()
                moveTo(15f, 8f)
                horizontalLineTo(22f)
                verticalLineTo(10f)
                horizontalLineTo(15f)
                verticalLineTo(8f)
                close()
                moveTo(15f, 12f)
                horizontalLineTo(21f)
                verticalLineTo(14f)
                horizontalLineTo(15f)
                verticalLineTo(12f)
                close()
                moveTo(3f, 18f)
                curveTo(3f, 19.1f, 3.9f, 20f, 5f, 20f)
                horizontalLineTo(11f)
                curveTo(12.1f, 20f, 13f, 19.1f, 13f, 18f)
                verticalLineTo(8f)
                horizontalLineTo(3f)
                verticalLineTo(18f)
                close()
                moveTo(14f, 5f)
                horizontalLineTo(11f)
                lineTo(10f, 4f)
                horizontalLineTo(6f)
                lineTo(5f, 5f)
                horizontalLineTo(2f)
                verticalLineTo(7f)
                horizontalLineTo(14f)
                verticalLineTo(5f)
                close()
            }
        }
    }

    val Backspace: ImageVector by lazy {
        materialIcon("Backspace") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(22f, 3f)
                horizontalLineTo(7f)
                curveTo(6.31f, 3f, 5.77f, 3.35f, 5.41f, 3.88f)
                lineTo(0f, 12f)
                lineTo(5.41f, 20.11f)
                curveTo(5.77f, 20.64f, 6.31f, 21f, 7f, 21f)
                horizontalLineTo(22f)
                curveTo(23.1f, 21f, 24f, 20.1f, 24f, 19f)
                verticalLineTo(5f)
                curveTo(24f, 3.9f, 23.1f, 3f, 22f, 3f)
                close()
                moveTo(19f, 15.59f)
                lineTo(17.59f, 17f)
                lineTo(14f, 13.41f)
                lineTo(10.41f, 17f)
                lineTo(9f, 15.59f)
                lineTo(12.59f, 12f)
                lineTo(9f, 8.41f)
                lineTo(10.41f, 7f)
                lineTo(14f, 10.59f)
                lineTo(17.59f, 7f)
                lineTo(19f, 8.41f)
                lineTo(15.41f, 12f)
                lineTo(19f, 15.59f)
                close()
            }
        }
    }

    val DirectionsCar: ImageVector by lazy {
        materialIcon("DirectionsCar") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(18.92f, 6.01f)
                curveTo(18.72f, 5.42f, 18.16f, 5f, 17.5f, 5f)
                horizontalLineTo(6.5f)
                curveTo(5.84f, 5f, 5.29f, 5.42f, 5.08f, 6.01f)
                lineTo(3f, 12f)
                verticalLineTo(20f)
                curveTo(3f, 20.55f, 3.45f, 21f, 4f, 21f)
                horizontalLineTo(5f)
                curveTo(5.55f, 21f, 6f, 20.55f, 6f, 20f)
                verticalLineTo(19f)
                horizontalLineTo(18f)
                verticalLineTo(20f)
                curveTo(18f, 20.55f, 18.45f, 21f, 19f, 21f)
                horizontalLineTo(20f)
                curveTo(20.55f, 21f, 21f, 20.55f, 21f, 20f)
                verticalLineTo(12f)
                lineTo(18.92f, 6.01f)
                close()
                moveTo(6.5f, 16f)
                curveTo(5.67f, 16f, 5f, 15.33f, 5f, 14.5f)
                reflectiveCurveTo(5.67f, 13f, 6.5f, 13f)
                reflectiveCurveTo(8f, 13.67f, 8f, 14.5f)
                reflectiveCurveTo(7.33f, 16f, 6.5f, 16f)
                close()
                moveTo(17.5f, 16f)
                curveTo(16.67f, 16f, 16f, 15.33f, 16f, 14.5f)
                reflectiveCurveTo(16.67f, 13f, 17.5f, 13f)
                reflectiveCurveTo(19f, 13.67f, 19f, 14.5f)
                reflectiveCurveTo(18.33f, 16f, 17.5f, 16f)
                close()
                moveTo(5f, 11f)
                lineTo(6.5f, 6.5f)
                horizontalLineTo(17.5f)
                lineTo(19f, 11f)
                horizontalLineTo(5f)
                close()
            }
        }
    }

    val Schedule: ImageVector by lazy {
        materialIcon("Schedule") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(11.99f, 2f)
                curveTo(6.47f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveTo(6.47f, 22f, 11.99f, 22f)
                curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
                reflectiveCurveTo(17.52f, 2f, 11.99f, 2f)
                close()
                moveTo(12f, 20f)
                curveTo(7.58f, 20f, 4f, 16.42f, 4f, 12f)
                reflectiveCurveTo(7.58f, 4f, 12f, 4f)
                reflectiveCurveTo(20f, 7.58f, 20f, 12f)
                reflectiveCurveTo(16.42f, 20f, 12f, 20f)
                close()
                moveTo(12.5f, 7f)
                horizontalLineTo(11f)
                verticalLineTo(13f)
                lineTo(16.25f, 16.15f)
                lineTo(17f, 14.92f)
                lineTo(12.5f, 12.25f)
                verticalLineTo(7f)
                close()
            }
        }
    }

    val DeleteOutline: ImageVector by lazy {
        materialIcon("DeleteOutline") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 19f)
                curveTo(6f, 20.1f, 6.9f, 21f, 8f, 21f)
                horizontalLineTo(16f)
                curveTo(17.1f, 21f, 18f, 20.1f, 18f, 19f)
                verticalLineTo(7f)
                horizontalLineTo(6f)
                verticalLineTo(19f)
                close()
                moveTo(8f, 9f)
                horizontalLineTo(16f)
                verticalLineTo(19f)
                horizontalLineTo(8f)
                verticalLineTo(9f)
                close()
                moveTo(15.5f, 4f)
                lineTo(14.5f, 3f)
                horizontalLineTo(9.5f)
                lineTo(8.5f, 4f)
                horizontalLineTo(5f)
                verticalLineTo(6f)
                horizontalLineTo(19f)
                verticalLineTo(4f)
                horizontalLineTo(15.5f)
                close()
            }
        }
    }

    val ScreenshotMonitor: ImageVector by lazy {
        materialIcon("ScreenshotMonitor") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(20f, 3f)
                horizontalLineTo(4f)
                curveTo(2.89f, 3f, 2f, 3.89f, 2f, 5f)
                verticalLineTo(17f)
                curveTo(2f, 18.1f, 2.89f, 19f, 4f, 19f)
                horizontalLineTo(8f)
                verticalLineTo(21f)
                horizontalLineTo(16f)
                verticalLineTo(19f)
                horizontalLineTo(20f)
                curveTo(21.1f, 19f, 22f, 18.1f, 22f, 17f)
                verticalLineTo(5f)
                curveTo(22f, 3.89f, 21.1f, 3f, 20f, 3f)
                close()
                moveTo(20f, 17f)
                horizontalLineTo(4f)
                verticalLineTo(5f)
                horizontalLineTo(20f)
                verticalLineTo(17f)
                close()
            }
        }
    }

    val Image: ImageVector by lazy {
        materialIcon("Image") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(21f, 19f)
                verticalLineTo(5f)
                curveTo(21f, 3.9f, 20.1f, 3f, 19f, 3f)
                horizontalLineTo(5f)
                curveTo(3.9f, 3f, 3f, 3.9f, 3f, 5f)
                verticalLineTo(19f)
                curveTo(3f, 20.1f, 3.9f, 21f, 5f, 21f)
                horizontalLineTo(19f)
                curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
                close()
                moveTo(8.5f, 13.5f)
                lineTo(11f, 16.51f)
                lineTo(14.5f, 12f)
                lineTo(19f, 18f)
                horizontalLineTo(5f)
                lineTo(8.5f, 13.5f)
                close()
            }
        }
    }

    val ContentCopy: ImageVector by lazy {
        materialIcon("ContentCopy") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(16f, 1f)
                horizontalLineTo(4f)
                curveTo(2.9f, 1f, 2f, 1.9f, 2f, 3f)
                verticalLineTo(17f)
                horizontalLineTo(4f)
                verticalLineTo(3f)
                horizontalLineTo(16f)
                verticalLineTo(1f)
                close()
                moveTo(19f, 5f)
                horizontalLineTo(8f)
                curveTo(6.9f, 5f, 6f, 5.9f, 6f, 7f)
                verticalLineTo(21f)
                curveTo(6f, 22.1f, 6.9f, 23f, 8f, 23f)
                horizontalLineTo(19f)
                curveTo(20.1f, 23f, 21f, 22.1f, 21f, 21f)
                verticalLineTo(7f)
                curveTo(21f, 5.9f, 20.1f, 5f, 19f, 5f)
                close()
                moveTo(19f, 21f)
                horizontalLineTo(8f)
                verticalLineTo(7f)
                horizontalLineTo(19f)
                verticalLineTo(21f)
                close()
            }
        }
    }

    val TableView: ImageVector by lazy {
        materialIcon("TableView") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(19f, 3f)
                horizontalLineTo(5f)
                curveTo(3.9f, 3f, 3f, 3.9f, 3f, 5f)
                verticalLineTo(19f)
                curveTo(3f, 20.1f, 3.9f, 21f, 5f, 21f)
                horizontalLineTo(19f)
                curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
                verticalLineTo(5f)
                curveTo(21f, 3.9f, 20.1f, 3f, 19f, 3f)
                close()
                moveTo(19f, 7f)
                horizontalLineTo(5f)
                verticalLineTo(5f)
                horizontalLineTo(19f)
                verticalLineTo(7f)
                close()
                moveTo(19f, 13f)
                horizontalLineTo(5f)
                verticalLineTo(9f)
                horizontalLineTo(19f)
                verticalLineTo(13f)
                close()
                moveTo(5f, 19f)
                verticalLineTo(15f)
                horizontalLineTo(19f)
                verticalLineTo(19f)
                horizontalLineTo(5f)
                close()
            }
        }
    }

    val Save: ImageVector by lazy {
        materialIcon("Save") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(17f, 3f)
                horizontalLineTo(5f)
                curveTo(3.89f, 3f, 3f, 3.9f, 3f, 5f)
                verticalLineTo(19f)
                curveTo(3f, 20.1f, 3.89f, 21f, 5f, 21f)
                horizontalLineTo(19f)
                curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
                verticalLineTo(7f)
                lineTo(17f, 3f)
                close()
                moveTo(12f, 19f)
                curveTo(10.34f, 19f, 9f, 17.66f, 9f, 16f)
                reflectiveCurveTo(10.34f, 13f, 12f, 13f)
                reflectiveCurveTo(15f, 14.34f, 15f, 16f)
                reflectiveCurveTo(13.66f, 19f, 12f, 19f)
                close()
                moveTo(15f, 9f)
                horizontalLineTo(5f)
                verticalLineTo(5f)
                horizontalLineTo(15f)
                verticalLineTo(9f)
                close()
            }
        }
    }

    val EditLocationAlt: ImageVector by lazy {
        materialIcon("EditLocationAlt") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f)
                curveTo(8.13f, 2f, 5f, 5.13f, 5f, 9f)
                curveTo(5f, 14.25f, 12f, 22f, 12f, 22f)
                reflectiveCurveTo(19f, 14.25f, 19f, 9f)
                curveTo(19f, 5.13f, 15.87f, 2f, 12f, 2f)
                close()
                moveTo(12f, 11.5f)
                curveTo(10.62f, 11.5f, 9.5f, 10.38f, 9.5f, 9f)
                reflectiveCurveTo(10.62f, 6.5f, 12f, 6.5f)
                reflectiveCurveTo(14.5f, 7.62f, 14.5f, 9f)
                reflectiveCurveTo(13.38f, 11.5f, 12f, 11.5f)
                close()
            }
        }
    }

    val Pause: ImageVector by lazy {
        materialIcon("Pause") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 19f)
                horizontalLineTo(10f)
                verticalLineTo(5f)
                horizontalLineTo(6f)
                verticalLineTo(19f)
                close()
                moveTo(14f, 5f)
                verticalLineTo(19f)
                horizontalLineTo(18f)
                verticalLineTo(5f)
                horizontalLineTo(14f)
                close()
            }
        }
    }

    val VolumeUp: ImageVector by lazy {
        materialIcon("VolumeUp") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 9f)
                verticalLineTo(15f)
                horizontalLineTo(7f)
                lineTo(12f, 20f)
                verticalLineTo(4f)
                lineTo(7f, 9f)
                horizontalLineTo(3f)
                close()
                moveTo(16.5f, 12f)
                curveTo(16.5f, 10.23f, 15.48f, 8.71f, 14f, 7.97f)
                verticalLineTo(16.02f)
                curveTo(15.48f, 15.29f, 16.5f, 13.77f, 16.5f, 12f)
                close()
            }
        }
    }

    val VolumeOff: ImageVector by lazy {
        materialIcon("VolumeOff") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(16.5f, 12f)
                curveTo(16.5f, 10.23f, 15.48f, 8.71f, 14f, 7.97f)
                verticalLineTo(10.18f)
                lineTo(16.45f, 12.63f)
                curveTo(16.48f, 12.43f, 16.5f, 12.22f, 16.5f, 12f)
                close()
                moveTo(4.27f, 3f)
                lineTo(3f, 4.27f)
                lineTo(7.73f, 9f)
                horizontalLineTo(3f)
                verticalLineTo(15f)
                horizontalLineTo(7f)
                lineTo(12f, 20f)
                verticalLineTo(13.27f)
                lineTo(18.73f, 20f)
                lineTo(20f, 18.73f)
                lineTo(4.27f, 3f)
                close()
                moveTo(12f, 4f)
                lineTo(9.91f, 6.09f)
                lineTo(12f, 8.18f)
                verticalLineTo(4f)
                close()
            }
        }
    }

    val Vibration: ImageVector by lazy {
        materialIcon("Vibration") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(0f, 15f)
                horizontalLineTo(2f)
                verticalLineTo(9f)
                horizontalLineTo(0f)
                verticalLineTo(15f)
                close()
                moveTo(3f, 17f)
                horizontalLineTo(5f)
                verticalLineTo(7f)
                horizontalLineTo(3f)
                verticalLineTo(17f)
                close()
                moveTo(22f, 9f)
                verticalLineTo(15f)
                horizontalLineTo(24f)
                verticalLineTo(9f)
                horizontalLineTo(22f)
                close()
                moveTo(19f, 17f)
                horizontalLineTo(21f)
                verticalLineTo(7f)
                horizontalLineTo(19f)
                verticalLineTo(17f)
                close()
                moveTo(16.5f, 3f)
                horizontalLineTo(7.5f)
                curveTo(6.67f, 3f, 6f, 3.67f, 6f, 4.5f)
                verticalLineTo(19.5f)
                curveTo(6f, 20.33f, 6.67f, 21f, 7.5f, 21f)
                horizontalLineTo(16.5f)
                curveTo(17.33f, 21f, 18f, 20.33f, 18f, 19.5f)
                verticalLineTo(4.5f)
                curveTo(18f, 3.67f, 17.33f, 3f, 16.5f, 3f)
                close()
                moveTo(16f, 19f)
                horizontalLineTo(8f)
                verticalLineTo(5f)
                horizontalLineTo(16f)
                verticalLineTo(19f)
                close()
            }
        }
    }

    val DarkMode: ImageVector by lazy {
        materialIcon("DarkMode") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 3f)
                curveTo(7.03f, 3f, 3f, 7.03f, 3f, 12f)
                reflectiveCurveTo(7.03f, 21f, 12f, 21f)
                curveTo(16.97f, 21f, 21f, 16.97f, 21f, 12f)
                curveTo(21f, 11.54f, 20.96f, 11.08f, 20.9f, 10.64f)
                curveTo(19.92f, 12.01f, 18.32f, 12.9f, 16.5f, 12.9f)
                curveTo(13.52f, 12.9f, 11.1f, 10.48f, 11.1f, 7.5f)
                curveTo(11.1f, 5.68f, 11.99f, 4.08f, 13.36f, 3.1f)
                curveTo(12.92f, 3.04f, 12.46f, 3f, 12f, 3f)
                close()
            }
        }
    }

    val LightMode: ImageVector by lazy {
        materialIcon("LightMode") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 7f)
                curveTo(9.24f, 7f, 7f, 9.24f, 7f, 12f)
                reflectiveCurveTo(9.24f, 17f, 12f, 17f)
                reflectiveCurveTo(17f, 14.76f, 17f, 12f)
                reflectiveCurveTo(14.76f, 7f, 12f, 7f)
                close()
                moveTo(2f, 13f)
                horizontalLineTo(4f)
                verticalLineTo(11f)
                horizontalLineTo(2f)
                verticalLineTo(13f)
                close()
                moveTo(20f, 13f)
                horizontalLineTo(22f)
                verticalLineTo(11f)
                horizontalLineTo(20f)
                verticalLineTo(13f)
                close()
                moveTo(11f, 2f)
                verticalLineTo(4f)
                horizontalLineTo(13f)
                verticalLineTo(2f)
                horizontalLineTo(11f)
                close()
                moveTo(11f, 20f)
                verticalLineTo(22f)
                horizontalLineTo(13f)
                verticalLineTo(20f)
                horizontalLineTo(11f)
                close()
            }
        }
    }

    val RestartAlt: ImageVector by lazy {
        materialIcon("RestartAlt") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 5f)
                verticalLineTo(2f)
                lineTo(8f, 6f)
                lineTo(12f, 10f)
                verticalLineTo(7f)
                curveTo(15.31f, 7f, 18f, 9.69f, 18f, 13f)
                curveTo(18f, 15.97f, 15.84f, 18.43f, 13f, 18.91f)
                verticalLineTo(20.93f)
                curveTo(16.95f, 20.44f, 20f, 17.08f, 20f, 13f)
                curveTo(20f, 8.58f, 16.42f, 5f, 12f, 5f)
                close()
                moveTo(6f, 13f)
                curveTo(6f, 11.35f, 6.67f, 9.85f, 7.76f, 8.76f)
                lineTo(6.34f, 7.34f)
                curveTo(4.9f, 8.79f, 4f, 10.79f, 4f, 13f)
                curveTo(4f, 17.08f, 7.05f, 20.44f, 11f, 20.93f)
                verticalLineTo(18.91f)
                curveTo(8.16f, 18.43f, 6f, 15.97f, 6f, 13f)
                close()
            }
        }
    }

    val TouchApp: ImageVector by lazy {
        materialIcon("TouchApp") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(9f, 11.24f)
                verticalLineTo(7.5f)
                curveTo(9f, 6.12f, 10.12f, 5f, 11.5f, 5f)
                reflectiveCurveTo(14f, 6.12f, 14f, 7.5f)
                verticalLineTo(11.24f)
                curveTo(15.21f, 10.43f, 16f, 9.06f, 16f, 7.5f)
                curveTo(16f, 5.01f, 13.99f, 3f, 11.5f, 3f)
                reflectiveCurveTo(7f, 5.01f, 7f, 7.5f)
                curveTo(7f, 9.06f, 7.79f, 10.43f, 9f, 11.24f)
                close()
                moveTo(18.84f, 15.87f)
                lineTo(14.3f, 13.6f)
                curveTo(14.13f, 13.53f, 13.95f, 13.5f, 13.76f, 13.5f)
                horizontalLineTo(13f)
                verticalLineTo(7.5f)
                curveTo(13f, 6.67f, 12.33f, 6f, 11.5f, 6f)
                reflectiveCurveTo(10f, 6.67f, 10f, 7.5f)
                verticalLineTo(18.24f)
                lineTo(6.57f, 17.52f)
                lineTo(5.8f, 18.3f)
                lineTo(10.18f, 22.68f)
                curveTo(10.55f, 23.05f, 11.05f, 23.27f, 11.59f, 23.27f)
                horizontalLineTo(17.5f)
                curveTo(18.48f, 23.27f, 19.31f, 22.56f, 19.47f, 21.6f)
                lineTo(20.22f, 17.1f)
                curveTo(20.34f, 16.4f, 19.99f, 15.69f, 18.84f, 15.87f)
                close()
            }
        }
    }

    val CallSplit: ImageVector by lazy {
        materialIcon("CallSplit") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(14f, 4f)
                lineTo(16.29f, 6.29f)
                lineTo(13.41f, 9.17f)
                lineTo(14.83f, 10.59f)
                lineTo(17.71f, 7.71f)
                lineTo(20f, 10f)
                verticalLineTo(4f)
                horizontalLineTo(14f)
                close()
                moveTo(10f, 4f)
                horizontalLineTo(4f)
                verticalLineTo(10f)
                lineTo(6.29f, 7.71f)
                lineTo(11f, 12.41f)
                verticalLineTo(20f)
                horizontalLineTo(13f)
                verticalLineTo(11.59f)
                lineTo(7.71f, 6.29f)
                lineTo(10f, 4f)
                close()
            }
        }
    }

    val Assessment: ImageVector by lazy {
        materialIcon("Assessment") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(19f, 3f)
                horizontalLineTo(5f)
                curveTo(3.9f, 3f, 3f, 3.9f, 3f, 5f)
                verticalLineTo(19f)
                curveTo(3f, 20.1f, 3.9f, 21f, 5f, 21f)
                horizontalLineTo(19f)
                curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
                verticalLineTo(5f)
                curveTo(21f, 3.9f, 20.1f, 3f, 19f, 3f)
                close()
                moveTo(9f, 17f)
                horizontalLineTo(7f)
                verticalLineTo(10f)
                horizontalLineTo(9f)
                verticalLineTo(17f)
                close()
                moveTo(13f, 17f)
                horizontalLineTo(11f)
                verticalLineTo(7f)
                horizontalLineTo(13f)
                verticalLineTo(17f)
                close()
                moveTo(17f, 17f)
                horizontalLineTo(15f)
                verticalLineTo(13f)
                horizontalLineTo(17f)
                verticalLineTo(17f)
                close()
            }
        }
    }
}
