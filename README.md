# ThorLink — controle e receptor Android

Versão **1.1.0**, com **gamepad Bluetooth HID nativo**. Atualize os **dois celulares**. O protocolo v3 exige as duas versões iguais. A assinatura de testes e os identificadores dos apps foram mantidos. Instalar por cima preserva o pareamento e as configurações.

## Usar como controle nativo — HID

1. Instale `ThorLink-Controle-HID-1.1.0.apk` no controlador e `ThorLink-Receptor-HID-1.1.0.apk` no receptor. São APKs debug assinados para testes.
2. No receptor, escolha **Ajustes → Gamepad HID**. Acessibilidade e calibração de toques não são necessárias nesse modo.
3. Pareie pelo Android se necessário. Abra **Conectar → Receber** no receptor e conecte ao aparelho pareado pelo Controle.
4. Compare os seis dígitos. No receptor marque **Gamepad Bluetooth HID**, leia o escopo dos comandos e autorize. Confirme o código e autorize no Controle também.
5. Aguarde **Bluetooth HID conectado**. Confirme no receptor qualquer solicitação do Android para conectar esse aparelho como dispositivo de entrada. Após pausa/falha, há o botão **Iniciar gamepad Bluetooth** no Controle.
6. Com **Visor** aberto no receptor, pressione A e mova os analógicos no Controle. O contador **Entradas NATIVAS recebidas neste visor** precisa aumentar. Esses eventos vêm do Android, independentemente do espelho RFCOMM.
7. Escolha GameHub em **Escolher aplicativo** no receptor e toque em **Abrir GameHub**. Configure o controle dentro da aplicação/jogo. A compatibilidade precisa ser testada na versão instalada.
8. Mantenha o Controle aberto. Sair de primeiro plano pausa/desregistra HID; ao voltar, toque em **Iniciar gamepad Bluetooth**. Desconectar a sessão revoga as permissões e encerra os canais.

O controlador precisa de **Android 9/API 28+** com suporte a HID Device; o receptor precisa aceitar HID Host. O app mostra erros de perfil, registro e conexão. A disponibilidade depende do fabricante e não pode ser comprovada apenas pela compilação.

HID envia um gamepad ao **app em primeiro plano**, conforme o consentimento específico; não é limitado ao aplicativo escolhido para abrir. Não lê conteúdo. Bloqueio ou ausência de renovação da autorização neutraliza a entrada em até 500 ms, além da programação do sistema.

ABXY, direcional, dois analógicos, L1/R1, L2/R2, SELECT/START e L3/R3 são funcionais no modo HID. Gatilhos da tela geram os extremos 0/255. É um gamepad genérico; não imita o protocolo completo de PS4. A conversão para XInput dentro de um jogo de Windows depende do emulador. Não há rumble, giroscópio ou touchpad.

Consulte `docs/MODO-GAMEPAD-HID.md` para testar e interpretar o resultado.

## Se os botões do modo de toques não fazem nada

Selecione **Ajustes → Toques** no receptor antes de seguir este fluxo. Mudar de modo encerra a sessão e exige nova autorização.

1. No receptor, confira **Ajustes → Escolher aplicativo** e escolha o app que ficará aberto. Se estiver em **Modo de teste**, os comandos aparecem no visor do receptor e não geram toques em outro app.
2. Para gestos externos, ative **ThorLink • Controle autorizado** na Acessibilidade do receptor e calibre A/B/X/Y e os analógicos sobre os controles de toque do jogo. O app não cria um gamepad físico.
3. Reconecte e marque **Joystick / toques e gestos** na autorização do receptor. Estar conectado por Bluetooth não concede essa categoria automaticamente.
4. Toque em **Abrir [app]** no receptor. No controle, abra **Visor** e pressione A. O visor mostra a quantidade de pacotes confirmados, o último botão recebido e o diagnóstico do receptor. O diagnóstico também aparece sobre a imagem compartilhada.
5. Se aparecer **Android concluiu o gesto** e o app não responder, confira primeiro o posicionamento do toque e se o app aceita esse tipo de gesto. Esse retorno comprova o callback do Android, não a execução de uma ação dentro do jogo.

