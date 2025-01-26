package com.example;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CucumberHtmlParserIntegrationTest {

    private CucumberHtmlParser parser;
    private final String testHtmlPath = "src/test/resources/Cucumber.html";

    @Before
    public void setUp() {
        parser = new CucumberHtmlParser();
    }

    @Test
    public void testParseFile_withRealFile() throws Exception {
        parser.parseFile(testHtmlPath);

        // 최소 1개 이상의 시나리오(=testCaseId)가 존재하는지
        assertFalse(parser.getScenarioStatus().isEmpty());

        // 임의로 하나를 뽑아서 PASSED or FAILED인지 확인
        String anyTcId = parser.getScenarioStatus().keySet().iterator().next();
        String status = parser.getScenarioStatus().get(anyTcId);

        assertTrue("PASSED".equals(status) || "FAILED".equals(status));
    }
}

