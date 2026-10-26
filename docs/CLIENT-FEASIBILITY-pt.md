# Relatório de Viabilidade do Cliente — MultiCore Mod

> **Entrega da Fase 5 (apenas relatório).** Nenhum código de cliente é integrado até este relatório
> ser revisto e aprovado. Decisão necessária antes de qualquer implementação.

## 1. Contexto

O mod tem como alvo **servidores dedicados Fabric** (Linux/Windows) hoje. O utilizador pediu, como
objetivo futuro: *"passar tbm a tentar ir para o minecraft de cliente"* (tentar suportar também o
Minecraft de cliente).

O cliente e o servidor partilham o mesmo codebase (o servidor integrado corre na JVM do cliente), mas
a **render thread** e o **game loop do cliente** têm restrições que o servidor dedicado não tem.

## 2. O que já funciona no cliente hoje (sem alterações)

- O mod é enviado como `environment: "*"` e usa o entrypoint comum `main`, por isso **já carrega no
  cliente**.
- Os eventos do lado servidor (`ServerLifecycleEvents`, `ServerTickEvents`) **disparam no
  singleplayer**, porque o servidor integrado é um `MinecraftServer` real. O worker pool, config,
  métricas e comando `/multicore` funcionam todos no singleplayer.
- O ecrã do Mod Menu funciona no cliente (singleplayer/LAN/dev).
- `ModCompatibility.scan()` corre também no cliente (vê os mods carregados no cliente).

Por isso, **no singleplayer, o mod já faz tudo o que faz num servidor dedicado.** A questão em
aberto é apenas se podemos ir mais além: offload de trabalho client-specific (chunk meshing,
paralelismo na render thread, etc.).

## 3. O que é viável no cliente

| Área | Viabilidade | Notas |
|------|-------------|-------|
| Servidor integrado (singleplayer) | ✅ Já funciona | Sem alterações; o entrypoint `main` cobre |
| Host LAN (host-and-play) | ✅ Já funciona | Igual ao servidor integrado |
| Paralelismo de chunk loading/meshing | 🔶 Moderada | O Sodium já faz isto na render thread; o nosso offload poderia complementar, mas a render thread tem de ser a owner de qualquer trabalho GL |
| Pré-cálculo de pathfinding / AI | ✅ Seguro | Computação pura, sem dependência GL; igual ao servidor |
| Mod menu / config | ✅ Já funciona | Já implementado |
| Networking do cliente (fan-out de pacotes) | 🔶 Moderada | O cliente envia menos pacotes que o servidor; ganho limitado |

## 4. O que NÃO é viável no cliente (restrições duras)

1. **A render thread.** Todo o trabalho OpenGL tem de acontecer na render thread. Não podemos
   paralelizar a renderização em si; podemos apenas offload da *preparação* (construção de mesh,
   culling, ordenação de chunks) e devolver os resultados à render thread antes de o frame ser
   desenhado.
2. **O game loop do cliente.** O cliente tica à taxa de frames (até 60+ Hz), não a 20 TPS. O nosso
   modelo de scheduling de regiões assume uma fronteira de tick a 20 TPS; o cliente não tem essa
   fronteira no mesmo sentido.
3. **Mods do cliente.** A maioria dos mods do cliente (rendering, UI, input) **não** é thread-safe e
   não pode ser movida para fora da render thread. O modo SAFE protegê-los-ia, mas o ganho seria
   mínimo.
4. **Sem `DedicatedServerModInitializer` no cliente.** Já evitámos isto usando o entrypoint comum
   `main` (D-013).

## 5. Recomendação

**NÃO implementar features client-specific por agora.** O mod já funciona no singleplayer porque o
servidor integrado é um servidor real. As oportunidades client-specific restantes (chunk meshing,
preparação de render) já são cobertas pelo Sodium e estão fora do nosso scope (não somos um mod de
rendering).

**Revisitar apenas se:**
- O benchmark (T-023) mostrar que o trabalho do lado servidor é o bottleneck mesmo no singleplayer.
- Um hot spot client-specific (ex: ordenação de chunks) for identificado por profiling.
- O utilizador pedir explicitamente depois da versão de servidor dedicado estar estável.

## 6. Se aprovado mais tarde (Phase 5 proper)

Um milestone separado com o seu próprio plano, cobrindo:
- Um entrypoint `client` (apenas se necessário) para offloads client-specific.
- Scheduling consciente da render thread (fork-join dentro de um frame, não por-tick).
- Um teste de viabilidade numa máquina cliente de baixo desempenho.

## 7. Decisão necessária

- **A (recomendada):** manter o mod server-side only; singleplayer já funciona via servidor
  integrado. Sem código de cliente.
- **B:** investir em offloads client-specific (preparação de render, ordenação de chunks). Risco
  alto, ganho esperado baixo dado o Sodium.

Recomendação por defeito: **A**.