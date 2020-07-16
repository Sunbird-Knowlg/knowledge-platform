package org.sunbird.models

import org.specs2.mutable.Specification
import play.api.mvc.QueryStringBindable

object UploadParamsSpec extends Specification {

    "UploadParams query string binder" should {
        val subject = implicitly[QueryStringBindable[UploadParams]]

        "Unbind UploadParams as string" in {
            subject.unbind("key", UploadParams(Some("composed-h5p-zip"), Some(true))) must be_==("fileFormat=composed-h5p-zip&validation=true")
        }

        "Unbind UploadParams as string" in {
            subject.unbind("key", UploadParams(Some(""), Some(true))) must be_==("fileFormat=&validation=true")
        }

        "Unbind UploadParams as string" in {
            subject.unbind("key", UploadParams(Some("composed-h5p-zip"), Some(false))) must be_==("fileFormat=composed-h5p-zip&validation=false")
        }

        "Bind fileFormat to UploadParams" in {
            subject.bind("key", Map("fileFormat" -> Seq("composed-h5p-zip"))) must be_==(Some(Right(UploadParams(Some("composed-h5p-zip"),None))))
        }

        "Bind validation to UploadParams" in {
            subject.bind("key", Map("validation" -> Seq("true"))) must be_==(Some(Right(UploadParams(None, Some(true)))))
        }

        "Fail on un parsable UploadParams" in {
            subject.bind("key", Map("key" -> Seq(""))) must be_==(Some(Right(UploadParams(None,None))))
        }
    }

    "QueryStringBindable.bindableString" should {
        "unbind with null values" in {
            import QueryStringBindable._
            val boundValue = bindableString.unbind("key", null)
            boundValue must beEqualTo("key=")
        }
    }

    "QueryStringBindable.bindableBoolean" should {
        "unbind with false values" in {
            import QueryStringBindable._
            val boundValue = bindableBoolean.unbind("key", false)
            boundValue must beEqualTo("key=false")
        }
    }
}