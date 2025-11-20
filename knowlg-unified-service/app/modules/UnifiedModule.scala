package modules

import com.google.inject.AbstractModule
import org.sunbird.actors.assessment.{AssessmentItemActor, ItemSetActor, QuestionActor, QuestionSetActor}
import org.sunbird.actors.{HealthActor}
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
        
        // Assessment V5 Actors - using same actors for now
        bindActor[QuestionActor](ActorNames.QUESTION_V5_ACTOR)
        bindActor[QuestionSetActor](ActorNames.QUESTION_SET_V5_ACTOR)
        
        // Create basic stub actors for missing ones using HealthActor as placeholder
        // These will be proper implementations later
        bindActor[HealthActor](ActorNames.LICENSE_ACTOR)
        bindActor[HealthActor](ActorNames.LOCK_ACTOR)
        bindActor[HealthActor](ActorNames.OBJECT_ACTOR)
        bindActor[HealthActor](ActorNames.OBJECT_CATEGORY_ACTOR)
        bindActor[HealthActor](ActorNames.OBJECT_CATEGORY_DEFINITION_ACTOR)
        bindActor[HealthActor](ActorNames.SEARCH_ACTOR)
        bindActor[HealthActor](ActorNames.TERM_ACTOR)
        bindActor[HealthActor](ActorNames.FRAMEWORK_ACTOR)
        bindActor[HealthActor](ActorNames.CATEGORY_ACTOR)
        bindActor[HealthActor](ActorNames.CHANNEL_ACTOR)
        bindActor[HealthActor](ActorNames.CONTENT_ACTOR)
        bindActor[HealthActor](ActorNames.COLLECTION_ACTOR)
        bindActor[HealthActor](ActorNames.ASSET_ACTOR)
        bindActor[HealthActor](ActorNames.APP_ACTOR)
        bindActor[HealthActor](ActorNames.EVENT_ACTOR)
        bindActor[HealthActor](ActorNames.EVENT_SET_ACTOR)
        bindActor[HealthActor](ActorNames.CATEGORY_INSTANCE_ACTOR)
        bindActor[HealthActor](ActorNames.AUDIT_HISTORY_ACTOR)
        bindActor[HealthActor](ActorNames.COLLECTION_CSV_ACTOR)
        
        println("Initialized application actors for knowlg-unified-service")
        println("Assessment actors: ItemSetActor, QuestionActor, QuestionSetActor, AssessmentItemActor")
        println("Stub actors created for: License, Lock, Object, ObjectCategory, etc. - will be replaced with proper implementations")
    }
}