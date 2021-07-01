package org.sunbird.mimetype.mgr.impl

import java.io.File
import java.util

import com.google.common.io.Resources
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.{AsyncFlatSpec, Matchers}
import org.sunbird.models.UploadParams
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.dto.{Request, Response}
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.{GraphService, OntologyEngineContext}
import org.sunbird.graph.dac.model.Node

import scala.concurrent.{ExecutionContext, Future}

class EcmlMimeTypeMgrImplTest extends AsyncFlatSpec with Matchers with AsyncMockFactory{

    implicit val ss = mock[StorageService]

    it should "Throw Client Exception for null file url" in {
        val exception = intercept[ClientException] {
            new EcmlMimeTypeMgrImpl().upload("do_1234", getNode(), "", None, UploadParams())
        }
        exception.getMessage shouldEqual "Please Provide Valid File Url!"
    }

    it should "Throw Client Except for non zip file " in {
        val exception = intercept[ClientException] {
            new EcmlMimeTypeMgrImpl().upload("do_1234", getNode(), new File(Resources.getResource("sample.pdf").toURI), None, UploadParams())
        }
        exception.getMessage shouldEqual "INVALID_CONTENT_PACKAGE_FILE_MIME_TYPE_ERROR | [The uploaded package is invalid]"
    }


    it should "upload ECML zip file and return public url" in {
        val node = getNode()
        val identifier = "do_1234"
        implicit val ss = mock[StorageService]
        (ss.uploadFile(_:String, _: File, _: Option[Boolean])).expects(*, *, *).returns(Array(identifier, identifier)).repeated(3)
        (ss.uploadDirectory(_:String, _:File, _: Option[Boolean])).expects(*, *, *)
        val resFuture = new EcmlMimeTypeMgrImpl().upload(identifier, node, new File(Resources.getResource("validecml.zip").toURI), None, UploadParams())
        resFuture.map(result => {
            assert(null != result)
            assert(result.nonEmpty)
            assert("do_123" == result.getOrElse("identifier",""))
        })

        assert(true)
    }

    it should "upload ECML with json zip file and return public url" in {
        val node = getNode()
        val identifier = "do_1234"
        implicit val ss = mock[StorageService]
        (ss.uploadFile(_:String, _: File, _: Option[Boolean])).expects(*, *, *).returns(Array(identifier, identifier))
        (ss.uploadDirectory(_:String, _:File, _: Option[Boolean])).expects(*, *, *)
        val resFuture = new EcmlMimeTypeMgrImpl().upload(identifier, node, new File(Resources.getResource("validecml_withjson.zip").toURI), None, UploadParams())
        resFuture.map(result => {
            assert(null != result)
            assert(result.nonEmpty)
            assert("do_123" == result.getOrElse("identifier",""))
        })

        assert(true)
    }

    it should "upload ECML with json zip file URL and return public url" in {
        val node = getNode()
        val identifier = "do_1234"
        implicit val ss = mock[StorageService]
        (ss.uploadFile(_:String, _: File, _: Option[Boolean])).expects(*, *, *).returns(Array(identifier, identifier))
        (ss.uploadDirectory(_:String, _:File, _: Option[Boolean])).expects(*, *, *)
        val resFuture = new EcmlMimeTypeMgrImpl().upload(identifier, node, "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/kp_ft_1594670084387/artifact/ecml_with_json.zip", None, UploadParams())
        resFuture.map(result => {
            assert(null != result)
            assert(result.nonEmpty)
            assert("do_123" == result.getOrElse("identifier",""))
        })

        assert(true)
    }

