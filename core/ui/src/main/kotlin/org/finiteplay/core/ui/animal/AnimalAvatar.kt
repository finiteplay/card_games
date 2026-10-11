package org.finiteplay.core.ui.animal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.finiteplay.cards.Animal

/** An animal's face drawn on a disc in its colour. Drawn, so the app ships no image assets. */
@Composable
fun AnimalAvatar(animal: Animal, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Canvas(modifier = modifier.size(size).clip(CircleShape).background(Color(animal.colorArgb))) {
        drawAnimal(animal)
    }
}

private val Ink = Color(0xFF263238)
private val White = Color.White

private fun DrawScope.disc(color: Color, x: Float, y: Float, r: Float) =
    drawCircle(color, radius = size.width * r, center = Offset(size.width * x, size.width * y))

private fun DrawScope.oval(color: Color, x: Float, y: Float, w: Float, h: Float) =
    drawOval(color, Offset(size.width * (x - w / 2), size.width * (y - h / 2)), Size(size.width * w, size.width * h))

private fun DrawScope.triangle(color: Color, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
    val s = size.width
    drawPath(Path().apply { moveTo(s * x1, s * y1); lineTo(s * x2, s * y2); lineTo(s * x3, s * y3); close() }, color)
}

private fun DrawScope.eyes(x: Float, y: Float, gap: Float, r: Float, color: Color = Ink) {
    disc(color, x - gap, y, r)
    disc(color, x + gap, y, r)
}

private fun DrawScope.drawAnimal(animal: Animal) {
    when (animal) {
        Animal.PANDA -> {
            disc(Ink, 0.22f, 0.24f, 0.13f); disc(Ink, 0.78f, 0.24f, 0.13f)
            disc(White, 0.5f, 0.56f, 0.34f)
            oval(Ink, 0.37f, 0.52f, 0.16f, 0.2f); oval(Ink, 0.63f, 0.52f, 0.16f, 0.2f)
            eyes(0.5f, 0.52f, 0.13f, 0.03f, White)
            oval(Ink, 0.5f, 0.66f, 0.12f, 0.08f)
        }
        Animal.OWL -> {
            triangle(Color(0xFF6D4C41), 0.2f, 0.12f, 0.4f, 0.3f, 0.16f, 0.4f)
            triangle(Color(0xFF6D4C41), 0.8f, 0.12f, 0.6f, 0.3f, 0.84f, 0.4f)
            disc(Color(0xFF8D6E63), 0.5f, 0.56f, 0.36f)
            disc(White, 0.36f, 0.5f, 0.14f); disc(White, 0.64f, 0.5f, 0.14f)
            eyes(0.5f, 0.5f, 0.14f, 0.06f)
            triangle(Color(0xFFFFA000), 0.44f, 0.6f, 0.56f, 0.6f, 0.5f, 0.74f)
        }
        Animal.FOX -> {
            triangle(Color(0xFFE65100), 0.14f, 0.1f, 0.42f, 0.3f, 0.14f, 0.5f)
            triangle(Color(0xFFE65100), 0.86f, 0.1f, 0.58f, 0.3f, 0.86f, 0.5f)
            disc(Color(0xFFEF6C00), 0.5f, 0.56f, 0.34f)
            oval(White, 0.5f, 0.74f, 0.48f, 0.3f)
            eyes(0.5f, 0.5f, 0.14f, 0.04f)
            disc(Ink, 0.5f, 0.68f, 0.05f)
        }
        Animal.GECKO -> {
            oval(Color(0xFF43A047), 0.5f, 0.62f, 0.78f, 0.62f)
            disc(Color(0xFF66BB6A), 0.3f, 0.32f, 0.15f); disc(Color(0xFF66BB6A), 0.7f, 0.32f, 0.15f)
            disc(Color(0xFFFFF176), 0.3f, 0.32f, 0.1f); disc(Color(0xFFFFF176), 0.7f, 0.32f, 0.1f)
            eyes(0.5f, 0.32f, 0.2f, 0.045f)
            eyes(0.5f, 0.66f, 0.07f, 0.02f)
            oval(Color(0xFF2E7D32), 0.5f, 0.8f, 0.3f, 0.05f)
        }
        Animal.TORTOISE -> {
            disc(Color(0xFF7CB342), 0.5f, 0.3f, 0.17f)
            eyes(0.5f, 0.28f, 0.07f, 0.025f)
            disc(Color(0xFF8D6E63), 0.5f, 0.68f, 0.36f)
            disc(Color(0xFF6D4C41), 0.5f, 0.68f, 0.11f)
            disc(Color(0xFF6D4C41), 0.3f, 0.78f, 0.06f); disc(Color(0xFF6D4C41), 0.7f, 0.78f, 0.06f)
            disc(Color(0xFF6D4C41), 0.34f, 0.56f, 0.06f); disc(Color(0xFF6D4C41), 0.66f, 0.56f, 0.06f)
        }
        Animal.HONEY_BADGER -> {
            disc(Ink, 0.24f, 0.28f, 0.1f); disc(Ink, 0.76f, 0.28f, 0.1f)
            disc(Color(0xFF455A64), 0.5f, 0.58f, 0.34f)
            oval(White, 0.5f, 0.34f, 0.62f, 0.22f)
            oval(Color(0xFF455A64), 0.5f, 0.42f, 0.62f, 0.12f)
            eyes(0.5f, 0.52f, 0.13f, 0.045f, White); eyes(0.5f, 0.52f, 0.13f, 0.02f)
            oval(Ink, 0.5f, 0.68f, 0.14f, 0.09f)
        }
        Animal.CAPYBARA -> {
            disc(Color(0xFF6D4C41), 0.24f, 0.26f, 0.07f); disc(Color(0xFF6D4C41), 0.76f, 0.26f, 0.07f)
            oval(Color(0xFF8D6E63), 0.5f, 0.56f, 0.74f, 0.62f)
            oval(Color(0xFF6D4C41), 0.5f, 0.7f, 0.46f, 0.28f)
            eyes(0.5f, 0.52f, 0.16f, 0.04f)
            eyes(0.5f, 0.68f, 0.07f, 0.025f, Color(0xFF3E2723))
        }
    }
}
