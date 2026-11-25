# Bunny Evolution — rota realista para tModLoader no Android

Branch: `bunny-evolution-dev`

## Decisão técnica

O objetivo "carregar DLLs tModLoader dentro do APK oficial Terraria Android" não é atendido pelo código atual e não deve ser implementado como injeção no processo de outro aplicativo. O caminho tecnicamente sustentado pela pesquisa é executar uma distribuição Android própria do tModLoader, com runtime ARM64, FNA/SDL adaptados e os assemblies do jogo preparados para Android.

Isso significa que Bunny Evolution deverá ser um launcher Android independente para tModLoader, não um injetor que transforma o app oficial instalado em tModLoader. O detector do Terraria oficial pode continuar existindo como recurso separado, mas abrir o app oficial não inicia o runtime de mods.

## Evidências no repositório atual

- `DotNetLauncher.hostfxrLaunch` inicia um assembly .NET por meio do hostfxr como aplicação do próprio launcher.
- `TModLoaderPatch/StartupHook.cs` usa `DOTNET_STARTUP_HOOKS` no processo .NET que o launcher inicia.
- `TerrariaInstallPlugin` instala distribuições desktop: arquivos ZIP/GOG, executáveis e bibliotecas de tModLoader desktop.
- `docs/TMODLOADER-1.3.md` documenta um caminho legado Mono + FNA para tModLoader 1.3.
- `BunnyModRepository` apenas importa e organiza .bmod; não carrega assemblies dentro do jogo.

Portanto, o runtime existente é uma base reutilizável, mas não constitui uma ponte para o processo do Terraria Android oficial.

## Arquitetura proposta

1. **Host Android próprio:** iniciar a sessão tModLoader como processo gerenciado hospedado pelo Bunny, com ABI ARM64.
2. **Game payload separado:** preparar localmente assemblies de tModLoader/Terraria e recursos de jogo obtidos legitimamente pelo usuário. Não embutir nem redistribuir arquivos proprietários no APK.
3. **Camada de plataforma:** adaptar SDL/FNA, renderização, áudio, ciclo de vida Android, entrada touch, teclado e caminhos de arquivos.
4. **Compatibilidade gerenciada:** compilar os patches contra a versão exata do payload escolhido; não reutilizar cegamente os patches do tModLoader 1.3.
5. **Mods:** instalar arquivos .tmod completos, validar dependências/versões e deixar o próprio tModLoader fazer a descoberta e o carregamento.
6. **Validação progressiva:** iniciar menu, entrar em mundo, salvar, testar um mod pequeno, depois testar mods grandes individualmente e medir memória/desempenho.

## Alvo de versão

O pedido atual é Terraria 1.4.4.9. A pesquisa encontrou projetos comunitários experimentais que executam tModLoader no Android por meio de um host ARM64 próprio; um deles relata ter chegado ao menu com recursos Terraria 1.4.4.9 e tModLoader 2026.09.3.1. Outro projeto relata uma sessão Android real com um mod de teste pequeno, mas usa Terraria 1.4.5.8. Esses resultados demonstram que a rota de um port Android independente é plausível; não demonstram que o APK oficial Android possa ser convertido em runtime tModLoader, nem que mods grandes funcionem.

## Compatibilidade dos mods pedidos

| Mod | Estado no Bunny |
|---|---|
| Calamity | Não testado; não declarar compatível |
| Thorium | Não testado; não declarar compatível |
| Fargo's Souls | Não testado; não declarar compatível |
| Magic Storage | Não testado; não declarar compatível |

Cada mod depende de versão do tModLoader, bibliotecas e requisitos próprios. A compatibilidade só pode ser declarada após carregar o pacote no host Android, entrar em mundo e verificar os sistemas principais do mod.

## Critérios para considerar a ponte pronta

- Build ARM64 reproduzível.
- Tela inicial do tModLoader abre no Android.
- Carregamento de um mod de teste .tmod confirmado por log.
- Entrada em mundo, salvamento e retorno ao menu sem crash.
- Relatório de RAM, tempo de carregamento e erros.
- Testes individuais dos quatro mods solicitados.

## Limites desta etapa

