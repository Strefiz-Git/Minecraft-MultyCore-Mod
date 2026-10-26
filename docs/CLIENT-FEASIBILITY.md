# Client Feasibility Report — MultiCore Mod

> **Phase 5 deliverable (report only).** No client code is merged until this report is reviewed
> and approved. Decision required before any implementation.

## 1. Context

The mod targets **dedicated Fabric servers** (Linux/Windows) today. The user asked, as a future
goal: *"passar tbm a tentar ir para o minecraft de cliente"* (try to support the client-side
Minecraft too).

The client and the server share the same codebase (the integrated server runs on the client's JVM),
but the **render thread** and the **client-side game loop** have constraints that the dedicated
server does not.

## 2. What already works on the client today (no changes needed)

- The mod ships as `environment: "*"` and uses the common `main` entrypoint, so it **loads on the
  client** already.
- The server-side events (`ServerLifecycleEvents`, `ServerTickEvents`) **fire in singleplayer**,
  because the integrated server is a real `MinecraftServer`. The worker pool, config, metrics and
  `/multicore` command all work in singleplayer.
- The Mod Menu config screen works on the client (singleplayer/LAN/dev).
- `ModCompatibility.scan()` runs on the client too (it sees the mods loaded on the client).

So, **in singleplayer, the mod already does everything it does on a dedicated server.** The open
question is only whether we can go further: offload work that is client-specific (chunk meshing,
render-thread parallelism, etc.).

## 3. What is viable on the client

| Area | Viability | Notes |
|------|-----------|-------|
| Integrated server (singleplayer) | ✅ Already works | No changes needed; the `main` entrypoint covers it |
| LAN host (host-and-play) | ✅ Already works | Same as integrated server |
| Chunk loading/meshing parallelism | 🔶 Moderate | Sodium already does this on the render thread; our offload could complement it, but the render thread must be the owner for any GL work |
| Pathfinding / AI precompute | ✅ Safe | Pure computation, no GL dependency; same as server |
| Mod menu / config | ✅ Already works | Already implemented |
| Client-side networking (packet fan-out) | 🔶 Moderate | The client sends fewer packets than the server; limited gain |

## 4. What is NOT viable on the client (hard constraints)

1. **The render thread.** All OpenGL work must happen on the render thread. We cannot parallelise
   rendering itself; we can only offload *preparation* (mesh building, culling, chunk sorting) and
   hand results back to the render thread before the frame is drawn.
2. **The client game loop.** The client ticks at the render frame rate (up to 60+ Hz), not 20 TPS.
   Our region-scheduling model assumes a 20 TPS tick boundary; the client has no such boundary in
   the same sense.
3. **Client mods.** Most client mods (rendering, UI, input) are *not* thread-safe and cannot be
   moved off the render thread. SAFE mode would protect them, but the gain would be minimal.
4. **No `DedicatedServerModInitializer` on the client.** We already avoided this by using the
   common `main` entrypoint (D-013).

## 5. Recommendation

**Do NOT implement client-specific features yet.** The mod already works in singleplayer because the
integrated server is a real server. The remaining client-specific opportunities (chunk meshing,
render prep) are already covered by Sodium and are out of our scope (we are not a rendering mod).

**Revisit only if:**
- The benchmark (T-023) shows that the server-side work is the bottleneck even in singleplayer.
- A specific client-side hot spot (e.g. chunk sorting) is identified by profiling.
- The user explicitly asks for it after the dedicated-server version is stable.

## 6. If approved later (Phase 5 proper)

A separate milestone with its own plan, covering:
- A `client` entrypoint (only if needed) for client-specific offloads.
- Render-thread-aware scheduling (fork-join within a frame, not per-tick).
- A feasibility test on a low-end client machine.

## 7. Decision needed

- **A (recommended):** keep the mod server-side only; singleplayer already works via the integrated
  server. No client code.
- **B:** invest in client-specific offloads (render prep, chunk sorting). High risk, low expected
  gain given Sodium.

Default recommendation: **A**.
