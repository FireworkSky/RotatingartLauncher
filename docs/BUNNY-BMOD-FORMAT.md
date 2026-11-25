# Bunny Evolution — formato experimental .bmod

Este documento define um formato de pacote e uma área de staging local. **Não significa que os mods já são executados pelo Terraria Android.** O bridge de runtime para Terraria 1.4.4.9 ainda não existe.

## Estrutura

Um arquivo `.bmod` é um ZIP com `manifest.json` na raiz e os arquivos do mod em subpastas.

```json
{ "id": "exemplo.mod", "name": "Mod de exemplo", "version": "1.0.0", "author": "Autor", "gameVersion": "1.4.4.9", "loaderApi": 1, "description": "Pacote experimental Bunny" }
```

## O que já faz o repositório

- Importa pacotes `.bmod` para o armazenamento privado do Bunny.
- Valida ID, versão-alvo e API declarada.
- Protege contra Zip Slip, excesso de entradas e arquivos descompactados acima do limite.
- Lista, ativa/desativa e remove pacotes.
- Não executa assemblies desconhecidos nem injeta código no processo do Terraria.

## Bloqueio técnico que ainda falta resolver

O instalador desktop existente usa Terraria de PC e tModLoader Mono/FNA, com executáveis e assemblies do desktop. Isso não é um caminho de carregamento para o APK Android 1.4.4.9. O catálogo mantém `runtimeReady=false` de propósito: ativar um pacote no catálogo ainda não o torna ativo dentro do jogo.

A próxima etapa exige identificar o runtime gerenciado e o ponto de entrada do binário Android 1.4.4.9, definir a ABI/bridge e executar um mod de teste mínimo no jogo. Só depois disso a compatibilidade de mods tModLoader poderá ser avaliada.