A versão 1.0.1 conserva pressionar/soltar em uma fila curta nos dois aparelhos, evitando perder um toque rápido entre uma confirmação Bluetooth e a próxima execução de gesto. Movimentos de analógicos continuam usando a posição mais recente. Eventos com mais de 250 ms de espera ou fila cheia são descartados, em vez de executar ações antigas.

Veja também `docs/ATUALIZACAO-1.0.1.md` para interpretar cada mensagem.

Projeto Kotlin / Jetpack Compose / MVVM com dois APKs, produzido a partir da parte inferior da referência AYN Thor. A tela superior foi removida. O desenho usa carcaça clara, tela central escura, analógico esquerdo acima do direcional e botões X/Y/A/B acima do analógico direito. É uma interpretação em Compose da fotografia: não é uma cópia pixel a pixel de uma tela de aplicativo que já existia.

## Configuração do modo alternativo de toques

1. Instale `ThorLink-Controle.apk` no celular que será o controle.
2. Instale `ThorLink-Receptor.apk` no celular que receberá os comandos.
3. Autorize a instalação desses APKs pelo gerenciador de arquivos quando o Android solicitar. Os APKs fornecidos são **debug assinados para testes**, não uma publicação de produção.
4. No receptor, abra **Ajustes → Escolher aplicativo**. Escolha o jogo/app que aceitará gestos. Aplicativos de sistema não aparecem nessa lista.
5. No receptor, use **Calibrar joystick e botões** para posicionar os controles nas coordenadas dos controles de toque do jogo. Use a mesma orientação de tela para calibrar e jogar.
6. Se for usar um aplicativo externo, abra **Ativar controle por gestos** e ative o serviço `ThorLink • Controle autorizado` na Acessibilidade. O Android pode exigir, para APK instalado manualmente, **Informações do aplicativo → menu → Permitir configurações restritas**. Essa decisão pertence ao usuário; o app não altera configurações sozinho.
7. Em **Conectar**, autorize as permissões, ative Bluetooth e toque em **Visível por 2 min** no receptor. No controle, toque em **Buscar** e **Parear**. Confirme o diálogo de pareamento nos dois aparelhos.
8. No receptor toque em **Receber**. No controle toque em **Conectar** ao aparelho já pareado.
9. Compare os **seis dígitos** nas duas telas. No receptor, marque somente os tipos de dados que deseja compartilhar. Para os joysticks, marque **Joystick / toques e gestos**. Nos dois celulares, marque que conferiu o código e toque em **Autorizar sessão**.
10. No receptor, se autorizou tela, vá a **Visor → Compartilhar tela** e aceite o diálogo de captura do Android. No Android 14+, prefira selecionar somente o jogo/app. Não há captura de áudio.
11. No receptor, toque em **Abrir [seu aplicativo]**. Os joysticks e botões do controle geram toques nas posições calibradas. Ao sair do app escolhido ou bloquear o celular, a injeção de gestos é interrompida.
12. **Desconectar** está no X da barra do visor e na notificação persistente dos dois aparelhos. Fechar o app pelo sistema também encerra a sessão.

No modo Toques, **Modo de teste** mostra o espelho de comandos sem Acessibilidade. No modo HID, o visor mostra entradas nativas entregues pelo Android.

## Limitações reais do Android e do Bluetooth

