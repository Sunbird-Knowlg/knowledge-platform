package modules

import com.google.inject.AbstractModule
import org.sunbird.channel.actors.ChannelActor
import org.sunbird.collectioncsv.actors.CollectionCSVActor
import org.sunbird.content.actors.{AppActor, AssetActor, CategoryActor, CollectionActor, ContentActor, EventActor, EventSetActor, HealthActor, LicenseActor, ObjectActor}
import play.libs.akka.AkkaGuiceSupport
import utils.ActorNames

class ContentModule extends AbstractModule with AkkaGuiceSupport {

    override def configure() = {
        // $COVERAGE-OFF$ Disabling scoverage as this code is impossible to test
        //super.configure()
        bindActor(classOf[HealthActor], ActorNames.HEALTH_ACTOR)
        bindActor(classOf[ContentActor], ActorNames.CONTENT_ACTOR)
        bindActor(classOf[LicenseActor], ActorNames.LICENSE_ACTOR)
        bindActor(classOf[CollectionActor], ActorNames.COLLECTION_ACTOR)
        bindActor(classOf[EventActor], ActorNames.EVENT_ACTOR)
        bindActor(classOf[EventSetActor], ActorNames.EVENT_SET_ACTOR)
        bindActor(classOf[ChannelActor], ActorNames.CHANNEL_ACTOR)
        bindActor(classOf[CategoryActor], ActorNames.CATEGORY_ACTOR)
        bindActor(classOf[AssetActor], ActorNames.ASSET_ACTOR)
        bindActor(classOf[AppActor], ActorNames.APP_ACTOR)
        bindActor(classOf[ObjectActor], ActorNames.OBJECT_ACTOR)
        bindActor(classOf[CollectionCSVActor], ActorNames.COLLECTION_CSV_ACTOR)
        println("Initialized application actors...")
        // $COVERAGE-ON
    }
}
