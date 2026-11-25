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
