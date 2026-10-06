package br.com.thorlink.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import br.com.thorlink.domain.*
import kotlin.math.*

/** Shoulder/start/stick-click keys; press lifetime supports holding together with the main controls. */
@Composable fun AuxControl(label:String,enabled:Boolean,modifier:Modifier=Modifier,onBlocked:()->Unit={},onPressed:(Boolean)->Unit) {
    var pressed by remember{mutableStateOf(false)}
    val callback by rememberUpdatedState(onPressed)
    val blocked by rememberUpdatedState(onBlocked)
    val p by animateFloatAsState(if(pressed)1f else 0f,tween(60),label="aux")
    DisposableEffect(enabled){onDispose{callback(false)}}
    BoxWithConstraints(modifier.semantics{contentDescription="Botão $label";if(!enabled)disabled()}
        .pointerInput(enabled){detectTapGestures(onPress={
            if(!enabled)blocked() else {
                pressed=true;callback(true)
                try{tryAwaitRelease()}finally{pressed=false;callback(false)}
            }
        })},contentAlignment=Alignment.Center){
        val fs=(minOf(maxWidth,maxHeight).value*.36f).coerceIn(5f,11f)
        Canvas(Modifier.fillMaxSize()){
            // A área de toque é o Box inteiro; o desenho fica um pouco recuado dela.
            val inset=min(size.width,size.height)*.08f
            val w=size.width-2*inset; val h=size.height-2*inset
            val corner=CornerRadius(h*.5f)
            // fenda na carcaça (parede de cima na sombra, de baixo iluminada)
            drawRoundRect(Color.White.copy(alpha=.55f),Offset(inset+1.2f,inset+1.6f),Size(w,h),corner)
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF5E5753),Color(0xFFA8A099)),inset,inset+h),Offset(inset,inset),Size(w,h),corner)
            // tecla
            val kw=w-h*.14f; val kh=h*.74f
            val kx=inset+h*.07f; val ky=inset+h*.06f+h*.09f*p
            if(p<1f)drawRoundRect(Color.Black.copy(alpha=.30f*(1f-p)),Offset(kx+h*.03f,ky+h*.08f),Size(kw,kh),CornerRadius(kh*.5f))
            val top=if(p>.5f)Color(0xFFA698B4) else Color(0xFFD4CBC6)
            val bottom=if(p>.5f)Color(0xFF857794) else Color(0xFFA79E9A)
            drawRoundRect(Brush.verticalGradient(listOf(top,bottom),ky,ky+kh),Offset(kx,ky),Size(kw,kh),CornerRadius(kh*.5f))
            drawRoundRect(Color.White.copy(alpha=.55f*(1f-p)+.15f),Offset(kx+kh*.25f,ky+kh*.08f),Size(kw-kh*.5f,kh*.1f),CornerRadius(kh*.05f))
        }
        Text(label,color=Color(0xFF514A52),fontSize=fs.sp,fontWeight=FontWeight.Bold,
            style=TextStyle(shadow=Shadow(Color.White.copy(alpha=.45f),Offset(0f,1f),0f)),
            modifier=Modifier.graphicsLayer{translationY=size.height*.1f*p})
    }
}

