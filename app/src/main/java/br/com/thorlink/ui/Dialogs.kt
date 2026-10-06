package br.com.thorlink.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.thorlink.domain.*

@Composable fun ConsentDialog(s:UiState,receiver:Boolean,grants:Grants,onGrants:(Grants)->Unit,
    agreed:Boolean,onAgreed:(Boolean)->Unit,onConfirm:()->Unit,onReject:()->Unit){
    Dialog(onDismissRequest=onReject,properties=DialogProperties(usePlatformDefaultWidth=false)){
        Surface(Modifier.widthIn(max=660.dp).fillMaxWidth(.92f).fillMaxHeight(.94f),shape=RoundedCornerShape(20.dp)){
            Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("Autorizar ${s.remoteName}?",fontWeight=FontWeight.Bold,fontSize=18.sp)
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)){
                    Text(s.code.chunked(3).joinToString(" "),fontWeight=FontWeight.Black,fontSize=34.sp,color=Accent,letterSpacing=3.sp)
                    Text("Confira se os 6 dígitos são iguais nos dois celulares. Se forem diferentes, recuse.",fontSize=11.sp,modifier=Modifier.weight(1f))
                }
                Text("Identidade: ${s.fingerprint.chunked(4).joinToString(" ")}",fontSize=8.sp,color=Color(0xFF9F96B3))
                if(receiver){
                    Text("Escolha exatamente o que compartilhar nesta sessão:",fontSize=12.sp)
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                        Column(Modifier.weight(1f)){
                            GrantSwitch("Nome do dispositivo",grants.name,s.localConfirmed){onGrants(grants.copy(name=it))}
                            GrantSwitch("Modelo do aparelho",grants.model,s.localConfirmed){onGrants(grants.copy(model=it))}
                            GrantSwitch("Nível da bateria",grants.battery,s.localConfirmed){onGrants(grants.copy(battery=it))}
                        }
                        Column(Modifier.weight(1f)){
                            GrantSwitch("Arquivos selecionados",grants.files,s.localConfirmed){onGrants(grants.copy(files=it))}
                            GrantSwitch("Tela • diálogo do Android",grants.screen,s.localConfirmed){onGrants(grants.copy(screen=it))}
                            GrantSwitch(if(s.controlMode==ControlMode.HID)"Gamepad Bluetooth HID"else"Joystick / toques e gestos",grants.controls,s.localConfirmed){onGrants(grants.copy(controls=it))}
                        }
                    }
                    if(grants.controls)Text(if(s.controlMode==ControlMode.HID)
                        "Autorizo entrada nativa de gamepad pelo Bluetooth. O Android entrega os botões ao app em primeiro plano, inclusive fora do app escolhido para abrir. Não exige Acessibilidade nem lê conteúdo. Bloquear ou desconectar interrompe os comandos."
                        else "Destino dos controles: ${s.targetLabel}. Para controlar outro app, escolha-o em Ajustes e ative a Acessibilidade manualmente antes da conexão.",fontSize=10.sp,color=Color(0xFFDFCCFF))
                    if(grants.screen)Text("Compartilhar tela só inicia após tocar no botão do visor e aceitar uma nova autorização do sistema. Prefira selecionar apenas o jogo/app no Android 14 ou superior.",fontSize=10.sp)
                }else Text("O receptor escolhe as permissões. Você só verá os dados que ele autorizar. Nenhum comando é liberado antes das duas confirmações.",fontSize=12.sp)
                Row(Modifier.toggleable(agreed,enabled=!s.localConfirmed,role=Role.Checkbox,onValueChange=onAgreed).semantics(mergeDescendants=true){},verticalAlignment=Alignment.CenterVertically){Checkbox(agreed,onCheckedChange=null,enabled=!s.localConfirmed);Text("Conferi o código nos dois aparelhos e autorizo esta sessão.",fontSize=11.sp)}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){TextButton(onClick=onReject){Text("Recusar / encerrar")};Spacer(Modifier.width(12.dp));Button(onClick=onConfirm,enabled=agreed&&!s.localConfirmed){Text(if(s.localConfirmed)"Aguardando…"else"Autorizar sessão")}}
                if(s.localConfirmed)Text("Confirmação enviada. Aguardando o outro usuário.",fontSize=11.sp,color=Accent)
                else Text(s.message,fontSize=10.sp,color=Color(0xFFF5B1BC))
            }
        }
    }
}
@Composable private fun GrantSwitch(label:String,value:Boolean,locked:Boolean,onChange:(Boolean)->Unit){Row(Modifier.fillMaxWidth().height(40.dp).toggleable(value,enabled=!locked,role=Role.Switch,onValueChange=onChange).semantics(mergeDescendants=true){},verticalAlignment=Alignment.CenterVertically){Text(label,fontSize=10.sp,modifier=Modifier.weight(1f));Switch(value,onCheckedChange=null,enabled=!locked,modifier=Modifier.height(30.dp))}}

