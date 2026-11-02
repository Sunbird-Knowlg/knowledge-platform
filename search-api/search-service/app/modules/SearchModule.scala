package modules

import com.google.inject.AbstractModule
import org.apache.pekko.routing.FromConfig
import org.sunbird.actors.{AuditHistoryActor, EnrichActor, HealthActor, SearchActor}
import org.sunbird.telemetry.TelemetryGenerator
import play.api.libs.concurrent.PekkoGuiceSupport
import utils.ActorNames

class SearchModule extends AbstractModule with PekkoGuiceSupport {

    override def configure() = {
        super.configure()
        bindActor[HealthActor](ActorNames.HEALTH_ACTOR)
        // Router (type, size, dispatcher) comes from pekko.actor.deployment."/SearchActor" in application.conf
        bindActor[SearchActor](ActorNames.SEARCH_ACTOR, _.withRouter(FromConfig))
        bindActor[AuditHistoryActor](ActorNames.AUDIT_HISTORY_ACTOR)
        bindActor[EnrichActor](ActorNames.ENRICH_ACTOR)
        TelemetryGenerator.setComponent("search-service")
        println("Initialized application actors for search-service")
    }

}
