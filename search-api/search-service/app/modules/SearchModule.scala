package modules

import com.google.inject.AbstractModule
import org.sunbird.actors.{AuditHistoryActor, EnrichActor, HealthActor, SearchActor}
import org.sunbird.telemetry.TelemetryGenerator
import play.api.libs.concurrent.PekkoGuiceSupport
import utils.ActorNames

class SearchModule extends AbstractModule with PekkoGuiceSupport {

    override def configure() = {
        super.configure()
        bindActor[HealthActor](ActorNames.HEALTH_ACTOR)
        bindActor[SearchActor](ActorNames.SEARCH_ACTOR)
        bindActor[AuditHistoryActor](ActorNames.AUDIT_HISTORY_ACTOR)
        bindActor[EnrichActor](ActorNames.ENRICH_ACTOR)
        TelemetryGenerator.setComponent("search-service")
        println("Initialized application actors for search-service")
    }

}
