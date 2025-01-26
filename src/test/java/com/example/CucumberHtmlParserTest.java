package com.example;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CucumberHtmlParserTest {
    private CucumberHtmlParser parser;

    @Before
    public void setUp() {
        parser = spy(new CucumberHtmlParser());
    }

    @Test
    public void testExtractCucumberMessagesJson_valid() {
        String html = "<script>window.CUCUMBER_MESSAGES = [{\"key\":\"val\"}];</script>";
        String result = parser.extractCucumberMessagesJson(html);
        assertEquals("[{\"key\":\"val\"}]", result);
    }

    @Test
    public void testExtractCucumberMessagesJson_noScript() {
        String html = "<html><body>no script</body></html>";
        String result = parser.extractCucumberMessagesJson(html);
        assertEquals("[]", result);
    }

    @Test
    public void testReadJsonArray_valid() {
        String json = "[{\"test\":\"ok\"}]";
        List<?> arr = parser.readJsonArray(json);
        assertEquals(1, arr.size());
    }

    @Test
    public void testReadJsonArray_invalid() {
        String json = "[invalid json";
        List<?> arr = parser.readJsonArray(json);
        assertTrue(arr.isEmpty());
    }

    @Test
    public void testParseHtml_callsSubMethods() {
        // Spy로 만든 parser에 대해, 특정 메서드들이 호출되는지 검사
        String html = "<script>window.CUCUMBER_MESSAGES = [];</script>";
        parser.parseHtml(html);
        verify(parser, atLeastOnce()).extractCucumberMessagesJson(html);
        verify(parser, atLeastOnce()).readJsonArray("[]");
    }

    @Test
    public void testExtractGherkinRows_empty() {
        parser.extractGherkinRows(Collections.emptyMap());
        assertTrue(parser.getRunIdToTestCaseId().isEmpty());
    }

    @Test
    public void testMarkStepStatus_failed() {
        // runIdToTestCaseId에 임의 값 미리 세팅
        parser.getRunIdToTestCaseId().put("run-1", "tc-1");
        parser.getScenarioStatus().put("tc-1", "PASSED");
        // 호출 인자
        Map<String, Object> input = Map.of(
                "testStepFinished", Map.of(
                        "testCaseStartedId","run-1",
                        "testStepResult",Map.of("status","FAILED")
                )
        );
        parser.markStepStatus(input);
        assertEquals("FAILED", parser.getScenarioStatus().get("tc-1"));
    }

    @Test
    public void testMarkStepStatus_passed() {
        parser.getRunIdToTestCaseId().put("run-2", "tc-2");
        parser.getScenarioStatus().put("tc-2", "PASSED");
        Map<String, Object> input = Map.of(
                "testStepFinished", Map.of(
                        "testCaseStartedId","run-2",
                        "testStepResult",Map.of("status","PASSED")
                )
        );
        parser.markStepStatus(input);
        assertEquals("PASSED", parser.getScenarioStatus().get("tc-2"));
    }

    @Test(expected = IOException.class)
    public void testParseFile_whenIOException() throws Exception {
        // parseFile 안에서 Files.readString(...)이 예외를 던진다면?
        CucumberHtmlParser mockParser = spy(new CucumberHtmlParser());
        doThrow(new IOException("No file")).when(mockParser).parseFile("invalid-file");
        mockParser.parseFile("invalid-file");
    }
}