Este documento registra a mudança de arquitetura e evita uma falsa promessa de injeção no app oficial. Nenhuma DLL de mod foi executada por esta alteração; nenhum APK foi compilado ou validado em aparelho. A próxima implementação deve migrar/adaptar o host Android tModLoader, não marcar .bmod como runtime funcional.


## Atualização de implementação — conversão de Terraria PE

Foi localizado um caminho comunitário mais específico: **FireworkSky/tML-Pocket-Launcher** (GPL-3.0), que documenta uma sessão em aparelho Android ARM64 chegando ao menu com Terraria 1.4.4.9 e tModLoader 2026.09.3.1. O projeto também contém uma conversão de recursos da instalação PE para o formato de conteúdo esperado pelo tModLoader desktop. Referências:

- Projeto: https://github.com/FireworkSky/tML-Pocket-Launcher
- Guia PE: https://github.com/FireworkSky/tML-Pocket-Launcher/blob/main/docs/terraria-pe.md
- Guia de desenvolvimento e validação: https://github.com/FireworkSky/tML-Pocket-Launcher/blob/main/docs/development.md

### O que a conversão realmente faz

A conversão não copia o APK para dentro de outro APK nem injeta o tModLoader no processo oficial. Ela lê o APK base e os APKs divididos instalados pelo Android, encontra os contêineres Unity (`data.unity3d` e `resources.resource`) e converte os recursos necessários para o formato de conteúdo PC que o tModLoader/FNA espera. O jogo modificado é iniciado pelo runtime próprio do launcher.

O perfil de conversão usado pelo projeto de referência contém metadados de texturas, shaders compilados, inventário de sons/fontes e fallback de texturas. Esse perfil é preparado a partir de uma instalação desktop legítima de Terraria e da versão exata do tModLoader. Portanto, não é correto prometer uma conversão completa usando apenas o APK Android sem antes resolver e validar esses recursos auxiliares.

### Trabalho já feito nesta branch

- `TerrariaAndroidDetector` enumera o APK base e os APKs divididos da instalação oficial.
- Verifica a assinatura UnityFS de `assets/bin/Data/data.unity3d` e a presença de `assets/bin/Data/resources.resource`.
- Foram adicionados testes unitários para assinatura inválida, ausência de stream e recursos distribuídos entre APK base e split APKs.
- O conversor C++/JNI do projeto de referência foi portado para `core/mobile-terraria`, incluindo módulos Unity, XNB, LZX, FSB5 e arte de controles.
- O CMake tem uma opção para compilar o conversor e suas dependências nativas somente na variante preparada com perfil.
- O Gradle aceita `-PbunnyTerrariaProfile=<arquivo.zip>`, valida os componentes obrigatórios e inclui a implementação Kotlin opcional.
- A conversão PE roda em thread de trabalho, valida espaço livre e grava o resultado em armazenamento privado.
- O Bunny consulta a publicação oficial do tModLoader correspondente ao perfil, baixa `tModLoader.zip`, valida tamanho e SHA-256 publicado pelo GitHub e instala o runtime numa pasta separada.
- A instalação cria as pastas `Mods` e `ModPacks`, registra o jogo no repositório e preserva o aplicativo Terraria original.
- A tela permite importar um arquivo `.tmod` para a pasta que o tModLoader usará.

### Próximas validações obrigatórias

1. Gerar o perfil local de conversão com recursos legítimos de Terraria PC e tModLoader 2026.09.3.1.
2. Compilar o source set opcional e a biblioteca nativa no CI; corrigir qualquer erro de Kotlin/CMake/NDK.
3. Gerar o APK com perfil real e instalar em aparelho ARM64.
4. Confirmar conversão completa do APK PE, menu do tModLoader, controles, áudio e renderização.
5. Importar um mod pequeno real, verificar o log de carregamento, entrar num mundo e salvar.
6. Medir RAM e FPS no Galaxy A07 antes de testar mods maiores.

**Estado honesto:** a cadeia de conversão, download e instalação foi implementada no código, mas ainda não foi compilada nem executada nesta sessão. O perfil real não está no repositório porque contém dados auxiliares derivados de arquivos proprietários. Ainda não existe evidência de um mod carregado pelo APK Bunny; não declarar o projeto pronto até os testes acima passarem.


