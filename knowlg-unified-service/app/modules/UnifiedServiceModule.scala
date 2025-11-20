package modules

import com.google.inject.AbstractModule
import org.apache.pekko.actor.ActorRef
import org.sunbird.channel.actors.ChannelActor
import org.sunbird.collectioncsv.actors.CollectionCSVActor
import org.sunbird.content.actors.{AppActor, AssetActor, CollectionActor, ContentActor, EventActor, EventSetActor, LicenseActor, ObjectActor}
import org.sunbird.actors.{AuditHistoryActor, SearchActor}
import org.sunbird.actors.{CategoryActor, CategoryInstanceActor, FrameworkActor, LockActor, ObjectCategoryActor, ObjectCategoryDefinitionActor, TermActor}
import org.sunbird.telemetry.TelemetryGenerator
import play.api.libs.concurrent.PekkoGuiceSupport
import utils.ActorNames

class UnifiedServiceModule extends AbstractModule with PekkoGuiceSupport {

    override def configure() = {
        super.configure()
        
        // Shared Health Actor (single instance for all services)
        bindActor[org.sunbird.content.actors.HealthActor](ActorNames.HEALTH_ACTOR)
        
        // Content Service Actors
        bindActor[ContentActor](ActorNames.CONTENT_ACTOR)
        bindActor[LicenseActor](ActorNames.LICENSE_ACTOR)
        bindActor[CollectionActor](ActorNames.COLLECTION_ACTOR)
        bindActor[EventActor](ActorNames.EVENT_ACTOR)
        bindActor[EventSetActor](ActorNames.EVENT_SET_ACTOR)
        bindActor[ChannelActor](ActorNames.CHANNEL_ACTOR)
        bindActor[org.sunbird.content.actors.CategoryActor](ActorNames.CATEGORY_ACTOR)
        bindActor[AssetActor](ActorNames.ASSET_ACTOR)
        bindActor[AppActor](ActorNames.APP_ACTOR)
        bindActor[ObjectActor](ActorNames.OBJECT_ACTOR)
        bindActor[CollectionCSVActor](ActorNames.COLLECTION_CSV_ACTOR)
        
        // Search Service Actors
        bindActor[SearchActor](ActorNames.SEARCH_ACTOR)
        bindActor[AuditHistoryActor](ActorNames.AUDIT_HISTORY_ACTOR)
        
        // Taxonomy Service Actors
        bindActor[ObjectCategoryActor](ActorNames.OBJECT_CATEGORY_ACTOR)
        bindActor[ObjectCategoryDefinitionActor](ActorNames.OBJECT_CATEGORY_DEFINITION_ACTOR)
        bindActor[FrameworkActor](ActorNames.FRAMEWORK_ACTOR)
        bindActor[CategoryActor](ActorNames.TAXONOMY_CATEGORY_ACTOR)
        bindActor[CategoryInstanceActor](ActorNames.CATEGORY_INSTANCE_ACTOR)
        bindActor[TermActor](ActorNames.TERM_ACTOR)
        bindActor[LockActor](ActorNames.LOCK_ACTOR)
        
        // Set telemetry component
        TelemetryGenerator.setComponent("knowlg-unified-service")
        println("Initialized application actors for unified knowledge service")
    }
}