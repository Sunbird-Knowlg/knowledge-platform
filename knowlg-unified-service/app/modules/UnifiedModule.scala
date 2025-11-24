package modules

import com.google.inject.AbstractModule
import org.sunbird.actors.assessment.{AssessmentItemActor, ItemSetActor, QuestionActor, QuestionSetActor}
import org.sunbird.actors.{HealthActor, FrameworkActor, CategoryActor, CategoryInstanceActor, LockActor, ObjectCategoryActor, ObjectCategoryDefinitionActor, TermActor, SearchActor, AuditHistoryActor}
import org.sunbird.channel.actors.ChannelActor
import org.sunbird.content.actors.{AssetActor, CollectionActor, ContentActor, EventActor, EventSetActor, LicenseActor, ObjectActor, AppActor}
import org.sunbird.collectioncsv.actors.CollectionCSVActor
import org.sunbird.v5.actors.{QuestionActor => QuestionV5Actor, QuestionSetActor => QuestionSetV5Actor}
import play.api.libs.concurrent.PekkoGuiceSupport
import utils.ActorNames

class UnifiedModule extends AbstractModule with PekkoGuiceSupport {

    override def configure() = {
        // Health Actor
        bindActor[HealthActor](ActorNames.HEALTH_ACTOR)
        
        // Assessment Actors
        bindActor[ItemSetActor](ActorNames.ITEM_SET_ACTOR)
        bindActor[AssessmentItemActor](ActorNames.ASSESSMENT_ITEM_ACTOR)
        bindActor[QuestionActor](ActorNames.QUESTION_ACTOR)
        bindActor[QuestionSetActor](ActorNames.QUESTION_SET_ACTOR)
        
        // Assessment V5 Actors - proper V5 implementations
        bindActor[QuestionV5Actor](ActorNames.QUESTION_V5_ACTOR)
        bindActor[QuestionSetV5Actor](ActorNames.QUESTION_SET_V5_ACTOR)
        
        // Taxonomy Actors
        bindActor[LicenseActor](ActorNames.LICENSE_ACTOR)
        bindActor[LockActor](ActorNames.LOCK_ACTOR)
        bindActor[ObjectActor](ActorNames.OBJECT_ACTOR)
        bindActor[ObjectCategoryActor](ActorNames.OBJECT_CATEGORY_ACTOR)
        bindActor[ObjectCategoryDefinitionActor](ActorNames.OBJECT_CATEGORY_DEFINITION_ACTOR)
        bindActor[TermActor](ActorNames.TERM_ACTOR)
        bindActor[FrameworkActor](ActorNames.FRAMEWORK_ACTOR)
        bindActor[CategoryActor](ActorNames.CATEGORY_ACTOR)
        bindActor[ChannelActor](ActorNames.CHANNEL_ACTOR)
        bindActor[CategoryInstanceActor](ActorNames.CATEGORY_INSTANCE_ACTOR)
        
        // Content Actors
        bindActor[ContentActor](ActorNames.CONTENT_ACTOR)
        bindActor[CollectionActor](ActorNames.COLLECTION_ACTOR)
        bindActor[AssetActor](ActorNames.ASSET_ACTOR)
        bindActor[AppActor](ActorNames.APP_ACTOR)
        bindActor[EventActor](ActorNames.EVENT_ACTOR)
        bindActor[EventSetActor](ActorNames.EVENT_SET_ACTOR)
        bindActor[CollectionCSVActor](ActorNames.COLLECTION_CSV_ACTOR)
        
        // Search Actors
        bindActor[SearchActor](ActorNames.SEARCH_ACTOR)
        bindActor[AuditHistoryActor](ActorNames.AUDIT_HISTORY_ACTOR)
        
        println("Initialized application actors for knowlg-unified-service")
        println("Assessment actors: ItemSetActor, QuestionActor, QuestionSetActor, AssessmentItemActor, QuestionV5Actor, QuestionSetV5Actor")
        println("Taxonomy actors: FrameworkActor, CategoryActor, ChannelActor, TermActor, LockActor, ObjectCategoryActor, etc.")
        println("Content actors: ContentActor, CollectionActor, AssetActor, AppActor, EventActor, EventSetActor, CollectionCSVActor")
        println("Search actors: SearchActor, AuditHistoryActor")
        println("All actors properly bound - no more HealthActor placeholders!")
    }
}