package com.publicissapient.kpidashboard.job.kipbenchmarkcalculation.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;

class KpiParserStrategyTest {

    @Mock
    private LineGraphParser lineGraphParser;
    @Mock
    private LineFilterGraphParser lineFilterGraphParser;
    @Mock
    private LineRadioFilterGraphParser lineRadioFilterGraphParser;
    @Mock
    private LineMultiFilterParser lineMultiFilterParser;

    private KpiParserStrategy strategy;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        strategy = new KpiParserStrategy(lineGraphParser, lineFilterGraphParser, 
                                       lineRadioFilterGraphParser, lineMultiFilterParser);
    }

    @Test
    void testGetParser_WithNullFilter() {
        KpiDataCountParser parser = strategy.getParser(null);
        assertEquals(lineGraphParser, parser);
    }

    @Test
    void testGetParser_WithEmptyFilter() {
        KpiDataCountParser parser = strategy.getParser("");
        assertEquals(lineGraphParser, parser);
    }

    @Test
    void testGetParser_WithDropdownFilter() {
        KpiDataCountParser parser = strategy.getParser("dropdown");
        assertEquals(lineFilterGraphParser, parser);
    }

    @Test
    void testGetParser_WithMultiselectdropdownFilter() {
        KpiDataCountParser parser = strategy.getParser("multiselectdropdown");
        assertEquals(lineFilterGraphParser, parser);
    }

    @Test
    void testGetParser_WithRadiobuttonFilter() {
        KpiDataCountParser parser = strategy.getParser("radiobutton");
        assertEquals(lineRadioFilterGraphParser, parser);
    }

    @Test
    void testGetParser_WithMultitypefiltersFilter() {
        KpiDataCountParser parser = strategy.getParser("multitypefilters");
        assertEquals(lineMultiFilterParser, parser);
    }

    @Test
    void testGetParser_WithUnknownFilter() {
        KpiDataCountParser parser = strategy.getParser("unknown");
        assertEquals(lineGraphParser, parser);
    }

    @Test
    void testGetParser_WithCaseInsensitive() {
        KpiDataCountParser parser1 = strategy.getParser("DROPDOWN");
        KpiDataCountParser parser2 = strategy.getParser("RadioButton");
        KpiDataCountParser parser3 = strategy.getParser("MULTITYPEFILTERS");
        
        assertEquals(lineFilterGraphParser, parser1);
        assertEquals(lineRadioFilterGraphParser, parser2);
        assertEquals(lineMultiFilterParser, parser3);
    }
}