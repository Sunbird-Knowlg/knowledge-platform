package modules

import com.google.inject.AbstractModule
import org.apache.pekko.routing.FromConfig
import content.utils.ActorNames
import org.sunbird.channel.actors.ChannelActor
import org.sunbird.collectioncsv.actors.CollectionCSVActor
import org.sunbird.content.actors._
import play.api.libs.concurrent.PekkoGuiceSupport

class ContentModule extends AbstractModule with PekkoGuiceSupport {

    override def configure() = {
        // $COVERAGE-OFF$ Disabling scoverage as this code is impossible to test
        super.configure()
        // Actors bound with FromConfig take router size/dispatcher from pekko.actor.deployment in application.conf
        bindActor[HealthActor](ActorNames.HEALTH_ACTOR, _.withRouter(FromConfig))
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
        
        println("Initialized application actors...")
        // $COVERAGE-ON
    }
}
