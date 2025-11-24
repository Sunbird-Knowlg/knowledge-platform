package handlers

import java.util.concurrent.TimeUnit

import org.apache.pekko.actor.ActorSystem
import javax.inject.{Inject, Singleton}
import org.slf4j.LoggerFactory
import play.api.inject.DefaultApplicationLifecycle
import sun.misc.Signal

import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext

@Singleton
class SignalHandler @Inject()(implicit actorSystem: ActorSystem, lifecycle: DefaultApplicationLifecycle) {
    val LOG = LoggerFactory.getLogger(classOf[SignalHandler])
    val STOP_DELAY = Duration.create(30, TimeUnit.SECONDS)
    var isShuttingDown = false

    println("Initializing SignalHandler...")
    Signal.handle(new Signal("TERM"), new sun.misc.SignalHandler() {
        override def handle(signal: Signal): Unit = {
            // $COVERAGE-OFF$ Disabling scoverage as this code is impossible to test
            isShuttingDown = true
            println("Termination required, swallowing SIGTERM to allow current requests to finish. : " + System.currentTimeMillis())
            actorSystem.scheduler.scheduleOnce(STOP_DELAY) {
                println("ApplicationLifecycle stop triggered... : " + System.currentTimeMillis())
                lifecycle.stop()
            }(ExecutionContext.global)
            // $COVERAGE-ON
        }
    })
}