- Modo HID: usa BluetoothHidDevice e um descritor de gamepad, sem root, ADB, Shizuku, API oculta ou Acessibilidade. Modo Toques: gera gestos de Acessibilidade e depende de o jogo aceitar esses gestos.
- O joystick esquerdo começa no centro calibrado e move o toque até a direção desejada. O direito faz o mesmo na segunda posição. A/B/X/Y mantêm toques enquanto pressionados. O direcional controla a direção do joystick esquerdo. Não há mapeamento de teclado, shell, root ou ação de navegação global.
- HID verifica a entrada a cada **8 ms**, envia mudanças e manutenção a cada 100 ms sem aguardar ACK do canal de sessão. Espelho/diagnóstico RFCOMM usa **20 ms** e um pacote pendente. Toques usa segmentos de **32 ms**. Esses valores são intervalos configurados, **não uma medição nem promessa de atraso final**.
- Eventos de pressionar/soltar não são sobrescritos por uma soltura rápida. Há uma fila limitada de 32 transições com validade de 250 ms tanto no envio quanto no receptor. Diagnósticos e último botão ficam apenas em memória, somente durante a sessão autorizada; não são adicionados ao histórico local.
- `RTT` mede o canal RFCOMM de sessão/espelho ou ping; não mede a latência de HID nem o atraso da imagem/apresentação do jogo.
- Imagem: JPEG, maior lado de **480 pixels**, qualidade **35**, máximo nominal **8 fps**, apenas o frame mais recente e uma imagem aguardando confirmação. É uma prévia Bluetooth de baixa resolução, não streaming para jogo competitivo. O rádio, a implementação de Bluetooth e o conteúdo da imagem afetam o atraso.
- Existem dois sockets RFCOMM seguros: sessão/consentimento/metadados/espelho e imagem/arquivos. HID acrescenta o perfil nativo do Android. Todos compartilham o rádio; captura/arquivos podem afetar a latência de rádio.
- Arquivos selecionados manualmente: até **1 GiB** e tamanho conhecido pelo provedor. Arquivos e imagem dividem o canal de dados; a captura é pausada durante a transferência. O envio é sequencial, com blocos de 8 KiB e confirmação de cada bloco.
- A captura depende do MediaProjection. Conteúdo protegido pelo Android pode aparecer preto. Não há tentativa de contornar essa proteção. A orientação não deve ser trocada durante a captura; pare e compartilhe novamente se precisar mudar.
- Para controle por gestos, as coordenadas se referem à tela inteira do receptor. Recortes/letterboxing do compartilhamento de um app podem não corresponder exatamente à proporção do visor central. Calibre no próprio receptor.
- Se ocorrer queda de conexão, todos os acessos são revogados. **Reconectar é uma ação manual**: abra o receptor, toque em Receber, conecte pelo controle e aprove novamente. Autorizar anteriormente não dispensa consentimento da nova sessão. Uma transferência interrompida não é retomada automaticamente.
- A remoção de autorização no Histórico não apaga o pareamento Bluetooth do sistema. Para isso, use as configurações Bluetooth do Android.

## Compilar no Android Studio

1. Extraia o ZIP e abra a pasta `ThorLink` no Android Studio.
2. Instale **Android SDK Platform 36** e **Build-Tools 36.0.0** no SDK Manager.
3. Use JDK **17** ou um JDK compatível com Gradle 8.13; o projeto foi compilado com JDK 17.
4. Aguarde o Gradle Sync. A primeira compilação precisa de internet para baixar as dependências; os APKs em execução não possuem permissão INTERNET.
5. Abra **Build Variants** e selecione `controllerDebug` ou `receiverDebug`.
6. Use **Build → Build APK(s)** ou as tarefas abaixo para gerar ambos.

```powershell
.\gradlew.bat assembleControllerDebug assembleReceiverDebug
.\gradlew.bat testControllerDebugUnitTest testReceiverDebugUnitTest lintControllerDebug lintReceiverDebug
```

APKs resultantes:

```text
app/build/outputs/apk/controller/debug/app-controller-debug.apk
app/build/outputs/apk/receiver/debug/app-receiver-debug.apk
```

