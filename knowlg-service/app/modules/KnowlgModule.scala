package modules

import com.google.inject.AbstractModule
import org.sunbird.channel.actors.ChannelActor
import org.sunbird.collectioncsv.actors.CollectionCSVActor
import org.sunbird.content.actors.{AppActor, AssetActor, CollectionActor, ContentActor, EventActor, EventSetActor, HealthActor => ContentHealthActor, LicenseActor, ObjectActor}
import org.sunbird.actors.{CategoryActor, CategoryInstanceActor, FrameworkActor, HealthActor => TaxonomyHealthActor, LockActor, ObjectCategoryActor, ObjectCategoryDefinitionActor, TermActor}
import play.api.libs.concurrent.PekkoGuiceSupport
import utils.ActorNames

class KnowlgModule extends AbstractModule with PekkoGuiceSupport {

    override def configure() = {
        // $COVERAGE-OFF$ Disabling scoverage as this code is impossible to test
        super.configure()
        
        // Content actors from content-actors JAR
        bindActor[ContentHealthActor](ActorNames.HEALTH_ACTOR)
        bindActor[ContentActor](ActorNames.CONTENT_ACTOR)
        bindActor[LicenseActor](ActorNames.LICENSE_ACTOR)
        bindActor[CollectionActor](ActorNames.COLLECTION_ACTOR)
        bindActor[EventActor](ActorNames.EVENT_ACTOR)
        bindActor[EventSetActor](ActorNames.EVENT_SET_ACTOR)
        bindActor[ChannelActor](ActorNames.CHANNEL_ACTOR)
        bindActor[AssetActor](ActorNames.ASSET_ACTOR)
        bindActor[AppActor](ActorNames.APP_ACTOR)
        bindActor[ObjectActor](ActorNames.OBJECT_ACTOR)
        bindActor[CollectionCSVActor](ActorNames.COLLECTION_CSV_ACTOR)
        
        // Taxonomy actors from taxonomy-actors JAR
        bindActor[ObjectCategoryActor](ActorNames.OBJECT_CATEGORY_ACTOR)
        bindActor[ObjectCategoryDefinitionActor](ActorNames.OBJECT_CATEGORY_DEFINITION_ACTOR)
        bindActor[FrameworkActor](ActorNames.FRAMEWORK_ACTOR)
        bindActor[CategoryActor](ActorNames.CATEGORY_ACTOR)
        bindActor[CategoryInstanceActor](ActorNames.CATEGORY_INSTANCE_ACTOR)
        bindActor[TermActor](ActorNames.TERM_ACTOR)
        bindActor[LockActor](ActorNames.LOCK_ACTOR)
        
        println("Initialized application actors from content-actors and taxonomy-actors JARs...")
        // $COVERAGE-ON
    }
}

