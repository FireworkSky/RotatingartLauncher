# Bunny Evolution — Plano de desenvolvimento

## Objetivo

Transformar esta cópia do Universe-loader em uma base de desenvolvimento para o **Bunny Evolution**, um loader Android com identidade visual roxo/preto, voltado ao Terraria Android **1.4.4.9**.

O objetivo é iniciar o Terraria Android instalado legitimamente no dispositivo e desenvolver, em etapas, uma camada de compatibilidade para mods. O projeto não deve exigir que o usuário baixe uma instalação Linux de Terraria como fluxo padrão.

## Regras do projeto

- Não alterar a branch principal `main`; todo o trabalho inicial ocorre em `bunny-evolution-dev`.
- Não incluir nem redistribuir APK, assets ou arquivos proprietários do Terraria.
- O usuário deverá fornecer/ter uma instalação legítima do jogo; o loader deverá detectar ou importar os arquivos necessários de forma compatível com as regras da plataforma.
- Não declarar compatibilidade com um mod sem teste reproduzível.
- Não substituir o runtime atual antes de mapear dependências e validar uma alternativa.
- Priorizar ARM64, consumo de memória, tempo de inicialização e estabilidade em dispositivos intermediários.
- Preservar avisos de licença e atribuições dos componentes de terceiros.

## Alvo inicial

- Plataforma: Android.
- Jogo-alvo: Terraria Android 1.4.4.9.
- Arquitetura prioritária: arm64-v8a.
- Dispositivo de referência para testes de desempenho: Galaxy A07 / Helio G99.
- Identidade: Bunny Evolution, roxo e preto.

## Arquitetura proposta

1. **Bunny UI** — interface, biblioteca de mods, configurações e perfis de desempenho.
2. **Terraria Android Bridge** — detecção da instalação legítima, verificação da versão 1.4.4.9 e inicialização do jogo.
3. **Bunny Runtime** — componentes nativos e gerenciados necessários à execução; não presumir que o runtime desktop atual possa carregar diretamente o binário Android.
4. **Compatibility Layer** — adaptação incremental de APIs, carregamento de assemblies, gráficos, áudio, entrada touch e armazenamento.
5. **Mod Manager** — instalação, ativação, desativação, dependências, logs e recuperação após falhas.
6. **Performance Profiles** — opções configuráveis de resolução/renderização, limites e diagnóstico de memória, sem prometer ganhos não medidos.

## Etapas

### Etapa 0 — Cópia e auditoria
- [x] Criar branch de desenvolvimento separada.
- [ ] Mapear módulos Android, runtimes, bibliotecas nativas e patches existentes.
- [ ] Identificar exatamente quais partes são específicas do Terraria desktop/tModLoader e quais podem ser reutilizadas.
- [ ] Registrar licenças e componentes que exigem atenção.

### Etapa 1 — Inicialização do Terraria Android
- [ ] Identificar o fluxo de inicialização suportado pelo Terraria Android 1.4.4.9.
- [ ] Definir detecção/importação da instalação legítima.
- [ ] Criar uma inicialização sem mods, com logs e diagnóstico.
- [ ] Confirmar que o jogo original continua funcionando sem alterações destrutivas.

### Etapa 2 — Base de mods Android
- [ ] Determinar o formato e o runtime necessários para mods Android.
- [ ] Implementar carregamento isolado e reversível.
- [ ] Começar com um mod de teste pequeno e próprio.
- [ ] Criar logs de falha e validação de dependências.

### Etapa 3 — Compatibilidade tModLoader
- [ ] Fixar uma versão de tModLoader compatível com a base do jogo, após auditoria.
- [ ] Mapear APIs incompatíveis com Android/ARM64.
- [ ] Portar/adaptar componentes por módulos.
- [ ] Testar mods simples antes de avançar para mods grandes como Calamity, Thorium, Fargo's Souls e Magic Storage.

### Etapa 4 — Otimização e interface
- [ ] Aplicar identidade Bunny Evolution roxo/preto.
- [ ] Criar perfis de desempenho com opções mensuráveis.
- [ ] Medir inicialização, RAM, FPS e estabilidade em hardware real.

## Critérios de aceite

Uma etapa só será marcada como concluída quando houver alteração verificável no repositório e teste documentado. Compatibilidade de mods será registrada individualmente como: não testado, incompatível, parcial ou testado.

## Nota técnica importante

A documentação atual desta base descreve suporte a tModLoader 1.3 no caminho Mono/FNA e patches para outros caminhos de execução. Isso não comprova compatibilidade direta com Terraria Android 1.4.4.9. A primeira tarefa técnica é auditar os binários, pontos de entrada e dependências do alvo Android antes de reaproveitar qualquer caminho desktop.
