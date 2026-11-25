# Bunny Evolution — preparar o perfil de conversão PE

O conversor Android está separado por build flag para que APKs comuns não incluam o código nativo pesado. Para compilar a variante com conversão Terraria PE, é obrigatório fornecer um perfil local compatível com a versão de tModLoader escolhida.

## Requisitos

- Windows com PowerShell 7 ou Windows PowerShell.
- Android NDK e CMake usados pelo projeto.
- Uma instalação legítima de Terraria para PC, versão compatível com o alvo 1.4.4.9.
- Uma instalação precompilada de tModLoader para Terraria 1.4.4.9 (o alvo documentado na referência é tModLoader 2026.09.3.1).
- A pasta Content de Terraria, a pasta Content do tModLoader, o manifesto de dimensões de imagens `images.tsv` e o ZIP de texturas de fallback.
- Pelo menos 2 GiB livres no dispositivo durante a conversão.

O perfil contém metadados e recursos auxiliares derivados das instalações de jogo usadas pelo desenvolvedor. Não inclua APK, executáveis ou conteúdo proprietário de Terraria no repositório ou no APK distribuído.

## Preparar o perfil

O script abaixo cria o ZIP que o Gradle injeta na variante com conversor:

```powershell
.scripts\build\prepare-bunny-terraria-profile.ps1 `
  -OutputPath build\bunny-terraria-profile.zip `
  -TargetTml 2026.09.3.1 `
  -TerrariaContent '<caminho para Terraria PC Content>' `
  -TmlContent '<caminho para tModLoader Content>' `
  -ImageLayout '<caminho para images.tsv>' `
  -TextureFallback '<caminho para texture-fallback.zip>'
```

O fallback deve conter somente texturas XNB ausentes na extração PE e encontradas nas instalações locais de Terraria/tModLoader. O script `prepare-bunny-terraria-textures.ps1` valida os caminhos e cria esse ZIP a partir de um relatório de conversão.

## Compilar

```powershell
.gradlew.bat :app:testDebugUnitTest :app:assembleDebug `
  -PbunnyTerrariaProfile="$((Resolve-Path build\bunny-terraria-profile.zip).Path)"
```

Sem `-PbunnyTerrariaProfile`, o build usa a implementação de segurança que informa que o conversor não está disponível. Isso evita gerar um APK que mostre um botão de conversão funcional sem conter o perfil e a biblioteca nativa.

## O que a conversão faz

1. Detecta o pacote oficial e enumera APK base e split APKs.
2. Confirma os contêineres Unity esperados.
3. Lê os recursos Unity localmente, sem modificar o APK instalado.
4. Converte imagens, fontes, efeitos sonoros e banco de música para os formatos de conteúdo que o runtime FNA/tModLoader espera.
5. Valida o resultado e o grava no armazenamento privado do Bunny.

**Limite atual:** esta etapa prepara os recursos convertidos. Ainda é necessário conectá-los à instalação isolada do tModLoader e ao GameLauncher, depois validar menu, mundo, salvamento e um mod real no aparelho. Não considerar o Bunny pronto para jogar até esses testes passarem.
