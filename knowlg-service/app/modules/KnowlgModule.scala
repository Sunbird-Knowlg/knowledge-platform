package modules

import com.google.inject.AbstractModule
import org.sunbird.channel.actors.ChannelActor
import org.sunbird.collectioncsv.actors.CollectionCSVActor
import org.sunbird.content.actors.{AppActor, AssetActor, CollectionActor, ContentActor, EventActor, EventSetActor, HealthActor => ContentHealthActor, LicenseActor, ObjectActor}
import org.sunbird.actors.{AssessmentItemActor, CategoryActor, CategoryInstanceActor, FrameworkActor, HealthActor => TaxonomyHealthActor, ItemSetActor, LockActor, ObjectCategoryActor, ObjectCategoryDefinitionActor, QuestionActor, QuestionSetActor, TermActor}
import org.sunbird.v5.actors.{QuestionActor => QuestionV5Actor, QuestionSetActor => QuestionSetV5Actor}
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
        
        // Assessment actors from assessment-actors JAR
        bindActor[AssessmentItemActor](ActorNames.ASSESSMENT_ITEM_ACTOR)
        bindActor[ItemSetActor](ActorNames.ITEM_SET_ACTOR)
        bindActor[QuestionActor](ActorNames.QUESTION_ACTOR)
        bindActor[QuestionSetActor](ActorNames.QUESTION_SET_ACTOR)
        bindActor[QuestionV5Actor](ActorNames.QUESTION_V5_ACTOR)
        bindActor[QuestionSetV5Actor](ActorNames.QUESTION_SET_V5_ACTOR)
        
        try {
            org.sunbird.graph.service.util.DriverUtil.closeConnections()
            println("DriverUtil: Closed existing graph connections to ensure fresh startup.")
        } catch {
            case e: Exception => println("DriverUtil: Error occurred while closing connections: " + e.getMessage)
        }
        
        println("Initialized application actors from content-actors, taxonomy-actors, and assessment-actors JARs...")
        // $COVERAGE-ON
    }
}