## Correção obrigatória: instalação somente Mobile

A revisão do código em 24/11/2026 confirmou que a conversão atualmente commitada **ainda não atende** ao requisito de usar somente Terraria Mobile:

- `MobileTerrariaConverter.prepareProfile` exige `PixelShader.xnb`, `ScreenShader.xnb`, `TileShader.xnb`, `TerrariaMusic.xgs`, `Sound Bank.xsb`, `images.tsv`, `assets.json` e `texture-fallback.zip` dentro de um perfil.
- `scripts/build/prepare-bunny-terraria-profile.ps1` ainda exige explicitamente `TerrariaContent` de uma instalação desktop para inventário de sons/fontes e bancos XACT.
- O conversor nativo usa o inventário para decidir quais XNBs produzir e usa os bancos XACT para reconstruir a música. Apenas extrair `data.unity3d` e `resources.resource` do APK não cria esses metadados.
- O workflow anterior tentava compilar usando `build/converter-smoke-profile.zip`, que não é gerado por nenhum passo do workflow. Isso não é um teste válido de conversão.

### Próxima implementação mobile-only

1. Inspecionar os `TextAsset`, `Texture2D` e `AudioClip` presentes nos bundles do próprio APK e gerar um inventário de recursos mobile sem usar uma pasta Terraria desktop.
2. Obter os shaders e demais componentes de compatibilidade apenas de artefatos públicos do tModLoader quando a licença e o formato permitirem; não assumir que o ZIP de release contém arquivos ausentes sem inspecioná-lo.
3. Reconstruir metadados de música a partir de uma tabela de cues compatível com a versão exata, validando cada faixa disponível no APK. Não copiar bancos proprietários de uma instalação PC.
4. Fazer o conversor produzir um relatório de cobertura: recursos convertidos, recursos opcionais ausentes e recursos obrigatórios ausentes. Se faltar um recurso obrigatório, parar antes de instalar e mostrar o nome exato.
5. Só depois compilar o perfil gerado, abrir o menu do tModLoader e carregar um mod de teste pequeno.

**Critério de bloqueio:** até os passos acima existirem e serem testados, a tela não deve sugerir que a instalação mobile-only está pronta. O perfil antigo permanece experimental e não pode ser tratado como solução para quem possui apenas Terraria Mobile.

## Atualização de 24/11/2026 — primeiro conserto real no caminho de mods

A revisão do importador encontrou um erro que impedia a instalação de mods tModLoader reais: o código tratava arquivos `.tmod` como ZIP comum usando `ZipFile`. O formato TMOD tem cabeçalho próprio (`TMOD`), versão do loader, hash, tabela de arquivos e área de dados; portanto, a validação anterior rejeitaria pacotes reais.

Correções enviadas para `bunny-evolution-dev`:

- Criado `TmodArchive.kt` para ler e validar o cabeçalho e a tabela do formato TMOD, conferir o tamanho do payload, exigir a entrada `Info` e verificar o SHA-1 antes de instalar.
- O importador agora salva o arquivo usando o nome interno do mod, em vez de confiar no nome escolhido pelo arquivo do usuário.
- Depois da instalação, o importador atualiza `Mods/enabled.json`, que é a lista de mods habilitados lida pelo tModLoader.
- Criados testes unitários para pacote TMOD válido, arquivo ZIP comum disfarçado de `.tmod` e payload alterado.

Commits:
- `b87f4aa` — parser do formato TMOD.
- `12d251a` — importação validada e ativação do mod.
- `a126ce8` — testes unitários do parser.

**O que isso resolve:** o Bunny deixa de confundir .tmod com ZIP e passa a preparar corretamente um pacote para o tModLoader.

**O que ainda não prova:** o runtime Android ainda precisa iniciar com os recursos convertidos, reconhecer a pasta Mods, carregar um mod pequeno, entrar em mundo e salvar. Nenhum mod foi executado nesta etapa; não considerar o loader pronto ainda.

### Próxima prova de execução

