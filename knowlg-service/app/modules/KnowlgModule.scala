package modules

import com.google.inject.AbstractModule
import org.apache.pekko.routing.FromConfig
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
        // Actors bound with FromConfig take router size/dispatcher from pekko.actor.deployment in application.conf
        bindActor[ContentHealthActor](ActorNames.HEALTH_ACTOR, _.withRouter(FromConfig))
        bindActor[ContentActor](ActorNames.CONTENT_ACTOR, _.withRouter(FromConfig))
        bindActor[LicenseActor](ActorNames.LICENSE_ACTOR, _.withRouter(FromConfig))
        bindActor[CollectionActor](ActorNames.COLLECTION_ACTOR, _.withRouter(FromConfig))
        bindActor[EventActor](ActorNames.EVENT_ACTOR)
        bindActor[EventSetActor](ActorNames.EVENT_SET_ACTOR)
        bindActor[ChannelActor](ActorNames.CHANNEL_ACTOR, _.withRouter(FromConfig))
        bindActor[AssetActor](ActorNames.ASSET_ACTOR, _.withRouter(FromConfig))
        bindActor[AppActor](ActorNames.APP_ACTOR)
        bindActor[ObjectActor](ActorNames.OBJECT_ACTOR)
        bindActor[CollectionCSVActor](ActorNames.COLLECTION_CSV_ACTOR, _.withRouter(FromConfig))
        
        // Taxonomy actors from taxonomy-actors JAR
        bindActor[ObjectCategoryActor](ActorNames.OBJECT_CATEGORY_ACTOR, _.withRouter(FromConfig))
        bindActor[ObjectCategoryDefinitionActor](ActorNames.OBJECT_CATEGORY_DEFINITION_ACTOR, _.withRouter(FromConfig))
        bindActor[FrameworkActor](ActorNames.FRAMEWORK_ACTOR, _.withRouter(FromConfig))
        bindActor[CategoryActor](ActorNames.CATEGORY_ACTOR)
        bindActor[CategoryInstanceActor](ActorNames.CATEGORY_INSTANCE_ACTOR, _.withRouter(FromConfig))
        bindActor[TermActor](ActorNames.TERM_ACTOR, _.withRouter(FromConfig))
        bindActor[LockActor](ActorNames.LOCK_ACTOR, _.withRouter(FromConfig))
        
        // Assessment actors from assessment-actors JAR
        bindActor[AssessmentItemActor](ActorNames.ASSESSMENT_ITEM_ACTOR, _.withRouter(FromConfig))
        bindActor[ItemSetActor](ActorNames.ITEM_SET_ACTOR, _.withRouter(FromConfig))
        bindActor[QuestionActor](ActorNames.QUESTION_ACTOR, _.withRouter(FromConfig))
        bindActor[QuestionSetActor](ActorNames.QUESTION_SET_ACTOR, _.withRouter(FromConfig))
        bindActor[QuestionV5Actor](ActorNames.QUESTION_V5_ACTOR, _.withRouter(FromConfig))
        bindActor[QuestionSetV5Actor](ActorNames.QUESTION_SET_V5_ACTOR, _.withRouter(FromConfig))
        
        println("Initialized application actors from content-actors, taxonomy-actors, and assessment-actors JARs...")
        // $COVERAGE-ON
    }
}

