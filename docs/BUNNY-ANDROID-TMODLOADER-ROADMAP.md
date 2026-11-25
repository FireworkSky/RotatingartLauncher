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
