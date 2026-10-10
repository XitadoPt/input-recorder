# Input Recorder — Forge 1.8.9

Mod de cliente para gravar e reproduzir os controles do Minecraft: WASD (ou as teclas configuradas), pulo, agachar, sprint, movimento da câmera, cliques esquerdo/direito/meio, Q/Ctrl+Q, seleção da hotbar por números/roda e perspectiva F5.

## Controles

| Tecla | Ação |
| --- | --- |
| F6 | Começar a gravar; apertar de novo salva e para |
| F7 | Reproduzir a última gravação ou a carregada |
| F8 | Parar a reprodução; se estiver gravando, salva e para |
| F9 | Ativar/desativar reprodução em loop, inclusive durante a reprodução |
| F10 | Abrir o menu de gravações |
| Esc | Abrir menu e encerrar a sessão ativa |

As teclas podem ser alteradas em **Opções → Controles → Input Recorder**. Um indicador no canto superior esquerdo mostra o estado e o tempo.

## Comandos

| Comando | Ação |
| --- | --- |
| `/rec` ou `/rec menu` | Abrir o menu de gravações |
| `/rec record` | Gravar com nome automático |
| `/rec record ponte` | Gravar com nome escolhido |
| `/rec play` | Reproduzir a última/carregada |
| `/rec play ponte` | Reproduzir uma gravação salva |
| `/rec loop` | Alternar o loop |
| `/rec loop on` | Ativar o loop para as próximas reproduções |
| `/rec loop off` | Desativar o loop |
| `/rec loop status` | Consultar se o loop está ativado |
| `/rec stop` | Parar e salvar, quando estiver gravando |
| `/rec list` | Listar gravações, mais recentes primeiro |
| `/rec load ponte` | Selecionar a gravação para o F7 |
| `/rec help` | Mostrar ajuda |

O comando também aceita `/recorder`. Use Tab para completar nomes. Nomes aceitam letras sem acentos, números, `_` e `-`, até 64 caracteres. Gravações existentes não são sobrescritas.

## Arquivos

As gravações ficam na pasta da instância do Minecraft em **`config/inputrecorder/recordings/*.json`**. Na instalação padrão: `%APPDATA%\.minecraft\config\inputrecorder\recordings`. A última gravação válida é carregada ao abrir o jogo. Você pode copiar esses arquivos entre instalações.