/** Independent pointer input per control allows simultaneous L/R sticks and ABXY presses. */
@Composable fun AnalogStick(label: String, enabled: Boolean, modifier: Modifier, onBlocked: () -> Unit = {}, onAxis: (Float,Float) -> Unit) {
    var vector by remember { mutableStateOf(Offset.Zero) }
    var touching by remember { mutableStateOf(false) }
    val callback by rememberUpdatedState(onAxis)
    val blocked by rememberUpdatedState(onBlocked)
    val t by animateFloatAsState(if (touching) 1f else 0f, tween(80), label = "stick")
    DisposableEffect(enabled) { onDispose { callback(0f,0f) } }
    Canvas(modifier.semantics { contentDescription = label; if (!enabled) disabled() }
        .pointerInput(enabled) {
            awaitPointerEventScope {
                while (true) {
                    val down = awaitPointerEvent().changes.firstOrNull { it.pressed && !it.previousPressed } ?: continue
                    val id = down.id; down.consume()
                    if (!enabled) { blocked(); continue }
                    fun update(p: Offset) {
                        val center = Offset(size.width/2f,size.height/2f); val travel = min(size.width,size.height)*.25f
                        var v = (p-center)/travel; val length = v.getDistance(); if (length>1) v /= length
                        vector = v; callback(if (abs(v.x)<.08f) 0f else v.x,if (abs(v.y)<.08f) 0f else v.y)
                    }
                    touching = true
                    update(down.position)
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == id } ?: break
                            change.consume(); if (!change.pressed) break; update(change.position)
                        }
                    } finally { touching = false; vector = Offset.Zero; callback(0f,0f) }
                }
            }
        }) {
        val r = min(size.width,size.height)*.45f; val c = center
        // cavidade na carcaça
        drawWell(c, r)
        // três alturas: piso (sombra de contato) -> colar/base -> topo da haste
        val s = 1f - .035f * t
        val collar = c + Offset(vector.x * r * .14f, vector.y * r * .14f)
        val cap = c + Offset(vector.x * r * .30f, vector.y * r * .30f)
        softCircleShadow(collar + Offset(r * .05f, r * .10f), r * .60f, .55f)
        drawCircle(Brush.linearGradient(listOf(Color(0xFF6C6970), Color(0xFF28272D)), collar - Offset(r * .6f, r * .6f), collar + Offset(r * .6f, r * .6f)), r * .60f, collar)
        drawCircle(Color.White.copy(alpha = .16f), r * .60f, collar, style = Stroke(r * .02f))
        // sombra da haste sobre o colar: mais longa quando solta, curta quando pressionada
        softCircleShadow(cap + Offset(r * (.07f - .03f * t), r * (.14f - .05f * t)), r * .50f * s, .5f)
        val cr = r * .50f * s
        drawCircle(Brush.linearGradient(listOf(Color(0xFF908D95), Color(0xFF202027)), cap - Offset(cr, cr), cap + Offset(cr, cr)), cr, cap)
        drawCircle(Brush.radialGradient(listOf(Color(0xFF7C7982), Color(0xFF4B4951), Color(0xFF2E2D35)), center = cap - Offset(cr * .32f, cr * .32f), radius = cr), cr * .92f, cap)
        // concavidade do topo (invertida: sombra em cima/esquerda, luz em baixo/direita)
        drawCircle(Brush.linearGradient(listOf(Color(0xFF232228), Color(0xFF5A5860)), cap - Offset(cr * .6f, cr * .6f), cap + Offset(cr * .6f, cr * .6f)), cr * .60f, cap)
        drawCircle(Color(0xFF3B3A42), cr * .50f, cap)
        // textura discreta de aderência
        for (i in 0 until 14) {
            val a = i * 2f * PI.toFloat() / 14f
            drawCircle(Color.Black.copy(alpha = .35f), cr * .035f, cap + Offset(cos(a), sin(a)) * (cr * .74f))
        }
        drawArc(Color.White.copy(alpha = .30f), 195f, 85f, false, cap - Offset(cr * .86f, cr * .86f), Size(cr * 1.72f, cr * 1.72f),
            style = Stroke(r * .028f, cap = StrokeCap.Round))
    }
}