O script `compilar.ps1` gera as duas variantes debug, roda testes e lint e copia os APKs para `dist/`. Ele usa o SDK configurado em `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `local.properties` ou no local padrão do Android Studio. O projeto não depende do caminho de SDK usado durante esta entrega.

```powershell
powershell -ExecutionPolicy Bypass -File .\compilar.ps1
```

## APK de produção assinado

No Android Studio, use **Build → Generate Signed App Bundle / APK → APK**, crie seu keystore e selecione cada flavor `controller` e `receiver`, build type `release`. Guarde a chave e suas senhas; futuras atualizações precisam da mesma chave.

Para compilar release pela linha de comando:

1. Crie um keystore seu com o Android Studio ou `keytool`.
2. Copie `keystore.properties.example` para `keystore.properties` e informe o caminho, alias e senhas.
3. Execute `gradlew.bat assembleControllerRelease assembleReceiverRelease`.

Se `keystore.properties` não existir, release será **não assinado**; use o assistente de assinatura do Android Studio. Não publique APK debug. Nenhuma chave privada de produção é incluída no projeto.

## Estrutura e responsabilidades

```text
ThorLink/
├── app/build.gradle.kts             # SDK, Kotlin, Compose e dois flavors
├── app/src/controller/AndroidManifest.xml # somente serviço de sessão
├── app/src/receiver/AndroidManifest.xml   # captura e Acessibilidade apenas no receptor
├── app/src/main/AndroidManifest.xml # permissões e serviços explícitos
├── app/src/main/java/br/com/thorlink/
│   ├── MainActivity.kt             # contratos de permissão, SAF e captura
│   ├── ThorApp.kt                  # repositório por processo
│   ├── domain/Models.kt            # estados, permissões e mapeamento
│   ├── domain/ControlInputBuffer.kt # movimentos recentes e transições de botão preservadas
│   ├── domain/HidReport.kt         # descritor, botões/eixos e gate de autorização
│   ├── data/LocalStore.kt          # preferências, identidades e histórico
│   ├── data/LinkRepository.kt      # sessão, consentimento, arquivos, desconexão
│   ├── security/Crypto.kt          # ECDH, HKDF, assinaturas e AES-GCM
│   ├── security/Identity.kt        # identidade privada no Android Keystore
│   ├── transport/BluetoothTransport.kt # descoberta, pareamento, RFCOMM e handshake
│   ├── transport/HidGamepad.kt     # perfil HID e envio direto de relatórios
│   ├── services/SessionService.kt  # notificação e encerramento visível
│   └── ui/                        # ViewModel, telas Compose, controles e diálogos
├── app/src/receiver/java/.../services/ # ScreenService e TouchService reais
├── app/src/controller/java/.../services/ # placeholders sem serviços de captura/gestos
├── app/src/test/                   # testes locais de segurança e interface
├── docs/SEGURANCA.md
├── docs/TESTES-DOIS-CELULARES.md
├── gradle/wrapper/                 # Gradle 8.13
├── compilar.ps1
└── keystore.properties.example
```

## Permissões por versão

| Versão | Permissões/fluxo | Finalidade |
|---|---|---|
| Android 8–11 (API 26–30) | BLUETOOTH, BLUETOOTH_ADMIN e ACCESS_FINE_LOCATION em runtime | O Android exige Localização para a descoberta Bluetooth. Nenhuma API GPS/localização é consultada. |
| Android 12+ | BLUETOOTH_SCAN com neverForLocation, BLUETOOTH_CONNECT e BLUETOOTH_ADVERTISE em runtime | Buscar, parear, conectar e tornar o receptor visível. |
| Android 13+ | POST_NOTIFICATIONS | Sessão e captura com aviso visível e botão Desconectar. A execução pede essa permissão junto às permissões Bluetooth. |
| Android 14+ | FOREGROUND_SERVICE_CONNECTED_DEVICE / MEDIA_PROJECTION | Tipos de serviço declarados; captura somente com token novo fornecido pelo sistema. |
| Todas as versões suportadas | OpenDocument / CreateDocument | Somente o arquivo/destino escolhido; nenhuma permissão ampla de armazenamento. |
| Receptor opcional | Serviço de Acessibilidade ativado nas configurações | Somente gestos. canRetrieveWindowContent=false. |

O APK controlador não declara o serviço de Acessibilidade nem o serviço de captura. Não existem permissões de internet, câmera, microfone, contatos, SMS, leitura de mídia, gerenciador de arquivos amplo, boot ou localização em segundo plano.

## Documentação oficial consultada

- [Permissões Bluetooth](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
- [Conexões RFCOMM](https://developer.android.com/develop/connectivity/bluetooth/connect-bluetooth-devices)
- [Captura com MediaProjection](https://developer.android.com/media/grow/media-projection)
- [Gestos com AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#dispatchGesture(android.accessibilityservice.GestureDescription,%20android.accessibilityservice.AccessibilityService.GestureResultCallback,%20android.os.Handler))
- [BluetoothHidDevice](https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice)
- [Mapeamento HID do Android](https://source.android.com/docs/compatibility/16/android-16-cdd#726_game_controller_support)
- [XInput no Windows](https://learn.microsoft.com/en-us/windows/win32/xinput/getting-started-with-xinput)

Consulte `VALIDACAO.md`, entregue junto aos APKs, para a evidência efetivamente obtida nesta máquina. O roteiro de testes reais está em `docs/TESTES-DOIS-CELULARES.md`.
