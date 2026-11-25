# Bunny Evolution — Auditoria inicial da base Android

Branch: `bunny-evolution-dev`

## O que foi confirmado no código

| Área | Estado encontrado | Consequência para o Bunny Evolution |
|---|---|---|
| Módulo Android | `app`, Kotlin + Jetpack Compose | Pode servir como ponto de partida para a interface e serviços Android. |
| Identificador atual | `com.app.ralaunch` | Ainda é o identificador do launcher-base; não foi renomeado nesta auditoria. |
| SDK | minSdk 28, compile/target SDK 36 | A base já exige Android 9 ou superior. |
| ABI | Build configurado somente para `arm64-v8a` | Alinha-se ao primeiro alvo ARM64, mas precisa de validação no aparelho real. |
| Renderização | CMake e bibliotecas SDL/FNA3D/FAudio/GL4ES via submódulos | Há infraestrutura gráfica desktop-portável; isso não prova compatibilidade com o renderer do Terraria Android. |
| Runtime | O README declara .NET 10 e Mono; há integração de execução de assemblies | O runtime atual foi pensado para jogos .NET/Mono, não foi comprovado como substituto do runtime interno do app Terraria Android. |
| Entrada do app | `InitializationActivity` abre o launcher; atividades de jogo usam orientação landscape e processo `:game` | O fluxo atual inicia uma sessão de jogo própria do launcher, não uma integração comprovada com o pacote Terraria Android. |
| tModLoader | `docs/TMODLOADER-1.3.md` descreve um caminho específico Mono + FNA para tModLoader 1.3 | A documentação não comprova suporte a tModLoader moderno nem ao Terraria Android 1.4.4.9. |

## Conclusão técnica

A base oferece bastante infraestrutura reutilizável para launcher, controles touch, logs, bibliotecas nativas e execução de jogos Mono/FNA. Porém, a rota documentada para Terraria/tModLoader é desktop/Mono/FNA. Não há evidência suficiente, nesta auditoria, de que ela consiga carregar diretamente o Terraria Android 1.4.4.9 ou seus assemblies.

Portanto, não devemos simplesmente trocar o nome do aplicativo, copiar DLLs de mods ou iniciar o runtime atual esperando que o APK Android funcione. O ponto de integração precisa ser identificado primeiro.

## Próxima investigação obrigatória

1. Inspecionar o código de instalação/lançamento do Terraria e o plugin de instalação tModLoader.
2. Verificar se o código atual baixa, extrai ou espera executáveis/assemblies de desktop.
3. Identificar o fluxo legítimo de detecção do app Terraria Android instalado, sem copiar nem redistribuir APKs ou assets proprietários.
4. Determinar quais pontos de extensão permitem iniciar o jogo Android e quais exigiriam um processo separado.
5. Só então implementar o primeiro protótipo: detectar a instalação/versão do Terraria Android 1.4.4.9 e apresentar diagnóstico claro, sem modificar o jogo.

## Limitações desta auditoria

Foi feita leitura estática de arquivos do repositório. Não foi executado Gradle, não foi compilado APK e não foi testado em dispositivo Android. A versão 1.4.4.9 permanece como alvo solicitado, não como compatibilidade já validada.
