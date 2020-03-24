package modules

import com.google.inject.AbstractModule
import org.sunbird.search.health.HealthActor
import play.libs.akka.AkkaGuiceSupport

class SearchModule extends AbstractModule with AkkaGuiceSupport {

    override def configure() = {
        super.configure()
        bindActor(classOf[HealthActor], ActorNames.HEALTH_ACTOR)
        println("Initialized application actors for assessment-service")
    }

}
