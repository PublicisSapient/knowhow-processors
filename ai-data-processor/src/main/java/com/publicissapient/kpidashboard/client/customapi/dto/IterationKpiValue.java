package com.publicissapient.kpidashboard.client.customapi.dto;

import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class IterationKpiValue implements Serializable {
    private static final long serialVersionUID = 1L;

    private String filter1;
    private String filter2;
    private List<DataCountGroup> dataGroup;

}
