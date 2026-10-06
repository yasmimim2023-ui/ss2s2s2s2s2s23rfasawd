package br.com.thorlink.ui

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.round
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.thorlink.domain.*

/** Posição do centro da tela pequena (fração da altura da carcaça). */
private const val SCREEN_CY = .47f

@Composable fun ThorScreen(vm: LinkViewModel,
    onScan: ()->Unit,onRefresh: ()->Unit,onPair: (Peer)->Unit,onConnect: (Peer)->Unit,onListen: ()->Unit,
    onBluetooth: ()->Unit,onDiscoverable: ()->Unit,onSelectFile: ()->Unit,onSaveFile: (FileOffer)->Unit,
    onCapture: ()->Unit,onStopCapture: ()->Unit,onAccessibility: ()->Unit,onLaunchTarget: ()->Unit,onAppSettings: ()->Unit,
    onNativeKey:(android.view.KeyEvent)->Boolean={false}) {
    val state by vm.state.collectAsStateWithLifecycle()
    // 0 = lista do MENU; 1..4 = painéis abertos dentro do MENU (Conectar, Arquivos, Histórico, Ajustes)
    var menuSub by remember { mutableStateOf(0) }
    // 0f = MENU DO SISTEMA, 1f = SEGUNDA TELA. Acompanha o dedo durante o arrasto.
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf(ControlState()) }
    var consent by remember { mutableStateOf(Grants()) }
    var agreed by remember { mutableStateOf(false) }
    var showCalibration by remember { mutableStateOf(false) }
    var showApps by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    val enabled = state.status==LinkStatus.CONNECTED && state.grants.controls && !vm.receiver &&
        (!state.grants.nativeGamepad || (state.hidConnected && state.controlHealth==ControlHealth.HID_CONNECTED))
    val extras=enabled && state.grants.nativeGamepad
    val testFocus=remember{FocusRequester()}
    val nativeTest=vm.receiver && state.status==LinkStatus.CONNECTED && state.grants.nativeGamepad
    val context = LocalContext.current
    fun goPage(target:Float){ scope.launch{ progress.animateTo(target,tween(240,easing=FastOutSlowInEasing)) } }
    fun openMenu(sub:Int){ menuSub=sub; goPage(0f) }
    fun blocked() {
        goPage(1f)   // a segunda tela mostra o diagnóstico do controle
        val reason = when {
            vm.receiver -> "Use os botões do ThorLink Controle no outro celular."
            state.status != LinkStatus.CONNECTED -> "Conecte e autorize a sessão nos dois celulares."
            !state.grants.controls -> "Reconecte e autorize os controles no receptor."
            else -> state.controlHealth.label
        }
        android.widget.Toast.makeText(context,reason,android.widget.Toast.LENGTH_LONG).show()
    }
    fun control(c:ControlState){input=c;vm.controls(c)}
    fun button(bit:Int,pressed:Boolean){control(input.copy(buttons=if(pressed)input.buttons or bit else input.buttons and bit.inv()))}
    fun auxBlocked(){if(state.status==LinkStatus.CONNECTED && !state.grants.nativeGamepad)
        android.widget.Toast.makeText(context,"Botões extras exigem o modo Gamepad Bluetooth HID no receptor.",android.widget.Toast.LENGTH_LONG).show()else blocked()}
    LaunchedEffect(state.code){consent=Grants();agreed=false}
    LaunchedEffect(enabled){if(!enabled){input=ControlState();vm.controls(input)}}
    LaunchedEffect(nativeTest){if(nativeTest)testFocus.requestFocus()}
    val dens = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().focusRequester(testFocus).onPreviewKeyEvent{onNativeKey(it.nativeKeyEvent)}.focusable(nativeTest)
        .background(Brush.radialGradient(listOf(Color(0xFF2E2A33),Color(0xFF0E0D12)))).safeDrawingPadding(),contentAlignment=Alignment.Center) {
        // Tudo abaixo deriva do tamanho da carcaça (proporção fixa), não de valores absolutos.
        val availW = maxWidth - 20.dp; val availH = maxHeight - 20.dp
        val shellW = availW.coerceAtMost(1100.dp).coerceAtMost(availH*1.95f)
        val shellH = shellW/1.95f
        val analog = (shellH*.32f).coerceAtMost(shellW*.16f)
        val direction = shellH*.27f
        val face = shellH*.33f
        val key = face*.40f
        val auxW = shellW*.062f; val auxH = auxW*.46f
        val screenW = shellW*.44f
        val screenH = (screenW*.78f).coerceAtMost(shellH*.70f)
        val uiScale = (shellW.value/760f).coerceIn(.8f,1.5f)
        Box(Modifier.size(shellW,shellH)) {
            ConsoleShell(Modifier.fillMaxSize(),screenW,screenH,SCREEN_CY)
            // ombros / bumpers no topo (mesmas funções de antes)
            At(shellW,shellH,.105f,.082f,auxW,auxH){AuxControl("L2",extras,Modifier.fillMaxSize(),::auxBlocked){button(Buttons.L2,it)}}
            At(shellW,shellH,.185f,.082f,auxW,auxH){AuxControl("L1",extras,Modifier.fillMaxSize(),::auxBlocked){button(Buttons.L1,it)}}
            At(shellW,shellH,.815f,.082f,auxW,auxH){AuxControl("R1",extras,Modifier.fillMaxSize(),::auxBlocked){button(Buttons.R1,it)}}
            At(shellW,shellH,.895f,.082f,auxW,auxH){AuxControl("R2",extras,Modifier.fillMaxSize(),::auxBlocked){button(Buttons.R2,it)}}
            // esquerda: analógico em cima, direcional embaixo
            At(shellW,shellH,.135f,.33f,analog){AnalogStick("Analógico esquerdo",enabled,Modifier.fillMaxSize(),onBlocked=::blocked){x,y->control(input.copy(lx=x,ly=y))}}
            At(shellW,shellH,.165f,.735f,direction){DPad(enabled,Modifier.fillMaxSize(),onBlocked=::blocked){bits->control(input.copy(buttons=(input.buttons and 0xf0.inv())or bits))}}
            // direita: ABXY em cima, analógico embaixo
            At(shellW,shellH,.865f,.31f,face){
                PressControl("X",enabled,Color(0xFF2355B8),Modifier.size(key).align(Alignment.TopCenter),onBlocked=::blocked){button(Buttons.X,it)}
                PressControl("Y",enabled,Color(0xFF298E5D),Modifier.size(key).align(Alignment.CenterStart),onBlocked=::blocked){button(Buttons.Y,it)}
                PressControl("A",enabled,Color(0xFFC22E3A),Modifier.size(key).align(Alignment.CenterEnd),onBlocked=::blocked){button(Buttons.A,it)}
                PressControl("B",enabled,Color(0xFFF1BE29),Modifier.size(key).align(Alignment.BottomCenter),onBlocked=::blocked){button(Buttons.B,it)}
            }
            At(shellW,shellH,.835f,.735f,analog){AnalogStick("Analógico direito",enabled,Modifier.fillMaxSize(),onBlocked=::blocked){x,y->control(input.copy(rx=x,ry=y))}}
            // faixa inferior: L3 · SELECT · marca · START · R3
            val small = auxW*.62f; val smallH = auxH*.9f
            At(shellW,shellH,.285f,.925f,small,smallH){AuxControl("L3",extras,Modifier.fillMaxSize(),::auxBlocked){button(Buttons.L3,it)}}
            At(shellW,shellH,.385f,.925f,auxW*1.05f,auxH*.95f){AuxControl("SELECT",extras,Modifier.fillMaxSize(),::auxBlocked){button(Buttons.SELECT,it)}}
            At(shellW,shellH,.5f,.925f,shellW*.12f,shellH*.06f){
                Text("THORLINK",Modifier.align(Alignment.Center),color=Color(0xFF8A8178),fontSize=(shellH.value*.026f).sp,fontWeight=FontWeight.Black,letterSpacing=2.sp,
                    style=androidx.compose.ui.text.TextStyle(shadow=Shadow(Color.White.copy(alpha=.6f),Offset(1f,1.2f),0f)))
            }
            At(shellW,shellH,.615f,.925f,auxW*1.05f,auxH*.95f){AuxControl("START",extras,Modifier.fillMaxSize(),::auxBlocked){button(Buttons.START,it)}}
            At(shellW,shellH,.715f,.925f,small,smallH){AuxControl("R3",extras,Modifier.fillMaxSize(),::auxBlocked){button(Buttons.R3,it)}}
            // tela central embutida
            At(shellW,shellH,.5f,SCREEN_CY,screenW,screenH){
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(screenH*.03f)).background(DisplayBg).drawWithContent{
                    drawContent()
                    // vidro: reflexo difuso de cima/esquerda + sombra interna da moldura
                    drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha=.09f),Color.Transparent),Offset.Zero,Offset(size.width*.65f,size.height*.65f)))
                    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha=.45f),Color.Transparent),0f,size.height*.07f))
                    drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha=.30f),Color.Transparent),0f,size.width*.04f))
                }) {
                    CompositionLocalProvider(LocalDensity provides androidx.compose.ui.unit.Density(dens.density*uiScale,dens.fontScale)) {
                        DualScreen(state,vm.receiver,progress,vm::disconnect,{goPage(it)},
                            menu={MenuPage(state,vm.receiver,menuSub,{menuSub=it},{goPage(1f)},{showPrivacy=true},
                                device={DevicePanel(state,vm.receiver,onScan,onRefresh,onPair,onConnect,onListen,onBluetooth,onDiscoverable,vm::disconnect)},
                                files={FilePanel(state,onSelectFile,vm.repo::cancelTransfer)},
                                history={HistoryPanel(state,vm.repo::forget,vm.repo::clearHistory)},
                                settings={SettingsPanel(state,vm.receiver,{showApps=true},{showCalibration=true},onAccessibility,{showPrivacy=true},onAppSettings,vm.repo::setControlMode,vm.repo::startNativeGamepad)})},
                            second={Dashboard(state,vm.receiver,{openMenu(1)},{openMenu(2)},onCapture,onStopCapture,onLaunchTarget,vm.repo::startNativeGamepad)})
                    }
                }
            }
        }
    }
    if(state.status==LinkStatus.VERIFYING) ConsentDialog(state,vm.receiver,consent,{consent=it},agreed,{agreed=it},{vm.confirm(consent)},vm::disconnect)
    state.offer?.let { offer -> AlertDialog(onDismissRequest=vm.repo::rejectFile,title={Text("Receber arquivo?")},
        text={Text("${offer.name}\n${formatBytes(offer.size)}\nEscolha manualmente onde salvar. Nada é recebido antes dessa escolha.")},
        confirmButton={TextButton(onClick={onSaveFile(offer)}){Text("Escolher destino")}},dismissButton={TextButton(onClick=vm.repo::rejectFile){Text("Recusar")}}) }
    if(showApps) AppPicker({showApps=false}){pkg,label->vm.repo.setTarget(pkg,label);showApps=false}
    if(showCalibration) CalibrationDialog(state.mapping,{showCalibration=false}){vm.repo.setMapping(it);showCalibration=false}
    if(showPrivacy) AlertDialog(onDismissRequest={showPrivacy=false},title={Text("Você controla a sessão")},
        text={Text("Sem internet, anúncios ou telemetria. Acesso somente a aparelhos pareados e aprovados nos dois lados. Arquivos usam o seletor do Android. A captura exige o diálogo do sistema em cada sessão e não inclui áudio. O modo HID envia gamepad ao app em primeiro plano, conforme consentimento específico. O modo de gestos usa Acessibilidade apenas no app escolhido, sem ler conteúdo.\n\nAutorizações não dispensam a aprovação de uma nova sessão. Desconectar revoga todos os acessos ativos. Não há inicialização automática após reiniciar.")},
        confirmButton={TextButton(onClick={showPrivacy=false}){Text("Entendi")}})
}

