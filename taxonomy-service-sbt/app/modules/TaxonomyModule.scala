package modules

import com.google.inject.AbstractModule
import org.sunbird.actors.{HealthActor, ObjectCategoryActor, ObjectCategoryDefinitionActor}
import play.libs.akka.AkkaGuiceSupport
import utils.ActorNames

class TaxonomyModule extends AbstractModule with AkkaGuiceSupport {

    override def configure() = {
        super.configure()
        bindActor(classOf[HealthActor], ActorNames.HEALTH_ACTOR)
        bindActor(classOf[ObjectCategoryActor], ActorNames.OBJECT_CATEGORY_ACTOR)
        bindActor(classOf[ObjectCategoryDefinitionActor], ActorNames.OBJECT_CATEGORY_DEFINITION_ACTOR)
        println("Initialized application actors for taxonomy service")
    }
}
