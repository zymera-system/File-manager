# Definição Oficial do Escopo — FileManager

## Objetivo

O FileManager será um gerenciador de arquivos Android moderno, rápido, estável, robusto e preparado para evoluir continuamente.

Seu objetivo é oferecer gerenciamento completo do sistema de arquivos do dispositivo, utilizando uma interface própria, simples, intuitiva, consistente e eficiente.

O foco deste projeto é unir uma identidade visual exclusiva com funcionalidades reais do Android.

A interface do aplicativo é um dos pilares do projeto e deve ser preservada integralmente durante todo o desenvolvimento.

Toda evolução deverá ocorrer na camada de lógica, mantendo o design original.

Este aplicativo não tem como objetivo substituir aplicativos especializados de edição, organização inteligente ou consumo avançado de mídia.

Sua responsabilidade é gerenciar arquivos e diretórios com excelência.

---

# Arquitetura Geral

O projeto é dividido em duas responsabilidades completamente independentes.

## Interface

A interface do aplicativo é a identidade oficial do projeto.

Ela contém:

- HTML
- CSS
- SVGs
- animações
- layout
- componentes
- identidade visual
- experiência do usuário

Ela deve permanecer como a fonte oficial da interface.

Não deve ser modificada para acomodar funcionalidades.

---

## Lógica

Toda funcionalidade deverá ser implementada na camada de lógica.

Incluindo:

- Java
- Kotlin
- JavaScript
- Bridges Android ↔ WebView
- APIs nativas
- Sistema de permissões
- Integrações

A lógica deve adaptar-se ao design.

Nunca o contrário.

---

# Regra Geral para todos os módulos

Cada categoria existente na Home representa um módulo funcional do FileManager.

Não implemente apenas uma tela contendo informações.

Cada categoria deverá ser desenvolvida como um módulo completo, utilizando dados reais do dispositivo.

Sempre que existir uma implementação equivalente no APK base utilizado como referência técnica, ela deverá ser estudada profundamente e adaptada ao meu aplicativo.

O APK base deve servir apenas como referência funcional e arquitetural.

A interface jamais deverá ser copiada.

Caso alguma funcionalidade existente no APK base não possua representação visual no meu projeto, desenvolva uma nova interface inspirada exclusivamente no padrão visual do meu aplicativo.

Os novos componentes devem seguir exatamente:

- identidade visual
- tipografia
- espaçamentos
- bordas
- animações
- tamanhos
- comportamento
- linguagem visual

O usuário nunca deverá perceber que aquele componente foi adicionado posteriormente.

Ele deve parecer parte do projeto desde o início.

Toda nova funcionalidade deverá ser integrada naturalmente ao meu design.

---

# Categorias da Home

## Armazenamento Principal

Este módulo deverá fornecer todas as funcionalidades relacionadas ao armazenamento principal do dispositivo.

Utilizar exclusivamente dados reais.

Adaptar toda funcionalidade ao meu design.

---

## Cartão SD / Dispositivos USB

Quando existir:

- detectar automaticamente
- montar corretamente
- permitir navegação
- permitir operações completas

Quando não existir:

informar ao usuário de forma elegante, utilizando meu padrão visual.

---

## Downloads

Implementar um módulo completo para gerenciamento da pasta Downloads.

Toda funcionalidade deverá utilizar arquivos reais.

---

## Imagens

Implementar um módulo completo para gerenciamento das imagens existentes no dispositivo.

---

## Áudios

Implementar um módulo completo para gerenciamento dos arquivos de áudio.

---

## Vídeos

Implementar um módulo completo para gerenciamento dos vídeos.

---

## Documentos

Implementar um módulo completo para gerenciamento de documentos.

---

## Aplicativos

Quero um módulo completo para gerenciamento dos aplicativos instalados no dispositivo.

Não quero apenas uma lista de aplicativos.

Quero que você estude profundamente como o APK base implementa esse módulo e adapte todas as funcionalidades ao meu aplicativo.

Utilize as APIs nativas do Android, como PackageManager, sempre que necessário.

Caso meu design ainda não possua algum elemento visual necessário para essa funcionalidade, crie novos componentes seguindo exatamente a identidade visual do meu aplicativo.

Não copie a interface do APK base.

Adapte apenas as funcionalidades.