@Composable fun AppPicker(onClose:()->Unit,onSelect:(String,String)->Unit){
    val context=LocalContext.current
    val apps=remember{
        context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
            .filter { it.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM==0 && it.activityInfo.packageName!=context.packageName }
            .distinctBy{it.activityInfo.packageName}.map{it.activityInfo.packageName to it.loadLabel(context.packageManager).toString()}.sortedBy{it.second.lowercase()}
    }
    AlertDialog(onDismissRequest=onClose,title={Text("Aplicativo autorizado")},text={Column(Modifier.heightIn(max=300.dp).verticalScroll(rememberScrollState())){
        Text("Escolha um app instalado por você. Os gestos são bloqueados quando ele sai de primeiro plano.",fontSize=12.sp)
        TextButton(onClick={onSelect("","Modo de teste")}){Text("Modo de teste do receptor")}
        apps.forEach{(pkg,label)->TextButton(onClick={onSelect(pkg,label)},modifier=Modifier.fillMaxWidth()){Column(Modifier.fillMaxWidth()){Text(label);Text(pkg,fontSize=9.sp,color=Color(0xFF958AA6))}}}
        if(apps.isEmpty())Text("Nenhum aplicativo de usuário encontrado.",fontSize=12.sp)
    }},confirmButton={TextButton(onClick=onClose){Text("Fechar")}})
}

@Composable fun CalibrationDialog(initial:Mapping,onClose:()->Unit,onSave:(Mapping)->Unit){
    var m by remember{mutableStateOf(initial)};var selected by remember{mutableStateOf(0)}
    val labels=listOf("Joystick L","Joystick R","A","B","X","Y")
    val coords=when(selected){0->m.stickX to m.stickY;1->m.rightX to m.rightY;2->m.aX to m.aY;3->m.bX to m.bY;4->m.xX to m.xY;else->m.yX to m.yY}
    fun update(x:Float,y:Float){val a=x.coerceIn(.03f,.97f);val b=y.coerceIn(.03f,.97f);m=when(selected){0->m.copy(stickX=a,stickY=b);1->m.copy(rightX=a,rightY=b);2->m.copy(aX=a,aY=b);3->m.copy(bX=a,bY=b);4->m.copy(xX=a,xY=b);else->m.copy(yX=a,yY=b)}}
    val ctx=LocalContext.current
    val ratio=ctx.resources.displayMetrics.widthPixels.toFloat()/ctx.resources.displayMetrics.heightPixels.coerceAtLeast(1)
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)){
        Surface(Modifier.widthIn(max=760.dp).fillMaxWidth(.94f).fillMaxHeight(.95f),shape=RoundedCornerShape(16.dp)){
            Row(Modifier.padding(15.dp),horizontalArrangement=Arrangement.spacedBy(18.dp)){
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(7.dp)){
                    Text("Calibrar controles",fontSize=19.sp,fontWeight=FontWeight.Bold)
                    Text("Use o segundo celular e o jogo na mesma orientação. Selecione um controle e toque no mapa na posição correspondente ao botão do jogo. Coordenadas são relativas à tela inteira.",fontSize=11.sp)
                    Canvas(Modifier.fillMaxWidth().aspectRatio(ratio).background(Color(0xFF0D0F1A),RoundedCornerShape(8.dp))
                        .pointerInput(selected){detectTapGestures{p->update(p.x/size.width,p.y/size.height)}}){
                        for(i in 1..9){drawLine(Color(0xFF232438),Offset(size.width*i/10,0f),Offset(size.width*i/10,size.height));drawLine(Color(0xFF232438),Offset(0f,size.height*i/10),Offset(size.width,size.height*i/10))}
                        val ps=listOf(m.stickX to m.stickY,m.rightX to m.rightY,m.aX to m.aY,m.bX to m.bY,m.xX to m.xY,m.yX to m.yY)
                        ps.forEachIndexed{i,(x,y)->drawCircle(if(i==selected)Accent else Color(0xFF50B3B0),if(i<2)size.minDimension*m.radius else 9.dp.toPx(),Offset(x*size.width,y*size.height),style=androidx.compose.ui.graphics.drawscope.Stroke(if(i==selected)3.dp.toPx()else 1.dp.toPx()))}
                    }
                    Text("${labels[selected]}: X ${(coords.first*100).toInt()}% • Y ${(coords.second*100).toInt()}%",fontSize=11.sp,color=Accent)
                    Slider(value=coords.first,onValueChange={update(it,coords.second)},valueRange=.03f.. .97f)
                    Slider(value=coords.second,onValueChange={update(coords.first,it)},valueRange=.03f.. .97f)
                    Text("Raio do joystick: ${(m.radius*100).toInt()}% do lado menor",fontSize=11.sp)
                    Slider(m.radius,onValueChange={m=m.copy(radius=it)},valueRange=.04f.. .25f)
                }
                Column(Modifier.width(130.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(5.dp)){
                    labels.forEachIndexed{i,label->FilterChip(selected==i,onClick={selected=i},label={Text(label,fontSize=11.sp)})}
                    TextButton(onClick={m=Mapping()}){Text("Restaurar",fontSize=11.sp)}
                    Button(onClick={onSave(m)}){Text("Salvar")}
                    TextButton(onClick=onClose){Text("Cancelar")}
                }
            }
        }
    }
}
