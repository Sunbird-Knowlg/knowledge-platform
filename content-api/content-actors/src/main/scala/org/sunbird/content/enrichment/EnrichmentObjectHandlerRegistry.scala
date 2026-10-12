package org.sunbird.content.enrichment

/** Dispatch table for EnrichmentObjectHandler, keyed by objectType. Adding a
 * new Enrichment child type (e.g. Summary) means writing one new handler and
 * adding one line here — no other generic code changes.
 */
object EnrichmentObjectHandlerRegistry {

  private val handlers: Map[String, EnrichmentObjectHandler] = Map(
    TranscriptObjectHandler.objectType -> TranscriptObjectHandler
  )

  def get(objectType: String): Option[EnrichmentObjectHandler] = handlers.get(objectType)
}