    it should "review ECML and return result" in {
        implicit val ss = mock[StorageService]
        implicit val oec: OntologyEngineContext = mock[OntologyEngineContext]
        val graphDB = mock[GraphService]
        val body = "{\"theme\":{\"id\":\"theme\",\"version\":\"1.0\",\"startStage\":\"7a0a78d7-8953-4048-b565-0b8bc06cc070\",\"stage\":[{\"x\":0,\"y\":0,\"w\":100,\"h\":100,\"id\":\"7a0a78d7-8953-4048-b565-0b8bc06cc070\",\"rotate\":null,\"config\":{\"__cdata\":\"{\\\"opacity\\\":100,\\\"strokeWidth\\\":1,\\\"stroke\\\":\\\"rgba(255, 255, 255, 0)\\\",\\\"autoplay\\\":false,\\\"visible\\\":true,\\\"color\\\":\\\"#FFFFFF\\\",\\\"instructions\\\":\\\"\\\",\\\"genieControls\\\":false}\"},\"param\":[{\"name\":\"next\",\"value\":\"4465a2b5-1361-439a-8766-3056ebc1c8ab\"}],\"manifest\":{\"media\":[{\"assetId\":\"do_2133122975753093121715\"}]},\"org.ekstep.video\":[{\"asset\":\"do_2133122975753093121715\",\"y\":7.9,\"x\":10.97,\"w\":78.4,\"h\":79.51,\"rotate\":0,\"z-index\":0,\"id\":\"02cc9142-4bb6-4659-9ecb-47ce7b854036\",\"config\":{\"__cdata\":\"{\\\"autoplay\\\":true,\\\"controls\\\":true,\\\"muted\\\":false,\\\"visible\\\":true}\"}}]},{\"x\":0,\"y\":0,\"w\":100,\"h\":100,\"id\":\"4465a2b5-1361-439a-8766-3056ebc1c8ab\",\"rotate\":null,\"config\":{\"__cdata\":\"{\\\"opacity\\\":100,\\\"strokeWidth\\\":1,\\\"stroke\\\":\\\"rgba(255, 255, 255, 0)\\\",\\\"autoplay\\\":false,\\\"visible\\\":true,\\\"color\\\":\\\"#FFFFFF\\\",\\\"genieControls\\\":false,\\\"instructions\\\":\\\"\\\"}\"},\"param\":[{\"name\":\"previous\",\"value\":\"7a0a78d7-8953-4048-b565-0b8bc06cc070\"},{\"name\":\"next\",\"value\":\"6e12d1a2-62a0-4ecd-8d80-7c4030936adb\"}],\"manifest\":{\"media\":[]},\"org.ekstep.text\":[{\"x\":10,\"y\":20,\"minWidth\":20,\"w\":35,\"maxWidth\":500,\"fill\":\"#000000\",\"fontStyle\":\"normal\",\"fontWeight\":\"normal\",\"stroke\":\"rgba(255, 255, 255, 0)\",\"strokeWidth\":1,\"opacity\":1,\"editable\":false,\"version\":\"V2\",\"offsetY\":0.2,\"h\":5.02,\"rotate\":0,\"textType\":\"text\",\"lineHeight\":1,\"z-index\":0,\"font\":\"'Noto Sans', 'Noto Sans Bengali', 'Noto Sans Malayalam', 'Noto Sans Gurmukhi', 'Noto Sans Devanagari', 'Noto Sans Gujarati', 'Noto Sans Telugu', 'Noto Sans Tamil', 'Noto Sans Kannada', 'Noto Sans Oriya', 'Noto Nastaliq Urdu', -apple-system, BlinkMacSystemFont, Roboto, Oxygen-Sans, Ubuntu, Cantarell, 'Helvetica Neue'\",\"fontsize\":48,\"weight\":\"\",\"id\":\"6710438b-2878-43aa-a392-60b117c21898\",\"config\":{\"__cdata\":\"{\\\"opacity\\\":100,\\\"strokeWidth\\\":1,\\\"stroke\\\":\\\"rgba(255, 255, 255, 0)\\\",\\\"autoplay\\\":false,\\\"visible\\\":true,\\\"text\\\":\\\"This is rabindranath tagore\\\",\\\"color\\\":\\\"#000000\\\",\\\"fontfamily\\\":\\\"'Noto Sans', 'Noto Sans Bengali', 'Noto Sans Malayalam', 'Noto Sans Gurmukhi', 'Noto Sans Devanagari', 'Noto Sans Gujarati', 'Noto Sans Telugu', 'Noto Sans Tamil', 'Noto Sans Kannada', 'Noto Sans Oriya', 'Noto Nastaliq Urdu', -apple-system, BlinkMacSystemFont, Roboto, Oxygen-Sans, Ubuntu, Cantarell, 'Helvetica Neue'\\\",\\\"fontsize\\\":18,\\\"fontweight\\\":false,\\\"fontstyle\\\":false,\\\"align\\\":\\\"left\\\"}\"}}]},{\"x\":0,\"y\":0,\"w\":100,\"h\":100,\"id\":\"6e12d1a2-62a0-4ecd-8d80-7c4030936adb\",\"rotate\":null,\"config\":{\"__cdata\":\"{\\\"opacity\\\":100,\\\"strokeWidth\\\":1,\\\"stroke\\\":\\\"rgba(255, 255, 255, 0)\\\",\\\"autoplay\\\":false,\\\"visible\\\":true,\\\"color\\\":\\\"#FFFFFF\\\",\\\"genieControls\\\":false,\\\"instructions\\\":\\\"\\\"}\"},\"param\":[{\"name\":\"previous\",\"value\":\"4465a2b5-1361-439a-8766-3056ebc1c8ab\"},{\"name\":\"next\",\"value\":\"cd40a4c4-a28b-41de-be00-ede9063d38e3\"}],\"manifest\":{\"media\":[{\"assetId\":\"do_2133016737715240961616\"}]},\"image\":[{\"asset\":\"do_2133016737715240961616\",\"x\":20,\"y\":20,\"w\":49.78,\"h\":58.35,\"rotate\":0,\"z-index\":0,\"id\":\"18da2053-5776-41a0-9009-edabe9c7e47d\",\"config\":{\"__cdata\":\"{\\\"opacity\\\":100,\\\"strokeWidth\\\":1,\\\"stroke\\\":\\\"rgba(255, 255, 255, 0)\\\",\\\"autoplay\\\":false,\\\"visible\\\":true}\"}}]},{\"x\":0,\"y\":0,\"w\":100,\"h\":100,\"id\":\"cd40a4c4-a28b-41de-be00-ede9063d38e3\",\"rotate\":null,\"config\":{\"__cdata\":\"{\\\"opacity\\\":100,\\\"strokeWidth\\\":1,\\\"stroke\\\":\\\"rgba(255, 255, 255, 0)\\\",\\\"autoplay\\\":false,\\\"visible\\\":true,\\\"color\\\":\\\"#FFFFFF\\\",\\\"genieControls\\\":false,\\\"instructions\\\":\\\"\\\"}\"},\"param\":[{\"name\":\"previous\",\"value\":\"6e12d1a2-62a0-4ecd-8d80-7c4030936adb\"}],\"manifest\":{\"media\":[{\"assetId\":\"do_2133016742018826241623\"}]},\"image\":[{\"asset\":\"do_2133016742018826241623\",\"x\":27.78,\"y\":18.17,\"w\":33.8,\"h\":60.08,\"rotate\":0,\"z-index\":0,\"id\":\"9781d0a7-2675-4acc-8430-2e31db0852c3\",\"config\":{\"__cdata\":\"{\\\"opacity\\\":100,\\\"strokeWidth\\\":1,\\\"stroke\\\":\\\"rgba(255, 255, 255, 0)\\\",\\\"autoplay\\\":false,\\\"visible\\\":true}\"}}]}],\"manifest\":{\"media\":[{\"id\":\"08d97e93-eb90-4970-8e27-49bb171b5895\",\"plugin\":\"org.ekstep.navigation\",\"ver\":\"1.0\",\"src\":\"/content-plugins/org.ekstep.navigation-1.0/renderer/controller/navigation_ctrl.js\",\"type\":\"js\"},{\"id\":\"195c4ae1-5cac-4e4b-88e3-7676c468cd26\",\"plugin\":\"org.ekstep.navigation\",\"ver\":\"1.0\",\"src\":\"/content-plugins/org.ekstep.navigation-1.0/renderer/templates/navigation.html\",\"type\":\"js\"},{\"id\":\"org.ekstep.navigation\",\"plugin\":\"org.ekstep.navigation\",\"ver\":\"1.0\",\"src\":\"/content-plugins/org.ekstep.navigation-1.0/renderer/plugin.js\",\"type\":\"plugin\"},{\"id\":\"org.ekstep.navigation_manifest\",\"plugin\":\"org.ekstep.navigation\",\"ver\":\"1.0\",\"src\":\"/content-plugins/org.ekstep.navigation-1.0/manifest.json\",\"type\":\"json\"},{\"id\":\"d1a56019-00d0-4bbc-a880-5b594eb6db87\",\"plugin\":\"org.ekstep.video\",\"ver\":\"1.5\",\"src\":\"/content-plugins/org.ekstep.video-1.5/renderer/libs/video.js\",\"type\":\"js\"},{\"id\":\"708aa6d2-314e-4d6e-ac53-9c389549cc60\",\"plugin\":\"org.ekstep.video\",\"ver\":\"1.5\",\"src\":\"/content-plugins/org.ekstep.video-1.5/renderer/libs/videoyoutube.js\",\"type\":\"js\"},{\"id\":\"2e2e060d-020d-48e8-aebd-4d8717aa6718\",\"plugin\":\"org.ekstep.video\",\"ver\":\"1.5\",\"src\":\"/content-plugins/org.ekstep.video-1.5/renderer/libs/videojs.css\",\"type\":\"css\"},{\"id\":\"org.ekstep.video\",\"plugin\":\"org.ekstep.video\",\"ver\":\"1.5\",\"src\":\"/content-plugins/org.ekstep.video-1.5/renderer/videoplugin.js\",\"type\":\"plugin\"},{\"id\":\"org.ekstep.video_manifest\",\"plugin\":\"org.ekstep.video\",\"ver\":\"1.5\",\"src\":\"/content-plugins/org.ekstep.video-1.5/manifest.json\",\"type\":\"json\"},{\"id\":\"org.ekstep.text\",\"plugin\":\"org.ekstep.text\",\"ver\":\"1.2\",\"src\":\"/content-plugins/org.ekstep.text-1.2/renderer/supertextplugin.js\",\"type\":\"plugin\"},{\"id\":\"org.ekstep.text_manifest\",\"plugin\":\"org.ekstep.text\",\"ver\":\"1.2\",\"src\":\"/content-plugins/org.ekstep.text-1.2/manifest.json\",\"type\":\"json\"},{\"id\":\"do_2133122975753093121715\",\"src\":\"https://www.youtube.com/watch?v=YH7p6ncEb6g\",\"assetId\":\"do_2133122975753093121715\",\"type\":\"youtube\"},{\"id\":\"do_2133016737715240961616\",\"src\":\"/assets/public/content/do_2133016737715240961616/artifact/do_2133016737715240961616_1623739474410_pexels-photo-594364.jpeg\",\"type\":\"image\"},{\"id\":\"do_2133016742018826241623\",\"src\":\"/assets/public/content/do_2133016742018826241623/artifact/do_2133016742018826241623_1623739526722_.png\",\"type\":\"image\"}]},\"plugin-manifest\":{\"plugin\":[{\"id\":\"org.ekstep.navigation\",\"ver\":\"1.0\",\"type\":\"plugin\",\"depends\":\"\"},{\"id\":\"org.ekstep.video\",\"ver\":\"1.5\",\"type\":\"widget\",\"depends\":\"\"},{\"id\":\"org.ekstep.text\",\"ver\":\"1.2\",\"type\":\"plugin\",\"depends\":\"\"}]},\"compatibilityVersion\":4}}"
        val response = new Response
        response.put("body", body)
        (oec.graphService _).expects().returns(graphDB).anyNumberOfTimes()
        (graphDB.readExternalProps(_: Request, _: List[String])).expects(*, *).returns(Future(response)).anyNumberOfTimes()
        val node = getNode()
        node.setIdentifier("do_2133122972131737601714")
        node.setObjectType("Content")
        node.getMetadata.put("artifactUrl", "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_113105564164997120111/artifact/1599796728064_do_11310551225702809612421.zip")
        val identifier = "do_2133122972131737601714"
        val resFuture = new EcmlMimeTypeMgrImpl().review(identifier, node)
        resFuture.map(result => {
            assert(null != result)
            assert(result.nonEmpty)
            assert("Review" == result.getOrElse("status",""))
        })
        assert(true)
    }

    def getNode(): Node = {
        val node = new Node()
        node.setIdentifier("do_1234")
        node.setMetadata(new util.HashMap[String, AnyRef](){{
            put("identifier", "do_1234")
            put("mimeType", "application/vnd.ekstep.ecml-archive")
            put("status","Draft")
            put("contentType", "Resource")
        }})
        node
    }
}
