# Roteiro de aceitação em dois smartphones reais

Anote: marca/modelo, versão Android, orientação, jogo/app escolhido e versão, distância entre celulares. Preencha resultados com `PASSOU`, `FALHOU` ou `NÃO TESTADO`; nenhum passo deste roteiro implica que já foi executado.

| Teste | Ação | Resultado esperado |
|---|---|---|
| Instalação | Controle no celular A, receptor no B | Nomes/ícones diferentes, interface abre em paisagem sem tela superior |
| Permissões negadas | Negar Dispositivos próximos | Mensagem de erro acionável, sem crash nem descoberta |
| Bluetooth desligado | Tentar buscar/conectar | Pedir ativação pelo diálogo do sistema |
| Descoberta | Tornar B visível, buscar em A | B aparece com estado de pareamento |
| Pareamento recusado | Recusar diálogo em B | Nenhum acesso; não conecta a aparelho não pareado |
| Pareamento aceito | Aceitar nos dois | B aparece como pareado, mas nenhum dado é liberado |
| Receptor fechado | Tentar conectar | Erro/timeout; não inicia escuta sozinho |
| Código comparado | Receber em B, conectar em A | Mesmo código de seis dígitos nos dois |
| Uma confirmação | Autorizar somente em A ou B | Sem dados/gestos/tela até a segunda confirmação |
| Categorias negadas | Deixar nome/modelo/bateria/tela/arquivos desmarcados | Não enviar essas categorias nem permitir UI correspondente |
| Recusa de sessão | Recusar em um aparelho | Sessão encerrada nos dois, códigos descartados |
| Teste de controles | Autorizar controles em Modo de teste | Dois pontos acompanham joysticks, estados A/B/X/Y mudam |
| Toque rápido | Tocar e soltar A rapidamente, incluindo com captura ativa | Último botão A é confirmado; pressão e soltura chegam em ordem |
| Diagnóstico sem controles | Conectar sem conceder Joystick / toques e gestos | Visor explica o bloqueio; tocar botão não transmite comando |
| Diagnóstico de Acessibilidade | Desativar o serviço durante sessão com app escolhido | Controle mostra a mensagem para ativar Acessibilidade; não há gestos externos |
| Callback Android | Abrir app autorizado, pressionar A calibrado | Diagnóstico mostra conclusão, cancelamento, recusa ou falha; conferir resposta do app separadamente |
| Versões diferentes | Tentar conectar 1.0.0 com 1.0.1 | Sessão recusada com instrução para atualizar os dois APKs |
| Multitoque | Segurar L e A, mover R junto | Entradas independentes, sem soltar controles por tocar outro |
| Soltar | Tirar dedos do controle | Estados voltam a zero e gestos terminam |
| App autorizado | Escolher/calibrar jogo, ativar serviço, abrir jogo | Toques surgem apenas no jogo escolhido |
| App não autorizado | Alternar B para outro aplicativo/launcher | Gestos bloqueados e toques anteriores liberados |
| Atraso de fila | Introduzir atraso acima de 250 ms e retomar conexão | Entradas antigas descartadas; nenhuma sequência atrasada executada |
| Bloqueio de tela | Bloquear B | Gestos bloqueados; captura segue as restrições do sistema |
| Acessibilidade desligada | Desativar serviço durante a sessão | Toques externos param; teste interno continua possível |
| Captura recusada | Recusar diálogo MediaProjection | Nenhuma imagem enviada; restante da sessão continua |
| Captura aprovada | Compartilhar somente app em Android 14+ | Imagem autorizada aparece no centro de A |
| Parar captura | Parar no sistema/no receptor | Central deixa de exibir captura; precisa de novo token para iniciar |
| Tela protegida | Abrir conteúdo FLAG_SECURE dentro do app escolhido | Proteção do Android mantida; nenhuma tentativa de bypass |
| Oferta recusada | Selecionar arquivo em A, recusar em B | Nenhum conteúdo salvo |
| Destino escolhido | Aceitar e escolher destino em B | Só então começa conteúdo; progresso e verificação final |
| Arquivo reverso | Enviar arquivo selecionado em B para A | Mesma confirmação de destino e integridade |
| Integridade | Comparar SHA-256 do arquivo original e recebido | Hashes iguais |
| Cancelamento | Cancelar no meio do envio | Streams fechados; documento parcial removido quando o provedor permite |
| Queda Bluetooth | Desligar Bluetooth/sair de alcance | Gestos neutralizados, serviços encerrados, estado erro/desconectado |
| Reconexão | Repetir Receber/Conectar | Novo código e nova escolha de categorias; sem retomar coleta |
| Revogação | Remover autorização ativa no Histórico | Sessão encerrada; fingerprint removido |
| Mudança de identidade | Reinstalar um lado mantendo autorização no outro | Identidade diferente recusada até remoção local da autorização |
| Notificação | Tocar Desconectar na notificação | Todos os canais e captura encerrados |
| Fechar/reiniciar | Remover app das recentes, reiniciar aparelho | Nenhuma escuta, coleta ou sessão iniciada automaticamente |
| Tela compacta | Verificar celular de menor resolução e fonte maior | Textos acessíveis por rolagem; diálogos e ações utilizáveis |

## Aceitação HID — versão 1.1.0

| Teste | Ação | Resultado esperado |
|---|---|---|
| Consentimento HID | Conectar sem marcar Gamepad Bluetooth HID | Nenhum registro, conexão HID ou relatório de controle |
| Recebimento nativo | Aprovar HID, deixar Visor aberto em B, pressionar A e mover L/R em A | Entradas NATIVAS em B aumentam; pontos e botões respondem por eventos Android |
| Botões extras | Pressionar L1/R1/L2/R2/SELECT/START/L3/R3 | Eventos correspondentes recebidos pelo Android e pelo jogo compatível |
| GameHub | Abrir GameHub depois do teste nativo | Configurar controle dentro da aplicação; registrar compatibilidade de cada botão/eixo |
| Bloqueio | Bloquear B durante uma pressão | Gate deixa de permitir dados ativos; neutralização em até 500 ms mais programação do sistema |
| Lease interrompida | Cortar o canal RFCOMM, mantendo HID temporariamente | Relatórios ativos param com expiração/revogação; nenhum controle continua por conta própria |
| Controle oculto | Tirar A de primeiro plano | Perfil desregistrado/pausado; retorno exige Iniciar gamepad Bluetooth |
| Troca de modo | Trocar HID para Toques em B | Sessão encerrada; nova aprovação obrigatória |
| Perfil indisponível | Executar em aparelho sem HID Device | Erro explícito, nenhum sucesso simulado |
| Outro host | Tentar conexão HID de um terceiro aparelho pareado | Desconectar host não autorizado, sem enviar comandos |

Os testes anteriores de gestos/Modo de teste são referentes ao modo Toques; selecione esse modo antes de executá-los. Para HID, o app escolhido é um atalho de abertura e o escopo autorizado é o app em primeiro plano.

## Medição no hardware

Registre pelo menos 60 segundos com comandos sem captura e depois com captura. Anote mediana e p95 do RTT indicado, sem confundir RTT com atraso de vídeo. Para medir o caminho completo, filme os dois celulares juntos com câmera de alta taxa de quadros e conte o intervalo entre o movimento no controle e a resposta visível no receptor. Teste a 1 m e 5 m, com/sem arquivo em transferência. Só depois desses resultados estabeleça uma meta de latência para aqueles aparelhos.
