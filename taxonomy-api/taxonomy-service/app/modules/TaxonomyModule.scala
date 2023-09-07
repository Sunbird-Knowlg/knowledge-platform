package modules

import com.google.inject.AbstractModule
import org.sunbird.actors.{CategoryActor, CategoryInstanceActor, HealthActor, ObjectCategoryActor, ObjectCategoryDefinitionActor, FrameworkActor, TermActor}
import play.libs.akka.AkkaGuiceSupport
import utils.ActorNames

class TaxonomyModule extends AbstractModule with AkkaGuiceSupport {

    override def configure() = {
        super.configure()
        bindActor(classOf[HealthActor], ActorNames.HEALTH_ACTOR)
        bindActor(classOf[ObjectCategoryActor], ActorNames.OBJECT_CATEGORY_ACTOR)
        bindActor(classOf[ObjectCategoryDefinitionActor], ActorNames.OBJECT_CATEGORY_DEFINITION_ACTOR)
        bindActor(classOf[FrameworkActor], ActorNames.FRAMEWORK_ACTOR)
        bindActor(classOf[CategoryActor], ActorNames.CATEGORY_ACTOR)
        bindActor(classOf[CategoryInstanceActor], ActorNames.CATEGORY_INSTANCE_ACTOR)
        bindActor(classOf[TermActor], ActorNames.TERM_ACTOR)
        println("Initialized application actors for taxonomy service")
    }
}
