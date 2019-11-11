
import com.google.inject.AbstractModule
import org.sunbird.actors.content.ContentActor
import org.sunbird.actors.license.LicenseActor
import play.libs.akka.AkkaGuiceSupport
import utils.ActorNames

class Module extends AbstractModule with AkkaGuiceSupport {

    override def configure() = {
        super.configure()
        bindActor(classOf[ContentActor], ActorNames.CONTENT_ACTOR)
        bindActor(classOf[LicenseActor], ActorNames.LICENSE_ACTOR)
        println("Initialized learning request router pool...")
    }
}
