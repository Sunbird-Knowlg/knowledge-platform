package filters

import org.apache.pekko.util.ByteString
import javax.inject.Inject
import play.api.Logging
import play.api.libs.streams.Accumulator
import play.api.mvc._

import scala.concurrent.ExecutionContext

class AccessLogFilter @Inject() (implicit ec: ExecutionContext) extends EssentialFilter with Logging {

    def apply(nextFilter: EssentialAction) = new EssentialAction {
      def apply(requestHeader: RequestHeader) = {

        val startTime = System.currentTimeMillis

        val accumulator: Accumulator[ByteString, Result] = nextFilter(requestHeader)

        accumulator.map { result =>
          val endTime     = System.currentTimeMillis
          val requestTime = endTime - startTime

          val path = requestHeader.uri
          if(!path.contains("/health")){
            logger.debug(s"${requestHeader.method} ${path} - ${result.header.status} - ${requestTime}ms")
          }
          result.withHeaders("Request-Time" -> requestTime.toString)
        }
      }
    }
  }