Os novos componentes deverão parecer parte do projeto original.

---

## Novos Arquivos (Recentes)

Implementar um módulo utilizando arquivos modificados recentemente.

Sempre utilizando dados reais.

---

## Nuvem

Ainda não será implementada.

Criar apenas a estrutura necessária para futura integração.

Nenhuma lógica de sincronização deverá ser implementada neste momento.

---

## Acesso Remoto

Ainda não será implementado.

Criar apenas a estrutura preparada para futuras implementações.

---

## Lixeira

Implementar uma lixeira real.

Sempre que possível, os arquivos excluídos deverão ser enviados para a lixeira antes da exclusão definitiva.

---

# Operações suportadas

O aplicativo deverá permitir:

- abrir
- copiar
- mover
- renomear
- excluir
- compartilhar
- criar pasta
- criar arquivo
- seleção múltipla
- selecionar tudo
- limpar seleção
- favoritos
- pesquisa
- propriedades

Todas essas operações deverão utilizar o sistema de arquivos real.

---

# Reprodução de mídia

O aplicativo deverá possuir:

- player de vídeo integrado
- player de áudio integrado

Esses players deverão ser simples, rápidos e leves.

Seu objetivo é permitir que o usuário visualize ou reproduza arquivos diretamente dentro do FileManager.

Não é objetivo competir com aplicativos especializados.

Sempre que possível utilizar componentes nativos do Android para obter maior desempenho e compatibilidade.

A interface dos players deverá seguir o mesmo padrão visual do restante do aplicativo.

---

# Engenharia de implementação

Sempre que uma funcionalidade existir no APK base:

1. estudar profundamente sua arquitetura;
2. compreender como ela funciona;
3. compreender o fluxo completo;
4. compreender as permissões utilizadas;
5. compreender o tratamento de erros;
6. compreender a comunicação entre módulos;
7. compreender como ela interage com o Android;
8. somente depois adaptar essa funcionalidade ao meu projeto.

Não copiar implementações superficialmente.

Quero uma reimplementação profissional baseada no conhecimento adquirido.

---

# Fora do escopo

Não implementar neste projeto:

- organização inteligente por IA;
- classificação automática;
- editor de texto;
- editor de imagens;
- editor de vídeo;
- editor de áudio;
- visualizadores avançados de documentos;
- análises estatísticas;
- dashboards complexos;
- gerenciamento avançado de mídia.

Esses recursos poderão ser desenvolvidos futuramente em aplicações específicas.

---

# Regras de arquitetura

- Preservar integralmente o design original.
- Não alterar HTML sem necessidade.
- Não alterar CSS sem necessidade.
- Não alterar SVGs sem necessidade.
- Não alterar animações sem necessidade.
- Não alterar layout sem necessidade.
- Não alterar a Home sem necessidade.

Toda evolução deverá ocorrer prioritariamente na camada de lógica.

Toda informação exibida deverá ser proveniente do dispositivo.

Jamais utilizar dados mockados como resultado final.

Cada funcionalidade deverá:

- ser implementada;
- compilada;
- testada;
- validada;
- integrada.

Somente após isso iniciar a próxima implementação.

Evitar regressões.

Nenhuma funcionalidade já concluída poderá deixar de funcionar após novas alterações.

Sempre manter um sistema interno de diagnóstico durante o desenvolvimento.

Esse sistema deverá registrar:

- erros Java;
- erros JavaScript;
- chamadas das Bridges;
- permissões;
- exceções;
- logs;
- stack traces.

Os logs deverão poder ser visualizados, copiados e exportados facilmente.

Esse sistema somente será removido quando todo o aplicativo estiver estável.

---

# Objetivo Final

Construir um gerenciador de arquivos Android profissional, moderno, robusto, modular e preparado para evoluir durante muitos anos.

A interface deverá continuar sendo exclusivamente a identidade visual do meu projeto.

As funcionalidades deverão atingir o mesmo nível de maturidade de um gerenciador de arquivos profissional, utilizando exclusivamente dados reais do dispositivo.

O resultado final deve ser um aplicativo com identidade própria, arquitetura limpa, excelente desempenho, alta estabilidade e preparado para futuras expansões, mantendo sempre a separação entre interface e lógica e garantindo que toda nova funcionalidade seja integrada naturalmente ao design original.
