package br.com.thorlink.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Linguagem visual compartilhada pelos dois apps (Controle e Receptor).
 * REGRA DE LUZ ÚNICA: a luz vem do canto superior ESQUERDO.
 *  - realces (highlights) ficam em cima/esquerda das peças;
 *  - sombras projetadas caem para baixo/direita;
 *  - dentro de cavidades é o contrário: a parede de cima/esquerda fica na sombra
 *    e a parede de baixo/direita recebe luz.
 */

internal val ShellHi = Color(0xFFF3EEE7)
internal val ShellMid = Color(0xFFDAD3CB)
internal val ShellLow = Color(0xFFBEB6AE)
internal val ShellEdgeDark = Color(0xFF7F7770)

internal fun Color.lighten(f: Float): Color = lerp(this, Color.White, f)
internal fun Color.darken(f: Float): Color = lerp(this, Color.Black, f)

/** Sombra projetada suave (aproxima um blur sem custo de RenderEffect). */
internal fun DrawScope.softCircleShadow(c: Offset, r: Float, alpha: Float) {
    val rr = r * 1.28f
    drawCircle(
        Brush.radialGradient(
            0f to Color.Black.copy(alpha = alpha),
            .62f to Color.Black.copy(alpha = alpha * .7f),
            1f to Color.Transparent,
            center = c, radius = rr
        ), rr, c
    )
}

/** Cavidade circular afundada na carcaça: lábio, parede interna e piso com sombra interna. */
internal fun DrawScope.drawWell(c: Offset, r: Float, floor: Color = Color(0xFF3A3735)) {
    val a = c - Offset(r, r); val b = c + Offset(r, r)
    // lábio externo: pega luz no lado esquerdo/superior
    drawCircle(Brush.linearGradient(listOf(Color.White.copy(alpha = .85f), Color(0xFFB9B1A9), Color(0xFF8E867F)), a, b), r * 1.09f, c)
    // parede interna: cima/esquerda na sombra, baixo/direita iluminada
    drawCircle(Brush.linearGradient(listOf(Color(0xFF58524E), Color(0xFF8B847D), Color(0xFFC3BBB3)), a, b), r, c)
    // piso
    drawCircle(
        Brush.radialGradient(listOf(floor.lighten(.08f), floor.darken(.25f)), center = c + Offset(r * .15f, r * .15f), radius = r * .95f),
        r * .88f, c
    )
    // sombra interna projetada pela parede superior/esquerda
    drawCircle(
        Brush.linearGradient(listOf(Color.Black.copy(alpha = .55f), Color.Transparent), a, c + Offset(r * .2f, r * .2f)),
        r * .9f, c, style = Stroke(r * .2f)
    )
}

private fun DrawScope.drawScrew(c: Offset, r: Float) {
    drawCircle(Color.White.copy(alpha = .6f), r * 1.15f, c + Offset(r * .15f, r * .2f))
    drawCircle(Brush.linearGradient(listOf(Color(0xFF8C847D), Color(0xFFC9C1B9)), c - Offset(r, r), c + Offset(r, r)), r, c)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFE9E3DC), Color(0xFFB4ACA5)), center = c - Offset(r * .25f, r * .25f), radius = r), r * .7f, c)
    drawLine(Color(0xFF6F6862), c - Offset(r * .5f, -r * .1f), c + Offset(r * .5f, r * .1f), r * .22f)
}

/**
 * Carcaça + tela embutida. A tela em si (conteúdo Compose) é posicionada por quem chama,
 * nas mesmas coordenadas: centro horizontal e [screenCy] (fração da altura).
 */