@Composable fun PressControl(label: String, enabled: Boolean, color: Color, modifier: Modifier, onBlocked: () -> Unit = {}, onPressed: (Boolean)->Unit) {
    var pressed by remember { mutableStateOf(false) }
    val callback by rememberUpdatedState(onPressed)
    val blocked by rememberUpdatedState(onBlocked)
    val p by animateFloatAsState(if (pressed) 1f else 0f, tween(70), label = "press")
    DisposableEffect(enabled) { onDispose { callback(false) } }
    BoxWithConstraints(modifier.semantics { contentDescription = "Botão $label"; if (!enabled) disabled() }
        .pointerInput(enabled) {
            awaitPointerEventScope {
                while (true) {
                    val down = awaitPointerEvent().changes.firstOrNull { it.pressed && !it.previousPressed } ?: continue
                    down.consume()
                    if (!enabled) { blocked(); continue }
                    pressed = true; callback(true)
                    try { while (true) { val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break; c.consume(); if (!c.pressed) break } }
                    finally { pressed = false; callback(false) }
                }
            }
        },contentAlignment=Alignment.Center) {
        val fs = (minOf(maxWidth, maxHeight).value * .34f).coerceIn(8f, 30f)
        Canvas(Modifier.fillMaxSize()) {
            val big = min(size.width,size.height) * .5f
            val seat = big * .90f
            val lift = 1f - p
            // assento rebaixado na carcaça
            drawWell(center, seat, Color(0xFF34312F))
            // botão: solto = mais alto (sombra longa, realce forte); pressionado = desce, sombra curta, realce fraco
            val rb = seat * .84f * (1f - .025f * p)
            val bc = center + Offset(-seat * .02f * lift, -seat * .05f * lift + seat * .045f * p)
            softCircleShadow(bc + Offset(seat * (.08f * lift + .01f), seat * (.14f * lift + .02f)), rb, .55f * lift + .22f)
            // parede lateral do cilindro (espessura visível só quando está alto)
            drawCircle(color.darken(.5f), rb, bc + Offset(0f, seat * .06f * lift))
            // face superior
            drawCircle(Brush.linearGradient(listOf(color.lighten(.30f), color, color.darken(.30f)), bc - Offset(rb, rb), bc + Offset(rb, rb)), rb, bc)
            drawCircle(Brush.linearGradient(listOf(Color.White.copy(alpha = .55f), Color.Transparent, Color.Black.copy(alpha = .3f)), bc - Offset(rb, rb), bc + Offset(rb, rb)),
                rb, bc, style = Stroke(rb * .045f))
            // leve concavidade no topo
            drawCircle(Brush.radialGradient(listOf(color.lighten(.12f + .10f * lift), color.darken(.12f)), center = bc - Offset(rb * .25f, rb * .25f), radius = rb * .75f), rb * .72f, bc)
            // reflexo discreto (esmaece quando pressionado)
            drawArc(Color.White.copy(alpha = .50f * lift + .12f), 200f, 75f, false, bc - Offset(rb * .86f, rb * .86f), Size(rb * 1.72f, rb * 1.72f),
                style = Stroke(rb * .05f, cap = StrokeCap.Round))
        }
        // letra impressa/gravada: tom mais escuro do botão + filete claro embaixo/direita
        Text(label, color = color.darken(.55f), fontSize = fs.sp, fontWeight = FontWeight.Black,
            style = TextStyle(shadow = Shadow(Color.White.copy(alpha = .40f), Offset(1f, 1.2f), 0f)),
            modifier = Modifier.graphicsLayer { translationX = -1.2f * (1f - p); translationY = -2f * (1f - p) + 1.5f * p })
    }
}

