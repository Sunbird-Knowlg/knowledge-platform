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
        val resFuture = new EcmlMimeTypeMgrImpl().upload(identifier, node, new File(Resources.getResource("validecml_withjson_new.zip").toURI), None, UploadParams())
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
        (ss.uploadFile(_:String, _: File, _: Option[Boolean])).expects(*, *, *).returns(Array(identifier, identifier)).anyNumberOfTimes()
        (ss.uploadDirectory(_:String, _:File, _: Option[Boolean])).expects(*, *, *).anyNumberOfTimes()
        val resFuture = new EcmlMimeTypeMgrImpl().upload(identifier, node, "https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/assets/do_113322230485778432142/validecml_withjson_new.zip", None, UploadParams())
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
        val body = """{"theme":{"id":"theme","version":"1.0","startStage":"7a0a78d7-8953-4048-b565-0b8bc06cc070","stage":[{"x":0,"y":0,"w":100,"h":100,"id":"7a0a78d7-8953-4048-b565-0b8bc06cc070","rotate":null,"config":{"__cdata":"{\"opacity\":100,\"strokeWidth\":1,\"stroke\":\"rgba(255, 255, 255, 0)\",\"autoplay\":false,\"visible\":true,\"color\":\"#FFFFFF\",\"instructions\":\"\",\"genieControls\":false}"},"param":[{"name":"next","value":"4465a2b5-1361-439a-8766-3056ebc1c8ab"}],"manifest":{"media":[{"assetId":"do_2133122975753093121715"}]},"org.ekstep.video":[{"asset":"do_2133122975753093121715","y":7.9,"x":10.97,"w":78.4,"h":79.51,"rotate":0,"z-index":0,"id":"02cc9142-4bb6-4659-9ecb-47ce7b854036","config":{"__cdata":"{\"autoplay\":true,\"controls\":true,\"muted\":false,\"visible\":true}"}}]},{"x":0,"y":0,"w":100,"h":100,"id":"4465a2b5-1361-439a-8766-3056ebc1c8ab","rotate":null,"config":{"__cdata":"{\"opacity\":100,\"strokeWidth\":1,\"stroke\":\"rgba(255, 255, 255, 0)\",\"autoplay\":false,\"visible\":true,\"color\":\"#FFFFFF\",\"genieControls\":false,\"instructions\":\"\"}"},"param":[{"name":"previous","value":"7a0a78d7-8953-4048-b565-0b8bc06cc070"},{"name":"next","value":"6e12d1a2-62a0-4ecd-8d80-7c4030936adb"}],"manifest":{"media":[]},"org.ekstep.text":[{"x":10,"y":20,"minWidth":20,"w":35,"maxWidth":500,"fill":"#000000","fontStyle":"normal","fontWeight":"normal","stroke":"rgba(255, 255, 255, 0)","strokeWidth":1,"opacity":1,"editable":false,"version":"V2","offsetY":0.2,"h":5.02,"rotate":0,"textType":"text","lineHeight":1,"z-index":0,"font":"'Noto Sans', 'Noto Sans Bengali', 'Noto Sans Malayalam', 'Noto Sans Gurmukhi', 'Noto Sans Devanagari', 'Noto Sans Gujarati', 'Noto Sans Telugu', 'Noto Sans Tamil', 'Noto Sans Kannada', 'Noto Sans Oriya', 'Noto Nastaliq Urdu', -apple-system, BlinkMacSystemFont, Roboto, Oxygen-Sans, Ubuntu, Cantarell, 'Helvetica Neue'","fontsize":48,"weight":"","id":"6710438b-2878-43aa-a392-60b117c21898","config":{"__cdata":"{\"opacity\":100,\"strokeWidth\":1,\"stroke\":\"rgba(255, 255, 255, 0)\",\"autoplay\":false,\"visible\":true,\"text\":\"This is rabindranath tagore\",\"color\":\"#000000\",\"fontfamily\":\"'Noto Sans', 'Noto Sans Bengali', 'Noto Sans Malayalam', 'Noto Sans Gurmukhi', 'Noto Sans Devanagari', 'Noto Sans Gujarati', 'Noto Sans Telugu', 'Noto Sans Tamil', 'Noto Sans Kannada', 'Noto Sans Oriya', 'Noto Nastaliq Urdu', -apple-system, BlinkMacSystemFont, Roboto, Oxygen-Sans, Ubuntu, Cantarell, 'Helvetica Neue'\",\"fontsize\":18,\"fontweight\":false,\"fontstyle\":false,\"align\":\"left\"}"}}]},{"x":0,"y":0,"w":100,"h":100,"id":"6e12d1a2-62a0-4ecd-8d80-7c4030936adb","rotate":null,"config":{"__cdata":"{\"opacity\":100,\"strokeWidth\":1,\"stroke\":\"rgba(255, 255, 255, 0)\",\"autoplay\":false,\"visible\":true,\"color\":\"#FFFFFF\",\"genieControls\":false,\"instructions\":\"\"}"},"param":[{"name":"previous","value":"4465a2b5-1361-439a-8766-3056ebc1c8ab"},{"name":"next","value":"cd40a4c4-a28b-41de-be00-ede9063d38e3"}],"manifest":{"media":[{"assetId":"do_2133016737715240961616"}]},"image":[{"asset":"do_2133016737715240961616","x":20,"y":20,"w":49.78,"h":58.35,"rotate":0,"z-index":0,"id":"18da2053-5776-41a0-9009-edabe9c7e47d","config":{"__cdata":"{\"opacity\":100,\"strokeWidth\":1,\"stroke\":\"rgba(255, 255, 255, 0)\",\"autoplay\":false,\"visible\":true}"}}]},{"x":0,"y":0,"w":100,"h":100,"id":"cd40a4c4-a28b-41de-be00-ede9063d38e3","rotate":null,"config":{"__cdata":"{\"opacity\":100,\"strokeWidth\":1,\"stroke\":\"rgba(255, 255, 255, 0)\",\"autoplay\":false,\"visible\":true,\"color\":\"#FFFFFF\",\"genieControls\":false,\"instructions\":\"\"}"},"param":[{"name":"previous","value":"6e12d1a2-62a0-4ecd-8d80-7c4030936adb"}],"manifest":{"media":[{"assetId":"do_2133016742018826241623"}]},"image":[{"asset":"do_2133016742018826241623","x":27.78,"y":18.17,"w":33.8,"h":60.08,"rotate":0,"z-index":0,"id":"9781d0a7-2675-4acc-8430-2e31db0852c3","config":{"__cdata":"{\"opacity\":100,\"strokeWidth\":1,\"stroke\":\"rgba(255, 255, 255, 0)\",\"autoplay\":false,\"visible\":true}"}}]}],"manifest":{"media":[{"id":"08d97e93-eb90-4970-8e27-49bb171b5895","plugin":"org.ekstep.navigation","ver":"1.0","src":"/content-plugins/org.ekstep.navigation-1.0/renderer/controller/navigation_ctrl.js","type":"js"},{"id":"195c4ae1-5cac-4e4b-88e3-7676c468cd26","plugin":"org.ekstep.navigation","ver":"1.0","src":"/content-plugins/org.ekstep.navigation-1.0/renderer/templates/navigation.html","type":"js"},{"id":"org.ekstep.navigation","plugin":"org.ekstep.navigation","ver":"1.0","src":"/content-plugins/org.ekstep.navigation-1.0/renderer/plugin.js","type":"plugin"},{"id":"org.ekstep.navigation_manifest","plugin":"org.ekstep.navigation","ver":"1.0","src":"/content-plugins/org.ekstep.navigation-1.0/manifest.json","type":"json"},{"id":"d1a56019-00d0-4bbc-a880-5b594eb6db87","plugin":"org.ekstep.video","ver":"1.5","src":"/content-plugins/org.ekstep.video-1.5/renderer/libs/video.js","type":"js"},{"id":"708aa6d2-314e-4d6e-ac53-9c389549cc60","plugin":"org.ekstep.video","ver":"1.5","src":"/content-plugins/org.ekstep.video-1.5/renderer/libs/videoyoutube.js","type":"js"},{"id":"2e2e060d-020d-48e8-aebd-4d8717aa6718","plugin":"org.ekstep.video","ver":"1.5","src":"/content-plugins/org.ekstep.video-1.5/renderer/libs/videojs.css","type":"css"},{"id":"org.ekstep.video","plugin":"org.ekstep.video","ver":"1.5","src":"/content-plugins/org.ekstep.video-1.5/renderer/videoplugin.js","type":"plugin"},{"id":"org.ekstep.video_manifest","plugin":"org.ekstep.video","ver":"1.5","src":"/content-plugins/org.ekstep.video-1.5/manifest.json","type":"json"},{"id":"org.ekstep.text","plugin":"org.ekstep.text","ver":"1.2","src":"/content-plugins/org.ekstep.text-1.2/renderer/supertextplugin.js","type":"plugin"},{"id":"org.ekstep.text_manifest","plugin":"org.ekstep.text","ver":"1.2","src":"/content-plugins/org.ekstep.text-1.2/manifest.json","type":"json"},{"id":"do_2133122975753093121715","src":"https://www.youtube.com/watch?v=YH7p6ncEb6g","assetId":"do_2133122975753093121715","type":"youtube"},{"id":"do_2133016737715240961616","src":"/assets/public/content/do_2133016737715240961616/artifact/do_2133016737715240961616_1623739474410_pexels-photo-594364.jpeg","type":"image"},{"id":"do_2133016742018826241623","src":"/assets/public/content/do_2133016742018826241623/artifact/do_2133016742018826241623_1623739526722_.png","type":"image"}]},"plugin-manifest":{"plugin":[{"id":"org.ekstep.navigation","ver":"1.0","type":"plugin","depends":""},{"id":"org.ekstep.video","ver":"1.5","type":"widget","depends":""},{"id":"org.ekstep.text","ver":"1.2","type":"plugin","depends":""}]},"compatibilityVersion":4}}"""
        //val body = """<theme startStage=\"d6442041-7f89-8ef3-890f-443847aa4768\" compatibilityVersion=\"2\" version=\"1.0\" id=\"theme\"><manifest><media plugin=\"org.ekstep.navigation\" id=\"org.ekstep.navigation_js\" ver=\"1.0\" src=\"content-plugins/org.ekstep.navigation-1.0/renderer/controller/navigation_ctrl.js\" type=\"js\"></media><media plugin=\"org.ekstep.navigation\" id=\"org.ekstep.navigation_html\" ver=\"1.0\" src=\"content-plugins/org.ekstep.navigation-1.0/renderer/templates/navigation.html\" type=\"js\"></media><media plugin=\"org.ekstep.navigation\" id=\"org.ekstep.navigation\" ver=\"1.0\" src=\"content-plugins/org.ekstep.navigation-1.0/renderer/plugin.js\" type=\"plugin\"></media><media plugin=\"org.ekstep.navigation\" id=\"org.ekstep.navigation_manifest\" ver=\"1.0\" src=\"content-plugins/org.ekstep.navigation-1.0/manifest.json\" type=\"json\"></media><media plugin=\"org.ekstep.iterator\" id=\"org.ekstep.iterator\" ver=\"1.0\" src=\"content-plugins/org.ekstep.iterator-1.0/renderer/plugin.js\" type=\"plugin\"></media><media plugin=\"org.ekstep.iterator\" id=\"org.ekstep.iterator_manifest\" ver=\"1.0\" src=\"content-plugins/org.ekstep.iterator-1.0/manifest.json\" type=\"json\"></media><media plugin=\"org.ekstep.questionset\" id=\"org.ekstep.questionset_telemetry_logger_js\" ver=\"1.0\" src=\"content-plugins/org.ekstep.questionset-1.0/renderer/utils/telemetry_logger.js\" type=\"js\"></media><media plugin=\"org.ekstep.questionset\" id=\"org.ekstep.questionset_audio_plugin_js\" ver=\"1.0\" src=\"content-plugins/org.ekstep.questionset-1.0/renderer/utils/html_audio_plugin.js\" type=\"js\"></media><media plugin=\"org.ekstep.questionset\" id=\"org.ekstep.questionset_feedback_popup_js\" ver=\"1.0\" src=\"content-plugins/org.ekstep.questionset-1.0/renderer/utils/qs_feedback_popup.js\" type=\"js\"></media><media plugin=\"org.ekstep.questionset\" id=\"org.ekstep.questionset\" ver=\"1.0\" src=\"content-plugins/org.ekstep.questionset-1.0/renderer/plugin.js\" type=\"plugin\"></media><media plugin=\"org.ekstep.questionset\" id=\"org.ekstep.questionset_manifest\" ver=\"1.0\" src=\"content-plugins/org.ekstep.questionset-1.0/manifest.json\" type=\"json\"></media><media plugin=\"org.ekstep.questionunit\" id=\"org.ekstep.questionunit.renderer.audioicon\" ver=\"1.1\" src=\"content-plugins/org.ekstep.questionunit-1.1/renderer/assets/audio-icon.png\" type=\"image\"></media><media plugin=\"org.ekstep.questionunit\" id=\"org.ekstep.questionunit.renderer.downarrow\" ver=\"1.1\" src=\"content-plugins/org.ekstep.questionunit-1.1/renderer/assets/down_arrow.png\" type=\"image\"></media><media plugin=\"org.ekstep.questionunit\" id=\"org.ekstep.questionunit_js\" ver=\"1.1\" src=\"content-plugins/org.ekstep.questionunit-1.1/renderer/components/js/components.js\" type=\"js\"></media><media plugin=\"org.ekstep.questionunit\" id=\"org.ekstep.questionunit_css\" ver=\"1.1\" src=\"content-plugins/org.ekstep.questionunit-1.1/renderer/components/css/components.css\" type=\"css\"></media><media plugin=\"org.ekstep.questionunit\" id=\"org.ekstep.questionunit\" ver=\"1.1\" src=\"content-plugins/org.ekstep.questionunit-1.1/renderer/plugin.js\" type=\"plugin\"></media><media plugin=\"org.ekstep.questionunit\" id=\"org.ekstep.questionunit_manifest\" ver=\"1.1\" src=\"content-plugins/org.ekstep.questionunit-1.1/manifest.json\" type=\"json\"></media><media plugin=\"org.sunbird.questionunit.quml\" id=\"org.sunbird.questionunit.quml_manifest\" ver=\"1.1\" src=\"content-plugins/org.sunbird.questionunit.quml-1.1/manifest.json\" type=\"json\"></media><media plugin=\"org.sunbird.questionunit.quml\" id=\"org.sunbird.questionunit.quml.plugin_js\" ver=\"1.1\" src=\"content-plugins/org.sunbird.questionunit.quml-1.1/renderer/plugin.js\" type=\"plugin\"></media><media plugin=\"org.sunbird.questionunit.quml\" id=\"org.sunbird.questionunit.quml.feedback_popup\" ver=\"1.1\" src=\"content-plugins/org.sunbird.questionunit.quml-1.1/renderer/utils/quml_feedback_popup.js\" type=\"js\"></media><media plugin=\"org.sunbird.questionunit.quml\" id=\"org.sunbird.questionunit.quml.feedback_close\" ver=\"1.1\" src=\"content-plugins/org.sunbird.questionunit.quml-1.1/renderer/assets/feedback-close.svg\" type=\"image\"></media><media plugin=\"org.sunbird.questionunit.quml\" id=\"org.sunbird.questionunit.quml.play\" ver=\"1.1\" src=\"content-plugins/org.sunbird.questionunit.quml-1.1/renderer/assets/player-play-button.png\" type=\"image\"></media><media plugin=\"org.sunbird.questionunit.quml\" id=\"org.sunbird.questionunit.quml_css\" ver=\"1.1\" src=\"content-plugins/org.sunbird.questionunit.quml-1.1/renderer/styles/style.css\" type=\"css\"></media><media plugin=\"org.ekstep.questionunit\" id=\"4b2b66a2-9c5b-42d5-891d-ec0c3d1d516c\" ver=\"1.1\" src=\"/content-plugins/org.ekstep.questionunit-1.1/renderer/libs/katex/katex.min.js\" type=\"js\"></media><media plugin=\"org.ekstep.questionunit\" id=\"7e02c56a-c048-444d-bb2a-d4b854723adc\" ver=\"1.1\" src=\"content-plugins/org.ekstep.questionunit-1.1/renderer/libs/katex/katex.min.css\" type=\"css\"></media><media plugin=\"org.ekstep.questionunit\" id=\"04ad980a-88ac-4c55-a597-266e42240670\" ver=\"1.1\" src=\"content-plugins/org.ekstep.questionunit-1.1/renderer/libs/katex/fonts/katex_main-bold.ttf\" type=\"js\"></media><media plugin=\"org.ekstep.questionunit\" id=\"75ab3bcd-2c52-47b0-8b46-b635256ce06a\" ver=\"1.1\" src=\"content-plugins/org.ekstep.questionunit-1.1/renderer/libs/katex/fonts/katex_main-bolditalic.ttf\" type=\"js\"></media><media plugin=\"org.ekstep.questionunit\" id=\"098cc69e-8658-483e-8597-8c392a1b3545\" ver=\"1.1\" src=\"content-plugins/org.ekstep.questionunit-1.1/renderer/libs/katex/fonts/katex_main-italic.ttf\" type=\"js\"></media><media plugin=\"org.ekstep.questionunit\" id=\"102d1165-9544-480b-9ec1-1cb83937e650\" ver=\"1.1\" src=\"content-plugins/org.ekstep.questionunit-1.1/renderer/libs/katex/fonts/katex_main-regular.ttf\" type=\"js\"></media><media plugin=\"org.ekstep.questionunit\" id=\"3bc5dce9-51ca-4a59-a0d9-d87d47c44670\" ver=\"1.1\" src=\"content-plugins/org.ekstep.questionunit-1.1/renderer/libs/katex/fonts/katex_math-bolditalic.ttf\" type=\"js\"></media><media plugin=\"org.ekstep.questionunit\" id=\"a95bb693-d688-4e7a-a9bc-08e0cb6d1d62\" ver=\"1.1\" src=\"content-plugins/org.ekstep.questionunit-1.1/renderer/libs/katex/fonts/katex_math-italic.ttf\" type=\"js\"></media><media plugin=\"org.ekstep.questionunit\" id=\"15757105-9023-4773-ba2a-8e619a0dc3a6\" ver=\"1.1\" src=\"content-plugins/org.ekstep.questionunit-1.1/renderer/libs/katex/fonts/katex_math-regular.ttf\" type=\"js\"></media><media plugin=\"org.ekstep.questionunit\" id=\"5b1a9e58-8b7e-4295-a90d-593dc831262a\" ver=\"1.1\" src=\"content-plugins/org.ekstep.questionunit-1.1/renderer/libs/katex/fonts/katex_size1-regular.ttf\" type=\"js\"></media><media plugin=\"org.ekstep.questionunit\" id=\"bc6ee547-9db0-480e-b298-b5509d43207a\" ver=\"1.1\" src=\"content-plugins/org.ekstep.questionunit-1.1/renderer/libs/katex/fonts/katex_size2-regular.ttf\" type=\"js\"></media><media plugin=\"org.ekstep.questionunit\" id=\"35b0245e-d7d3-4ac0-9cac-6c2c328c8948\" ver=\"1.1\" src=\"content-plugins/org.ekstep.questionunit-1.1/renderer/libs/katex/fonts/katex_size3-regular.ttf\" type=\"js\"></media><media plugin=\"org.ekstep.questionunit\" id=\"80a19118-48eb-4859-9b0c-576a90a01148\" ver=\"1.1\" src=\"content-plugins/org.ekstep.questionunit-1.1/renderer/libs/katex/fonts/katex_size4-regular.ttf\" type=\"js\"></media></manifest><stage x=\"0\" y=\"0\" id=\"d6442041-7f89-8ef3-890f-443847aa4768\" h=\"100\" w=\"100\"><config><![CDATA[{'opacity':100,'strokeWidth':1,'stroke':'rgba(255, 255, 255, 0)','autoplay':false,'visible':true,'color':'#FFFFFF','genieControls':false,'instructions':''}]]></config><org.ekstep.questionset z-index=\"0\" x=\"9\" y=\"6\" rotate=\"0\" id=\"39306de6-b9c0-0f23-dca3-893ce0ecbf0c\" h=\"85\" w=\"80\"><data><![CDATA[[{\"identifier\":\"39306de6-b9c0-0f23-dca3-893ce0ecbf0c\"}]]]></data><config><![CDATA[{\"max_score\":1,\"allow_skip\":true,\"show_feedback\":false,\"shuffle_questions\":false,\"shuffle_options\":false,\"total_items\":1}]]></config><org.ekstep.question x=\"9\" y=\"6\" pluginId=\"org.sunbird.questionunit.quml\" id=\"b077184c-a43c-e0fc-1f7f-5d6806dab050\" templateId=\"qumltemplate\" h=\"85\" type=\"quml\" w=\"80\" pluginVer=\"1.1\"><data><![CDATA[{\"question\":\"<p>A</p>\",\"media\":[],\"answer\":\"<p>B</p>\",\"options\":[],\"questionCount\":0}]]></data><config><![CDATA[{\"max_score\":1,\"partial_scoring\":false,\"isShuffleOption\":false,\"responseDeclaration\":{},\"metadata\":{\"copyright\":\"2020\",\"itemType\":\"UNIT\",\"code\":\"fd2dbbda-1876-736e-ed82-3ec2e589d3bc\",\"subject\":\"Science\",\"qlevel\":\"MEDIUM\",\"qumlVersion\":1,\"channel\":\"012983850117177344161\",\"responseDeclaration\":{\"responseValue\":{\"cardinality\":\"single\",\"type\":\"string\",\"correct_response\":{\"value\":\"<p>B</p>\"}}},\"language\":[\"English\"],\"medium\":\"English\",\"type\":\"reference\",\"templateId\":\"NA\",\"createdOn\":\"2020-09-11T03:12:59.107+0000\",\"gradeLevel\":[\"Class 10\"],\"appId\":\"dev.sunbird.portal\",\"contentPolicyCheck\":true,\"lastUpdatedOn\":\"2020-09-11T03:13:46.143+0000\",\"identifier\":\"do_1131055122768445441310\",\"creator\":\"devreviewer7@yopmail.com\",\"lastStatusChangedOn\":\"2020-09-11T03:12:59.107+0000\",\"author\":\"devreviewer7@yopmail.com\",\"consumerId\":\"028d6fb1-2d6f-4331-86aa-f7cf491a41e0\",\"version\":3,\"versionKey\":\"1599794026143\",\"license\":\"CC BY 4.0\",\"framework\":\"ekstep_ncert_k-12\",\"rejectComment\":\"\",\"name\":\"vsa_ekstep_ncert_k-12\",\"template_id\":\"NA\",\"category\":\"VSA\",\"board\":\"CBSE\",\"programId\":\"29a5a630-c29c-11ea-b3d3-3bcdd8c1d450\",\"status\":\"Draft\"}}]]></config></org.ekstep.question></org.ekstep.questionset></stage><plugin-manifest><plugin depends=\"\" id=\"org.ekstep.navigation\" ver=\"1.0\" type=\"plugin\"></plugin><plugin depends=\"\" id=\"org.ekstep.questionunit\" ver=\"1.1\" type=\"plugin\"></plugin><plugin depends=\"\" id=\"org.ekstep.iterator\" ver=\"1.0\" type=\"plugin\"></plugin><plugin depends=\"org.ekstep.questionset.quiz,org.ekstep.iterator\" id=\"org.ekstep.questionset\" ver=\"1.0\" type=\"plugin\"></plugin><plugin depends=\"org.ekstep.questionunit\" id=\"org.sunbird.questionunit.quml\" ver=\"1.1\" type=\"plugin\"></plugin></plugin-manifest></theme>"""
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