@Composable internal fun ConsoleShell(modifier: Modifier, screenW: Dp, screenH: Dp, screenCy: Float) {
    Canvas(modifier) {
        val w = size.width; val h = size.height; val cr = h * .085f
        val px = 1.dp.toPx()
        val shape = RoundRect(0f, 0f, w, h, CornerRadius(cr))
        val shellPath = Path().apply { addRoundRect(shape) }

        // 1) sombra de contato/ambiente sob a carcaça (cai para baixo/direita)
        for (i in 1..10) {
            val g = i * h * .004f
            drawRoundRect(Color.Black.copy(alpha = .045f), Offset(-g + h * .006f, -g + h * .022f),
                Size(w + 2 * g, h + 2 * g), CornerRadius(cr + g))
        }
        // 2) corpo de plástico + varredura de luz vinda de cima/esquerda
        drawRoundRect(Brush.verticalGradient(listOf(ShellHi, ShellMid, ShellLow)), Offset.Zero, size, CornerRadius(cr))
        drawRoundRect(Brush.radialGradient(listOf(Color.White.copy(alpha = .26f), Color.Transparent), center = Offset(w * .18f, h * .08f), radius = w * .75f),
            Offset.Zero, size, CornerRadius(cr))
        // 3) faixa da dobradiça no topo (nível diferente da face)
        clipPath(shellPath) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF9F978F), Color(0xFFDDD6CF), Color(0xFF857D76))), Offset.Zero, Size(w, h * .05f))
            drawRect(Color.Black.copy(alpha = .22f), Offset(0f, h * .05f), Size(w, px * 1.5f))
            drawRect(Color.White.copy(alpha = .6f), Offset(0f, h * .05f + px * 1.5f), Size(w, px))
        }
        // 4) borda biselada: realce em cima/esquerda, sombra em baixo/direita
        val sw = h * .012f
        drawRoundRect(Brush.linearGradient(listOf(Color.White.copy(alpha = .95f), Color.Transparent, Color.Black.copy(alpha = .28f)), Offset.Zero, Offset(w, h)),
            Offset(sw / 2, sw / 2), Size(w - sw, h - sw), CornerRadius(cr - sw / 2), style = Stroke(sw))
        // 5) junta entre peças (sulco escuro + filete de luz logo abaixo)
        val inset = h * .03f
        drawRoundRect(Color.Black.copy(alpha = .17f), Offset(inset, inset + h * .04f), Size(w - 2 * inset, h - 2 * inset - h * .04f), CornerRadius(cr * .8f), style = Stroke(px * 1.2f))
        drawRoundRect(Color.White.copy(alpha = .5f), Offset(inset + px * 1.3f, inset + h * .04f + px * 1.3f), Size(w - 2 * inset, h - 2 * inset - h * .04f), CornerRadius(cr * .8f), style = Stroke(px))

        // 6) tela embutida: rebaixo na carcaça -> moldura preta -> vidro (conteúdo)
        val sW = screenW.toPx(); val sH = screenH.toPx()
        val sx = w * .5f - sW / 2; val sy = h * screenCy - sH / 2
        val bezel = sH * .035f; val rec = sH * .05f
        val ox = sx - bezel - rec; val oy = sy - bezel - rec
        val ow = sW + 2 * (bezel + rec); val oh = sH + 2 * (bezel + rec)
        val rr = CornerRadius(sH * .075f)
        drawRoundRect(Color.White.copy(alpha = .6f), Offset(ox + px * 1.6f, oy + px * 1.8f), Size(ow, oh), rr)   // luz na borda baixo/direita
        drawRoundRect(Color.Black.copy(alpha = .28f), Offset(ox - px * 1.3f, oy - px * 1.3f), Size(ow, oh), rr)  // sombra na borda cima/esquerda
        drawRoundRect(Brush.linearGradient(listOf(Color(0xFF6B645E), Color(0xFFA29A93), Color(0xFFD2CAC2)), Offset(ox, oy), Offset(ox + ow, oy + oh)),
            Offset(ox, oy), Size(ow, oh), rr)
        drawRoundRect(Color(0xFF09090C), Offset(sx - bezel, sy - bezel), Size(sW + 2 * bezel, sH + 2 * bezel), CornerRadius(sH * .055f))
        drawRoundRect(Brush.linearGradient(listOf(Color.White.copy(alpha = .28f), Color.Transparent, Color.White.copy(alpha = .1f)), Offset(sx - bezel, sy - bezel), Offset(sx + sW + bezel, sy + sH + bezel)),
            Offset(sx - bezel, sy - bezel), Size(sW + 2 * bezel, sH + 2 * bezel), CornerRadius(sH * .055f), style = Stroke(px))

        // 7) grelhas dos alto-falantes e parafusos
        val gap = w * .011f; val slitW = w * .0055f
        for (side in 0..1) {
            val x0 = if (side == 0) w * .034f else w * .966f - 4 * gap
            for (i in 0..4) {
                val x = x0 + i * gap
                drawRoundRect(Color.White.copy(alpha = .6f), Offset(x + px, h * .845f + px), Size(slitW, h * .095f), CornerRadius(slitW / 2))
                drawRoundRect(Color(0xFF5B544F).copy(alpha = .8f), Offset(x, h * .845f), Size(slitW, h * .095f), CornerRadius(slitW / 2))
            }
        }
        val sr = h * .014f
        drawScrew(Offset(w * .034f, h * .5f), sr); drawScrew(Offset(w * .966f, h * .5f), sr)
        drawScrew(Offset(w * .034f, h * .09f), sr * .9f); drawScrew(Offset(w * .966f, h * .09f), sr * .9f)
    }
}
