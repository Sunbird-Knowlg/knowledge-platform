package modules

import com.google.inject.AbstractModule
import org.sunbird.actors.{CategoryActor, CategoryInstanceActor, FrameworkActor, HealthActor, LockActor, ObjectCategoryActor, ObjectCategoryDefinitionActor, TermActor}
import play.api.libs.concurrent.PekkoGuiceSupport
import taxonomy.utils.ActorNames

class TaxonomyModule extends AbstractModule with PekkoGuiceSupport {

    override def configure() = {
        super.configure()
        bindActor[HealthActor](ActorNames.HEALTH_ACTOR)
        bindActor[ObjectCategoryActor](ActorNames.OBJECT_CATEGORY_ACTOR)
        bindActor[ObjectCategoryDefinitionActor](ActorNames.OBJECT_CATEGORY_DEFINITION_ACTOR)
        bindActor[FrameworkActor](ActorNames.FRAMEWORK_ACTOR)
        bindActor[CategoryActor](ActorNames.CATEGORY_ACTOR)
        bindActor[CategoryInstanceActor](ActorNames.CATEGORY_INSTANCE_ACTOR)
        bindActor[TermActor](ActorNames.TERM_ACTOR)
        bindActor[LockActor](ActorNames.LOCK_ACTOR)
        println("Initialized application actors for taxonomy service")
    }
}
