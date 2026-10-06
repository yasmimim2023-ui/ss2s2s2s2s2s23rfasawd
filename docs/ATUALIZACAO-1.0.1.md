# Atualização 1.0.1 — botões e diagnóstico de gestos

Atualize **ThorLink Controle** e **ThorLink Receptor** usando os dois novos APKs. Instale por cima da versão anterior. O pareamento e o mapeamento são preservados pela atualização assinada com a mesma chave de testes. Se o Android disser que as versões dos aplicativos são diferentes, confira a instalação nos dois aparelhos.

## O que foi corrigido

- Pressionar e soltar antes da confirmação Bluetooth podia resultar em transmitir somente o estado solto. Os dois eventos agora permanecem em ordem.
- O receptor também podia perder uma pressão curta enquanto executava um segmento de gesto anterior. Ele agora consome as transições separadamente.
- Botões bloqueados por conexão, autorização ou uso do APK receptor agora explicam o bloqueio ao tocar.
- Recusa, cancelamento e exceção do serviço de gestos eram silenciosos. Agora o receptor envia um código de diagnóstico pelo canal autenticado, somente com controles autorizados.
- Comandos antigos nunca são reproduzidos após uma espera superior a 250 ms. Desconectar, sair do app escolhido ou bloquear o receptor interrompe a execução e descarta comandos pendentes.

## Teste em dois passos

1. No receptor, escolha **Modo de teste** em Ajustes. Reconecte e marque **Joystick / toques e gestos**. Pressione A no Controle: os contadores aumentam e o texto **último botão: A** aparece nos dois aparelhos. Esse teste confirma o caminho de envio/recebimento, sem depender da Acessibilidade.
2. No receptor, escolha o app, ative a Acessibilidade e calibre as coordenadas. Toque em **Abrir [app]**. Pressione A no Controle e observe o diagnóstico abaixo.

| Mensagem no visor | Significado e ação |
|---|---|
| Controles não autorizados | Reconecte e marque Joystick / toques e gestos no receptor. Não é possível aumentar permissões pelo controlador. |
| Modo de teste | O comando chegou; escolha um app no receptor para aplicar gestos externos. |
| Ative ThorLink na Acessibilidade | Ative o serviço ThorLink • Controle autorizado nas configurações do receptor. |
| Abra o app escolhido | O app escolhido não está em primeiro plano. Toque em Abrir no receptor e deixe esse app visível. |
| Receptor bloqueado | Desbloqueie o receptor e abra o app escolhido. |
| Comandos pausados | O receptor não recebeu uma entrada recente. Verifique se o Controle está aberto e se a conexão responde. |
| Gestos prontos | Permissões e foco permitem tentar o gesto; ainda não houve confirmação de execução. |
| Android concluiu o gesto | O Android chamou onCompleted. Confira as posições calibradas e a resposta do app. Não comprova que o jogo executou uma ação. |
| Android cancelou o gesto | Houve cancelamento do Android. Solte o controle, evite tocar no receptor e tente novamente. |
| Android recusou o gesto | dispatchGesture retornou false. Revise o serviço de Acessibilidade e tente desativá-lo/ativá-lo manualmente. |
| Falha ao criar gesto | O serviço encontrou uma exceção. Revise o mapeamento e reinicie a sessão. |
| Comandos atrasados descartados | Fila cheia: ações foram descartadas para evitar executar entradas antigas. Solte os controles e tente novamente. |

Para GameHub, configure as posições sobre os botões/analógicos de toque realmente apresentados pelo app ou jogo. Este modo envia toques; uma configuração que espera eventos de um controle físico não recebe eventos HID deste aplicativo. A compatibilidade precisa ser verificada no aparelho.

Nenhum app, texto de janela ou conteúdo privado é transmitido pelo diagnóstico. O receptor envia somente um código fixo de estado. Contadores e último botão são temporários e não são salvos.
