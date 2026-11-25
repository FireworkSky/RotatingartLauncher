# Bunny Evolution — auditoria de integração do host Android

Data da auditoria: 2026-11-25  
Branch: `bunny-evolution-dev`  
Alvo do usuário: Terraria Android 1.4.4.9, sem instalação de Terraria para PC.

## Resultado

A branch contém peças úteis de um launcher Android/.NET, mas ainda não contém a integração de plataforma necessária para declarar o tModLoader Android funcional. O importador `.tmod` não é o carregador: ele valida e copia o pacote para a pasta de dados; quem precisa descobrir e executar o mod é o próprio tModLoader iniciado pelo host.

A referência mais próxima encontrada é [FireworkSky/tML-Pocket-Launcher](https://github.com/FireworkSky/tML-Pocket-Launcher), GPL-3.0. O repositório tem uma implementação Android dedicada, não apenas um detector de Terraria PE. A documentação do projeto informa uma sessão ARM64 chegando ao menu com Terraria PE 1.4.4.9 e tModLoader 2026.09.3.1. Isso é evidência do projeto de referência, não um teste do Bunny.

## Comparação técnica

| Área | Bunny nesta branch | Referência tML Pocket | Trabalho necessário |
|---|---|---|---|
| Host CoreCLR | Reutiliza `GameLauncher` e `DotNetLauncher` genéricos do RALauncher | `GameRuntime` prepara e inicia um host próprio | Integrar bootstrap específico do tML, sem presumir que iniciar qualquer DLL seja suficiente |
| Runtime ARM64 | Resolve runtime instalado/selecionado pelo RALauncher | Prepara runtime Android empacotado e valida hostfxr/CoreCLR | Definir e instalar runtime compatível com o `runtimeconfig.json` exato do tML |
| Bootstrap gerenciado | Não há equivalente validado ao host tML dedicado | `MobileLauncher.Host` e `MobileLauncher.Bootstrap` resolvem assemblies e entrada do tML | Portar/adaptar a sequência de bootstrap e o resolver de assemblies |
| Ambiente | Configuração genérica SDL/FNA do launcher-base | Define `TML_ANDROID`, raízes de conteúdo, dados XDG, backend SDL2, áudio e renderer | Comparar variáveis, caminhos e bibliotecas nativas com a release escolhida |
| Patches Android | Há hooks/patches do RALauncher, ainda não validados contra esta release tML | Patches tML específicos, controles touch e compatibilidade Android | Integrar patches compilados para a versão exata e testar entrada touch |
| Conteúdo PE | Conversor JNI foi trazido, com perfil auxiliar obrigatório | Converte APK instalado localmente e usa perfil de metadados para layouts/shaders | Validar inventário e geração de recursos sem exigir que o usuário tenha Terraria PC |
| Mods | Importa e valida `.tmod`; grava `enabled.json` | Repositório de mods integrado ao diretório de dados tML | Confirmar que o runtime reconhece o pacote, resolve dependências e carrega o mod |

## Arquivos de referência que merecem adaptação

- `android/app/src/main/java/app/com/tmlpocket/core/runtime/GameRuntime.kt`: ambiente de processo, preparação do host, CoreCLR e SDL/FNA.
- `android/app/src/main/java/app/com/tmlpocket/core/runtime/AssetBootstrap.kt`: preparação e validação do runtime e do host gerenciado.
- `android/app/src/main/java/app/com/tmlpocket/core/runtime/PreparedGame.kt`: contrato explícito de caminhos do jogo, runtime, conteúdo e dados.
- `managed/MobileLauncher.Bootstrap/Runtime/TmlLoaderEntrypoint.cs`: entrada específica do tModLoader.
- `managed/MobileLauncher.Bootstrap/Runtime/GameAssemblyResolver.cs`: resolução de assemblies do jogo.
- `managed/MobileLauncher.Bootstrap/Compatibility/`: adaptações de serviços de plataforma e conteúdo.
- `managed/MobileLauncher.Controls/`: controles e compatibilidade touch.
- `scripts/build/prepare-mobile-terraria-android.ps1` e `scripts/build/convert-mobile-terraria.py`: preparação e conversão de recursos.

Não copiar esses componentes às cegas. O projeto de referência é GPL-3.0; antes de incorporar código derivado, conferir compatibilidade de licença, preservar avisos e atribuição e manter as obrigações da licença aplicáveis.

## Perfil de conversão e requisito mobile-only

O perfil atualmente exigido pelo Bunny inclui:

- `images.tsv` com nomes e dimensões das texturas;
- inventário de sons e fontes;
- `TerrariaMusic.xgs` e `Sound Bank.xsb`;
- shaders compilados;
- pacote de fallback de texturas.

O script desta branch ainda recebe `TerrariaContent`, `TmlContent`, `ImageLayout` e `TextureFallback` como entradas de preparação. Portanto, a interface de conversão no Android não torna o processo mobile-only por si só: o perfil precisa existir antes da compilação do APK.

A referência também documenta a preparação de um perfil com metadados de uma instalação desktop, enquanto a conversão no dispositivo usa o APK PE instalado para extrair recursos de Unity. O requisito do Bunny deve ser mantido: o usuário não deve precisar instalar Terraria para PC. Isso exige gerar/validar os metadados auxiliares por um processo de desenvolvimento reproduzível, ou provar que podem ser derivados do APK PE e dos componentes públicos do runtime sem dados proprietários ausentes. Não marcar esta etapa como resolvida até haver teste real com apenas o Terraria Android instalado.

## Plano de integração e critérios de conclusão

1. Fixar uma release exata do tModLoader e extrair seu `runtimeconfig.json`, `deps.json`, lista de assemblies e requisitos nativos.
2. Comparar o runtime exigido com os runtimes ARM64 que o Bunny realmente instala e seleciona.
3. Adaptar o bootstrap específico do tML e o ambiente SDL/FNA/FAudio do host de referência para a arquitetura existente do Bunny.
4. Compilar patches Android contra as assemblies exatas da release, incluindo entrada touch.
5. Fechar a geração do perfil PE sem exigir Terraria PC no aparelho do usuário; validar conversão com o APK base e splits instalados.
6. Fazer o APK ARM64 compilar em CI e executar testes unitários/instrumentados.
7. No aparelho Android: detectar Terraria PE, converter recursos, abrir o menu tML, importar um mod pequeno e não relacionado a armas, confirmar no log que foi carregado, entrar em mundo, salvar, voltar ao menu e abrir novamente.

## Estado de validação em 2026-11-25

- O parser TMOD e o marcador de versão tiveram smoke checks isolados de lógica.
- Esses smoke checks não são um build Gradle nem uma execução do repositório completo.
- A API de Actions do repositório retornou `total_count: 0`; não há build CI a inspecionar.
- Não há APK Bunny validado em aparelho.
- Nenhum `.tmod` foi carregado pelo Bunny; não declarar compatibilidade de mods ainda.

**Regra de conclusão:** só considerar o objetivo atingido depois do critério 7, com logs e resultado observável no aparelho. Importar o arquivo, criar a pasta `Mods` ou abrir apenas o menu não basta.
