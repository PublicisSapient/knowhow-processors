package com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.parser;

import org.springframework.stereotype.Component;

@Component
public class KpiParserStrategy {

    private final LineGraphParser lineGraphParser;
    private final LineFilterGraphParser lineFilterGraphParser;
    private final LineRadioFilterGraphParser lineRadioFilterGraphParser;
    private final LineMultiFilterParser lineMultiFilterParser;

    public KpiParserStrategy(LineGraphParser lineGraphParser,
                             LineFilterGraphParser lineFilterGraphParser,
                             LineRadioFilterGraphParser lineRadioFilterGraphParser, LineMultiFilterParser lineMultiFilterParser) {
        this.lineGraphParser = lineGraphParser;
        this.lineFilterGraphParser = lineFilterGraphParser;
        this.lineRadioFilterGraphParser = lineRadioFilterGraphParser;
        this.lineMultiFilterParser = lineMultiFilterParser;
    }

    public KpiDataCountParser getParser(String kpiFilter) {
        if (kpiFilter == null || kpiFilter.isEmpty()) {
            return lineGraphParser;
        }
        
        return switch (kpiFilter.toLowerCase()) {
            case "dropdown", "multiselectdropdown" -> lineFilterGraphParser;
            case "radiobutton" -> lineRadioFilterGraphParser;
            case "multitypefilters" -> lineMultiFilterParser;
            default -> lineGraphParser;
        };
    }
}