/** Posiciona um componente pelo centro, em fração do tamanho da carcaça. */
@Composable private fun At(shellW:Dp,shellH:Dp,cx:Float,cy:Float,w:Dp,h:Dp=w,content:@Composable BoxScope.()->Unit){
    Box(Modifier.offset(shellW*cx-w/2,shellH*cy-h/2).size(w,h),content=content)
}

/**
 * Tela pequena de duas páginas, estilo tela inferior de um portátil de duas telas.
 * Arrastar para a DIREITA = MENU; arrastar para a ESQUERDA = SEGUNDA TELA.
 * A página acompanha o dedo; ao soltar conclui ou volta, conforme a distância.
 */
@Composable private fun DualScreen(s:UiState,receiver:Boolean,progress:Animatable<Float,AnimationVector1D>,onDisconnect:()->Unit,
    onGoto:(Float)->Unit,menu:@Composable ()->Unit,second:@Composable ()->Unit){
    val scope=rememberCoroutineScope()
    Column(Modifier.fillMaxSize()){
        StatusBar(s,receiver,onDisconnect)
        Box(Modifier.weight(1f).fillMaxWidth().testTag("tela-central").pointerInput(Unit){
            var dir=0f
            fun settle(){
                val p=progress.value
                val target=when{dir<0f&&p>.22f->1f;dir>0f&&p<.78f->0f;else->round(p)}
                scope.launch{progress.animateTo(target,tween(200,easing=FastOutSlowInEasing))}
            }
            detectHorizontalDragGestures(
                onDragStart={dir=0f;scope.launch{progress.stop()}},
                onDragEnd={settle()},onDragCancel={settle()},
                onHorizontalDrag={change,amount->
                    change.consume();dir=amount
                    val w=size.width.toFloat().coerceAtLeast(1f)
                    scope.launch{progress.snapTo((progress.value-amount/w).coerceIn(0f,1f))}
                })
        }) {
            Box(Modifier.fillMaxSize().graphicsLayer{translationX=-progress.value*size.width}){menu()}
            Box(Modifier.fillMaxSize().graphicsLayer{translationX=(1f-progress.value)*size.width}){second()}
        }
        PageDots(progress,onGoto)
    }
}
@Composable private fun PageDots(progress:Animatable<Float,AnimationVector1D>,onGoto:(Float)->Unit){
    Row(Modifier.fillMaxWidth().height(18.dp).background(Color(0xFF101018)),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){
        listOf("MENU" to 0f,"2ª TELA" to 1f).forEach{(label,t)->
            val a=1f-abs(progress.value-t).coerceAtMost(1f)
            Text(label,fontSize=7.sp,letterSpacing=.8.sp,color=lerp(Color(0xFF5C5C6C),Accent,a),
                modifier=Modifier.clickable{onGoto(t)}.padding(horizontal=10.dp,vertical=4.dp))
        }
    }
}
@Composable private fun MenuPage(s:UiState,receiver:Boolean,sub:Int,onSub:(Int)->Unit,onVisor:()->Unit,onPrivacy:()->Unit,
    device:@Composable ()->Unit,files:@Composable ()->Unit,history:@Composable ()->Unit,settings:@Composable ()->Unit){
    if(sub==0){
        Column(Modifier.fillMaxSize().padding(8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Text("THORLINK",fontSize=14.sp,fontWeight=FontWeight.Black,fontStyle=androidx.compose.ui.text.font.FontStyle.Italic,letterSpacing=1.sp,color=Accent)
                    Text("MENU DO SISTEMA",fontSize=6.sp,letterSpacing=1.2.sp,color=Color(0xFF6E6D7E))
                }
                InfoChip("RTT",s.rttMs?.let{"$it ms"}?:"—");Spacer(Modifier.width(4.dp))
                InfoChip("BAT",s.deviceInfo.battery?.let{"$it%"}?:"—");Spacer(Modifier.width(4.dp))
                InfoChip("CANAL",if(s.bulkReady)"2 RFCOMM"else"0")
            }
            Row(Modifier.weight(1f).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                MenuTile(Icons.Rounded.Gamepad,"Visor",Modifier.weight(1f).fillMaxHeight(),onVisor)
                MenuTile(Icons.Rounded.Bluetooth,"Conectar",Modifier.weight(1f).fillMaxHeight()){onSub(1)}
                MenuTile(Icons.Rounded.Folder,"Arquivos",Modifier.weight(1f).fillMaxHeight()){onSub(2)}
            }
            Row(Modifier.weight(1f).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                MenuTile(Icons.Rounded.History,"Histórico",Modifier.weight(1f).fillMaxHeight()){onSub(3)}
                MenuTile(Icons.Rounded.Settings,"Ajustes",Modifier.weight(1f).fillMaxHeight()){onSub(4)}
                MenuTile(Icons.Rounded.PrivacyTip,"Privacidade",Modifier.weight(1f).fillMaxHeight(),onPrivacy)
            }
        }
    }else Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().height(26.dp).padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick={onSub(0)},Modifier.size(26.dp)){Icon(Icons.Rounded.ChevronLeft,"Voltar ao menu",tint=Accent,modifier=Modifier.size(18.dp))}
            Text(when(sub){1->"CONECTAR";2->"ARQUIVOS";3->"HISTÓRICO";else->"AJUSTES"},fontSize=8.sp,letterSpacing=1.sp,color=Color(0xFF84828E))
        }
        Box(Modifier.weight(1f).fillMaxWidth()){when(sub){1->device();2->files();3->history();else->settings()}}
    }
}
@Composable private fun MenuTile(icon:ImageVector,label:String,modifier:Modifier,onClick:()->Unit){
    val shape=RoundedCornerShape(9.dp)
    Column(modifier.clip(shape).background(Brush.verticalGradient(listOf(Color(0xFF242435),Color(0xFF15151F)))).border(1.dp,Color(0xFF34344A),shape).clickable(onClick=onClick),
        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
        Icon(icon,null,tint=Accent,modifier=Modifier.size(20.dp));Spacer(Modifier.height(3.dp))
        Text(label,fontSize=8.sp,color=Color(0xFFCFCCDB),maxLines=1)
    }
}
@Composable private fun InfoChip(label:String,value:String){
    Column(Modifier.background(Color(0xFF15151F),RoundedCornerShape(5.dp)).padding(horizontal=6.dp,vertical=3.dp)){
        Text(label,fontSize=5.sp,color=Color(0xFF77758A));Text(value,fontSize=8.sp,fontWeight=FontWeight.Bold,maxLines=1)
    }
}
@Composable private fun StatusBar(s:UiState,receiver:Boolean,disconnect:()->Unit){
    val color by animateColorAsState(if(s.status==LinkStatus.CONNECTED)Color(0xFF6CDFB4)else if(s.status==LinkStatus.ERROR)Color(0xFFF0778D)else Accent,label="status")
    Row(Modifier.fillMaxWidth().height(31.dp).padding(start=10.dp,end=5.dp),verticalAlignment=Alignment.CenterVertically){
        Box(Modifier.size(5.dp).background(color,CircleShape));Spacer(Modifier.width(5.dp));Text(s.status.label,color=color,fontSize=9.sp,maxLines=1,modifier=Modifier.weight(1f))
        Text(if(receiver)"RECEPTOR"else"CONTROLE",color=Color(0xFF84828E),fontSize=7.sp,letterSpacing=1.sp)
        if(s.status in listOf(LinkStatus.CONNECTED,LinkStatus.LISTENING,LinkStatus.CONNECTING,LinkStatus.VERIFYING))IconButton(onClick=disconnect,Modifier.size(28.dp)){Icon(Icons.Rounded.Close,"Desconectar imediatamente",tint=Color(0xFFF48695),modifier=Modifier.size(16.dp))}
    }
}
@Composable private fun Dashboard(s:UiState,receiver:Boolean,onConnect:()->Unit,onFiles:()->Unit,onCapture:()->Unit,onStop:()->Unit,onLaunch:()->Unit,onNativeStart:()->Unit){
    if(s.frame!=null&&!receiver){Box(Modifier.fillMaxSize()){Image(s.frame.asImageBitmap(),"Tela autorizada do receptor",Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
        Text("BT • ${s.rttMs?.let{"$it ms RTT"}?:"medindo"}",Modifier.align(Alignment.TopEnd).padding(5.dp).background(Color.Black.copy(alpha=.65f)).padding(4.dp),color=Color.White,fontSize=8.sp)
        ControlDiagnostic(s,false,Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha=.8f)).padding(6.dp))};return}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=13.dp,vertical=4.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
            Column{Text("THORLINK",fontSize=19.sp,fontWeight=FontWeight.Black,fontStyle=androidx.compose.ui.text.font.FontStyle.Italic,letterSpacing=1.sp,color=Accent);Text("BLUETOOTH CONSOLE",fontSize=7.sp,letterSpacing=1.5.sp,color=Color(0xFF6E6D7E))}
            Icon(Icons.Rounded.VerifiedUser,"Sessão com consentimento",tint=Color(0xFF64CCC1),modifier=Modifier.size(22.dp))
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Metric("COMANDO RTT",s.rttMs?.toString()?:"—","ms",Modifier.weight(1f))
            Metric("BATERIA",s.deviceInfo.battery?.toString()?:"—","%",Modifier.weight(1f))
            Metric("CANAL",if(s.bulkReady)"2"else"0","RFCOMM",Modifier.weight(1f))
        }
        if(s.status==LinkStatus.CONNECTED)ControlDiagnostic(s,receiver,Modifier.fillMaxWidth())
        if(receiver&&s.status==LinkStatus.CONNECTED&&s.grants.controls){
            ControlTest(if(s.grants.nativeGamepad)s.nativeControl else s.control,Modifier.fillMaxWidth().height(100.dp))
            Text(if(s.grants.nativeGamepad)"Entradas NATIVAS recebidas neste visor: ${s.nativeInputCount}. Teste aqui antes de abrir o jogo."
                else if(s.targetPackage.isEmpty())"Teste ativo • mova os controles no outro celular"else"${s.targetLabel} • ${if(s.foregroundTarget)"gestos liberados"else"abra o app escolhido para liberar gestos"}",fontSize=9.sp,color=Color(0xFF9D9BAC))
            if(s.targetPackage.isNotEmpty())SmallButton("Abrir ${s.targetLabel}",onLaunch)
        }else{
            Text(if(s.status==LinkStatus.CONNECTED)s.remoteName else "SEU SEGUNDO CELULAR.\nSEU CONTROLE.",fontSize=13.sp,fontWeight=FontWeight.Bold,color=Color(0xFFE5E3EE))
            Text(s.message,fontSize=9.sp,lineHeight=13.sp,color=Color(0xFF9795A9))
        }
        s.deviceInfo.model?.let{Text(it,fontSize=9.sp,color=Color(0xFF70DAD5))}
        Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
            if(s.status!=LinkStatus.CONNECTED)SmallButton(if(receiver)"Preparar receptor"else"Conectar aparelho",onConnect,Modifier.weight(1f))
            else if(receiver&&s.grants.screen)SmallButton(if(s.screenActive)"Parar tela"else"Compartilhar tela",if(s.screenActive)onStop else onCapture,Modifier.weight(1f),s.bulkReady)
            if(s.status==LinkStatus.CONNECTED&&s.grants.files)SmallButton("Arquivos",onFiles,Modifier.weight(1f))
        }
        if(!receiver && s.status==LinkStatus.CONNECTED && s.grants.nativeGamepad && !s.hidConnected)
            SmallButton("Iniciar gamepad Bluetooth",onNativeStart,Modifier.fillMaxWidth())
        if(!receiver&&s.status==LinkStatus.CONNECTED&&s.grants.screen)Text("Aguardando o receptor tocar em Compartilhar tela e aceitar o diálogo do Android.",fontSize=8.sp,color=Color(0xFF777588))
        Text("SEM NUVEM  •  SEM COLETA OCULTA",fontSize=6.sp,letterSpacing=.9.sp,color=Color(0xFF4F4F61))
    }
}
@Composable internal fun ControlDiagnostic(s:UiState,receiver:Boolean,modifier:Modifier=Modifier){
    val title = if(!s.grants.controls)"Controles não autorizados • reconecte e autorize no receptor"
        else s.controlHealth.label
    Column(modifier,verticalArrangement=Arrangement.spacedBy(3.dp)){
        Text(title,fontSize=9.sp,lineHeight=12.sp,color=if(s.controlHealth in setOf(ControlHealth.READY,ControlHealth.ANDROID_COMPLETED,ControlHealth.TEST_MODE,ControlHealth.HID_CONNECTED)&&s.grants.controls)Color(0xFF70DAD5)else Color(0xFFF2C184))
        if(s.grants.controls) {
            val names = listOf(Buttons.A to "A",Buttons.B to "B",Buttons.X to "X",Buttons.Y to "Y",
                Buttons.UP to "↑",Buttons.DOWN to "↓",Buttons.LEFT to "←",Buttons.RIGHT to "→",Buttons.L1 to "L1",Buttons.R1 to "R1",
                Buttons.L2 to "L2",Buttons.R2 to "R2",Buttons.SELECT to "SELECT",Buttons.START to "START",Buttons.L3 to "L3",Buttons.R3 to "R3")
                .filter { (bit,_) -> s.lastButtons and bit != 0 }.joinToString(" + "){it.second}.ifEmpty{"—"}
            Text("${if(receiver)"Recebidos"else"Confirmados pelo receptor"}: ${s.inputCount} • último botão: $names",fontSize=8.sp,color=Color(0xFFB6B1C4))
            if(s.grants.nativeGamepad && !receiver)Text("Relatórios enviados ao Bluetooth HID: ${s.hidReports}",fontSize=8.sp,color=Color(0xFFB6B1C4))
        }
    }
}
@Composable private fun Metric(label:String,value:String,unit:String,modifier:Modifier){Column(modifier.background(Color(0xFF15151F),RoundedCornerShape(6.dp)).padding(8.dp)){Text(label,fontSize=6.sp,color=Color(0xFF77758A));Row(verticalAlignment=Alignment.Bottom){Text(value,fontSize=23.sp,fontWeight=FontWeight.Bold);Text(unit,fontSize=7.sp,color=Accent,modifier=Modifier.padding(start=3.dp,bottom=4.dp))}}}
@Composable private fun ControlTest(c:ControlState,modifier:Modifier){Canvas(modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF10131D))){
    drawLine(Color(0xFF242B3A),Offset(size.width/2,0f),Offset(size.width/2,size.height));drawLine(Color(0xFF242B3A),Offset(0f,size.height/2),Offset(size.width,size.height/2))
    drawCircle(Accent,10.dp.toPx(),Offset(size.width*.25f+c.lx*size.width*.18f,size.height*.5f+c.ly*size.height*.35f))
    drawCircle(Color(0xFF70DAD5),10.dp.toPx(),Offset(size.width*.75f+c.rx*size.width*.18f,size.height*.5f+c.ry*size.height*.35f))
    for(i in 0..3)drawCircle(if(c.buttons and (1 shl i)!=0)Color(0xFFF7C74A)else Color(0xFF3B3D51),4.dp.toPx(),Offset(size.width*.43f+i*size.width*.05f,size.height*.9f))
}}
@Composable private fun SmallButton(label:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true){Button(onClick,modifier.heightIn(min=34.dp),enabled=enabled,shape=RoundedCornerShape(6.dp),contentPadding=PaddingValues(horizontal=8.dp,vertical=3.dp)){Text(label,fontSize=9.sp,maxLines=2,textAlign=TextAlign.Center)}}
@Composable private fun PanelTitle(title:String,detail:String?=null){Text(title,fontSize=15.sp,fontWeight=FontWeight.Bold);detail?.let{Text(it,fontSize=9.sp,color=Color(0xFF9C99AA),lineHeight=13.sp)}}
@Composable private fun DevicePanel(s:UiState,receiver:Boolean,onScan:()->Unit,onRefresh:()->Unit,onPair:(Peer)->Unit,onConnect:(Peer)->Unit,onListen:()->Unit,onBluetooth:()->Unit,onDiscoverable:()->Unit,onDisconnect:()->Unit){
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(11.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
        PanelTitle("${if(receiver)"Receber conexão"else"Dispositivos Bluetooth"}",s.message)
        Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){SmallButton("Ativar BT",onBluetooth,Modifier.weight(1f));SmallButton("Buscar",onScan,Modifier.weight(1f));SmallButton("Pareados",onRefresh,Modifier.weight(1f))}
        if(receiver)Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){SmallButton("Visível por 2 min",onDiscoverable,Modifier.weight(1f));SmallButton("Receber",onListen,Modifier.weight(1f),s.status!=LinkStatus.CONNECTED)}
        if(s.status==LinkStatus.DISCOVERING)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(s.peers.isEmpty())Text("Toque em Buscar. No receptor, torne o aparelho visível. Pareie pelo diálogo do Android antes de conectar.",fontSize=9.sp,lineHeight=13.sp,color=Color(0xFF9F9BAB))
        s.peers.forEach{peer->Row(Modifier.fillMaxWidth().background(Color(0xFF171720),RoundedCornerShape(6.dp)).padding(7.dp),verticalAlignment=Alignment.CenterVertically){
            Icon(Icons.Rounded.PhoneAndroid,null,Modifier.size(19.dp),tint=Accent);Column(Modifier.weight(1f).padding(horizontal=7.dp)){Text(peer.name,fontSize=10.sp,maxLines=1,overflow=TextOverflow.Ellipsis);Text(if(peer.bonded)"Pareado no Android"else"Pareamento necessário",fontSize=7.sp,color=Color(0xFF8D889C))}
            if(!peer.bonded)SmallButton("Parear",{onPair(peer)})else if(!receiver)SmallButton("Conectar",{onConnect(peer)},enabled=s.status !in listOf(LinkStatus.CONNECTED,LinkStatus.CONNECTING,LinkStatus.VERIFYING))
        }}
        if(s.status in listOf(LinkStatus.CONNECTED,LinkStatus.LISTENING,LinkStatus.CONNECTING))SmallButton("Desconectar / parar",onDisconnect,Modifier.fillMaxWidth())
    }
}
@Composable private fun FilePanel(s:UiState,onSelect:()->Unit,onCancel:()->Unit){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
    PanelTitle("Transferir arquivos","Seleção manual no envio e confirmação de destino no outro aparelho.")
    Icon(Icons.Rounded.FolderCopy,null,tint=Accent,modifier=Modifier.size(32.dp))
    Text(s.transfer.label,fontSize=10.sp)
    if(s.transfer.active){if(s.transfer.total>0)LinearProgressIndicator(progress={s.transfer.done.toFloat()/s.transfer.total},modifier=Modifier.fillMaxWidth());Text("${formatBytes(s.transfer.done)} / ${formatBytes(s.transfer.total)}",fontSize=8.sp,color=Accent);SmallButton("Cancelar",onCancel)}
    else SmallButton("Escolher imagem, vídeo ou documento",onSelect,Modifier.fillMaxWidth(),s.status==LinkStatus.CONNECTED&&s.grants.files&&s.bulkReady)
    Text("AES-GCM • verificação SHA-256 • limite 1 GiB\nA imagem é pausada durante o envio. Os controles usam o canal separado.",fontSize=8.sp,lineHeight=12.sp,color=Color(0xFF898499))
}}
@Composable private fun HistoryPanel(s:UiState,onForget:(AuthorizedPeer)->Unit,onClear:()->Unit){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(11.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
    PanelTitle("Autorizações locais","Remover encerra a sessão ativa. O pareamento do Android é removido nas configurações do sistema.")
    s.authorized.forEach{peer->Row(Modifier.fillMaxWidth().background(Color(0xFF171720),RoundedCornerShape(6.dp)).padding(6.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(peer.name,fontSize=10.sp);Text(peer.fingerprint.take(16).chunked(4).joinToString(" "),fontSize=7.sp,color=Accent)};IconButton(onClick={onForget(peer)},modifier=Modifier.size(30.dp)){Icon(Icons.Rounded.Delete,"Remover autorização",Modifier.size(16.dp))}}}
    if(s.authorized.isEmpty())Text("Nenhum aparelho autorizado.",fontSize=9.sp,color=Color(0xFF938CA3))
    Text("SESSÕES RECENTES",fontSize=8.sp,color=Accent,letterSpacing=1.sp)
    s.history.forEach{h->Column{Text("${java.text.SimpleDateFormat("dd/MM HH:mm",java.util.Locale.getDefault()).format(java.util.Date(h.time))} • ${h.name}",fontSize=9.sp);Text(h.result,fontSize=8.sp,color=Color(0xFF8E879F))}}
    SmallButton("Limpar histórico",onClear)
}}
@Composable private fun SettingsPanel(s:UiState,receiver:Boolean,onApps:()->Unit,onCalibrate:()->Unit,onAccessibility:()->Unit,onPrivacy:()->Unit,onSettings:()->Unit,onMode:(ControlMode)->Unit,onNativeStart:()->Unit){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(11.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
    PanelTitle("Controle e segurança")
    if(receiver){
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
            FilterChip(selected=s.controlMode==ControlMode.HID,onClick={onMode(ControlMode.HID)},label={Text("Gamepad HID",fontSize=9.sp)})
            FilterChip(selected=s.controlMode==ControlMode.GESTURES,onClick={onMode(ControlMode.GESTURES)},label={Text("Toques",fontSize=9.sp)})
        }
        Text(if(s.controlMode==ControlMode.HID)"Entrada nativa de gamepad. Sem Acessibilidade. Atua no app em primeiro plano; mudar o modo exige nova aprovação."
            else "Gestos somente no app escolhido; exige Acessibilidade. Mudar o modo exige nova aprovação.",fontSize=9.sp,lineHeight=12.sp,color=Color(0xFF9790A5))
        Text("APP PARA ABRIR: ${s.targetLabel}",fontSize=9.sp,color=Accent);SmallButton("Escolher aplicativo",onApps,Modifier.fillMaxWidth())
        if(s.controlMode==ControlMode.GESTURES){SmallButton("Calibrar joystick e botões",onCalibrate,Modifier.fillMaxWidth());SmallButton(if(s.touchAvailable)"Acessibilidade ativa • revisar"else"Ativar controle por gestos",onAccessibility,Modifier.fillMaxWidth())}
    }
    else Text("O receptor escolhe o modo. HID usa botões e eixos nativos, com envio a cada 8 ms quando necessário. Toques usam coordenadas calibradas. RTT mede o canal da sessão.",fontSize=9.sp,lineHeight=13.sp,color=Color(0xFF9992A7))
    if(!receiver && s.grants.nativeGamepad)SmallButton("Iniciar gamepad Bluetooth",onNativeStart,Modifier.fillMaxWidth())
    SmallButton("Privacidade e consentimento",onPrivacy,Modifier.fillMaxWidth());SmallButton("Permissões do Android",onSettings,Modifier.fillMaxWidth())
    Text("ThorLink ${br.com.thorlink.BuildConfig.VERSION_NAME}",fontSize=8.sp,color=Color(0xFF82778F))
}}
fun formatBytes(n:Long):String=when{n>=1_073_741_824L->"%.2f GiB".format(n/1_073_741_824.0);n>=1_048_576->"%.1f MiB".format(n/1_048_576.0);n>=1024->"%.1f KiB".format(n/1024.0);else->"$n B"}
