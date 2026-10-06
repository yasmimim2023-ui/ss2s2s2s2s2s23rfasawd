# ThorLink 1.1.0 — gamepad Bluetooth HID nativo

## Instalação e teste

1. Atualize os dois APKs para 1.1.0, instalando por cima. A versão aparece em Ajustes.
2. No receptor escolha **Gamepad HID**. Escolha GameHub em **Escolher aplicativo** para abri-lo pelo visor. Não precisa de Acessibilidade.
3. Conecte os aparelhos previamente pareados, compare os seis dígitos e autorize nos dois. No receptor marque **Gamepad Bluetooth HID** e leia o escopo de atuação.
4. Aguarde **Bluetooth HID conectado**. Confirme eventuais pedidos do Android no receptor para conectar esse aparelho como dispositivo de entrada.
5. Deixe **Visor** aberto no receptor e pressione A/mova L e R no Controle. **Entradas NATIVAS recebidas neste visor** precisa aumentar; pontos/botões precisam responder. O teste usa KeyEvent/MotionEvent entregues pelo Android à nossa Activity, em vez dos pacotes de espelho RFCOMM.
6. Toque em **Abrir GameHub** e configure o controle dentro da aplicação/jogo. O teste do ThorLink deixa de receber eventos enquanto outra aplicação está em primeiro plano; isso é esperado. Nenhum evento de outro app é capturado pelo receptor.

Desconecte outros gamepads para isolar o teste: eles também podem produzir eventos normais na tela do receptor.

## Diagnóstico

| Estado | Significado/ação |
|---|---|
| Conectado na barra | Sessão aprovada; ainda confira o estado HID. |
| Gamepad autorizado / Conectando | Aguardando perfil, registro ou host Android. |
| Bluetooth HID conectado | O perfil confirmou a conexão; teste o recebimento nativo no receptor. |
| Perfil HID indisponível | Controlador sem suporte ao perfil/API. Android 9+ é necessário, mas não suficiente. |
| HID não conectou | Confira Bluetooth, permissões e conexão de entrada nas configurações do receptor. Use Iniciar gamepad Bluetooth no Controle para nova tentativa manual. |
| HID pausado | Controle saiu de primeiro plano. Volte e toque em Iniciar gamepad Bluetooth. |
| Receptor bloqueado/autorização sem resposta | Desbloqueie e confira a sessão; entradas pendentes são descartadas. |
| Relatórios aumentam, entradas nativas ficam em zero | A API aceitou o envio, mas o recebimento como gamepad não foi comprovado. Informe modelos/Android para investigar o perfil. |
| Teste nativo funciona, GameHub não | Reconhecimento nativo foi observado; confira configuração/mapeamento no GameHub/jogo. |

## Funcionamento e limites

O Controle usa a API pública BluetoothHidDevice, disponível desde API 28/Android 9. O fabricante precisa disponibilizar HID Device; o receptor precisa aceitar HID Host. Não há root, ADB, Shizuku, driver externo ou API oculta. Somente um app HID Device pode registrar por vez. A API desativa temporariamente HID Host no controlador enquanto HID Device está registrado; ao encerrar, o ThorLink desregistra.

O descritor de gamepad usa ABXY, direcional/hat, dois analógicos X/Y/Z/Rz de 16 bits, L1/R1, L2/R2, SELECT/START e L3/R3. Gatilhos da tela são digitais e geram os extremos 0/255 nos eixos Brake/Accelerator. Não inclui rumble, touchpad, giroscópio, teclado ou mouse.

É um **gamepad HID genérico**. Não implementa a identidade/protocolo completo de um DualShock 4. XInput é uma API de Windows; um emulador que executa jogos de Windows precisa converter a entrada Android para o formato exigido pelo jogo. A compatibilidade com GameHub precisa ser testada na versão instalada.

Um worker separado verifica mudanças a cada 8 ms e envia manutenção a cada 100 ms, sem aguardar ACK do espelho RFCOMM. São intervalos do código; rádio, aparelhos, host, jogo e captura afetam o atraso. Não houve medição em dois smartphones nesta entrega.

## Consentimento e proteção

- O perfil inicia somente após aprovação nos dois aparelhos e concessão específica de Gamepad Bluetooth HID. O alvo é o mesmo endereço Bluetooth pareado e autenticado pela sessão. Conexões de outros hosts são desconectadas; nenhum estado de controle é enviado a eles.
- O canal RFCOMM conserva autenticação de identidades e AES-GCM. Relatórios HID usam a autenticação/criptografia do Bluetooth do Android; não recebem uma camada AES própria que o HID Host padrão não entenderia. O AOSP configura os canais HID com autenticação e criptografia, conforme referência abaixo.
- O receptor renova uma autorização pelo canal autenticado a cada 50 ms, informando se está desbloqueado. Sem renovação por mais de 500 ms, sem consentimento ou com Controle fora de primeiro plano, somente estado neutro pode ser enviado. A programação do sistema pode acrescentar atraso.
- Desconectar revoga, neutraliza quando possível, fecha e desregistra. Sair da tela do Controle pausa/desregistra. Não há reconexão automática após pausa/falha.
- Mudar entre HID e Toques encerra a sessão e exige nova aprovação. Concessões não são aumentadas durante a sessão.
- **HID atua no app em primeiro plano do receptor**, pelas regras do Android. Não limita o gamepad a um pacote específico e não observa foco/conteúdo. Esse escopo aparece na confirmação do receptor. O app escolhido serve para abrir o jogo. O modo Toques conserva o bloqueio por app escolhido.
- Relatórios/eventos não são gravados. Teste, espelho e contadores são temporários. Só configurações, dispositivos autorizados e histórico limitado de sessões são persistidos.

## Referências oficiais

- [BluetoothHidDevice](https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice)
- [Mapeamento de gamepad HID no Android](https://source.android.com/docs/compatibility/16/android-16-cdd#726_game_controller_support)
- [Proteção dos canais HID no AOSP](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/1fdde1b31884edd37d0348db6f6ebb390aebc947/system/stack/hid/hidd_conn.cc)
- [XInput no Windows](https://learn.microsoft.com/en-us/windows/win32/xinput/getting-started-with-xinput)