@Composable fun DPad(enabled: Boolean, modifier: Modifier, onBlocked: () -> Unit = {}, onDirection: (Int)->Unit) {
    var active by remember { mutableStateOf(0) }
    val callback by rememberUpdatedState(onDirection)
    val blocked by rememberUpdatedState(onBlocked)
    val tilt by animateFloatAsState(if (active != 0) 1f else 0f, tween(60), label = "dpad")
    DisposableEffect(enabled) { onDispose { callback(0) } }
    Canvas(modifier.semantics { contentDescription = "Direcional"; if (!enabled) disabled() }.pointerInput(enabled) {
        awaitPointerEventScope {
            while(true) {
                val down=awaitPointerEvent().changes.firstOrNull { it.pressed&&!it.previousPressed }?:continue
                if (!enabled) { down.consume(); blocked(); continue }
                fun move(p:Offset) {
                    val x=p.x-size.width/2; val y=p.y-size.height/2
                    active = if(hypot(x,y)<size.width*.10)0 else if(abs(x)>abs(y)){if(x<0)Buttons.LEFT else Buttons.RIGHT}else{if(y<0)Buttons.UP else Buttons.DOWN}
                    callback(active)
                }
                down.consume();move(down.position)
                try { while(true){val c=awaitPointerEvent().changes.firstOrNull {it.id==down.id}?:break;c.consume();if(!c.pressed)break;move(c.position)} }
                finally{active=0;callback(0)}
            }
        }
    }) {
        val w=min(size.width,size.height); val a=w*.145f; val b=w*.40f; val c=center
        val path=Path().apply{moveTo(c.x-a,c.y-b);lineTo(c.x+a,c.y-b);lineTo(c.x+a,c.y-a);lineTo(c.x+b,c.y-a);lineTo(c.x+b,c.y+a);lineTo(c.x+a,c.y+a);lineTo(c.x+a,c.y+b);lineTo(c.x-a,c.y+b);lineTo(c.x-a,c.y+a);lineTo(c.x-b,c.y+a);lineTo(c.x-b,c.y-a);lineTo(c.x-a,c.y-a);close()}
        drawWell(c,w*.45f)
        // a cruz se inclina um pouco para o lado pressionado
        val d=when(active){Buttons.UP->Offset(0f,-1f);Buttons.DOWN->Offset(0f,1f);Buttons.LEFT->Offset(-1f,0f);Buttons.RIGHT->Offset(1f,0f);else->Offset.Zero}
        val sx=d.x*w*.014f*tilt; val sy=d.y*w*.014f*tilt
        translate(sx,sy){
            translate(w*.032f*(1f-.55f*tilt),w*.048f*(1f-.55f*tilt)){drawPath(path,Color.Black.copy(alpha=.5f))}
            drawPath(path,Brush.linearGradient(listOf(Color(0xFF55535A),Color(0xFF1B1A1E)),c-Offset(b,b),c+Offset(b,b)))
            scale(.93f,c){drawPath(path,Brush.linearGradient(listOf(Color(0xFF64616A),Color(0xFF2C2B31)),c-Offset(b,b),c+Offset(b,b)))}
            drawPath(path,Brush.linearGradient(listOf(Color.White.copy(alpha=.45f),Color.Transparent,Color.Black.copy(alpha=.3f)),c-Offset(b,b),c+Offset(b,b)),style=Stroke(w*.009f))
            // setas gravadas
            val m=a*.42f; val k=b*.74f
            fun arrow(dir:Offset,bit:Int){
                val px=c+dir*k; val nx=Offset(-dir.y,dir.x)
                val tri=Path().apply{moveTo(px.x+dir.x*m,px.y+dir.y*m);lineTo(px.x-dir.x*m*.7f+nx.x*m,px.y-dir.y*m*.7f+nx.y*m);lineTo(px.x-dir.x*m*.7f-nx.x*m,px.y-dir.y*m*.7f-nx.y*m);close()}
                drawPath(tri,Color.White.copy(alpha=if(active==bit).55f else .22f))
            }
            arrow(Offset(0f,-1f),Buttons.UP);arrow(Offset(0f,1f),Buttons.DOWN);arrow(Offset(-1f,0f),Buttons.LEFT);arrow(Offset(1f,0f),Buttons.RIGHT)
            // concavidade central
            drawCircle(Brush.linearGradient(listOf(Color(0xFF1F1E23),Color(0xFF5C5A62)),c-Offset(a,a),c+Offset(a,a)),a*.95f,c)
            drawCircle(Color(0xFF302F36),a*.72f,c)
            if(active!=0)drawCircle(Color.White.copy(alpha=.10f),a*1.15f,c+d*(b*.62f))
        }
    }
}