1. Fazer o CI compilar Kotlin, JNI/CMake e executar `TmodArchiveTest`.
2. Preparar um perfil de conversão compatível com o APK Mobile 1.4.4.9 sem exigir que o usuário tenha Terraria PC instalado no aparelho.
3. Confirmar o caminho de dados efetivamente usado pelo runtime no arranque Android.
4. Instalar um mod de teste pequeno e confirmar no log que o tModLoader o carrega, depois entrar em mundo e salvar.
## Atualização de 24/11/2026 — alinhamento da pasta de mods e interface mobile-only

- O importador foi alinhado com o ambiente real do GameLauncher: ele define XDG_DATA_HOME como /storage/emulated/0/RALauncher quando o armazenamento compartilhado está disponível. Portanto, a pasta esperada é RALauncher/Terraria/tModLoader/Mods; se o launcher cair para o diretório do assembly, o caminho alternativo é relativo a game/tModLoader.
- A instalação agora tenta movimentação atômica e usa substituição normal como fallback quando o sistema de arquivos não oferece suporte a ATOMIC_MOVE.
- A tela não diz mais que o usuário precisa obter Terraria para PC. Ela informa que o perfil mobile-only ainda não foi gerado e que a preparação deve partir dos arquivos do Terraria Mobile.
- Foi consultado o caminho documentado pelo tModLoader para Linux: os dados ficam em $XDG_DATA_HOME/Terraria/tModLoader, com os mods dentro de Mods. Isso sustenta o caminho usado pelo launcher quando XDG_DATA_HOME está configurado.

**Validação pendente:** o GitHub Actions continua sem registrar execução para a branch (workflow_runs vazio e status sem checks). Assim, os commits mais recentes ainda não foram compilados nem tiveram os testes executados. O runtime mobile-only também continua sem perfil gerado. Não existe ainda prova de mod simples carregado em jogo.

## Atualização de 25/11/2026 — referência Android com mod carregado

Foi revisada a documentação atual do projeto comunitário FireworkSky/tML-Pocket-Launcher. Ela relata que um aparelho ARM64 chegou ao menu usando Terraria 1.4.4.9, tModLoader 2026.09.3.1 e o mod cliente MobileJoystick. Isso é uma referência técnica concreta de que um host Android próprio pode iniciar o tModLoader e carregar ao menos um mod integrado; não prova que o Bunny já faça isso nem que qualquer arquivo .tmod externo funcione.

Fontes consultadas:
- https://github.com/FireworkSky/tML-Pocket-Launcher
- https://github.com/FireworkSky/tML-Pocket-Launcher/blob/main/docs/development.md
- https://github.com/FireworkSky/tML-Pocket-Launcher/blob/main/docs/terraria-pe.md

A documentação de desenvolvimento também confirma dois requisitos distintos que não podem ser confundidos:
- O APK padrão não inclui o conversor PE. A variante com conversor precisa de um perfil de build.
- O perfil de conversão de referência ainda é preparado com inventário/layout de texturas, shaders compilados, inventário de sons/fontes e metadados XACT obtidos de referências desktop. Portanto, esse projeto de referência também não resolve automaticamente o requisito de gerar tudo somente com o Terraria Mobile.

### Direção de implementação no Bunny

1. Portar o host Android ARM64 e os patches móveis necessários para tModLoader 2026.09.3.1, respeitando as licenças dos componentes.
2. Integrar um mod de teste mínimo compilado para a versão exata do host e registrar no log a confirmação de carregamento.
3. Substituir as dependências de inventário desktop por geração/validação a partir do APK PE e dos artefatos públicos compatíveis do tModLoader. Não aceitar conversão parcial como completa.
4. Executar o APK em aparelho ARM64, entrar em mundo, salvar e reiniciar. Só então testar importação de um .tmod externo.

**Estado em 25/11/2026:** foi identificada uma referência comunitária que relata menu Android e um mod cliente carregado, mas o Bunny ainda não incorporou esse host nem provou carregar um mod. O perfil mobile-only continua ausente. Não existe teste em Galaxy A07 nem APK Bunny validado.

## Atualização de 25/11/2026 — compatibilidade de mods e verificação de versão

O importador agora consulta um marcador criado ao final da instalação móvel (bunny-runtime.json, ao lado de tModLoader.dll) e compara a versão exata do runtime com a versão gravada no cabeçalho TMOD. Um mod feito para outra versão é recusado antes de entrar na lista de habilitados. O importador também deixa de substituir silenciosamente um enabled.json inválido por uma lista vazia.

