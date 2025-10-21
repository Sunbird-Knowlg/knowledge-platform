package modules

import com.google.inject.AbstractModule
import org.sunbird.actors.{AuditHistoryActor, HealthActor, SearchActor}
import org.sunbird.telemetry.TelemetryGenerator
import org.apache.pekko.actor.typed.ActorRef
import play.api.libs.concurrent.PekkoGuiceSupport
import utils.ActorNames

class SearchModule extends AbstractModule with PekkoGuiceSupport {

    override def configure() = {
        super.configure()
        bindActor(classOf[HealthActor], ActorNames.HEALTH_ACTOR)
        bindActor(classOf[SearchActor], ActorNames.SEARCH_ACTOR)
        bindActor(classOf[AuditHistoryActor], ActorNames.AUDIT_HISTORY_ACTOR)
        TelemetryGenerator.setComponent("search-service")
        println("Initialized application actors for search-service")
    }

}
