
import com.google.inject.AbstractModule
import org.sunbird.actors.{CollectionActor, ContentActor, HealthActor, LicenseActor}
import play.libs.akka.AkkaGuiceSupport
import utils.ActorNames

class Module extends AbstractModule with AkkaGuiceSupport {

    override def configure() = {
        super.configure()
        bindActor(classOf[HealthActor], ActorNames.HEALTH_ACTOR)
        bindActor(classOf[ContentActor], ActorNames.CONTENT_ACTOR)
        bindActor(classOf[LicenseActor], ActorNames.LICENSE_ACTOR)
        bindActor(classOf[CollectionActor], ActorNames.COLLECTION_ACTOR)
        println("Initialized application actors...")
    }
}
