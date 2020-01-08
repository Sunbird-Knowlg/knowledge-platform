package filters

import akka.util.ByteString
import javax.inject.Inject
import org.sunbird.telemetry.util.TelemetryAccessEventUtil
import play.api.Logging
import play.api.libs.streams.Accumulator
import play.api.mvc._
import play.core.server.akkahttp.AkkaHeadersWrapper

import scala.concurrent.ExecutionContext
import scala.collection.JavaConverters._

class AccessLogFilter @Inject()(implicit ec: ExecutionContext) extends EssentialFilter with Logging {
    def apply(nextFilter: EssentialAction) = new EssentialAction {
      def apply(requestHeader: RequestHeader) = {

        val startTime = System.currentTimeMillis

        val accumulator: Accumulator[ByteString, Result] = nextFilter(requestHeader)

        accumulator.map { result =>
          val endTime     = System.currentTimeMillis
          val requestTime = endTime - startTime

          val path = requestHeader.headers.asInstanceOf[AkkaHeadersWrapper].request.uri.toString();
          if(!path.contains("/health")){
            var data:Map[String, Any] = Map[String, Any]()
            data += ("StartTime" -> startTime)
            data += ("env" -> "content")
            data += ("RemoteAddress" -> requestHeader.remoteAddress)
            data += ("ContentLength" -> result.body.contentLength.getOrElse(0))
            data += ("Status" -> result.header.status)
            data += ("Protocol" -> requestHeader.headers.asInstanceOf[AkkaHeadersWrapper].request.protocol)
            data += ("path" -> path)
            data += ("Method" -> requestHeader.method.toString)
            val headers = requestHeader.headers.asInstanceOf[AkkaHeadersWrapper].headers.groupBy(_._1).mapValues(_.map(_._2));
            if(None != headers.get("X-Session-ID"))
              data += ("X-Session-ID" -> headers.get("X-Session-ID").head.head)
            if(None != headers.get("X-Consumer-ID"))
              data += ("X-Consumer-ID" -> headers.get("X-Consumer-ID").head.head)
            if(None != headers.get("X-Device-ID"))
              data += ("X-Device-ID" -> headers.get("X-Device-ID").head.head)
            if(None != headers.get("X-App-Id"))
              data += ("APP_ID" -> headers.get("X-App-Id").head.head)
            if(None != headers.get("X-Authenticated-Userid"))
              data += ("X-Authenticated-Userid" -> headers.get("X-Authenticated-Userid").head.head)
            if(None != headers.get("X-Channel-Id"))
              data += ("X-Channel-Id" -> headers.get("X-Channel-Id").head.head)

            TelemetryAccessEventUtil.writeTelemetryEventLog(data.asInstanceOf[Map[String, AnyRef]].asJava)
          }
          result.withHeaders("Request-Time" -> requestTime.toString)
        }
      }
    }
  }