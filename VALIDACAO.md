# Validação da entrega — ThorLink

Data: 05/10/2026. Dois APKs debug assinados para testes, versão 1.1.0 (versionCode 3). Min SDK 26 / compile e target SDK 36. Modo HID exige API 28/Android 9+ e perfil HID Device no controlador. Kotlin 2.2.21, AGP 8.13.2, Gradle 8.13, JDK 17, Build-Tools 36.0.0.

## Correção desta versão

Modo nativo Bluetooth HID com descritor de gamepad e envio por worker separado. ABXY, direcional, dois analógicos, L1/R1/L2/R2/SELECT/START/L3/R3 funcionais nesse modo. O receptor escolhe HID ou Toques; mudar exige nova sessão/aprovação. Uma autorização temporária renovada pelo receptor interrompe relatórios ativos após 500 ms sem resposta ou ao bloquear. O visor do receptor conta eventos nativos KeyEvent/MotionEvent recebidos em sua própria Activity, separadamente do espelho RFCOMM. Protocolo v3 exige atualizar os dois APKs. Compatibilidade nativa nos aparelhos/GameHub ainda não foi comprovada em hardware.

## Verificado nesta máquina

- Compilação dos flavors controllerDebug e receiverDebug: **PASSOU**.
- Script `compilar.ps1`, usando o Gradle Wrapper entregue: **PASSOU**; gerou os dois APKs em dist.
- Assinatura dos dois APKs: **PASSOU** em `apksigner verify --verbose`, esquema v2. São assinaturas debug, não uma chave de publicação.
- Criptografia: 6 testes por flavor (ECDH, ECDSA, HKDF, AES-GCM, alteração de ciphertext, replay, limite de frame, chave incorreta): **PASSOU**.
- Handshake: 4 testes por flavor (autenticação dos dois papéis e mensagens criptografadas em streams locais, rejeição de dois receptores, rejeição de revelação diferente do compromisso, rejeição de versão antiga com mensagem para atualizar os APKs): **PASSOU**.
- Buffer de entradas: 6 testes por flavor (toque rápido, movimentos recentes, botões simultâneos, reset, expiração e estouro de fila): **PASSOU**.
- HID: 9 testes por flavor (estado neutro, usages Android ABXY, botões auxiliares, eixos de 16 bits, valores inválidos, oito direções/opostas, gatilhos, parser independente do descritor com 104 bits/13 bytes, gate de consentimento/visibilidade/lease/bloqueio): **PASSOU**. São testes do formato e das regras; não simulam o rádio/perfil HID do fabricante.
- Entradas Compose: 4 testes por flavor com eventos de ponteiro reais no Robolectric (pressão/soltura em 5 ms, botão bloqueado explica sem transmitir, analógico e botão simultâneos, START auxiliar): **PASSOU**.
- Interface: 5 testes por flavor, com Android API 35 e Compose reais em Robolectric: **PASSOU**. Incluem navegação, concessões desmarcadas e aprovação desabilitada antes de conferir o código, layout compacto, diagnóstico e consentimento HID inicialmente desmarcado com explicação do escopo em primeiro plano.
- Total: **68 testes executados, 0 falhas**, incluindo 34 em cada flavor.
- Android Lint: **0 erros nas duas variantes**. Há 24 avisos no controle e 23 no receptor, principalmente versões mais novas disponíveis, compatibilidade de atributos com Android antigo, preferência por extensões KTX, recursos exclusivos do receptor que ficam sem uso no controle e orientação em telas grandes. Os relatórios completos permanecem em validation. Não houve baseline nem desativação global de verificações para passar o lint.
- Manifestos gerados: conferidos automaticamente. Sem internet, câmera, microfone, contatos, SMS, leitura ampla de arquivos/mídia, localização em segundo plano ou boot. Captura e serviço de Acessibilidade aparecem apenas no receptor.
- Gradle 8.13: distribuição comparada com o SHA-256 oficial e checksum fixado no wrapper.
- Capturas em preview: renderização do layout real pelo Android/Compose via Robolectric; revisadas visualmente. Não são capturas de dois smartphones conectados.

## Ainda depende de dois smartphones reais

Disponibilidade e registro de HID Device no controlador, conexão HID Host no receptor, reconhecimento de KeyEvent/MotionEvent no hardware, compatibilidade/mapeamento no GameHub, descoberta/pareamento, conexões RFCOMM simultâneas com HID, concessões do Android, MediaProjection, gestos opcionais, arquivos com provedores reais, quedas de rádio e latência de ponta a ponta. Nenhum aparelho físico estava conectado via ADB nesta sessão.

Os intervalos de 8 ms do worker HID, manutenção HID de 100 ms, lease de 50 ms/validade de 500 ms, espelho de 20 ms, gestos de 32 ms e imagem nominal de 8 fps descrevem o código; não são medições nem garantias de desempenho. O contador de relatórios enviados comprova somente aceitação pela API; o teste nativo precisa receber entradas no receptor real. Não implementa o protocolo completo PS4 ou um driver Windows XInput. A interface segue a parte inferior da referência com aproximação em Compose, sem tela superior; não é uma cópia pixel a pixel da fotografia.

O roteiro completo está em docs/TESTES-DOIS-CELULARES.md. A entrega está pronta para instalação e essa rodada de testes; homologação em hardware e auditoria independente de produção não foram realizadas.

## Integridade dos APKs

- `ThorLink-Controle.apk` — 17,916,443 bytes — SHA-256 `8c86708403f6b01c688140e20155fc8e8cfefde4285ec8adb7c877ded464b63f`
- `ThorLink-Receptor.apk` — 17,916,713 bytes — SHA-256 `d069f432ab28af752f3984bcf90a2fa157a2e9036bc0e3d59a315677a17093c4`
