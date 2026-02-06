package modules

import com.google.inject.AbstractModule
import content.utils.ActorNames
import org.sunbird.channel.actors.ChannelActor
import org.sunbird.collectioncsv.actors.CollectionCSVActor
import org.sunbird.content.actors._
import play.api.libs.concurrent.PekkoGuiceSupport

class ContentModule extends AbstractModule with PekkoGuiceSupport {

    override def configure() = {
        // $COVERAGE-OFF$ Disabling scoverage as this code is impossible to test
        super.configure()
        bindActor[HealthActor](ActorNames.HEALTH_ACTOR)
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
        
        try {
            org.sunbird.graph.service.util.DriverUtil.closeConnections()
            println("DriverUtil: Closed existing graph connections to ensure fresh startup.")
        } catch {
            case e: Exception => println("DriverUtil: Error occurred while closing connections: " + e.getMessage)
        }
        
        println("Initialized application actors...")
        // $COVERAGE-ON
    }
}
