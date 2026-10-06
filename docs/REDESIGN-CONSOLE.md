# Redesign de interface — console portátil (parte inferior)

Arquivos alterados (somente UI, compartilhados pelos flavors `controller` e `receiver`):

- `ui/Hardware.kt` (novo): carcaça, junta/bisel, tela embutida (rebaixo + moldura + vidro), cavidades (`drawWell`), sombras suaves.
- `ui/Gamepad.kt`: novo desenho de `AnalogStick`, `PressControl` (ABXY), `DPad`, `AuxControl`. **A lógica de ponteiros/callbacks e as assinaturas não mudaram.**
- `ui/ThorScreen.kt`: nova composição proporcional à carcaça; tela central com duas páginas (swipe).
- `test/.../UiRenderTest.kt`: testes atualizados para a nova navegação + teste de swipe.

Não foram alterados: `applicationId`, manifests, serviços, transporte, segurança, ViewModel, dados.

## Luz
Fonte única no canto superior esquerdo: realces em cima/esquerda, sombras projetadas para baixo/direita; dentro de cavidades o contrário.

## Tela central
- **MENU DO SISTEMA** (arrastar para a DIREITA): status (RTT, bateria, canal) e atalhos: Visor, Conectar, Arquivos, Histórico, Ajustes, Privacidade. Cada painel abre dentro da tela, com botão de voltar.
- **SEGUNDA TELA** (arrastar para a ESQUERDA): o antigo "Visor" (espelho da tela do receptor no Controle, diagnóstico dos controles, métricas, ações rápidas).
- A página acompanha o dedo; ao soltar, conclui se passou de ~22% do arrasto, senão volta. Toques e rolagem vertical dentro das páginas continuam funcionando.
- Quando um controle bloqueado é tocado, a tela desliza sozinha para a SEGUNDA TELA, onde está o diagnóstico (antes ia para a aba Visor).
