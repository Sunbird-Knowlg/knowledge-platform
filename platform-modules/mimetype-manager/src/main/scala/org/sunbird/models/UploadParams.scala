package org.sunbird.models

import play.api.mvc.QueryStringBindable


case class UploadParams(fileFormat: Option[String] = Some(""), validation: Option[Boolean] = Some(true))

object UploadParams {
    implicit def queryStringBinder(implicit stringBinder: QueryStringBindable[String],
                                   booleanBinder: QueryStringBindable[Boolean]) = new QueryStringBindable[UploadParams] {

        private def subBind[T](key: String, subKey: String, params: Map[String, Seq[String]])(implicit b: QueryStringBindable[T]): Either.RightProjection[String, Option[T]] = {
            b.bind(subKey, params).map(_.right.map(r => Option(r))).getOrElse(Right(None)).right
        }

        override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, UploadParams]] = Some {
            def bnd[T](s: String)(implicit b: QueryStringBindable[T]) = subBind[T](key, s, params)

            for {
                fileFormat <- bnd[String]("fileFormat")
                validation <- bnd[Boolean]("validation")
            } yield UploadParams(fileFormat, validation)
        }

        override def unbind(key: String, params: UploadParams): String = {
            def ubnd[T](key: String, s: Option[T])(implicit b: QueryStringBindable[T]) = s.map(f => b.unbind(key, f))

            val keys = Seq(
                ubnd("fileFormat", params.fileFormat),
                ubnd("validation", params.validation)
            ).flatten
            keys.mkString("&")
        }
    }
}
