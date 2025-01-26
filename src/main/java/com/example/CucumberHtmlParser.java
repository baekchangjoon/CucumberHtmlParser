package com.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import java.io.FileWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CucumberHtmlParser {

    private final Map<String, ScenarioData> rowIdToScenarioDataMap = new HashMap<>();
    private final Map<String, String> pickleIdToRowIdMap = new HashMap<>();
    private final Map<String, String> testCaseIdToPickleIdMap = new HashMap<>();
    private final Map<String, String> testCaseIdToStatusMap = new HashMap<>();
    private final Map<String, String> runIdToTestCaseIdMap = new HashMap<>();

    public static void main(String[] args) throws IOException {
        CucumberHtmlParser parser = new CucumberHtmlParser();
        parser.parseFile("cucumber_report.html");
        parser.printResults();
        parser.exportResultsToCsv("result.csv");
        parser.exportResultsToHtml("result.html");
    }

    public void parseFile(String filePath) throws IOException {
        String htmlContent = Files.readString(Paths.get(filePath));
        parseHtml(htmlContent);
    }

    public void parseHtml(String htmlContent) {
        String cucumberJsonArray = extractCucumberMessagesJson(htmlContent);
        List<?> dataArray = readJsonArray(cucumberJsonArray);
        for (Object element : dataArray) {
            if (element instanceof Map) {
                Map elementMap = (Map) element;
                extractGherkinRows(elementMap);
                extractPickleMap(elementMap);
                extractTestCaseMap(elementMap);
                markTestCaseStart(elementMap);
                markStepStatus(elementMap);
            }
        }
        updateFinalStatuses();
    }

    public String extractCucumberMessagesJson(String htmlContent) {
        int startIndex = htmlContent.indexOf("window.CUCUMBER_MESSAGES =");
        if (startIndex < 0) {
            return "[]";
        }
        int openIndex = htmlContent.indexOf("[", startIndex);
        int endIndex = htmlContent.indexOf("];", openIndex);
        if (openIndex < 0 || endIndex < 0) {
            return "[]";
        }
        return htmlContent.substring(openIndex, endIndex + 1);
    }

    public List<?> readJsonArray(String jsonArrayString) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(jsonArrayString, List.class);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public void extractGherkinRows(Map elementMap) {
        if (!elementMap.containsKey("gherkinDocument")) {
            return;
        }
        Map gherkinDocumentMap = (Map) elementMap.get("gherkinDocument");
        Map featureMap = (Map) gherkinDocumentMap.get("feature");
        if (featureMap == null) {
            return;
        }
        List childrenList = (List) featureMap.get("children");
        if (childrenList == null) {
            return;
        }
        for (Object child : childrenList) {
            if (child instanceof Map) {
                Map childMap = (Map) child;
                Map scenarioMap = (Map) childMap.get("scenario");
                if (scenarioMap == null) {
                    continue;
                }
                List examplesList = (List) scenarioMap.get("examples");
                if (examplesList == null) {
                    continue;
                }
                for (Object example : examplesList) {
                    if (example instanceof Map) {
                        Map exampleMap = (Map) example;
                        extractRowsFromExample(exampleMap);
                    }
                }
            }
        }
    }

    public void extractRowsFromExample(Map exampleMap) {
        List tableBodyList = (List) exampleMap.get("tableBody");
        if (tableBodyList == null) {
            return;
        }
        for (Object rowObject : tableBodyList) {
            if (rowObject instanceof Map) {
                Map rowMap = (Map) rowObject;
                String rowId = (String) rowMap.get("id");
                List cellsList = (List) rowMap.get("cells");
                if (cellsList == null || cellsList.isEmpty()) {
                    continue;
                }
                ScenarioData scenarioData = new ScenarioData();
                if (cellsList.size() >= 1) {
                    Map cellMap = (Map) cellsList.get(0);
                    scenarioData.testcaseId = (String) cellMap.get("value");
                }
                if (cellsList.size() >= 2) {
                    Map cellMap = (Map) cellsList.get(1);
                    scenarioData.method = (String) cellMap.get("value");
                }
                if (cellsList.size() >= 3) {
                    Map cellMap = (Map) cellsList.get(2);
                    scenarioData.apiEndpoint = (String) cellMap.get("value");
                }
                if (cellsList.size() >= 4) {
                    Map cellMap = (Map) cellsList.get(3);
                    scenarioData.statusCode = (String) cellMap.get("value");
                }
                rowIdToScenarioDataMap.put(rowId, scenarioData);
            }
        }
    }

    public void extractPickleMap(Map elementMap) {
        if (!elementMap.containsKey("pickle")) {
            return;
        }
        Map pickleMap = (Map) elementMap.get("pickle");
        String pickleId = (String) pickleMap.get("id");
        List stepsList = (List) pickleMap.get("steps");
        if (stepsList == null) {
            return;
        }
        for (Object step : stepsList) {
            if (step instanceof Map) {
                Map stepMap = (Map) step;
                List astNodeIds = (List) stepMap.get("astNodeIds");
                if (astNodeIds == null) {
                    continue;
                }
                for (Object nodeIdObj : astNodeIds) {
                    String nodeId = (String) nodeIdObj;
                    if (rowIdToScenarioDataMap.containsKey(nodeId)) {
                        pickleIdToRowIdMap.put(pickleId, nodeId);
                        break;
                    }
                }
            }
        }
    }

    public void extractTestCaseMap(Map elementMap) {
        if (!elementMap.containsKey("testCase")) {
            return;
        }
        Map testCaseMap = (Map) elementMap.get("testCase");
        String testCaseId = (String) testCaseMap.get("id");
        String pickleId = (String) testCaseMap.get("pickleId");
        testCaseIdToStatusMap.put(testCaseId, "PASSED");
        testCaseIdToPickleIdMap.put(testCaseId, pickleId);
    }

    public void markTestCaseStart(Map elementMap) {
        if (!elementMap.containsKey("testCaseStarted")) {
            return;
        }
        Map testCaseStartedMap = (Map) elementMap.get("testCaseStarted");
        String runId = (String) testCaseStartedMap.get("id");
        String testCaseId = (String) testCaseStartedMap.get("testCaseId");
        runIdToTestCaseIdMap.put(runId, testCaseId);
    }

    public void markStepStatus(Map elementMap) {
        if (!elementMap.containsKey("testStepFinished")) {
            return;
        }
        Map testStepFinishedMap = (Map) elementMap.get("testStepFinished");
        String runId = (String) testStepFinishedMap.get("testCaseStartedId");
        Map testStepResultMap = (Map) testStepFinishedMap.get("testStepResult");
        String stepStatus = (String) testStepResultMap.get("status");

        // (선택) 스텝 실행 시간 파싱
        Map durationMap = (Map) testStepResultMap.get("duration");
        long durationSeconds = 0L;
        long durationNanos = 0L;
        if (durationMap != null) {
            Object secObj = durationMap.get("seconds");
            Object nanosObj = durationMap.get("nanos");
            if (secObj instanceof Number) {
                durationSeconds = ((Number) secObj).longValue();
            }
            if (nanosObj instanceof Number) {
                durationNanos = ((Number) nanosObj).longValue();
            }
        }
        double stepTimeSeconds = durationSeconds + (durationNanos / 1_000_000_000.0);

        if (runIdToTestCaseIdMap.containsKey(runId)) {
            String testCaseId = runIdToTestCaseIdMap.get(runId);
            if (!"PASSED".equals(stepStatus)) {
                testCaseIdToStatusMap.put(testCaseId, "FAILED");
            }
            String pickleId = testCaseIdToPickleIdMap.get(testCaseId);
            if (pickleId != null) {
                String rowId = pickleIdToRowIdMap.get(pickleId);
                if (rowId != null) {
                    ScenarioData scenarioData = rowIdToScenarioDataMap.get(rowId);
                    if (scenarioData != null) {
                        scenarioData.totalDurationSeconds += stepTimeSeconds;
                    }
                }
            }
        }
    }

    public void updateFinalStatuses() {
        for (Map.Entry<String, String> entry : testCaseIdToStatusMap.entrySet()) {
            String testCaseId = entry.getKey();
            String finalStatus = entry.getValue();
            String pickleId = testCaseIdToPickleIdMap.get(testCaseId);
            if (pickleId == null) {
                continue;
            }
            String rowId = pickleIdToRowIdMap.get(pickleId);
            if (rowId == null) {
                continue;
            }
            ScenarioData scenarioData = rowIdToScenarioDataMap.get(rowId);
            if (scenarioData != null) {
                scenarioData.finalStatus = finalStatus;
            }
        }
    }

    public void printResults() {
        for (ScenarioData scenarioData : rowIdToScenarioDataMap.values()) {
            String line = scenarioData.finalStatus
                    + ", "
                    + scenarioData.testcaseId
                    + ", "
                    + scenarioData.method
                    + ", "
                    + scenarioData.apiEndpoint
                    + ", "
                    + String.format("%.3f sec", scenarioData.totalDurationSeconds);
            System.out.println(line);
        }
        printStatsToConsole();
    }

    public void exportResultsToCsv(String csvFilePath) throws IOException {
        try (FileWriter fileWriter = new FileWriter(csvFilePath)) {
            fileWriter.write("status,testcase_id,method,api_endpoint,total_duration(sec)\n");
            for (ScenarioData scenarioData : rowIdToScenarioDataMap.values()) {
                String csvLine = safeCsv(scenarioData.finalStatus)
                        + ","
                        + safeCsv(scenarioData.testcaseId)
                        + ","
                        + safeCsv(scenarioData.method)
                        + ","
                        + safeCsv(scenarioData.apiEndpoint)
                        + ","
                        + String.format("%.3f", scenarioData.totalDurationSeconds)
                        + "\n";
                fileWriter.write(csvLine);
            }
            fileWriter.write("\n[Stats]\n");
        }
        appendStatsToCsv(csvFilePath);
    }

    public void exportResultsToHtml(String htmlFilePath) throws IOException {
        StringBuilder htmlBuilder = new StringBuilder();
        htmlBuilder.append("<html>\n");
        htmlBuilder.append("<head><meta charset=\"UTF-8\"></head>\n");
        htmlBuilder.append("<body>\n");
        htmlBuilder.append("<table border=\"1\" cellspacing=\"0\" cellpadding=\"5\">\n");
        htmlBuilder.append("<tr><th>status</th><th>testcase_id</th><th>method</th><th>api_endpoint</th><th>time(sec)</th></tr>\n");
        for (ScenarioData scenarioData : rowIdToScenarioDataMap.values()) {
            htmlBuilder.append("<tr>");
            htmlBuilder.append(td(scenarioData.finalStatus));
            htmlBuilder.append(td(scenarioData.testcaseId));
            htmlBuilder.append(td(scenarioData.method));
            htmlBuilder.append(td(scenarioData.apiEndpoint));
            htmlBuilder.append(td(String.format("%.3f", scenarioData.totalDurationSeconds)));
            htmlBuilder.append("</tr>\n");
        }
        htmlBuilder.append("</table>\n");
        htmlBuilder.append("<hr/>\n");
        htmlBuilder.append("</body>\n");
        htmlBuilder.append("</html>\n");
        try (FileWriter fileWriter = new FileWriter(htmlFilePath)) {
            fileWriter.write(htmlBuilder.toString());
        }
        appendStatsToHtml(htmlFilePath);
    }

    private void printStatsToConsole() {
        StatsResult statsResult = calculateStats(rowIdToScenarioDataMap.values());
        System.out.println("[전체 통계]");
        System.out.println("전체 테스트 수: " + statsResult.totalCount
                + ", PASSED: " + statsResult.passedCount
                + ", FAILED: " + statsResult.failedCount
                + ", PASS_RATE: " + String.format("%.1f", statsResult.passRate) + "%");
        System.out.println("전체 실행 시간(초): " + String.format("%.3f", statsResult.totalTimeSec)
                + ", 평균(초/TC): " + String.format("%.3f", statsResult.avgTimePerTc));
        System.out.println("전체 API 개수: " + statsResult.totalApiCount
                + ", API 별 평균 TC 수: " + String.format("%.1f", statsResult.avgTcPerApi)
                + ", API 별 평균 Pass rate: " + String.format("%.1f", statsResult.avgApiPassRate) + "%"
                + ", API 별 평균 실행 시간: " + String.format("%.3f", statsResult.avgApiExecTime) + " sec");
        System.out.println("--- 메서드별 ---");
        for (Map.Entry<String, TimeStats> entry : statsResult.methodStatsMap.entrySet()) {
            String methodName = entry.getKey();
            TimeStats timeStats = entry.getValue();
            System.out.println("method = " + methodName
                    + ", total = " + timeStats.totalCount
                    + ", passed = " + timeStats.passedCount
                    + ", failed = " + timeStats.failedCount
                    + ", passRate = " + String.format("%.1f", timeStats.getPassRate()) + "%"
                    + ", totalTime = " + String.format("%.3f", timeStats.totalTimeSec)
                    + " sec, avgTime = " + String.format("%.3f", timeStats.avgTimeSec) + " sec/TC"
                    + ", API 개수 = " + timeStats.distinctApiCount
                    + ", API별 평균 TC 수 = " + String.format("%.1f", timeStats.avgTcPerApi)
                    + ", API별 평균 Pass rate = " + String.format("%.1f", timeStats.avgApiPassRate) + "%"
                    + ", API별 평균 실행 시간 = " + String.format("%.3f", timeStats.avgApiExecTime) + " sec");
        }
        System.out.println("--- 엔드포인트별 (method + endpoint) ---");
        for (Map.Entry<String, TimeStats> entry : statsResult.endpointStatsMap.entrySet()) {
            // key 예:  "GET /api/v1/resource"
            String combinedApiKey = entry.getKey();
            TimeStats timeStats = entry.getValue();
            System.out.println("api = " + combinedApiKey
                    + ", total = " + timeStats.totalCount
                    + ", passed = " + timeStats.passedCount
                    + ", failed = " + timeStats.failedCount
                    + ", passRate = " + String.format("%.1f", timeStats.getPassRate()) + "%"
                    + ", totalTime = " + String.format("%.3f", timeStats.totalTimeSec)
                    + " sec, avgTime = " + String.format("%.3f", timeStats.avgTimeSec) + " sec/TC");
        }
    }

    private void appendStatsToCsv(String csvFilePath) throws IOException {
        StatsResult sr = calculateStats(rowIdToScenarioDataMap.values());
        try (FileWriter fw = new FileWriter(csvFilePath, true)) {
            fw.write("Overall,"
                    + sr.totalCount
                    + ",passed="
                    + sr.passedCount
                    + ",failed="
                    + sr.failedCount
                    + ",passRate="
                    + String.format("%.1f", sr.passRate)
                    + "%,totalTime="
                    + String.format("%.3f", sr.totalTimeSec)
                    + ",avgTime="
                    + String.format("%.3f", sr.avgTimePerTc)
                    + ",totalAPI="
                    + sr.totalApiCount
                    + ",avgTcPerApi="
                    + String.format("%.1f", sr.avgTcPerApi)
                    + ",avgApiPassRate="
                    + String.format("%.1f", sr.avgApiPassRate)
                    + "%,avgApiExecTime="
                    + String.format("%.3f", sr.avgApiExecTime)
                    + "\n");
            fw.write("Method Stats:\n");
            for (Map.Entry<String, TimeStats> e : sr.methodStatsMap.entrySet()) {
                String methodName = e.getKey();
                TimeStats ts = e.getValue();
                fw.write(methodName
                        + ","
                        + ts.totalCount
                        + ",passed="
                        + ts.passedCount
                        + ",failed="
                        + ts.failedCount
                        + ",passRate="
                        + String.format("%.1f", ts.getPassRate())
                        + "%,totalTime="
                        + String.format("%.3f", ts.totalTimeSec)
                        + ",avgTime="
                        + String.format("%.3f", ts.avgTimeSec)
                        + ",APIcount="
                        + ts.distinctApiCount
                        + ",avgTcPerApi="
                        + String.format("%.1f", ts.avgTcPerApi)
                        + ",avgApiPassRate="
                        + String.format("%.1f", ts.avgApiPassRate)
                        + "%,avgApiExecTime="
                        + String.format("%.3f", ts.avgApiExecTime)
                        + "\n");
            }
            fw.write("Endpoint Stats (method + endpoint):\n");
            for (Map.Entry<String, TimeStats> e : sr.endpointStatsMap.entrySet()) {
                String combinedApiKey = e.getKey();
                TimeStats ts = e.getValue();
                fw.write(combinedApiKey
                        + ","
                        + ts.totalCount
                        + ",passed="
                        + ts.passedCount
                        + ",failed="
                        + ts.failedCount
                        + ",passRate="
                        + String.format("%.1f", ts.getPassRate())
                        + "%,totalTime="
                        + String.format("%.3f", ts.totalTimeSec)
                        + ",avgTime="
                        + String.format("%.3f", ts.avgTimeSec)
                        + "\n");
            }
        }
    }

    private void appendStatsToHtml(String htmlFilePath) throws IOException {
        StatsResult sr = calculateStats(rowIdToScenarioDataMap.values());
        String oldHtml = new String(Files.readAllBytes(Paths.get(htmlFilePath)));
        StringBuilder sb = new StringBuilder();
        sb.append("<h3>통계</h3>\n");
        sb.append("<p>전체 테스트 수: ")
                .append(sr.totalCount)
                .append(", PASSED: ")
                .append(sr.passedCount)
                .append(", FAILED: ")
                .append(sr.failedCount)
                .append(", PASS_RATE: ")
                .append(String.format("%.1f", sr.passRate))
                .append("%<br/>")
                .append("전체 시간: ")
                .append(String.format("%.3f", sr.totalTimeSec))
                .append(" sec, 평균: ")
                .append(String.format("%.3f", sr.avgTimePerTc))
                .append(" sec/TC<br/>")
                .append("전체 API 개수: ")
                .append(sr.totalApiCount)
                .append(", API별 평균TC수: ")
                .append(String.format("%.1f", sr.avgTcPerApi))
                .append(", API별 평균 PassRate: ")
                .append(String.format("%.1f", sr.avgApiPassRate))
                .append("%, API별 평균 실행시간: ")
                .append(String.format("%.3f", sr.avgApiExecTime))
                .append(" sec</p>\n");

        sb.append("<h4>메서드별 통계</h4><ul>\n");
        for (Map.Entry<String, TimeStats> e : sr.methodStatsMap.entrySet()) {
            String methodName = e.getKey();
            TimeStats ts = e.getValue();
            sb.append("<li>")
                    .append(methodName)
                    .append(": total=")
                    .append(ts.totalCount)
                    .append(", passed=")
                    .append(ts.passedCount)
                    .append(", failed=")
                    .append(ts.failedCount)
                    .append(", passRate=")
                    .append(String.format("%.1f", ts.getPassRate()))
                    .append("%, totalTime=")
                    .append(String.format("%.3f", ts.totalTimeSec))
                    .append(", avgTime=")
                    .append(String.format("%.3f", ts.avgTimeSec))
                    .append(", API개수=")
                    .append(ts.distinctApiCount)
                    .append(", API별평균TC수=")
                    .append(String.format("%.1f", ts.avgTcPerApi))
                    .append(", API별평균PassRate=")
                    .append(String.format("%.1f", ts.avgApiPassRate))
                    .append("%, API별평균실행시간=")
                    .append(String.format("%.3f", ts.avgApiExecTime))
                    .append("</li>\n");
        }
        sb.append("</ul>\n<h4>Endpoint Stats (method + endpoint)</h4><ul>\n");
        for (Map.Entry<String, TimeStats> e : sr.endpointStatsMap.entrySet()) {
            String combinedApiKey = e.getKey();
            TimeStats ts = e.getValue();
            sb.append("<li>")
                    .append(combinedApiKey)
                    .append(": total=")
                    .append(ts.totalCount)
                    .append(", passed=")
                    .append(ts.passedCount)
                    .append(", failed=")
                    .append(ts.failedCount)
                    .append(", passRate=")
                    .append(String.format("%.1f", ts.getPassRate()))
                    .append("%, totalTime=")
                    .append(String.format("%.3f", ts.totalTimeSec))
                    .append(", avgTime=")
                    .append(String.format("%.3f", ts.avgTimeSec))
                    .append("</li>\n");
        }
        sb.append("</ul>\n");
        String newHtml = oldHtml + sb.toString();
        try (FileWriter writer = new FileWriter(htmlFilePath)) {
            writer.write(newHtml);
        }
    }

    /**
     * 변경 핵심: endpointStatsMap 에 넣을 때, "method + endpoint" 를 하나의 키로 사용
     */
    private StatsResult calculateStats(Collection<ScenarioData> dataList) {
        StatsResult statsResult = new StatsResult();
        for (ScenarioData data : dataList) {
            boolean isPassed = "PASSED".equalsIgnoreCase(data.finalStatus);
            statsResult.totalCount++;
            if (isPassed) {
                statsResult.passedCount++;
            } else {
                statsResult.failedCount++;
            }
            statsResult.totalTimeSec += data.totalDurationSeconds;

            // methodKey
            String methodKey = data.method == null ? "" : data.method.toUpperCase();
            statsResult.methodStatsMap.putIfAbsent(methodKey, new TimeStats());
            TimeStats methodStats = statsResult.methodStatsMap.get(methodKey);
            methodStats.totalCount++;
            if (isPassed) {
                methodStats.passedCount++;
            } else {
                methodStats.failedCount++;
            }
            methodStats.totalTimeSec += data.totalDurationSeconds;

            // endpointKey => "METHOD + ENDPOINT"
            String combinedApiKey = methodKey + " " + (data.apiEndpoint == null ? "" : data.apiEndpoint);
            statsResult.endpointStatsMap.putIfAbsent(combinedApiKey, new TimeStats());
            TimeStats endpointStats = statsResult.endpointStatsMap.get(combinedApiKey);
            endpointStats.totalCount++;
            if (isPassed) {
                endpointStats.passedCount++;
            } else {
                endpointStats.failedCount++;
            }
            endpointStats.totalTimeSec += data.totalDurationSeconds;
        }
        if (statsResult.totalCount > 0) {
            statsResult.passRate = 100.0 * statsResult.passedCount / statsResult.totalCount;
            statsResult.avgTimePerTc = statsResult.totalTimeSec / statsResult.totalCount;
        }
        // 기존 + API(=method+endpoint) 개수
        statsResult.totalApiCount = statsResult.endpointStatsMap.size();
        if (statsResult.totalApiCount > 0) {
            statsResult.avgTcPerApi = (double) statsResult.totalCount / statsResult.totalApiCount;
            double sumApiRates = 0.0;
            double sumApiAvgTime = 0.0;
            for (TimeStats es : statsResult.endpointStatsMap.values()) {
                es.computeDerived();
                sumApiRates += es.getPassRate();
                sumApiAvgTime += es.avgTimeSec;
            }
            statsResult.avgApiPassRate = sumApiRates / statsResult.totalApiCount;
            statsResult.avgApiExecTime = sumApiAvgTime / statsResult.totalApiCount;
        }
        // 메서드별 API 개수/평균
        for (Map.Entry<String, TimeStats> methodEntry : statsResult.methodStatsMap.entrySet()) {
            String methodName = methodEntry.getKey();
            TimeStats mStats = methodEntry.getValue();
            Map<String, TimeStats> methodEndpointMap = new HashMap<>();
            // 같은 method 인 row -> 묶어서 distinct api 계산
            for (ScenarioData data : dataList) {
                String mKey = data.method == null ? "" : data.method.toUpperCase();
                if (mKey.equals(methodName)) {
                    // method + endpoint
                    String epKey = mKey + " " + (data.apiEndpoint == null ? "" : data.apiEndpoint);
                    methodEndpointMap.putIfAbsent(epKey, new TimeStats());
                    TimeStats epStats = methodEndpointMap.get(epKey);
                    epStats.totalCount++;
                    if ("PASSED".equalsIgnoreCase(data.finalStatus)) {
                        epStats.passedCount++;
                    } else {
                        epStats.failedCount++;
                    }
                    epStats.totalTimeSec += data.totalDurationSeconds;
                }
            }
            mStats.distinctApiCount = methodEndpointMap.size();
            if (mStats.distinctApiCount > 0) {
                mStats.avgTcPerApi = (double) mStats.totalCount / mStats.distinctApiCount;
                double sumRates = 0.0;
                double sumEpAvgTime = 0.0;
                for (TimeStats ep : methodEndpointMap.values()) {
                    ep.computeDerived();
                    sumRates += ep.getPassRate();
                    sumEpAvgTime += ep.avgTimeSec;
                }
                mStats.avgApiPassRate = sumRates / mStats.distinctApiCount;
                mStats.avgApiExecTime = sumEpAvgTime / mStats.distinctApiCount;
            }
            mStats.computeDerived();
        }
        return statsResult;
    }

    private String safeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"")) {
            value = "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String td(String cellValue) {
        if (cellValue == null) {
            cellValue = "";
        }
        return "<td>" + cellValue + "</td>";
    }

    // 시나리오 데이터
    static class ScenarioData {
        public String testcaseId;
        public String method;
        public String apiEndpoint;
        public String statusCode;
        public String finalStatus = "PASSED";
        public double totalDurationSeconds = 0.0;
    }

    // 최종 통계 결과
    static class StatsResult {
        public int totalCount;
        public int passedCount;
        public int failedCount;
        public double passRate;
        public double totalTimeSec;
        public double avgTimePerTc;

        public int totalApiCount;
        public double avgTcPerApi;
        public double avgApiPassRate;
        public double avgApiExecTime;

        public Map<String, TimeStats> methodStatsMap = new HashMap<>();
        // 주의: 이제 여기서는 "METHOD + ENDPOINT"를 key로 사용!
        public Map<String, TimeStats> endpointStatsMap = new HashMap<>();
    }

    // 메서드별 or API별 통계
    static class TimeStats {
        public int totalCount;
        public int passedCount;
        public int failedCount;
        public double totalTimeSec;
        public double avgTimeSec;

        // 메서드별 API 통계 추가 필드
        public int distinctApiCount;
        public double avgTcPerApi;
        public double avgApiPassRate;
        public double avgApiExecTime;

        public void computeDerived() {
            if (totalCount > 0) {
                avgTimeSec = totalTimeSec / totalCount;
            }
        }

        public double getPassRate() {
            if (totalCount == 0) {
                return 0.0;
            }
            return 100.0 * passedCount / totalCount;
        }
    }

    // 필요 시 테스트에서 접근할 getter
    public Map<String, String> getScenarioStatus() {
        return testCaseIdToStatusMap;
    }
    public Map<String, ScenarioData> getRowIdToScenarioData() {
        return rowIdToScenarioDataMap;
    }
    public Map<String, String> getPickleIdToRowId() {
        return pickleIdToRowIdMap;
    }
    public Map<String, String> getTestCaseIdToPickleId() {
        return testCaseIdToPickleIdMap;
    }
    public Map<String, String> getRunIdToTestCaseId() {
        return runIdToTestCaseIdMap;
    }
}

