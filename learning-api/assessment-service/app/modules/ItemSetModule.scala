package modules

import actors.{HealthActor, ItemSetActor}
import com.google.inject.AbstractModule
import play.libs.akka.AkkaGuiceSupport
import utils.ActorNames

class ItemSetModule extends AbstractModule with AkkaGuiceSupport {

    override def configure() = {
        super.configure()
        bindActor(classOf[HealthActor], ActorNames.HEALTH_ACTOR)
        bindActor(classOf[ItemSetActor], ActorNames.ITEM_SET_ACTOR)
        println("Initialized application actors for assessment-service")
    }
}
