package modules

import com.google.inject.AbstractModule
import org.sunbird.actors.{HealthActor}
import play.libs.akka.AkkaGuiceSupport
import utils.ActorNames

class TaxonomyModule extends AbstractModule with AkkaGuiceSupport {

    override def configure() = {
        super.configure()
        bindActor(classOf[HealthActor], ActorNames.HEALTH_ACTOR)
        println("Initialized application actors for assessment-service")
    }
}
