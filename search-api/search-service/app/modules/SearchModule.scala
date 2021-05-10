package modules

import com.google.inject.AbstractModule
import org.sunbird.actors.{HealthActor, SearchActor}
import org.sunbird.telemetry.TelemetryGenerator
import play.libs.akka.AkkaGuiceSupport
import utils.ActorNames

class SearchModule extends AbstractModule with AkkaGuiceSupport {

    override def configure() = {
        super.configure()
        bindActor(classOf[HealthActor], ActorNames.HEALTH_ACTOR)
        bindActor(classOf[SearchActor], ActorNames.SEARCH_ACTOR)
        TelemetryGenerator.setComponent("search-service")
        println("Initialized application actors for search-service")
    }

}
