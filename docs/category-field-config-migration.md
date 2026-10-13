# Making `primaryCategory` config-driven instead of hardcoded

## Goal

Right now, the field name the platform reads to look up a node's category (`primaryCategory`) is a literal string baked into a handful of shared functions. This doc lists every place that needs to change so a type (like `EnrichmentObject`) can declare its own field name (`enrichmentObjectType`) via config, while everything else keeps working exactly as it does today (default stays `primaryCategory`, nothing else breaks).

## The core idea

Add one optional key to any type's `config.json`:
```json
"categoryField": "enrichmentObjectType"
```
Introduce **one** shared helper, and change every hardcoded read site to call it instead of the literal string `"primaryCategory"`.

```scala
// New, in DefinitionNode.scala (or a new small shared object) — ontology-engine, graph-engine_2.13
def categoryFieldName(schemaName: String, version: String = "1.0"): String = {
  try {
    val validator = SchemaValidatorFactory.getInstance(schemaName.toLowerCase, version)
    if (validator.getConfig.hasPath("categoryField")) validator.getConfig.getString("categoryField")
    else "primaryCategory"
  } catch { case _: Exception => "primaryCategory" }
}
```
Defaults to `"primaryCategory"` if the config doesn't declare one, or if the lookup fails for any reason — existing types (Content, Collection, Question, ...) never notice this exists.

## Every hardcoded call site that needs to change

All in `ontology-engine` (confirmed by direct grep, real code, not guessed):

### `graph-engine_2.13/.../schema/DefinitionNode.scala` — 6 occurrences

1. **Line 25** — `validate(request, setDefaultValue)`:
   ```scala
   val objectCategoryDefinition = getObjectCategoryDefinition(
     request.getRequest.getOrDefault("primaryCategory", "").asInstanceOf[String], schemaName, ...)
   ```
   → replace `"primaryCategory"` with `categoryFieldName(schemaName, version)`.

2. **Line 96** — inside `validate(identifier, request)`:
   ```scala
   val primaryCategory = dbNode.getMetadata.getOrDefault("primaryCategory", "").asInstanceOf[String]
   ```
   → same swap, using that method's own `schemaName`.

3. **Line 140** — a third internal validate path, same pattern, reading off `node.getMetadata`.

4. **Line 274** — `validateContentNodes`:
   ```scala
   val ocd = ObjectCategoryDefinition(node.getMetadata.getOrDefault("primaryCategory", "")..., node.getObjectType, ...)
   ```
   → same swap, using `node.getObjectType` as the schemaName input.

5. **Lines 316–317** — the guard inside `getObjectCategoryDefinition`'s caller:
   ```scala
   if (null != request && request.containsKey("primaryCategory")) {
     val categoryName = request.get("primaryCategory").asInstanceOf[String]
   ```
   → needs the resolved field name available in this scope too (may need `schemaName` threaded into this helper if it isn't already a parameter here — check signature before editing).

### `graph-engine_2.13/.../path/DataSubGraph.scala` — 1 occurrence

**Line 98**:
```scala
val objectCategoryDefinition = DefinitionNode.getObjectCategoryDefinition(
  node.getMetadata.getOrDefault("primaryCategory", "")..., node.getObjectType.toLowerCase.replace("image", ""), ...)
```
→ same swap, `schemaName` already available as `node.getObjectType...`.

### `graph-engine_2.13/.../utils/NodeUtil.scala` — 1 occurrence

**Line 24** (inside whatever calls `getRelationDefinitionMap` — separate from the `relationObjectAttributes` function reverted earlier, a different read):
```scala
val objectCategoryDefinition = DefinitionNode.getObjectCategoryDefinition(
  node.getMetadata.getOrDefault("primaryCategory", "")..., node.getObjectType.toLowerCase.replace("image", ""), ...)
```
→ same swap.

## What does **not** need to change

- **`CategoryDefinitionValidator`, `ObjectCategoryDefinitionMap`, `ObjectCategoryDefinition`** — these already take the category *name* (the value, e.g. `"Transcript"`) as a plain parameter. They have no idea what field it came from. Nothing to change here — they're already generic.
- **`content-controllers/BaseController.scala`'s `setContentAndCategoryTypes`/`validatePrimaryCategory`** — this is Content's own `contentType↔primaryCategory` business mapping, a different concern entirely (it's about deriving one field's value from another, not about which key name identifies the category). Not part of this mechanism.
- **`EnrichmentObjectManager`'s `persist()`/`toResponse()` rename logic** — if this config-driven approach ships, this code goes away entirely. No more manual `metadata.remove("enrichmentObjectType"); metadata.put("primaryCategory", ...)` — the platform would read/write `enrichmentObjectType` directly, because that's what `categoryField` tells it to use.
- **`schemas/enrichmentobject/1.0/schema.json`/`config.json`** — stays exactly as already committed, with `enrichmentObjectType` as the real declared/required property. This is the one genuinely nice side effect: the `primaryCategory` schema fix becomes unnecessary if this direction is taken instead — add `"categoryField": "enrichmentObjectType"` to `config.json` and the schema is correct as-is.

## Net effort comparison

| | Hardcoded rename (current) | Config-driven `categoryField` |
|---|---|---|
| Files touched | 1 (our own manager) | 3 shared `ontology-engine` files + 1 new helper |
| Risk surface | None — fully contained to our new code | Every objectType's create/update/validate path, platform-wide |
| Schema.json | Needs the `primaryCategory` fix | Stays as originally committed |
| Manager code | Extra rename/reverse-rename logic, permanent | None — removed entirely |

The tradeoff: config-driven is architecturally cleaner and removes the schema-fix requirement, but it's real surgery on code every other objectType depends on today, versus a change fully contained to `EnrichmentObjectManager`.