O leitor TMOD passou a rejeitar versões de loader malformadas, caminhos internos absolutos ou com segmentos . ou .., nomes duplicados e conteúdo descompactado acima do limite definido. Os testes unitários foram ampliados para cobrir versão inválida e caminho inseguro.

Commits desta etapa:
- 6f6ddb6 — grava a versão exata do runtime depois da instalação.
- 57d8fb7 — bloqueia importação de mods com versão incompatível.
- a562e26 — valida o campo de versão do cabeçalho TMOD.
- 8e4fe72 — corrige a ordem de resolução do diretório da instalação.
- ae4fbf0 — valida nomes e limites das entradas TMOD.
- ed6dae5 — adiciona teste para caminho interno inseguro.

### Bloqueios que continuam abertos

1. O perfil de conversão mobile-only ainda não é gerado. O conversor atual depende de inventário de sons/fontes, layout de imagens, shaders e metadados XACT que o perfil atual recebe de referências desktop.
2. O host de jogo Android do tML Pocket Launcher ainda não foi portado para esta base Bunny. A simples instalação do ZIP oficial e o lançamento de tModLoader.dll pelo GameLauncher não provam que SDL/FNA/FAudio, ciclo de vida Android, recursos e entrada estejam compatíveis.
3. A workflow do branch está configurada para compilar APK e executar testes após alterações em app/** e core/**, mas a API do GitHub ainda retorna zero execuções e nenhum status de CI para os commits desta etapa. Portanto, não há compilação/teste confirmados.
4. Não existe APK Bunny testado em aparelho, nem log de um mod simples carregado.

**Decisão:** não considerar o Bunny pronto para mods. O próximo marco de código é portar/adaptar o host Android funcional de referência e substituir o perfil de conversão desktop por geração a partir dos recursos do APK PE e de componentes públicos compatíveis. Depois disso, compilar, iniciar, carregar um mod de teste pequeno, entrar em mundo e salvar no aparelho.

### Ajuste de diagnóstico — host existente versus host validado

A base Bunny já possui um host genérico .NET/SDL e bibliotecas nativas SDL, FNA3D e FAudio, além de configuração de CoreCLR e startup hooks. Portanto, não é correto dizer que não existe host Android algum. O que ainda falta é validar esse conjunto especificamente com a distribuição tModLoader escolhida, aplicar/confirmar os patches Android compatíveis com essa versão e comprovar o ciclo completo de jogo.

A instalação também passou a preparar a lista de habilitação antes de substituir o pacote importado, para que um enabled.json inválido não deixe o novo mod instalado em estado parcial.

Commits adicionais:
- d2e2ca1 — centraliza a comparação de versão do mod com o runtime.
- 13997e4 — usa a validação centralizada no importador.
- 6c03708 — testa rejeição de mod com versão diferente.
- 72e6777 — prepara a lista de mods antes de substituir o arquivo importado.

**Validação ainda pendente:** não foi possível executar Gradle/NDK nesta sessão. A consulta ao GitHub Actions continua retornando zero execuções para o branch, e não há APK nem log de jogo. A próxima afirmação de prontidão só deve ocorrer depois que a compilação e a execução do teste simples forem confirmadas.

### Verificação isolada dos componentes Kotlin

O marcador de runtime foi extraído para TerrariaRuntimeMarker, sem dependências Android: ele grava a versão junto do assembly, valida o formato e impede que um caminho canônico saia da pasta da instalação. Foram adicionados testes unitários para gravação/leitura, versão inválida e caminho externo.

Nesta sessão, versões isoladas equivalentes do parser TMOD e do marcador foram compiladas com kotlinc e passaram por smoke checks com arquivos sintéticos. Isso confirma a lógica básica reproduzida nesses checks, mas **não equivale** a compilar as fontes do projeto nem substitui Gradle, JNI/NDK, APK completo ou teste de jogo real.

Commits:
- ad4da36 — usa o marcador após instalar o runtime.
- f64aeb3 — consulta a versão pelo marcador.
- 4703e38 — adiciona o marcador isolado.
- 9396644 — testa gravação, leitura e validação de caminhos do marcador.
